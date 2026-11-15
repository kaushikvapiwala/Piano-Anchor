# Contribute a piano

If your piano is not supported yet, the app can save one small file that tells us what we need. It only
reads: the app sends the standard Identity Request (`docs/protocols/midi-identity.md`) and listens. Nothing
is uploaded; you attach the file yourself.

## Steps

1. Open the note monitor (the Pianos screen) and connect your piano.
2. Press "Start recording", play a few notes such as C-D-E, then press "Stop recording".
3. Press "Save contribution kit" and pick where to save `piano-anchor-kit.json`.
4. Open a "Support for my piano" issue on GitHub and attach the file.

## What the file holds

The piano's name (inside the recording), its Identity Reply if it answered, the notes you played with their
timing, and the app version. Nothing else.

## File format

One JSON object on one line, ended by a newline. Field order is fixed and nulls are written:

```json
{"kit":1,"appVersion":"0.1.0","identity":{"manufacturerId":[65],"family":25,"model":1,"version":[0,1,0,0]},"fixture":"..."}
```

- `kit`: the format version (1).
- `appVersion`: the app that wrote the file.
- `identity`: the decoded Identity Reply (manufacturer id bytes, 14-bit family and model, 4 version bytes), or
  `null` when the piano did not answer.
- `fixture`: the recording as fixture JSONL text (`fixtures/SCHEMA.md`), byte for byte as the fixture export
  writes it.

To get the recording back out: `jq -r .fixture piano-anchor-kit.json > fixture.jsonl`.
