# Living Studio (UI essentials)

The app's look, from the owner's Living Studio mock. Code: `PianoAnchorTheme` and `PianoAnchorColors` in
`ui/shell/src/commonMain/kotlin/pianoanchor/ui/shell/PianoAnchorTheme.kt`.

- **Material 3**, light and dark, following the system setting. Every screen is checked in both (screenshots).
- **Palette** (the studio teal of the first screens is gone):

  | Role | Light | Dark |
  |---|---|---|
  | Primary (plum) | `#7A3E6B`, white text | `#E3A6D0`, text `#3B1834` |
  | Background, surface | `#FBFAF8` | `#1B1B1F` |
  | Text | `#1B1B1F` | `#F7F5F2` |
  | Secondary text | `#5E5A66` | `#CFC8D3` |
  | Dividers / chip borders | `#EEE9EF` / `#D9CFDC` | `#3F3B45` / `#5E5A66` |

- **Feedback tokens** (`MaterialTheme.pianoAnchorColors`): correct `#1F5F8B` (dark `#8CC4E8`), waiting `#E69F00`
  (dark `#F2B84B`), wrong key `#B86FA3` (dark `#E3A6D0`), timing `#D55E00` (dark `#F08A4B`, our choice: the mock
  has none; the hollow mark of a timing-off note). Staff lines `#3F3B45` (dark `#CFC8D3`), upcoming notes `#1B1B1F`
  (dark `#F7F5F2`), target-key band `#FBE3B0`. Ear card accent `#8A5300` with white text (dark `#FFC857` with `#1B1B1F`).
- **Contrast** (WCAG, against the background): all text ≥ 4.5:1 in both themes; dark marks all ≥ 8:1. In light, the
  waiting amber `#E69F00` is 2.2:1, under the 3:1 for marks, and the target band is decorative (1.3:1 on a white
  key). Kept as in the mock because the waiting note is never told by colour alone (the cursor stands beside it,
  the legend names it and TalkBack reads "Waiting for D4"); a darker amber (e.g. `#A86F00`, 4.1:1) is the fix if
  the owner wants one.
- **Fonts**, bundled (SIL OFL 1.1, licences in `ui/shell/src/commonMain/composeResources/files/licenses/`): Inter
  400/500/600 for body and labels, Space Grotesk 500/700 for titles and headlines. Both are variable fonts; one file
  each serves every weight.
- **Errors** sit in an `errorContainer` card at the top of the screen, with the action that fixes them (open
  settings, turn on Bluetooth, back to pianos) and Dismiss where it applies; buttons use the card's content colour.
  A piano the monitor couldn't store shows as usual under the card "Couldn't save this piano. It will be forgotten
  when the app closes." (Dismiss only; the next connect tries again).
- **Live MIDI** (note names, times) in the monitor log is set in a monospace font so columns line up.
- **Text** comes from Compose resources (`composeResources/values/strings.xml`), never hard-coded. Every control
  has a label TalkBack can read; rows that act on a device name the device in their content description.
- No infinite animations on screens with screenshots (a spinner never lets the test go idle): show progress as text.
- **Screen on**: the phone sits on the music rest throughout, so the app keeps the screen on while it is in the
  foreground (`MainActivity` sets the window's keep-screen-on flag; the emulator end-to-end test checks it).

## Practice screen (wait mode)

Landscape: the phone sits on the music rest (`app-android` forces landscape while it is open). Reference layout
915×412 dp; screenshots render at that size.

- **Top bar** 52 dp with a divider: back (44 dp), title (Space Grotesk 700, 16 sp) and "Wait mode" (Inter 11 sp),
  the tempo ladder (30 dp chips, radius 15, 1.5 dp border; the selected rung in plum, see Tempo runs) and
  the "End run" pill (36 dp), which discards the run.
- **Staff** 196 dp: five 1.5 dp lines 16 dp apart, treble clef, then the time signature (two numbers stacked,
  Space Grotesk 700 26 sp, ink). One notehead per step (19×14 dp ellipse at −18°, chords stacked), ledger lines
  below and above the staff. Done notes blue, the waiting note amber, upcoming notes ink; a written length of a half
  note or more is hollow (a 2.5 dp ring in the same colour), shorter notes filled. A 3×110 dp plum cursor (85 %)
  left of the waiting note; legend "correct" and "waiting for you" top right. No stems, rests or timing marks yet.
- **Spacing** (`StaffLayout.kt`): x is linear in ticks from just after the time signature to the final bar, which
  sits at the last note's end. Every gap between steps (and the last note's gap to the final bar) is at least 28 dp;
  the other gaps share the rest in proportion. If even 28 dp gaps don't fit (about 28 steps), every gap gets the
  same width; long passages wait for scrolling.
