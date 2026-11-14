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
  has none; unused until timing exists). Staff lines `#3F3B45` (dark `#CFC8D3`), upcoming notes `#1B1B1F`
  (dark `#F7F5F2`), target-key band `#FBE3B0`.
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

## Practice screen (wait mode)

Landscape: the phone sits on the music rest (`app-android` forces landscape while it is open). Reference layout
915×412 dp; screenshots render at that size.

- **Top bar** 52 dp with a divider: back (44 dp), title (Space Grotesk 700, 16 sp) and "Wait mode" (Inter 11 sp),
  the tempo ladder (30 dp chips, radius 15, 1.5 dp border; Wait selected in plum, 60/70/85/100 % disabled) and
  the "End run" pill (36 dp), which discards the run.
- **Staff** 196 dp: five 1.5 dp lines 16 dp apart, treble clef, one notehead per step (19×14 dp ellipse at −18°,
  chords stacked), evenly spaced, ledger lines below and above the staff. Done notes blue, the waiting note amber,
  upcoming notes ink; a 3×110 dp plum cursor (85 %) left of the waiting note; legend "correct" and "waiting for
  you" top right. No time signature, bar lines or timing marks yet.
- **Key strip**: "Live keys · play the piano" and the last key's verdict ("E4 · correct, moving on",
  "F4 · waiting for E4"), then two octaves from the C at or below the passage's lowest note. White keys 60 dp on a
  62 dp pitch, black keys 34×46 dp; the waiting step's keys carry an 18 dp amber band; held and flashing keys turn
  correct or wrong with a 150 ms colour change; the flash lasts `KEY_FLASH_MS`.
- **Accessibility**: the staff reads as "Treble staff. Notes C4, D4, E4. Waiting for D4."; each key is named; the
  verdict is a polite live region announcing "Correct" or "Wrong, F4".
- **Disconnected**: the error card "Piano disconnected" with "Back to pianos"; the run is discarded.
- **Save failed**: the error card "Couldn't save this run." with "Retry" (saves the same run again) and "Discard"
  (drops it and starts a fresh run on the same piano).

## Results screen

Landscape like practice, 915×412 dp; page `#F7F5F2` (dark `#1B1B1F`), padding 12/18 dp, 10 dp gaps
(`pianoAnchorColors.page` and `.tile`).

- **Header**: back (44 dp), the caption "C-D-E · run 3 · wait mode" (Inter 11 sp, secondary) and the headline, a
  heading (Space Grotesk 700, 22 sp) chosen by band: "Every note right." (clean), "Mostly right. A few slips to
  tidy." (good), "Let's go slower and try again." (keep practising). The run number counts the passage's older runs.
- **Tiles**: the mock's 4-column grid; Phase 1 fills only the first, **Pitch** (white, dark `#2A2730`, radius 16,
  padding 10/12, 1 dp shadow): "PITCH", "3 / 3" in the correct blue (Space Grotesk 700, 22 sp) and "every note
  right" or the slips that aren't zero ("1 wrong note", "2 wrong notes, 1 chord incomplete"). TalkBack reads it as
  one sentence: "Pitch: 3 of 3 correct, every note right". Timing, note length and hesitation tiles, spots and
  "Next, and why" come when their data exists; empty columns are blank, not placeholders.
- **Try again**: 44 dp plum pill bottom right; it practises the passage again on the same piano. Back goes to the
  pianos.
- **Not found**: the error card "Couldn't find this run" with "Back to pianos".
- **Unavailable** (the store can't be read): the error card "Couldn't load this run." with "Retry" and "Back to
  pianos".
