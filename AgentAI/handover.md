# Agent Handover

> You are an AI agent starting a new session. Read this file first, then `memory.md`.

---

## Project Overview

ACC (Agent Control Center) — a local daemon + web dashboard that orchestrates,
monitors and gates headless Claude Code agents. Java 21 / Spring Boot backend,
React + Vite frontend, SQLite history. See `README.md` for the full picture.

Layout:
- `backend/`  — Spring Boot daemon (`eu.nexuslayer.acc`)
- `frontend/` — React dashboard, builds into `backend/src/main/resources/static`
- `acc`       — control script (build/start/attach/run)
- `AgentAI/`  — this knowledge base

## Current State

See `state.md`. Everything specified in the original three markdown docs is built
and verified working against Claude Code 2.1.232.

## What To Do Next

1. `./acc build && ./acc start`, open http://127.0.0.1:4000.
2. `./acc attach` — required after upgrading, to pick up the SessionEnd hook.
3. Highest-value work, in order:
   - **Tests for the lifecycle**: TranscriptReader, AdoptionService, SessionJanitor
     are all verified by observation only. Today's eight fixes have nothing
     guarding them against regression.
   - **Run the Windows and Linux bundles on their own OS.** Both are structurally
     verified and have never been executed there.
   - JVM tuning: ~150 MB resident is more than this daemon needs.

## Key Files & Directories

| Path | Why it matters |
|------|----------------|
| `runner/StreamJsonParser.java` | Turns Claude Code's JSON stream into tree nodes |
| `runner/StartSessionRequest.java` | The auto-approve rule — the core product decision |
| `approval/ApprovalService.java` | The gate: blocking, de-duplication, timeouts |
| `approval/RequestKey.java` | Why one click satisfies Claude Code's hook retries |
| `hooks/HookInstaller.java` | Generates the bridge script, merges settings.json safely |
| `runner/ToolSummary.java` | One-line labels + risk banding shown everywhere |
| `frontend/src/components/FlowTree.jsx` | The primary view |
| `frontend/src/lib/useTimeline.js` | Reference-stable selector — read the note before touching |
| `frontend/src/index.css` | The whole visual system (Mission Control) |

## Conventions

- Java records, immutable models, `with*` helpers for state changes.
- Comments explain *why*, never *what*.
- Every behavioural rule in `memory.md` has a test; keep it that way.
