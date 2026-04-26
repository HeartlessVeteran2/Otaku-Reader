# Release Checklist

Use this checklist when preparing a new release for distribution.

---

## Pre-Release (Code)

- [ ] All tests passing (`./gradlew test`)
- [ ] Detekt clean (`./gradlew detekt`)
- [ ] Version updated in `app/build.gradle.kts`:
  ```kotlin
  versionCode = X      // Increment by 1
  versionName = "X.Y.Z" // Semantic versioning
  ```
- [ ] CHANGELOG.md updated with new version section
- [ ] Release signing configured (see below)

---

## Release Signing Setup (One-time)

### Local Builds

1. **Generate keystore** (if not done):
   ```bash
   keytool -genkey -v -keystore ~/otaku-reader-release.keystore \
           -alias otaku-reader -keyalg RSA -keysize 2048 -validity 10000
   ```

2. **Create signing config**:
   ```bash
   cd Otaku-Reader
   cp keystore.properties.template keystore.properties
   # Edit keystore.properties with your actual values
   ```

3. **⚠️ BACKUP YOUR KEYSTORE** — Store `otaku-reader-release.keystore` in a secure location (password manager, encrypted drive). **If you lose this file, you cannot update your app.**

### CI/CD Builds (GitHub Actions)

1. Go to GitHub → Settings → Secrets and variables → Actions
2. Add these repository secrets:
   - `KEYSTORE_BASE64` — Run: `base64 -w 0 ~/otaku-reader-release.keystore`
   - `KEYSTORE_PASSWORD` — Your keystore password
   - `KEY_ALIAS` — `otaku-reader`
   - `KEY_PASSWORD` — Your key password

---

## Build Release APK

### Option 1: Local Build
```bash
./gradlew clean assembleRelease

# Output:
# app/build/outputs/apk/full/release/app-full-release.apk
# app/build/outputs/apk/foss/release/app-foss-release.apk
```

### Option 2: CI Build
1. Push to `main` with updated version
2. GitHub Actions builds automatically
3. Download artifacts from workflow run

---

## Create GitHub Release

1. Go to GitHub → Releases → Draft new release
2. **Tag version:** `vX.Y.Z` (matches versionName)
3. **Release title:** `Otaku Reader vX.Y.Z`
4. **Description:** Copy relevant section from CHANGELOG.md
5. **Attach APKs:**
   - `app-full-release.apk` (full features)
   - `app-foss-release.apk` (open source only)
6. **Mark as:** Pre-release (for beta) or Latest (for stable)
7. **Publish release**

---

## Post-Release

- [ ] Test install on clean device/emulator
- [ ] Verify onboarding flow works
- [ ] Verify extension installation works
- [ ] Verify reader functions (download chapter, read)
- [ ] Update any external links (website, social media)
- [ ] Announce to users (if applicable)

---

## Versioning Guide

| Version Change | When to Use | Example |
|----------------|-------------|---------|
| **Major (X.0.0)** | Breaking changes, major features | `1.0.0` → `2.0.0` |
| **Minor (x.Y.0)** | New features, backwards compatible | `1.0.0` → `1.1.0` |
| **Patch (x.y.Z)** | Bug fixes, small improvements | `1.0.0` → `1.0.1` |

---

## Quick Reference

| Task | Command |
|------|---------|
| Run tests | `./gradlew test` |
| Check style | `./gradlew detekt` |
| Build debug | `./gradlew assembleDebug` |
| Build release | `./gradlew assembleRelease` |
| Install debug | `adb install app/build/outputs/apk/debug/app-debug.apk` |
| Install release | `adb install app/build/outputs/apk/full/release/app-full-release.apk` |

---

## Emergency: Lost Keystore

**If you lose your keystore, you cannot update the existing app.** Users will need to:
1. Uninstall the old version (losing data)
2. Install the new version with new signature

**Prevention:**
- Store keystore in password manager
- Keep backup on encrypted external drive
- Upload to secure cloud storage (encrypted)

---

*Last updated: April 16, 2026*
