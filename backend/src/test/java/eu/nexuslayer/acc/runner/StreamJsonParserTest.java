package eu.nexuslayer.acc.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import eu.nexuslayer.acc.event.EventService;
import eu.nexuslayer.acc.model.AgentEvent;
import eu.nexuslayer.acc.model.EventType;
import eu.nexuslayer.acc.repo.EventRepository;
import eu.nexuslayer.acc.util.Json;
import eu.nexuslayer.acc.ws.Broadcaster;

class StreamJsonParserTest {

    private static final String SESSION = "sess-1";

    private List<AgentEvent> recorded;
    private List<AgentEvent> updated;
    private EventService events;
    private AtomicReference<String> claudeSessionId;
    private AtomicReference<JsonNode> result;

    /** Captures events in memory instead of touching SQLite. */
    private static class CapturingEventService extends EventService {
        final List<AgentEvent> recorded = new ArrayList<>();
        final List<AgentEvent> updated = new ArrayList<>();
        private final AtomicLong seq = new AtomicLong();

        CapturingEventService() {
            super(null, null);
        }

        @Override
        public AgentEvent record(AgentEvent event) {
            recorded.add(event);
            return event;
        }

        @Override
        public AgentEvent record(String sessionId, EventType type, String title, String payload) {
            return record(AgentEvent.of(sessionId, seq.incrementAndGet(), type, title, payload));
        }

        @Override
        public AgentEvent update(AgentEvent event) {
            updated.add(event);
            return event;
        }

        @Override
        public long nextSeq(String sessionId) {
            return seq.incrementAndGet();
        }
    }

    private CapturingEventService capturing;

    @BeforeEach
    void setUp() {
        capturing = new CapturingEventService();
        events = capturing;
        recorded = capturing.recorded;
        updated = capturing.updated;
        claudeSessionId = new AtomicReference<>();
        result = new AtomicReference<>();
    }

    private StreamJsonParser parser() {
        return new StreamJsonParser(SESSION, "/work", events,
                init -> claudeSessionId.set(Json.text(init, "session_id")),
                result::set);
    }

    @Test
    @DisplayName("the init frame surfaces the Claude session id used to match hooks")
    void capturesSessionId() {
        parser().accept("""
                {"type":"system","subtype":"init","session_id":"abc-123","model":"claude-opus-5","cwd":"/tmp"}
                """);
        assertEquals("abc-123", claudeSessionId.get());
        assertEquals(EventType.SESSION_START, recorded.get(0).type());
    }

    @Test
    @DisplayName("assistant text and thinking become separate nodes")
    void splitsAssistantBlocks() {
        parser().accept("""
                {"type":"assistant","message":{"content":[
                  {"type":"thinking","thinking":"let me look"},
                  {"type":"text","text":"I will read the file"}]}}
                """);
        assertEquals(2, recorded.size());
        assertEquals(EventType.THINKING, recorded.get(0).type());
        assertEquals(EventType.ASSISTANT_TEXT, recorded.get(1).type());
        assertEquals("I will read the file", recorded.get(1).title());
    }

    @Test
    @DisplayName("a tool result is parented to the call that produced it")
    void linksResultToCall() {
        StreamJsonParser parser = parser();
        parser.accept("""
                {"type":"assistant","message":{"content":[
                  {"type":"tool_use","id":"tu_1","name":"Bash","input":{"command":"echo hi"}}]}}
                """);
        parser.accept("""
                {"type":"user","message":{"content":[
                  {"type":"tool_result","tool_use_id":"tu_1","content":"hi"}]}}
                """);

        AgentEvent call = recorded.get(0);
        AgentEvent toolResult = recorded.get(1);

        assertEquals(EventType.TOOL_CALL, call.type());
        assertEquals("Bash", call.toolName());
        assertEquals("echo hi", call.title());
        assertEquals("running", call.status());

        assertEquals(EventType.TOOL_RESULT, toolResult.type());
        assertEquals(call.id(), toolResult.parentId());

        // The original call node is re-emitted with its final status.
        assertEquals(1, updated.size());
        assertEquals("ok", updated.get(0).status());
        assertNotNull(updated.get(0).durationMs());
    }

    @Test
    @DisplayName("an errored tool result marks the call as failed")
    void marksErroredCalls() {
        StreamJsonParser parser = parser();
        parser.accept("""
                {"type":"assistant","message":{"content":[
                  {"type":"tool_use","id":"tu_9","name":"Read","input":{"file_path":"/nope"}}]}}
                """);
        parser.accept("""
                {"type":"user","message":{"content":[
                  {"type":"tool_result","tool_use_id":"tu_9","is_error":true,"content":"no such file"}]}}
                """);

        assertEquals("error", updated.get(0).status());
        assertTrue(recorded.get(1).payload().contains("\"isError\":true"));
    }

    @Test
    @DisplayName("array-shaped tool results are flattened to text")
    void flattensArrayResults() {
        StreamJsonParser parser = parser();
        parser.accept("""
                {"type":"assistant","message":{"content":[
                  {"type":"tool_use","id":"tu_2","name":"Grep","input":{"pattern":"x"}}]}}
                """);
        parser.accept("""
                {"type":"user","message":{"content":[
                  {"type":"tool_result","tool_use_id":"tu_2","content":[
                    {"type":"text","text":"line one"},{"type":"text","text":"line two"}]}]}}
                """);

        assertTrue(recorded.get(1).payload().contains("line one"));
        assertTrue(recorded.get(1).payload().contains("line two"));
    }

    @Test
    @DisplayName("a result frame is handed to the completion callback")
    void reportsResult() {
        parser().accept("""
                {"type":"result","subtype":"success","is_error":false,"result":"done",
                 "total_cost_usd":0.0421,"num_turns":6,"duration_ms":12345}
                """);
        assertNotNull(result.get());
        assertEquals("done", Json.text(result.get(), "result"));
        assertEquals(6, result.get().get("num_turns").asInt());
    }

    @Test
    @DisplayName("non-JSON and blank lines are ignored rather than throwing")
    void ignoresNoise() {
        StreamJsonParser parser = parser();
        parser.accept("");
        parser.accept("   ");
        parser.accept("some stray stderr text");
        parser.accept("{not valid json");
        parser.accept(null);
        assertTrue(recorded.isEmpty());
    }

    @Test
    @DisplayName("an orphan tool result without a matching call is still recorded")
    void handlesOrphanResults() {
        parser().accept("""
                {"type":"user","message":{"content":[
                  {"type":"tool_result","tool_use_id":"unknown","content":"stray"}]}}
                """);
        assertEquals(1, recorded.size());
        assertNull(recorded.get(0).parentId());
    }

    /** Guards against the broadcaster/repository being touched in this test path. */
    @SuppressWarnings("unused")
    private static final Broadcaster UNUSED_BROADCASTER = (channel, payload) -> {
        throw new AssertionError("broadcast should not happen in parser tests");
    };

    @SuppressWarnings("unused")
    private static final Class<EventRepository> UNUSED_REPO = EventRepository.class;
}
