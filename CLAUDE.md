# EME APP: instructions for Claude

The rules for every change are in CONTRIBUTING.md. Read and follow them:

@CONTRIBUTING.md

## Before you start

- Run `git pull` on `main` first. Other people's pull requests get merged, so the local copy
  can be behind.
- Check the open issues and pull requests (`gh issue list`, `gh pr list`) to see whether the
  problem is already reported or someone is already working on it.

## How to make a change

- Never commit on `main` and never push to `main` directly, also not as the owner. Create a
  branch (`fix/...` or `feat/...`), commit there, push the branch and open a pull request.
- One logical change per branch and per pull request. Link the issue in the pull request:
  `Fixes #12` when it solves the whole issue (GitHub closes the issue on merge), `Part of #12`
  when it solves a part. After merging the last part of an issue, close it by hand with a
  comment that links the pull requests.
- Run `./gradlew testDebugUnitTest` before every commit. Do not commit when a test fails.
- A change in logic comes with a unit test. A bug fix comes with a test that fails without
  the fix.
- A new or changed text goes into all 13 `strings.xml` files.
- Stage files by name (`git add <file>`), not `git add -A` or `git add .`.
- Never force-push and never rewrite history that is already on GitHub.
- Merge a pull request only when the "Unit tests" check is green.

## Never

- Never make the app send anything to logger.hb9q.ch other than the login and the read
  requests it does today. The HB9Q chat is read only, as agreed with HB9Q.
- Never poll HB9Q or W3SZ more often than the current intervals.
- Never put a login, password or cookie in code, test data, a commit, an issue or a pull
  request.
- Never add an e-mail address to the test data; replace it by `xxxx@test.com`.

## Releases

- When the maintainer asks for a release ("make a release", "maak een release"), follow
  `.claude/skills/release/SKILL.md`.
- A fix or feature pull request never changes `versionCode` or `versionName` and never adds
  an APK. Only the release pull request changes the version, together with
  `docs/releases/vX.Y.Z.md`.
- Merging the release pull request publishes the release: `.github/workflows/release.yml`
  builds the APK, checks its signing certificate and creates the GitHub release. Never build
  an APK by hand to share, and never delete or move a published release or tag.

## Communication

Say in plain words which git or GitHub step you are about to take and why, before you take it.
Say what you tested and what you did not test.
