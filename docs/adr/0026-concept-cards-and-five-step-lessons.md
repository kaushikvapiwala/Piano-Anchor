# 0026. Concept cards and five-step lessons

- Status: accepted
- Date: 2026-11-15

## Context
On the owner's hardware check, the units drilled the learner without teaching. No lesson said what a concept is or
why it matters. After the first timed run the owner couldn't tell what they had played, and Results showed bare
numbers. The course is for adults with no background (`docs/course/beginner-course.md`).

## Decision
- **Concept cards in content:** every concept carries four required one-line fields: what, why it exists, how it's
  used, and when you meet it. They are typed Kotlin next to the curriculum (ADR 0023), with UI strings in Compose
  resources. A content test fails on an empty field.
- **Five-step lessons:** Why (card) → See (the app plays, keys light up) → Try (one action checked by the piano) →
  Use (a tiny piece) → Recap. Every passage is played once before the learner tries it, and they can skip that.
- **Nothing is locked:** skill prerequisites stay, but they only choose the suggested "Next" lesson and explain what a
  lesson builds on. Every lesson can be opened at any time. A lesson counts as passed when its Try has the right
  notes in wait mode; timing never blocks a lesson.
- **Animations are drawn in Compose,** not shipped as video: small, offline, themed, translatable and accessible.
- **Results speak in words:** what happened plus one tip, with the numbers behind "Details".
- **The order comes from the course document's map:** keys before the staff, the beat from day 1, landmark and
  distance reading, and sharps and flats last.

Rejected:
- Gating lessons on mastery: the owner wants an open course; review polishes skills instead.
- Video lessons: they would make the APK much bigger and are hard to change.
- Free black-key play as the first sound: the owner chose middle C.
- Patching the current units without a new map: the order itself was part of the problem.

## Consequences
The content model gains concept cards and lesson steps, the practice flow gains See, Try and Recap, and the skill
graph's `unlocked` becomes a suggestion. Existing passages are moved into the new map. The work is split into ROADMAP
items, each needing a spec first.
