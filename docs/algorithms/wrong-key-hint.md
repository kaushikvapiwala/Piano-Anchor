# Wrong-key hint (landmark hint)

A beginner who presses a wrong key learns little from "wrong". What helps is a way to find the right key without
counting. The piano has one: the black keys come in a group of two and a group of three, and every white key has a fixed
place against them. `wrongKeyHint(played, expected)` in `WrongKeyHint.kt` in the engine's guidance package picks that landmark; the practice screen
turns it into one line of text.

- **Landmarks by pitch class.** C is just left of the two black keys, D between them, E just right of them; F is just
  left of the three, G and A sit in the three (left and right of the middle key), B is just right of them. Every black
  key is found as the black key just right of the white key below it. The description names the expected key's letter,
  not its octave, since the landmark is the same in every octave.
- **Direction.** LEFT or RIGHT by pitch: the side of the played key on which the expected key lies. When both keys share
  a letter (an octave apart) the direction is SAME_KEY_OTHER_OCTAVE and the line says so instead of a landmark, since
  the learner found the right letter and only needs another octave.
- **Which key.** In a chord the expected key is the one still to be played that lies nearest the wrong key.
- **When it shows.** Only a wrong key in wait mode: the played key flashes grey (`KEY_FLASH_MS`), the keys of the waiting
  step glow in the accent until they are played, and the line stays until then. A timed run only flashes grey, because
  stopping to read would cost the beat. There is no red and no sound.
- **Unit 0.** The first unit teaches the keyboard itself, so its passages hide the staff and always glow the next key,
  whatever the key-hint level.
