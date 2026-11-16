# Product Specification

## Problem

Default Android TV keyboards make editing existing text awkward with a remote. Focus and cursor behavior can be unpredictable, common actions take too many clicks, and voice recognition often misreads speech.

## Product goal

Build a remote-first Android input method that makes text entry and editing predictable, fast, and controllable.

## Requirements captured

1. Preserve the text cursor position when the user enters editing with the D-pad.
2. Provide an explicit one-step clear-all action. It clears the field, places the cursor at the start, and leaves the keyboard ready for new input. No confirmation.
3. Provide a Spanish (Chile) QWERTY layout with ñ and basic punctuation.
4. Provide opt-in speech dictation into the active field using free Android on-device recognition. Request the es-CL locale, use an installed local Spanish model when available, allow cancellation, and never fall back to cloud recognition.
5. Explore an opt-in “Hey Flowy” voice agent that can open the keyboard, enter a query, and submit it. OS, device, privacy, battery, and background-microphone restrictions must be researched before implementation.

## MVP

- Install and activate Flowy as a system input method.
- Show a native keyboard surface with clear D-pad focus.
- Enter text, delete characters, add spaces, and submit editor actions.
- Test through a dedicated text field.
- Provide tap-to-talk dictation with explicit microphone permission and a cancelable listening screen.

## Out of scope for the first prototype

- Background microphone listening.
- Cloud speech recognition or remote audio transcription.
- Automatic control of third-party apps.
- Production-grade speech recognition and predictive text.

## Product decisions still open

- Supported Android TV devices and OS versions.
- Additional keyboard languages and layouts.
- Visual design and measurement baseline.
