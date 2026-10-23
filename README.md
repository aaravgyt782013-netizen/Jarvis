# JARVIS Android

A Kotlin + Jetpack Compose Android assistant starter focused on native Android actions. It does not require an AI API key.

## Current features
- Futuristic dark interface and action log.
- Android speech recognition and spoken replies.
- Local command routing for supported device actions.
- Opens selected installed apps, Android/Wi-Fi/Bluetooth settings, dialer, and web searches.
- Optional user-enabled AccessibilityService for supported navigation, scrolling, tap gestures, and text entry.
- First-launch permission onboarding with shortcuts to Android Accessibility settings, app permissions, and microphone permission.
- GitHub Actions builds a debug APK.

## Install and onboard
1. Download the latest successful `jarvis-debug-apk` artifact from the repository's Actions page.
2. Install `app-debug.apk` on your Android phone. If Android asks, allow installation from that source.
3. Open JARVIS and grant microphone permission when requested.
4. Tap **ENABLE ACCESSIBILITY**, find JARVIS in Android's Accessibility list, and enable it only if you want the supported UI automation features.
5. Try voice or text commands such as `open YouTube`, `open settings`, `go back`, `scroll down`, `type hello`, or `search latest space news`.

## Important limits
- No API key or external AI API is used by this build. General questions that need information are sent to a browser search; the app does not yet contain an on-device large language model for free-form answers.
- Android requires the user to grant permissions in system settings. An app cannot silently grant itself every permission.
- Accessibility controls are limited to implemented commands and the current screen; some apps block automation, and sensitive operations may require user interaction.
- Microphone recognition depends on the Android speech-recognition provider and may need network access.
- No always-on wake word or guaranteed locked-screen control is included. Android restricts background microphone access.
- Wi-Fi/Bluetooth actions open system settings; Android may require manual changes.
- Calling opens the dialer and does not place calls automatically.

## Security
Accessibility permission is powerful. Enable it only if you trust this app and want its listed controls. Never share a build with credentials or private data embedded in it.
