# 0009. Local-first data, optional Google Drive sync later

- Status: accepted
- Date: 2026-11-11

## Context
Learner data is personal. There is no server and no account requirement.

## Decision
The local database is the source of truth, with export/import files and Android backup. A `SyncPort` is
defined early; a Google Drive app-data adapter comes after the core works. Sign-in stays optional.

## Consequences
No analytics or telemetry. Cross-device sync waits for the Drive adapter.
