# Living Studio (UI essentials)

The app's look, kept small until the practice screen needs more. Code: `PianoAnchorTheme` in
`ui/shell/src/commonMain/kotlin/pianoanchor/ui/shell/PianoAnchorTheme.kt`.

- **Material 3**, light and dark, following the system setting. Every screen is checked in both (screenshots).
- **One brand colour:** studio teal, `#1F6F6B` as primary in light, `#8AD3CC` in dark. Everything else uses the
  Material 3 default scheme until a design pass replaces it.
- **Errors** sit in an `errorContainer` card at the top of the screen, with the action that fixes them (open
  settings, turn on Bluetooth) and Dismiss; buttons use the card's content colour for contrast.
- **Live MIDI** (note names, times) is set in a monospace font so columns line up.
- **Text** comes from Compose resources (`composeResources/values/strings.xml`), never hard-coded. Every control
  has a label TalkBack can read; rows that act on a device name the device in their content description.
- No infinite animations on screens with screenshots (a spinner never lets the test go idle): show progress as text.
