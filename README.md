# Kerja Android

Kerja is the Android companion app for the Kerja workspace. It keeps the web workspace at the center while adding native Android navigation, file selection, downloads, push notifications, and secure platform integrations.

## Features

- Native Material 3 app bar and bottom navigation for Workspace, Home, Blog, and Support.
- System-aware light and dark appearance.
- WebView session persistence and swipe-to-refresh.
- Offline and server-error recovery screen with a retry action.
- Safe in-app routing for Kerja domains and external-browser handoff for other websites.
- Android file picker integration for web uploads.
- Downloads through Android Download Manager.
- Firebase Cloud Messaging and periodic notification checks.
- Deep links using the \`thio://\` scheme and verified HTTPS links.
- Runtime permissions requested only when the related feature is used.

## Project setup

The project uses Java 17, Android Gradle Plugin 8.7.3, Gradle 8.11.1, and Android SDK 35.

1. Open the project in Android Studio.
2. Ensure Android SDK Platform 35 and Build Tools 35.0.0 are installed.
3. Keep a valid \`app/google-services.json\` for the Firebase Android application ID \`com.kerja.thio\`.
4. Sync Gradle and run the \`app\` configuration.

The repository's GitHub Actions workflow installs Gradle directly, so a Gradle wrapper JAR is not required for CI.

## Build from a local Gradle installation

\`\`\`bash
gradle :app:assembleDebug
gradle :app:bundleRelease
gradle :app:lintDebug
gradle :app:testDebugUnitTest
\`\`\`

Debug APK and release AAB outputs are generated under \`app/build/outputs/\`.

## Website and Firebase integration

The app currently loads \`https://thio-cc-cd.pages.dev/kerja\`. The custom domain \`thio.cc.cd\` and its subdomains are also accepted as trusted in-app destinations.

Firebase Cloud Messaging tokens are registered with the website using a JSON POST request. Tokens are not sent in URL query strings. The website can receive the current token through the \`fcm_token_ready\` browser event while a trusted Kerja page is active.

Background notification polling uses the authenticated WebView cookie session and checks \`/notifications.php?ajax=check\` at WorkManager's minimum periodic interval. Android may defer background work based on battery and system conditions.

## Security notes

- Cleartext HTTP traffic is disabled.
- Mixed HTTP content is blocked inside the WebView.
- File-system access from web content is disabled; uploads use Android's document picker.
- Camera, microphone, and location access are restricted to trusted Kerja origins and requested only when needed.
- Third-party cookies are disabled by default.
- App data backup is disabled to avoid copying authenticated session state.