- **Bar lines**: 1.5 dp at each bar boundary inside the passage (a bar is beats × 4 / beat unit quarters, so 6/8 is
  three quarters); one where a step starts sits before it by 30 dp or half the gap to the step before, whichever is
  less; one inside a held note sits at its tick. The final double bar is 1.5 dp + 3 dp, 3 dp apart. At the 28 dp
  minimum a sharp can touch a bar line or the note before it, and ledger lines can join; built-in passages are
  far wider than that.
- **Key strip**: "Live keys · play the piano" and the last key's verdict ("E4 · correct, moving on",
  "F4 · waiting for E4"), then two octaves from the C at or below the passage's lowest note. White keys 60 dp on a
  62 dp pitch, black keys 34×46 dp; the waiting step's keys carry an 18 dp amber band; held and flashing keys turn
  correct or wrong with a 150 ms colour change; the flash lasts `KEY_FLASH_MS`. All Notes Off and All Sound Off clear every held and wrong key.
- **Accessibility**: the staff reads as "Treble staff, 4/4. Notes C4, D4, E4. Waiting for D4." (or "… Done."); each key is named; the
  verdict is a polite live region announcing "Correct" or "Wrong, F4".
- **Finishing**: nothing new shows after the last note. Results open once the last notes stop sounding (keys up, pedal
  up), at most 3 s after it (`WAIT_MODE_RELEASE_TIMEOUT_MS`); a new key press ends the wait at once.
- **Disconnected**: the error card "Piano disconnected" with "Back to pianos"; the run is discarded.
- **Save failed**: the error card "Couldn't save this run." with "Retry" (saves the same run again) and "Discard"
  (drops it and starts a fresh run on the same piano).

### Tempo runs

- **Tempo ladder**: every chip selects its rung (selecting ends and discards the run). Selected is plum; a rung with a
  clean timed attempt at its tempo is drawn blue and read as "60%, done". Chips keep their 30 dp look inside a 48 dp
  touch target. The subtitle reads "Tempo run, 56 BPM".
- **Key hints** (`docs/algorithms/guidance.md`): the amber band on the waiting step's keys fades per passage through
  three levels. Full shows it at once; Delayed shows it only after the learner's usual pause (counted from the
  previous step, or from the written onset in a timed run); Off shows nothing. The band is per step: the next step
  starts without it again at Delayed and Off.
- **Mode switch**: a two-segment "Practice | Perform" control right of the tempo ladder. Perform is disabled on Wait
  (its description reads "Pick a tempo to perform"), and picking Wait switches to Practice; changing mode discards a
  run in progress. In Perform the band, Hint button, the waiting note's amber, blue and orange note colours, timing tags, the legend and the key
  strip's verdict text are hidden; the staff, cursor, count-in and click stay and pressed keys light in a neutral
  colour. Results label the run "Performed" beside "run N". Today's cold open card reads "Perform cold open" at a
  timed rung.
- **Spot loop** (`docs/algorithms/guidance.md`): Results show an outlined "Loop bars 5–6" ("Loop bar 3" for one bar)
  under "Try again" when a window of bars had findings. The loop run reads "Spot loop · bars 5–6 · clean 0 of 2" as
  its subtitle, has no mode switch, and restarts at once after each loop with a short result line under the staff
  ("Clean", "2 wrong notes"). After two clean loops in a row a card says "Bars 5–6 are clean. Play the whole piece?"
  with "Play it" and "Keep looping". Back leaves at any time.
