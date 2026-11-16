# Voice Input Architecture

## Status

The first version uses free Android on-device speech recognition. It does not include a paid API or cloud transcription fallback.

## Product requirements

- A visible microphone key starts a user-requested listening session.
- Speech is transcribed into the currently focused text field at the current cursor.
- Recognition uses Chilean Spanish where the device's local language model supports it.
- The user can cancel listening before transcription is committed.
- Ordinary dictation does not submit the search automatically.
- No audio is captured while the user has not started a session.
- Voice recognition never silently falls back to cloud processing.

## First implementation

Flowy uses Android's on-device SpeechRecognizer on Android 12 or later. On Android 13 and later it checks whether an installed on-device Spanish language model is available before listening. It requests the locale es-CL and does not use the general recognizer as a fallback. On Android 12, language support is confirmed by attempting the local recognizer.

If there is no local recognizer or Spanish model, Flowy reports that dictation is unavailable on the device. If local recognition proves inadequate on the target TV, evaluate whisper.cpp as an optional local model; measure model size, download, accuracy, latency, and memory first.

The app does not store audio. Downloading a local model may use network data once; recognition itself runs on the TV.

## Tap-to-talk flow

1. The user focuses the microphone key and presses OK.
2. Flowy opens a visible listening screen and requests microphone permission if needed.
3. Flowy checks local recognition and Spanish support.
4. The user can cancel; otherwise Flowy commits the recognized transcript through the active InputConnection.
5. Dictation never submits a search automatically.

## Always-on wake word

“Hey Flowy” is a separate, later feature. Android's VoiceInteractionService is intended for the system's selected voice interaction provider. A generic microphone foreground service faces background-start and while-in-use microphone restrictions, and device manufacturers can add their own limits. Do not assume continuous listening will work across Android TV devices.

## Delivery stages

1. Evaluate dictation accuracy on the target TV with Chilean Spanish phrases.
2. Evaluate whisper.cpp local model only if the platform recognizer is missing or inaccurate.
3. Research wake-word support per target TV platform.

## References

- [Android SpeechRecognizer](https://developer.android.com/reference/android/speech/SpeechRecognizer)
- [Android RecognitionSupport](https://developer.android.com/reference/android/speech/RecognitionSupport)
- [whisper.cpp Android example](https://github.com/ggml-org/whisper.cpp/tree/master/examples/whisper.android.java)
- [whisper.cpp](https://github.com/ggml-org/whisper.cpp)
- [Android microphone foreground-service restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)
- [Android VoiceInteractionService](https://developer.android.com/reference/android/service/voice/VoiceInteractionService.html)
