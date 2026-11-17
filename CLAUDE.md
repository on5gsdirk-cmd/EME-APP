# EME APP: instructions for Claude

The rules for every change are in CONTRIBUTING.md. Read and follow them:

@CONTRIBUTING.md

## Before you start

- Run `git pull` on `main` first. Other people's pull requests get merged, so the local copy
  can be behind.
- Check the open issues and pull requests (`gh issue list`, `gh pr list`) to see whether the
  problem is already reported or someone is already working on it.
- Before you take an issue, read its comments. If someone has already claimed it, ask in the
  issue first. When you take it, leave a comment that you do.

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
- A new or changed text goes into all 15 `strings.xml` files.
- A change that users see goes into `app/` and `web/` in the same pull request. A change that
  only exists on one platform says so in the pull request.
- Run `npm test` in `web/` and `go test ./...` in `web/server` before a commit that touches `web/`.
- Stage files by name (`git add <file>`), not `git add -A` or `git add .`.
- Never force-push and never rewrite history that is already on GitHub.
- Merge a pull request only when the "Unit tests" check is green.
- Merge a pull request only after a review on it: a review comment or a GitHub review that says
  what was checked. The author may review their own pull request, also when Claude wrote it;
  a review by someone else is not required. For a release pull request, the maintainer's
  explicit "ok" on the release notes is the review.

## Never

- Never make the app or `web/server` send anything to logger.hb9q.ch other than the login, the
  read requests and the logout they do today. The HB9Q chat is read only, as agreed with HB9Q.
- Never make the app post anything to www.chris.org (N0UK chat). It is read only.
- Never make the app send anything to livecq.n6nu.org other than the GET request for the spots.
  N6NU Live CQ is read only.
- Never poll HB9Q, W3SZ, N6NU or N0UK more often than the current intervals. The web server
  obeys the same limits for all web users together.
- Never put a login, password or cookie in code, test data, a commit, an issue or a pull
  request.
- Never add an e-mail address to the test data; replace it by `xxxx@test.com`.
- Never put the signing key (`debug.keystore`) or its base64 text in the repository, a commit,
  an issue, a pull request or the chat, and never build or sign an APK with it. It is kept only
  as a secret in the GitHub environment `release`.

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
