# Voice Input Architecture

## Status

Free, on-device recognition is the plan for the first version. No cloud transcription or paid API will be used.

## Product requirements

- A visible microphone key starts a user-requested listening session.
- Speech is transcribed into the currently focused text field at the current cursor.
- Recognition must work well with Chilean Spanish.
- The user can cancel listening before transcription is committed.
- A separate voice-agent flow may submit a search after an explicit spoken command.
- Ordinary dictation must not submit the search automatically.
- No audio is captured while the user has not started a session.
- Voice recognition must not silently fall back to cloud processing.

## First implementation

Use Android's on-device SpeechRecognizer when the device reports that it is available and supports the installed Spanish language model. The app will explicitly use the on-device recognizer and check language support; it will not use the general recognizer as a fallback because that service may send audio to a remote server.

If the Android TV does not provide on-device recognition for Spanish, evaluate whisper.cpp as an optional local model. It supports Android and multilingual transcription. Model size, download, recognition accuracy, latency, and memory usage must be measured on the target TV before selection.

The app will not include a cloud provider or backend in this version. Downloading a local model may use network data once; recognition itself runs on the TV.

## Tap-to-talk flow

1. The user focuses the microphone key and presses OK.
2. Flowy explains and requests microphone permission if needed.
3. The speech controller checks local recognition support for Spanish (Chile).
4. If available, Flowy starts a visible listening session.
5. The user can cancel; otherwise Flowy displays the transcript.
6. Flowy commits the confirmed transcript through the active InputConnection.
7. A separate explicit voice-agent command can submit a search after text is committed.

No audio should be stored by default. If no local Spanish recognizer is available, Flowy reports that voice input is unavailable on that device instead of sending audio elsewhere.

## Always-on wake word

“Hey Flowy” is a separate, later feature. Android's VoiceInteractionService is intended for the system's selected voice interaction provider. A generic microphone foreground service faces background-start and while-in-use microphone restrictions, and device manufacturers can add their own limits. Do not assume continuous listening will work across Android TV devices.

## Delivery stages

1. Add tap-to-talk UI, permission handling, listening state, and cancellation.
2. Integrate Android on-device recognition with an es-CL capability check.
3. Evaluate recognition accuracy on the target TV with Chilean Spanish phrases.
4. Evaluate whisper.cpp local model only if the platform recognizer is missing or inaccurate.
5. Research wake-word support per target TV platform.

## References

- [Android SpeechRecognizer](https://developer.android.com/reference/android/speech/SpeechRecognizer)
- [Android RecognitionSupport](https://developer.android.com/reference/android/speech/RecognitionSupport)
- [whisper.cpp Android example](https://github.com/ggml-org/whisper.cpp/tree/master/examples/whisper.android.java)
- [whisper.cpp](https://github.com/ggml-org/whisper.cpp)
- [Android microphone foreground-service restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)
- [Android VoiceInteractionService](https://developer.android.com/reference/android/service/voice/VoiceInteractionService.html)
