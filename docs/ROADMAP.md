# Roadmap

## Foundation

- [x] Create Android application and IME service skeleton.
- [x] Add a local text-entry test screen.
- [x] Record initial product requirements and architecture.
- [x] Build a debug APK in GitHub Actions.
- [ ] Install and evaluate on an Android TV device.

## MVP

- [x] Add Spanish (Chile) QWERTY layout with ñ and punctuation.
- [x] Add high-contrast focus styling for remote navigation.
- [ ] Verify D-pad focus order and visible focus on device.
- [ ] Validate character entry, deletion, space, and editor action.
- [x] Restore the latest editor selection when Flowy opens.
- [x] Add one-step clear-all behavior.
- [ ] Validate cursor preservation on target apps and devices.
- [ ] Select supported devices and Android versions.
- [x] Choose free, on-device speech recognition for the first version.
- [x] Implement tap-to-talk, microphone permission, local Spanish checks, cancellation, and text insertion.
- [ ] Evaluate local Whisper fallback on the target TV if needed.

## Later

- [ ] Measure interaction time and remote clicks against the default keyboard.
- [ ] Evaluate wake-word feasibility per Android TV platform.
