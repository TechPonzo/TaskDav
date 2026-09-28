# Contributing to TaskDav

Thanks for helping. TaskDav is GPLv3 — by opening a PR you agree your contribution is licensed under the same terms.

## Branches

| Branch | Purpose |
|--------|---------|
| `main` | Stable / release-ready line. Prefer green CI. |
| `develop` | Integration branch for the next release. Day-to-day work lands here. |
| `feature/…` | New features (branched from `develop`) |
| `fix/…` | Bug fixes (branched from `develop`, or from `main` for hotfixes) |
| `docs/…` | README, metadata, translations-only changes |

**Workflow**

1. Fork the repo (or clone if you have write access).
2. Branch from `develop` for normal work:  
   `git checkout develop && git pull && git checkout -b feature/short-name`
3. Open a PR **into `develop`**.
4. Maintainers merge `develop` → `main` when cutting a release.

Small doc-only fixes may target `main` directly if they do not change app behavior.

## Pull requests

- Keep PRs focused (one concern per PR when you can).
- Describe *why*, not only *what*.
- UI strings must go through `res/values/strings.xml` (and `values-es` / `values-it` when you add user-visible text).
- CI must pass (`assembleDebug` on GitHub Actions).
- Do not commit secrets, personal CalDAV URLs, or real account data.
- Screenshots: use the debug demo seeder (see README), never personal tasks.

## Local build

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)   # JDK 17+
export ANDROID_HOME=~/Library/Android/sdk
./gradlew assembleDebug
```

## Demo data for screenshots

Debug builds only:

```bash
adb shell am broadcast -n app.taskdav/.debug.DemoSeedReceiver -a app.taskdav.debug.SEED_DEMO
./scripts/capture-screenshots.sh
```

## Issues

Use the bug / feature templates when possible. Search existing issues first.

## Code map

| Path | Role |
|------|------|
| `app/src/main/java/app/taskdav/caldav/` | CalDAV + iCalendar |
| `app/src/main/java/app/taskdav/domain/` | Repository / task tree |
| `app/src/main/java/app/taskdav/ui/` | Compose screens |
| `app/src/main/res/values*/strings.xml` | EN / ES / IT strings |
| `app/src/debug/` | Debug-only helpers (demo seeder) |
