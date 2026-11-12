# 0010. Own Compose notation renderer

- Status: accepted
- Date: 2026-11-11

## Context
The practice screen needs per-note colouring, fades and low latency. Engraving libraries are web-based or
LGPL native code (awkward for static linking on iOS later).

## Decision
Render notation in Compose with the Bravura (SMuFL, OFL) font, starting with the beginner subset. Parse
MusicXML into our own model. Verovio is not used.

## Consequences
Full control and shared rendering on every target; engraving coverage grows with the curriculum.
