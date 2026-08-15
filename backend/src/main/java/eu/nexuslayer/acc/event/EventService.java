package eu.nexuslayer.acc.event;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Service;

import eu.nexuslayer.acc.model.AgentEvent;
import eu.nexuslayer.acc.model.EventType;
import eu.nexuslayer.acc.repo.EventRepository;
import eu.nexuslayer.acc.ws.Broadcaster;

/**
 * Persists activity-tree nodes and pushes each one to the dashboard. Sequence
 * numbers are held in memory per session so ordering survives the burst of
 * events a fast agent emits.
 */
@Service
public class EventService {

    private final EventRepository repository;
    private final Broadcaster broadcaster;
    private final ConcurrentHashMap<String, AtomicLong> sequences = new ConcurrentHashMap<>();

    public EventService(EventRepository repository, Broadcaster broadcaster) {
        this.repository = repository;
        this.broadcaster = broadcaster;
    }

    public AgentEvent record(AgentEvent event) {
        repository.save(event);
        broadcaster.broadcast("event", event);
        return event;
    }

    public AgentEvent record(String sessionId, EventType type, String title, String payload) {
        return record(AgentEvent.of(sessionId, nextSeq(sessionId), type, title, payload));
    }

    /** Re-saves an existing node (status/duration change) and re-broadcasts it. */
    public AgentEvent update(AgentEvent event) {
        repository.save(event);
        broadcaster.broadcast("event:update", event);
        return event;
    }

    public long nextSeq(String sessionId) {
        return sequences
                .computeIfAbsent(sessionId, id -> new AtomicLong(repository.nextSeq(id) - 1))
                .incrementAndGet();
    }

    public List<AgentEvent> timeline(String sessionId) {
        return repository.findBySession(sessionId);
    }

    public void forget(String sessionId) {
        sequences.remove(sessionId);
    }
}
