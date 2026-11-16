# Voice Input Architecture

## Status

Architecture proposed. No speech provider or audio backend is selected.

## Product requirements

- A visible microphone key starts a user-requested listening session.
- Speech is transcribed into the currently focused text field at the current cursor.
- Recognition must work well with Chilean Spanish.
- The user can cancel listening before transcription is committed.
- A separate voice-agent flow may submit a search after an explicit spoken command.
- Ordinary dictation must not submit the search automatically.
- No audio is captured while the user has not started a session.

## Recognition options

| Option | Advantages | Limits |
| --- | --- | --- |
| Android speech service | Adapts to the device and can provide on-device recognition where installed. Android exposes availability checks and supported-language information. | Service, model, network behavior, and Spanish quality vary by TV manufacturer and OS. The standard API may stream audio to remote servers. |
| AI transcription backend | Selects a known model and gives Flowy control over the recognition pipeline and Spanish quality evaluation. | Requires a backend and may send audio off-device. Usage may incur API costs. The Android app must not contain the provider's secret API key. |

## Recommended design

Use a provider interface so the UI and text insertion do not depend on one recognizer:

1. The user focuses the microphone key and presses OK.
2. Flowy explains and requests microphone permission if needed.
3. The speech controller checks recognition support for es-CL.
4. It uses a locally available recognizer only if Spanish is installed and it meets the quality bar.
5. Otherwise, it can use a user-approved AI transcription backend.
6. Flowy shows the transcript for correction or confirmation, then commits it through the active InputConnection.
7. A separate explicit voice-agent command can submit the search after text is committed.

No audio should be stored by default. If the remote provider requires an upload, Flowy must disclose that before recording. The service must authenticate to any AI provider on the server side; never embed a provider key in the APK.

## Always-on wake word

“Hey Flowy” is a separate, later feature. Android's VoiceInteractionService is intended for the system's selected voice interaction provider. A generic microphone foreground service faces background-start and while-in-use microphone restrictions, and device manufacturers can add their own limits. Do not assume continuous listening will work across Android TV devices.

## Delivery stages

1. Add tap-to-talk UI, permission handling, listening state, and cancellation.
2. Select the recognition provider and audio-processing policy.
3. Implement recognition and transcript insertion.
4. Measure Chilean Spanish accuracy with a consented evaluation set; do not retain raw audio by default.
5. Research wake-word support per target TV platform.

## Decisions needed before remote transcription

- Whether Flowy may send a voice recording to an AI service.
- Who operates and pays for the backend.
- Whether transcript confirmation is required before search submission.

## References

- [Android SpeechRecognizer](https://developer.android.com/reference/android/speech/SpeechRecognizer)
- [Android TV on-screen keyboard](https://developer.android.com/training/tv/get-started/onscreen-keyboard)
- [OpenAI speech-to-text](https://developers.openai.com/api/docs/guides/speech-to-text)
- [OpenAI API key safety](https://developers.openai.com/api/docs/guides/api-keys)
- [Android microphone foreground-service restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)
- [Android VoiceInteractionService](https://developer.android.com/reference/android/service/voice/VoiceInteractionService.html)
