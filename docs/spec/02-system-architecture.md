# System Architecture

## 1. System Overview
The ACC operates as a local ecosystem running on the developer's machine. It consists of a lightweight background daemon that manages the lifecycle of agent processes and a frontend dashboard for user interaction. 

## 2. Technology Stack
* **Backend Daemon:** Java (Spring Boot or Javalin for a fast-booting, lightweight local server).
* **Frontend Webview:** React JS (Vite, Tailwind CSS, Framer Motion for state transitions).
* **Terminal Emulation:** `xterm.js` integrated into the React frontend for raw PTY streaming.
* **Persistence:** Local SQLite database to store session history, transcripts, and telemetry.

## 3. Core Components

### 3.1. Java Local Daemon (The Orchestrator)
* **Process Manager:** Spawns agent CLIs via pseudo-terminals (PTY) so the agents behave exactly as they would in a standard terminal.
* **WebSocket Server:** Streams PTY output (`stdout`/`stderr`) to the React frontend and receives user input (`stdin`).
* **API Gateway:** Provides REST endpoints for the React frontend to fetch session lists, historical transcripts, and configuration states.
* **Protocol Adapter:** Implements the Agent Client Protocol (ACP)—a JSON-RPC standard—to parse structured states like tool calls and diffs directly from the agent.

### 3.2. React JS Frontend (The Dashboard)
* **Session List View:** A unified table displaying all active and historical sessions, fetching state from the Java REST API.
* **Terminal Pane:** An `xterm.js` component connected to the WebSocket for raw CLI monitoring.
* **Structured UI View:** React components that parse ACP JSON-RPC payloads to render interactive cards for current agent tasks and pending approvals.

### 3.3. Interception & Hooks Engine
* **Hook Server:** A local HTTP endpoint within the Java daemon that receives webhooks from the agents (e.g., Claude Code's `PreToolUse` or `Stop` events). 
* When a dangerous command is intercepted, the Java daemon holds the HTTP response, pushes a notification to the React UI via WebSocket, and waits for the developer's approval before returning a signal to the agent.

## 4. Data Flow (Tool Approval)
1. Agent decides to execute a bash script.
2. Agent fires a `PreToolUse` hook to the Java Daemon.
3. Java Daemon pauses the agent request and pushes an "Approval Required" WebSocket event to the React UI.
4. React UI renders the command.
5. Developer clicks "Approve".
6. React UI sends an approval REST call to the Java Daemon.
7. Java Daemon completes the `PreToolUse` hook response, allowing the agent to proceed.