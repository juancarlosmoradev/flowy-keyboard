# ADR 0001: Build Flowy as a native input method

## Status

Accepted for the prototype.

## Decision

Implement Flowy as an Android `InputMethodService` using native Views.

## Rationale

A system input method can write into the focused field through Android's input method framework. Native Views provide direct control over focus and D-pad navigation.

## Consequences

The prototype must be enabled as an input method in Android settings. Cursor behavior and compatibility depend partly on each host app and must be tested on target devices. Compose and AI services are deferred.
