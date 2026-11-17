# Local Voice Dictation

## Speech pipeline

Tapping the keyboard microphone starts dictation immediately after microphone permission is granted. If the local model is missing, the app downloads it and starts recording as soon as it is ready. Flowy records mono, 16 kHz PCM audio for up to 30 seconds. A lightweight energy-based voice activity detector tracks the noise floor and ends the utterance after one second of trailing silence; it also stops waiting if speech has not started within 12 seconds. The user can still end recording manually or cancel.

The audio is converted to float samples and passed to the native Whisper.cpp library through JNI. Whisper transcribes Spanish on the device; the result is returned and inserted at the active editor selection without an extra confirmation. Audio is not uploaded and Android's speech-recognition service is not used.

## Model download

The first dictation downloads the quantized multilingual Whisper tiny Q5_1 model from a pinned Hugging Face revision. The app stores it in private internal storage and checks the published SHA-256 before making it available. The model is about 32 MB and is downloaded once per app installation. Once downloaded, transcription works offline.

A failed or interrupted download is discarded. The user can retry. The app never substitutes a cloud recognizer.

## Native build

The Android app builds Whisper.cpp from a pinned upstream commit through CMake and the Android NDK. The APK includes arm64-v8a for current Android phones and x86_64 for the Intel Android TV target. CI assembles the debug APK; physical device performance and microphone compatibility still require manual validation.

## Model reference

- Model: ggml-tiny-q5_1.bin
- Upstream repository: ggerganov/whisper.cpp
- Pinned revision: c521a4b02f422512d734391fdf08bb08c0862f68
- SHA-256: 818710568da3ca15689e31a743197b520007872ff9576237bda97bd1b469c3d7
- Format/library license: MIT, as published by the Whisper.cpp project.
