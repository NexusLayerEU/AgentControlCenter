# Agent Implementation Plan

**Agent Directive:** Read the `01_product_spec.md` and `02_system_architecture.md` files. Build this project iteratively in the following phases. 

## Phase 1: Foundation (Backend)
1. Initialize a new Java project using Maven/Gradle.
2. Set up a lightweight web server.
3. Implement a basic Process Builder module capable of launching a background shell process.
4. Set up an embedded SQLite database with schema for `sessions` and `transcripts`.

## Phase 2: Foundation (Frontend)
1. Initialize the React JS frontend using Vite.
2. Set up basic routing and Tailwind CSS.
3. Build the primary layout: Sidebar (Session List) and Main Content Area.
4. Implement `xterm.js` in a React component to prepare for terminal rendering.

## Phase 3: Connectivity
1. Implement a WebSocket endpoint in the Java backend.
2. Pipe the `stdout` and `stderr` of the spawned shell processes into the WebSocket.
3. Connect the React `xterm.js` component to the WebSocket to render the output in real-time.
4. Allow `stdin` from the React terminal to pipe back to the Java process manager.

## Phase 4: Structured State & Hooks
1. Create a REST controller in the Java backend to act as the target for agent lifecycle hooks (`PreToolUse`, `PostToolUse`).
2. Build the state management logic to hold hook HTTP responses open until user action is received.
3. Create React components (Cards) to display pending tool approvals.
4. Wire the frontend approval buttons to the backend to release the held hook responses.

**Execution Rules:** Write clean, modular code. Implement comprehensive unit tests for the Java backend process management. Do not proceed to the next phase until the current phase builds and runs successfully.