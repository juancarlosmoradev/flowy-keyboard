# Roadmap

## Foundation

- [x] Create Android application and IME service skeleton.
- [x] Add a local text-entry test screen.
- [x] Record initial product requirements and architecture.
- [x] Build a debug APK in GitHub Actions.
- [ ] Install and evaluate on an Android TV device. Use the [Android TV validation checklist](TV_VALIDATION.md).

## MVP

- [x] Add Spanish (Chile) QWERTY layout with ñ and punctuation.
- [x] Add high-contrast focus styling for remote navigation.
- [x] Add long-press number and symbol alternates to letter keys.
- [x] Keep button labels within their available width.
- [ ] Verify D-pad focus order and visible focus on device.
- [ ] Validate character entry, deletion, space, and editor action.
- [x] Restore the latest editor selection when Flowy opens.
- [x] Add one-step clear-all behavior.
- [ ] Validate cursor preservation on target apps and devices.
- [ ] Select supported devices and Android versions.
- [x] Implement tap-to-talk with a one-time verified Whisper model download and local Spanish transcription.
- [ ] Validate local transcription quality, latency, and microphone support on phone and target TV.

## Later

- [ ] Measure interaction time and remote clicks against the default keyboard.
- [ ] Evaluate wake-word feasibility per Android TV platform.
- [ ] Configure the persistent APK signing key in GitHub repository secrets.
