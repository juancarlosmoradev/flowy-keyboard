# Product Specification

## Problem

Default Android TV keyboards make editing existing text awkward with a remote. Focus and cursor behavior can be unpredictable, common actions take too many clicks, and voice recognition often misreads speech.

## Product goal

Build a remote-first Android input method that makes text entry and editing predictable, fast, and controllable.

## Requirements captured

1. Preserve the text cursor position when the user enters editing with the D-pad.
2. Provide an explicit one-step clear-all action. It clears the field, places the cursor at the start, and leaves the keyboard ready for new input. No confirmation.
3. Provide a Spanish (Chile) QWERTY layout with ñ and basic punctuation.
4. Provide AI-assisted speech recognition that transcribes speech into the active search field and handles Chilean Spanish pronunciation well.
5. Explore an opt-in “Hey Flowy” voice agent that can open the keyboard, enter a query, and submit it. OS, device, privacy, battery, and background-microphone restrictions must be researched before implementation.

## MVP

- Install and activate Flowy as a system input method.
- Show a native keyboard surface with clear D-pad focus.
- Enter text, delete characters, add spaces, and submit editor actions.
- Test through a dedicated text field.

## Out of scope for the first prototype

- Background microphone listening.
- Remote AI requests.
- Automatic control of third-party apps.
- Production voice recognition and predictive text.

## Product decisions still open

- Supported Android TV devices and OS versions.
- Additional keyboard languages and layouts.
- Voice provider, privacy model, and network behavior.
- Visual design and measurement baseline.