- **Hint button**: a 44 dp lightbulb in the top bar, left of the click switch. A tap shows the band on the waiting step
  for that step only; TalkBack reads "Show hint".
- **Click switch**: a 44 dp note glyph left of "End run", struck through while off; TalkBack reads "Click on" or
  "Click off". On by default and kept until the app closes.
- **Status line** (36 dp, between staff and keys, timed rungs only): "Play any key to count in" until the first key; then
  one pulse dot per beat of the bar (the accent beat larger, the current beat filled), and during the count-in the
  words "Count-in" and the beat number. The pulse is read as "Beat 2 of 4" and is not a live region (it would
  chatter). The cursor moves in quarter-beat steps, not smoothly, so screenshots stay still.
- **Live marks** on the staff: a correct note turns blue; a timing-off note is a hollow orange ring (`timing` token)
  with a tag such as "+92 ms" or "−40 ms" above it; unjudged notes keep their colour and the cursor step is amber.
- **Key strip text** in a timed run: "E4 · on time", "E4 · late, 92 ms", "E4 · early, 40 ms", "F4 · wrong note".
  TalkBack announces "Correct", "Late, 92 ms", "Early, 40 ms" or "Wrong, F4". The staff reads "… Cursor on D4."

## Calibration screen

Landscape like practice, 915×412 dp, page colour and a card (radius 20) with pill buttons (44 dp high).

- **Header**: back (44 dp, same as Cancel), the title "Calibrate timing" (heading, Space Grotesk 700 22 sp) and the
  line "Play any key on each click. 16 clicks, about 16 seconds."
- **Start**: one pill button in the card. It plays the click pattern (no accent).
- **Running**: a pulse dot (filled for half a beat after each click), "Get ready" while the warm-up clicks play and
  then "Play a key on each click", and the tap counter "7 / 12" (taps on the counted clicks). TalkBack reads the
  counter as "7 of 12 taps".
- **Accepted**: "Your piano answers 38 ms after the click. Saved." ("before" when negative) and Done. The line is a
  polite live region, so TalkBack reads it when it appears.
- **Any other result**: one line ("Taps were uneven", "Too few taps" or "That's more than 0.4 s: check the connection")
  with Try again and Cancel. A dropped piano shows the "Piano disconnected" card above Start.
- **Accepted but not saved**: "Couldn't save the result." with Retry (filled; saves the same offset again, as often
  as needed), Try again (outlined; a new 16-click run) and Cancel (outlined). Success shows the Accepted line and Done.

The **calibrate card** on the practice screen (secondary container colour, under the top bar) reads "Calibrate timing ·
about 16 s" with Calibrate and Skip. It shows when a timed rung is selected on a piano with no stored offset; the run
waits for the choice. Skip plays uncalibrated and hides the card for that piano until the app restarts. The monitor's
"Calibrate timing" button (next to Practice, enabled while connected) opens the same screen and returns to the monitor.

## Results screen

Landscape like practice, 915×412 dp; page `#F7F5F2` (dark `#1B1B1F`), padding 12/18 dp, 10 dp gaps
(`pianoAnchorColors.page` and `.tile`).

- **Header**: back (44 dp), the caption "C-D-E · run 3 · wait mode" (Inter 11 sp, secondary) and the headline, a
  heading (Space Grotesk 700, 22 sp) chosen by band: "Every note right." (clean), "Mostly right. A few slips to
  tidy." (good), "Let's go slower and try again." (keep practising). The run number counts the passage's older runs.
