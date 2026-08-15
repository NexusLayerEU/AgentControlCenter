# Product Specification: Agent Control Center (ACC)

## 1. Overview
The Agent Control Center (ACC) is a local daemon and web-based dashboard designed to orchestrate, monitor, and control headless CLI coding agents (like Claude Code and Antigravity). It solves the friction of managing multiple concurrent terminal sessions, blind destructive edits, and lack of visual diffing by bringing terminal outputs into a structured, visual interface.

## 2. Core Value Proposition
* **Centralized Dashboard:** Monitor multiple agent sessions from a single unified view.
* **Safety & Guardrails:** Intercept tools and shell commands before they execute.
* **Visual Review:** Replace raw terminal patches with side-by-side visual diffs.
* **Extensibility:** Standardized hooks to support various agent protocols.

## 3. Core Features (MVP)
* **Session Manager:** List all active, backgrounded, and completed agent sessions.
* **Live PTY Streaming:** Render standard CLI ANSI output in the browser.
* **Structured State View:** Parse agent outputs into discrete UI cards (Plans, Tool Calls, Diffs).
* **Approval Gate:** Intercept CLI prompts that require human approval (e.g., file writes, destructive bash commands) and present an Approve/Deny/Edit UI.
* **Lifecycle Hooks:** Integrate with agent hooks (e.g., `PreToolUse`, `PostToolUse`, `Stop`) to enforce local project rules automatically.

## 4. User Workflows
1. **Dispatch:** Developer runs `acc start my-agent "refactor auth logic"`. The agent starts in the background.
2. **Monitor:** Developer opens `localhost:4000` to see the agent's step-by-step plan.
3. **Approve:** Agent attempts to run a database migration. The web UI pauses the agent and shows a pending approval card. Developer clicks "Approve."
4. **Review:** Agent generates code. The web UI renders a side-by-side diff. Developer requests a tweak directly from the UI before the file is saved.