# Voice Input Architecture

## Status

Tap-to-talk uses a local Whisper.cpp model. The Android system speech recognizer is not used, and there is no cloud transcription fallback. See [Local Voice Dictation](LOCAL_VOICE_DICTATION.md) for model, checksum, and native build details.

## Product requirements

- A visible microphone key starts a user-requested listening session.
- Speech is transcribed into the currently focused text field at the current cursor.
- Spanish transcription runs locally on the device.
- The user can cancel before transcription is committed.
- Ordinary dictation does not submit the search automatically.
- No audio is captured before the user starts a session.
- Audio is never uploaded.

## Tap-to-talk flow

1. The user presses the microphone key.
2. Flowy opens a visible screen and requests microphone permission if needed.
3. On the first use, the user starts a one-time download of the quantized model. Flowy verifies its SHA-256 and saves it in private app storage.
4. Flowy captures up to 30 seconds of mono audio.
5. Whisper.cpp transcribes Spanish on-device, then Flowy inserts the text through the active InputConnection.
6. The user can cancel the operation; dictation never submits a search automatically.

## Always-on wake word

“Hey Flowy” is a separate, later feature. Android's VoiceInteractionService is intended for the system's selected voice interaction provider. A generic microphone foreground service faces background-start and while-in-use microphone restrictions, and device manufacturers can add their own limits. Do not assume continuous listening will work across Android TV devices.

## Delivery status

- [x] Implement local Whisper.cpp transcription and a one-time verified model download.
- [ ] Check transcription quality, latency, and microphone support on the target phone and Android TV.
- [ ] Research wake-word support per target TV platform.

## References

- [whisper.cpp Android example](https://github.com/ggml-org/whisper.cpp/tree/master/examples/whisper.android.java)
- [whisper.cpp](https://github.com/ggml-org/whisper.cpp)
- [Android microphone foreground-service restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)
- [Android VoiceInteractionService](https://developer.android.com/reference/android/service/voice/VoiceInteractionService.html)