- **Caption**: "C-D-E · run 3 · ♩ = 72" for a timed run, "… · wait mode" otherwise.
- **Tiles**: the mock's 4-column grid, filled left to right: **Pitch**, **Timing**, **Note length**, **Hesitation**
  (white, dark `#2A2730`, radius 16, padding 10/12, 1 dp shadow; label, value in Space Grotesk 700 22 sp, detail line).
  A wait-mode run shows Pitch and Hesitation, a timed run all four; a missing tile leaves its column blank, not a
  placeholder (a run stored before played notes were kept shows Pitch only).
  - Pitch: "3 / 3" in the correct blue and "every note right" or the slips that aren't zero ("1 wrong note",
    "2 wrong notes, 1 note missed, 1 chord incomplete"). Timed runs count score notes without a pitch finding, and the
    detail counts the same findings (wrong, missed, incomplete chords), so it never says "every note right" next to a
    value below full.
  - Timing: "±31 ms" (mean deviation), in the timing orange when any note was early or late, else blue; detail "in
    time", "late at note 6" or "early at note 3" (notes numbered from 1 in score order). A run played without a latency
    offset adds " · not calibrated" to the detail.
  - Note length: "86%" of notes held right; detail "every note held right", "half note held short" or "note 4 held
    long" (a note value is named only for whole, half and quarter notes).
  - Hesitation: "no pauses", "1 pause", "2 pauses"; detail the longest pause, "1.4 s before D4".
  - TalkBack reads each tile as one sentence: "Pitch: 3 of 3 correct, every note right", "Timing: within 31
    milliseconds on average, late at note 6".
  A timed run with no note at its exact pitch has no Timing or Note length tile (nothing to measure).
  Spots and "Next, and why" come when their data exists.
- **Try again**: 44 dp plum pill bottom right; it practises the passage again on the same piano. Back goes to the
  pianos.
