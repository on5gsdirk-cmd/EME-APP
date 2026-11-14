---
name: release
description: Prepare a new EME APP release - choose the version number, bump versionCode and versionName, write the release notes and open the release pull request. Use when the maintainer says "make a release" or "maak een release".
---

# Release

GitHub builds, signs and publishes the APK by itself (`.github/workflows/release.yml`) as soon as
a new `versionName` lands on `main`. This skill prepares that pull request. Never build an APK to
share by hand and never commit one.

Say in plain words what you are about to do before each git or GitHub step (see CLAUDE.md).

## 1. What is new since the last release

```bash
git checkout main && git pull
gh release list --limit 1 --exclude-pre-releases --json tagName,publishedAt
```

- The last release is that tag. Before the first GitHub release (v0.16 was committed as an APK
  file), the starting point is commit `069841a` ("Add app download (apk/EME-APP.apk, v0.16)").
- List the merged pull requests since then:
  `gh pr list --state merged --base main --limit 100 --search "merged:>=<date>" --json number,title,body`
  where `<date>` is the date of that tag or commit (`git log -1 --format=%cs <ref>`).
- Nothing a user would notice (only tests, CI, docs)? Say so and stop: no release.

## 2. Choose the version

`MAJOR.MINOR.PATCH`. `versionCode` always goes up by 1.

| Since the last release | Example | Version |
|---|---|---|
| Only bug fixes | login error fixed | 0.17.0 -> 0.17.1 |
| Something new, everything else still works | N0UK chat added | 0.17.1 -> 0.18.0 |
| Users must redo something, or "version 1" | first complete version | 0.x -> 1.0.0 |

- The first release after 0.16 is 0.17.0 (or 0.16.1 if it holds only fixes).
- 1.0.0 only when the maintainer asks for it.
- A test release gets a suffix, for example `0.17.0-test1`. It is published as a pre-release and
  the "latest" download link keeps pointing to the last real release.

Tell the maintainer the version and the reason in one sentence before you continue.

## 3. Make the release pull request

1. Branch from `main`: `git checkout -b release/vX.Y.Z`.
2. In `app/build.gradle.kts`: `versionCode` + 1, `versionName = "X.Y.Z"`. Nothing else.
3. Write `docs/releases/vX.Y.Z.md` (the workflow refuses to publish without it):

   ```markdown
   # EME APP X.Y.Z

   One sentence: what this release is about.

   ## New
   - What the user can do now, in plain words. (#PR)

   ## Fixed
   - What went wrong before and works now, as the user saw it. (#PR)

   ## Updating
   - Installs over the previous version; settings and HB9Q login are kept.
   ```

   - Written for radio amateurs, not developers: no class names, no file names.
   - Every line links its pull request. Leave out sections that are empty.
   - Changes users never see (tests, CI, refactoring) get at most one line, "Under the hood".
   - Mention any step the user must take under "Updating".
4. One commit with both files, message `release: vX.Y.Z`. Stage both files by name.
5. Push and open the pull request, title `release: vX.Y.Z`, body: the release notes, then
   "Merging this pull request publishes the release."

## 4. Wait for "ok"

Show the maintainer the release notes and the pull request link. Change them on request.
Merge only after an explicit "ok" and with the "Unit tests" check green:
`gh pr merge <number> --merge`.

## 5. After the merge

```bash
gh run list --workflow release.yml --limit 1
gh run watch <id>
```

- Green: give the release link and the download link
  `https://github.com/on5gsdirk-cmd/EME-APP/releases/latest/download/EME-APP.apk`.
- Red: nothing was published. Show the failing step and its error. Fix it in a new pull request;
  a fix to the release notes only can be re-run with `gh workflow run release.yml`.
- Never delete, move or reuse a tag or release that was published: users may have installed it.
  A wrong release is followed by a new patch release.
