# Contributing to EME APP

Thanks for helping. This page describes how changes get into the app.

## Workflow

1. **Issue first.** Open an issue for a bug or a feature idea before writing code, or pick an
   existing one. Search the open issues first to avoid duplicates.
   Small fixes (a typo, a translation correction) can go straight to a pull request.
2. **Say that you take it.** Leave a comment on the issue you start working on, so two people
   do not fix the same thing.
3. **Fork and branch.** Fork the repository and create a branch from `main`, for example
   `fix/chat-login-cookies` or `feat/moon-libration`.
4. **One logical change per pull request.** Keep unrelated changes in separate pull requests.
5. **Open the pull request** against `main` and link the issue in the description.
   The unit tests run automatically on every pull request and must pass.
   - `Fixes #12` when the pull request solves the whole issue. GitHub then closes the issue
     when the pull request is merged.
   - `Part of #12` when it solves only a part. Say in the issue what is left.
6. The maintainer (ON5GS) reviews and merges.
7. **Closing an issue by hand.** An issue that was solved in parts does not close by itself.
   Whoever merges the last part closes the issue, with a comment that links the pull requests.

## Building and testing

Requirements: Android Studio, or JDK 17+ and the Android SDK (platform 35).

```bash
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # unit tests
./gradlew testDebugUnitTest -Pscreenshots   # also renders screenshots into app/build/screenshots/
```

## Rules for code changes

- **Tests.** A change in logic (parsers, calculations, stores) comes with a unit test in
  `app/src/test/`. A bug fix comes with a test that fails without the fix.
- **Translations.** A new or changed text goes into all 13 `strings.xml` files
  (`app/src/main/res/values*/`). `TranslationsTest` fails when a language is missing a text
  or has different placeholders than the English one.
- **Test data.** The samples in `app/src/test/resources/` are real data from the HB9Q chat and
  W3SZ Live CQ. Replace every e-mail address by `xxxx@test.com` before you add a new sample.
- **No secrets.** Never put a login, password or cookie in code, test data, an issue or a
  pull request.
- **No APK and no version number in a pull request.** A fix or feature pull request does not
  change `versionCode` or `versionName` in `app/build.gradle.kts` and never adds an APK.
  Releases are separate, see below.

## Rules for the external services

The app is a guest on other people's servers. These rules are not negotiable in a pull request;
discuss them in an issue first.

- **HB9Q chat is read only**, as agreed with HB9Q. The app does not send messages and does not
  change anything on logger.hb9q.ch.
- **Do not poll more often.** The chat is fetched every 20 s (`ChatStore.MESSAGES_REFRESH_MS`),
  Live CQ every 120 s with a minimum of 30 s between two fetches (`LiveCqStore`).
- **Keep the honest User-Agent** (`EME-ON5GS-Android/<version>`), so the operators of HB9Q and
  W3SZ can see where the requests come from.

## Releases

A release is its own pull request: it raises `versionCode` and `versionName` and adds the
release notes `docs/releases/vX.Y.Z.md`, nothing else. When it is merged, GitHub builds, signs
and publishes the APK as a GitHub release (`.github/workflows/release.yml`). Claude prepares
that pull request when the maintainer asks "make a release" (`.claude/skills/release/SKILL.md`).

## Reporting a bug

Use the bug report form under *Issues > New issue*. Mention the app version (see the README or
the APK file name), your Android version and the tab where it happens.
