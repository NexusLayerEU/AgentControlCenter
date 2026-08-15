package eu.nexuslayer.acc.runner;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Turns a raw tool_use input blob into the one-line label the tree view shows.
 * Keeping this in one place means the UI never has to know tool-specific shapes.
 */
public final class ToolSummary {

    private ToolSummary() {
    }

    public static String describe(String toolName, JsonNode input) {
        return describe(toolName, input, null);
    }

    /**
     * @param workingDir the session's cwd; paths under it are shown relative to
     *                   it, because an absolute path repeated on every row buries
     *                   the part the reader actually needs.
     */
    public static String describe(String toolName, JsonNode input, String workingDir) {
        if (toolName == null) {
            return "tool";
        }
        if (input == null || input.isNull()) {
            return toolName;
        }
        return switch (toolName) {
            case "Bash" -> truncate(relative(text(input, "command"), workingDir), 120);
            case "Read" -> relative(text(input, "file_path"), workingDir);
            case "Write" -> relative(text(input, "file_path"), workingDir);
            case "Edit" -> relative(text(input, "file_path"), workingDir);
            case "NotebookEdit" -> relative(text(input, "notebook_path"), workingDir);
            case "Glob" -> text(input, "pattern");
            case "Grep" -> text(input, "pattern");
            case "WebFetch" -> text(input, "url");
            case "WebSearch" -> text(input, "query");
            case "Task", "Agent" -> text(input, "description");
            case "TodoWrite" -> "update todo list";
            case "Skill" -> text(input, "skill");
            default -> firstStringValue(input);
        };
    }

    /** Coarse risk banding used to colour approval cards and tree nodes. */
    public static String risk(String toolName, JsonNode input) {
        if (toolName == null) {
            return "normal";
        }
        if ("Bash".equals(toolName)) {
            String cmd = text(input, "command");
            if (cmd == null) {
                return "elevated";
            }
            String lower = cmd.toLowerCase();
            boolean destructive = lower.contains("rm -rf")
                    || lower.contains("mkfs")
                    || lower.contains("dd if=")
                    || lower.contains("wipefs")
                    || lower.contains("shred ")
                    || lower.contains("drop table")
                    || lower.contains("drop database")
                    || lower.contains("git push --force")
                    || lower.contains("truncate ");
            return destructive ? "destructive" : "elevated";
        }
        return switch (toolName) {
            case "Write", "Edit", "NotebookEdit" -> "elevated";
            case "Read", "Glob", "Grep", "TodoWrite", "WebSearch", "WebFetch" -> "safe";
            default -> "normal";
        };
    }

    private static String firstStringValue(JsonNode input) {
        var fields = input.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            if (entry.getValue().isTextual()) {
                return truncate(entry.getValue().asText(), 100);
            }
        }
        return "";
    }

    private static String text(JsonNode node, String field) {
        JsonNode child = node.get(field);
        return child == null || child.isNull() ? null : child.asText();
    }

    /**
     * Rewrites absolute paths for display: anything inside the session's working
     * directory becomes relative to it, anything else under $HOME becomes {@code ~/…}.
     * Applied to whole shell commands too, so a long invocation stays scannable.
     */
    static String relative(String value, String workingDir) {
        if (value == null) {
            return "";
        }
        String result = value;
        if (workingDir != null && !workingDir.isBlank()) {
            String base = workingDir.endsWith("/")
                    ? workingDir.substring(0, workingDir.length() - 1)
                    : workingDir;
            // Children of the directory lose the prefix entirely; a reference to
            // the directory itself becomes "." so it stays visible as a path.
            result = result.replace(base + "/", "");
            result = result.replace(base, ".");
        }
        String home = System.getProperty("user.home");
        if (home != null && !home.isBlank()) {
            result = result.replace(home + "/", "~/");
        }
        return result;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        String single = value.replaceAll("\\s+", " ").trim();
        return single.length() <= max ? single : single.substring(0, max - 1) + "…";
    }
}