- **Not found**: the error card "Couldn't find this run" with "Back to pianos".
- **Unavailable** (the store can't be read): the error card "Couldn't load this run." with "Retry" and "Back to
  pianos".

## Settings screen

Portrait, opened from the 44 dp gear in the monitor's header (TalkBack "Settings"); back returns to the monitor.
Grouped rows under plum group titles, every target at least 44 dp, every change stored at once.

- **Practice**: "Daily practice time" as a three-way segmented control (15, 20, 30 min; each segment is read as
  "20 minutes"), then "Weekly goal" with the value below ("4 days a week") and a minus and plus stepper (read as
  "Fewer days" and "More days"; each disables at its end of 1 to 7).
- **Playing**: the "Click in timed runs" switch, the same choice as the practice screen's click glyph.
- **Strictness** (below the click switch, in the Playing group): "Strictness" with three radio rows, each a title and
  a one-line description: "Gentle: more room for timing and slips", "Standard: the default", "Strict: tight timing,
  few slips". The whole row selects and reads as a radio button; a change applies to the next run.
- **Updates**: the "Check for updates" switch with the note "Checks GitHub once a day for a new version. Sends
  nothing about you." The whole row toggles and reads as a switch.
- **Storage failure**: the usual `errorContainer` card "Couldn't save the settings. Try again." with Dismiss; the
  controls show what is stored.

## Onboarding screen

Shown once, on first launch, until `onboarded` is stored; then the app opens on Today. Portrait, page colour, one step at
a time under "Step n of 4" (no accounts, no sign-in line).

1. **Press any key on your piano**: the line "Searching for your piano…" becomes "Connected · FP-30X" (polite live
   region) as soon as a piano is listed (a Bluetooth piano once the scan has connected it), and the first note on any
   listed input picks that piano. A listed piano that drops keeps the line for 5 s, since a Bluetooth link often
   comes back by itself. After 30 s without a note, the button "Not found? Open the piano list" opens Pianos,
   with the Bluetooth pianos the search connected still connected; the back button or gesture returns here.
2. **Hear it, then play it back**: a card with five keys (C to G, C, E and G in the primary container colour). The piano
   plays C4, E4, G4 (500 ms apart, velocity 80), the card says "Play C, E, G" and any octave counts. A piano with no MIDI
   output skips the playing; the keys are then read as "C, play this key". A match says "You just played your first chord
   by ear." with Continue; a miss says "Close! Start again from C." with Replay, and Skip joins it after three misses.
3. **Calibration**: the calibration screen, hosted in place (`docs/algorithms/latency-calibration.md`). Back or Cancel
   skips it and stores no offset.
4. **Where do you want to start?**: "Start at the beginning" (Unit 0, filled card) or, under "I can already play", Unit 1
   or Unit 2 (title and a one-line summary of what it assumes). Each card is one 44 dp-high-or-more button. A failed save
   shows the `errorContainer` card "Couldn't save your start point. Tap one to try again."

## Ear screen

Portrait, opened by Today's Ear card; back (or Done) returns to Today after the piano is closed. Layout after the mock's
Flashcards artboard: a back arrow (TalkBack "Back"), the skill title and "Skill 1 of 3", the counter "Item 2 of 4"
(read out) with a progress bar, then a card.
- **Identify** item: "Listen, then choose what you heard." and two outlined buttons of at least 48 dp with the choice
  names. **Echo** item: "Listen, then play it back on your piano." and a strip of five keys (C4 to G4).
- The prompt reads "Listening…" while the item sounds. **Replay (2 left)** plays it again (disabled when none are left).
- A wrong answer says "Not quite. Listen again." (polite live region) and offers **Show me**. After a second wrong answer
  or Show me, the answer is shown: for identify the answer button is filled and carries a check mark, for echo the keys
  of the answer are filled with a check mark before the letter, and the line says "Here is the answer. Listen once
  more." with a **Next** button.
- **Summary**: "Ear practice done", "3 skills · 10 of 12 first try" and Done.

## Songs screen

Portrait, with the navigation rail (Songs selected; its glyph is a music note). A "Songs" heading, then one bordered
card per unit in curriculum order, each with its pieces in content order. Rows are at least 56 dp.

- **Unlocked row**: the title and a progress line: "New", "rung 3 of 5" (the next rung to play, as on Today's Song
  card) or "✓ Mastered". Tapping opens Practice on that piece at its next rung (a mastered piece at its top rung);
  with no piano connected it opens Pianos.
- **Locked row**: dimmed, with a lock and "Needs: <skill title>"; not tappable. TalkBack reads "Locked. Needs <skill
  title>."
- **Storage error**: an `errorContainer` card with Retry. The list reloads when the screen is opened again after a run.

## Pianos screen

The piano connected last through onboarding or Pianos is remembered. Its row on Pianos (when it is listed) has a
"Forget" text button (TalkBack: "Forget FP-30X"): it disconnects the piano and stops Today reconnecting it.

## Today screen

The start screen, portrait. A navigation rail (Today selected, Songs, Pianos, Settings gear; shared by Today and Songs as `AppNavRail`) on the left; every item has a
text label. Reading order: the greeting by time of day ("Good morning" from 05:00, afternoon from 12:00, evening from
18:00) and the line "Middle C is calling." naming the cold-open skill ("Ready when you are." with none); the piano
chip (name, USB or BLE and the latency offset when calibrated, e.g. "FP-30X · BLE · 18 ms"; "No piano" opens Pianos; "Connecting…" while the app reconnects the remembered piano in
the background when Today opens, with no scan and no dialog; if that fails the chip reads "No piano", with no error
card and no retry until Today opens again)
and the weekly goal dots (one per goal day, plum for each day practised since Monday); the cards; the session bar;
"Practised 9 of the last 16 days".

- **Cold open** card: dark (`#2A2730`, light text, light button) with "Start cold open", the skill, its minutes and
  "n more to review". **New lesson**: surface with a border, "Begin" and the "why now" line. **Song**: "♩ = 72 · rung 3
  of 5" (wait mode reads "Wait mode · rung 1 of 5") and "Continue". Each opens Practice on the card's passage and
  rung; with no piano connected a card opens Pianos.
- **Ear** card: surface with a 2 dp border in the ear accent (above), the label "EAR · 3 MIN" in amber,
  the first skill's title, "3 skills today" and an amber "Start ear" button. It opens the Ear screen with its skills.
- **Session bar**: Review, Lesson, Song and (amber) Ear segments with widths proportional to their minutes (a zero
  segment is left out), decorative; the caption under it, "Today's 20 minutes: review 4, lesson 7, song 6, ear 3", is
  what TalkBack reads.
- **All caught up**: "All caught up. Play a song or come back tomorrow." above the Song card, if any.
- **Storage error**: an `errorContainer` card with Retry.
- Nothing animates (the mock's breathing dot is static).

