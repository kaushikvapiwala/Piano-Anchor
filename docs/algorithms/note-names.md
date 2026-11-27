# Note names on keys

Beginners match the screen to the piano by letter, but the letters should not become a crutch. `NoteNameRule`
(`engine/src/commonMain/kotlin/pianoanchor/engine/lesson/NoteNameRule.kt`) decides which keys of a strip carry one; the
strips only draw what it returns.

## Stages

The stage follows the course concept "C D E, then F G A B" (the white-key names skill) and `CourseProgress`:

- **All white**: the concept is not passed yet, so every white key is lettered.
- **Target only**: it is passed but not yet learned (a later unaided review), so only the key or keys to play keep
  their letter, as a reminder rather than a map.
- **None**: it is learned, so the letters are gone.

The setting `Settings.noteNames` can be Off, which is stage None and also stops hint letters. Passed and learned come
from the same `CourseProgress.passed` and `learned` the Today and Songs screens use; `NoteLabelsUseCase` loads them
(plus the setting) for each screen.

## Black keys

Black keys stay blank until the "Sharps and flats" concept is passed, wherever letters show. After that they show the
sharp name, with the flat name under it when the key is wide enough.

## Wrong-key hint

A wrong-key hint always letters the pressed key and the right key, at any stage (but not with the setting Off, and not
on a black key before sharps are passed), and only while the hint is live: a stale wrong key letters nothing. A
perform take shows no hint, so no letters.

## Quizzes

A quiz asks for a key by name ("Play the E"), so its strip never shows stage letters: it keeps only the wrong-key
hint letters.

## Storage failure

If the setting, attempts or reviews can't be read, the screen falls back to the beginner view (every white key
lettered) rather than hiding the help.

## Not covered

The Ear echo card and the onboarding echo always name their five keys (no black keys, so the name is the only way to tell
the keys apart), and the card animations' keyboard has its own rules.
