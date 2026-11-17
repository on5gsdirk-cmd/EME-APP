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
6. **Review, then merge.** Every pull request gets a review before it is merged: a review
   comment or a GitHub review on the pull request. The author may do that review themselves;
   what matters is that someone read the change after CI and wrote down what they checked.
   For a release pull request, the maintainer's explicit "ok" on the release notes is the review.
   The maintainer (ON5GS) merges.
7. **Closing an issue by hand.** An issue that was solved in parts does not close by itself.
   Whoever merges the last part closes the issue, with a comment that links the pull requests.

## Building and testing

Requirements: Android Studio, or JDK 17+ and the Android SDK (platform 35).

```bash
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # unit tests
./gradlew testDebugUnitTest -Pscreenshots   # also renders screenshots into app/build/screenshots/
```

The web app (`web/`) has its own tests. They need Node 24 and Go (the version in `web/server/go.mod`):

```bash
(cd web && npm ci && npm test)    # web app tests
(cd web/server && go test ./...)  # web server tests
```

## Rules for code changes

- **Tests.** A change in logic (parsers, calculations, stores) comes with a unit test in
  `app/src/test/`. A bug fix comes with a test that fails without the fix.
- **Translations.** A new or changed text goes into all 15 `strings.xml` files
  (`app/src/main/res/values*/`). `TranslationsTest` fails when a language is missing a text
  or has different placeholders than the English one.
- **Layout.** `LayoutTest` renders every screen in all 15 languages on a small phone with a large
  font, and in English on a phone and a tablet. It fails on text that is cut off, an element
  outside the screen or a touch target under 48 dp, and writes a picture of each failing screen
  to `app/build/layout-failures/`. A known problem goes into its `KNOWN` list only with an issue.
  On a pull request it runs as its own check, `layout`; when it fails, the pictures and the test
  report are in the artifact `layout-failures` of that run.
- **Android and web together.** A change that users see goes into `app/` and `web/` in the
  same pull request. A change that only exists on one platform says so in the pull request,
  with the reason.
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
  Live CQ (W3SZ and N6NU together) every 120 s with a minimum of 30 s between two fetches
  (`LiveCqStore`), the N0UK chat
  at most once every 90 s (`N0ukStore.REFRESH_MS`, the interval the page itself asks for).
- **N0UK chat is read only.** The app reads www.chris.org/cgi-bin/jt65emeA and never posts to it.
- **N6NU Live CQ is read only.** The app reads livecq.n6nu.org/api/spots with a GET request and
  sends nothing else to it.
- **The web server polls for all web users together.** `web/server` fetches W3SZ, N6NU and
  N0UK itself and keeps the same limits as the app, however many people have the web app open.
- **The web server logs in at HB9Q per web user.** `web/server` logs in with the user's own HB9Q
  login, keeps one HB9Q session per web session, fetches the chat at most every 20 s per session
  and band, and uses the same login backoff as the app. It never stores the password.
- **Keep the honest User-Agent** (`EME-ON5GS-Android/<version>`), so the operators of HB9Q and
  W3SZ, N6NU and N0UK can see where the requests come from.

## Releases

A release is its own pull request: it raises `versionCode` and `versionName` and adds the
release notes `docs/releases/vX.Y.Z.md`, nothing else. When it is merged, GitHub builds, signs
and publishes the APK as a GitHub release (`.github/workflows/release.yml`). Claude prepares
that pull request when the maintainer asks "make a release" (`.claude/skills/release/SKILL.md`).
The same workflow also attaches `eme-web-vX.Y.Z.zip` (the web app and its server). eme.on8ar.eu
updates itself from it within about 5 minutes (`eme-web-update.timer` on the server). The maintainer
(ON8AR) can deploy at once with `web/deploy/deploy-release.sh vX.Y.Z`. A failed deploy rolls back by
itself and is not retried for that version until someone deploys that version by hand; newer
releases are still deployed. To stay on an older version, first run
`systemctl stop eme-web-update.timer` on the server (see `web/deploy/README.md`).

## Reporting a bug

Use the bug report form under *Issues > New issue*. Mention the app version (see the README or
the APK file name), your Android version and the tab where it happens.
