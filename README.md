# JARVIS Android

A Kotlin + Jetpack Compose Android assistant starter. This is a foundation, not a finished Iron Man-level assistant.

## Features in this starter
- Futuristic dark interface and action log.
- Android speech recognition and text-to-speech replies.
- Offline command fallback.
- Optional OpenAI-compatible Chat Completions endpoint and model configuration.
- Limited Android actions: open selected installed apps, open Android/Wi-Fi/Bluetooth settings, open dialer, search web, and open a URL.

## Build on Android
1. Open this repository in Android Studio.
2. Install Android SDK Platform 35 if prompted and allow Gradle sync.
3. Run the `app` configuration on an Android device.
4. Grant microphone permission.
5. Try: `open YouTube`, `open settings`, `Wi-Fi settings`, `search space news`, or `what can you do`.
6. Optional AI: open SETUP and enter an OpenAI-compatible endpoint, model ID, and API key. Free tiers and limits vary by provider.

## Current limitations
- This initial commit has not been built/tested in Android Studio yet.
- API keys are stored in local app preferences in this prototype; do not use a valuable key in a public/shared build. A production app should use Android Keystore-backed protection or a backend proxy.
- SpeechRecognizer relies on the device's speech recognition provider; it may require network access and is not guaranteed offline.
- No always-on wake word or locked-screen listener is included. Android restricts background microphone use. A foreground microphone service, visible notification, explicit permission, battery testing, and OS-version checks are needed.
- App launch uses a small list of known package IDs. Some manufacturers/apps use different package names.
- Wi-Fi/Bluetooth settings open system settings; Android may require the user to change radio settings manually.
- Calls only open the dialer; the app does not place calls automatically.
- AI responses are untrusted. The app allows only a small set of actions and does not grant the model arbitrary shell or device access.

## Roadmap
1. Build and test the starter on the target phone.
2. Separate networking, conversation state, and action execution into testable components.
3. Add safer credential storage, retries, and structured tool calls.
4. Add user-started foreground voice service with clear stop controls; evaluate wake-word detection and battery impact.
5. Add user-enabled AccessibilityService only for explicit, auditable UI automation where Android permits it.
6. Add multi-step planner/executor with verification, cancel/retry, and confirmation for sensitive actions.
7. Add tests, privacy controls, and signed release builds.

## Security
Never commit API keys or other secrets. Production code must validate every action and argument locally, ask for confirmation for sensitive operations, and report actual Android outcomes.
