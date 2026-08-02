# CI & Build Directions

How to use the CI tooling introduced in sprint milestones S3–S15.

---

## Quick Reference

| Task | Command | Output |
|---|---|---|
| Run all unit tests | `./gradlew testDebugUnitTest` | `**/build/test-results/**/*.xml` |
| Run Detekt | `./gradlew detekt` | `build/reports/detekt/detekt.html` |
| Generate license report | `./gradlew :app:generateLicenseReport` | `docs/DEPENDENCY_LICENSES.md` |
| Generate SBOM | `./gradlew :app:cyclonedxBom` | `docs/sbom.json` |
| Security scan (BuildConfig) | `bash scripts/check-buildconfig-security.sh` | stdout |
| Assemble debug APK | `./gradlew assembleDebug` | `app/build/outputs/apk/debug/` |

---

## License Report (S14)

The license report uses `com.github.jk1.dependency-license-report` (v2.9).

```bash
./gradlew :app:generateLicenseReport
```

- Reads the `releaseRuntimeClasspath` configuration
- Writes `docs/DEPENDENCY_LICENSES.md`
- CI runs this on every push via the `license-report` job and uploads the artifact

If a dependency is missing a recognized license, the report flags it with `UNKNOWN`. Fix by adding the license to `gradle/libs.versions.toml` or the dependency's POM.

---

## SBOM (S15)

CycloneDX 1.6 JSON SBOM generation via `org.cyclonedx.bom` (v1.10.0).

```bash
./gradlew :app:cyclonedxBom
```

- Writes `docs/sbom.json`
- Attached to every GitHub release automatically
- Format: CycloneDX 1.6 JSON with full component tree and hashes

---

## Build Toolchain Version Matrix

The Kotlin / KSP / Compose Compiler / AGP versions are tightly coupled. Drift between them is the #1 source of "won't build" pain. The single source of truth lives in [`gradle/libs.versions.toml`](../../gradle/libs.versions.toml); these values must be changed together.

| Component | Version key in `libs.versions.toml` | Current | Notes |
|---|---|---|---|
| Kotlin | `kotlin` | `2.3.21` | Drives `kotlin-android`, `kotlin-jvm`, `kotlin-serialization`, and the `kotlin-compose` plugin (Compose Compiler is shipped by the Kotlin Compose plugin and tracks the Kotlin version automatically) |
| KSP | `ksp` | `2.3.7` | Standalone versioning since Kotlin 2.3.x — verify on the [KSP releases page](https://github.com/google/ksp/releases) before bumping. Must be from the same Kotlin major.minor series |
| Compose Compiler plugin | (uses `kotlin` ref) | tracks `kotlin` | Pinned via `compose-gradlePlugin` library and `kotlin-compose` plugin entries — do not pin separately |
| AGP | `agp` | `9.1.1` | Drives `com.android.application`, `com.android.library`, `com.android.test` |
| Compose BOM | `compose-bom` | `2026.04.01` | Manages all `androidx.compose.*` artifact versions |
| Hilt | `hilt` | `2.59.2` | Plugin and runtime share the same key |
| Room | `room` | `2.8.4` | Plugin, runtime, ktx, paging, testing, and compiler share the same key |

### SDK API Levels

Android SDK levels are also centralised in `gradle/libs.versions.toml` and read by every convention plugin via `AndroidConfig.kt` in `build-logic/`. **Never hardcode `compileSdk`, `targetSdk`, or `minSdk` in a plugin or module `build.gradle.kts`.**

| Key | Current | Purpose |
|---|---|---|
| `compile-sdk` | `36` | `compileSdk` for all library and application modules |
| `target-sdk` | `36` | `targetSdk` for application modules only |
| `min-sdk` | `26` | `minSdk` for all modules |

To upgrade API levels:

1. Update `compile-sdk`, `target-sdk`, and/or `min-sdk` in `gradle/libs.versions.toml`.
2. Run `./gradlew assembleDebug --warning-mode all` and confirm zero API-level warnings.
3. Commit only the TOML change — no plugin files need editing.

### Bumping the toolchain

1. Pick a target Kotlin version.
2. Look up the matching KSP release on https://github.com/google/ksp/releases — KSP releases are tagged `<kotlin>-<ksp>` (e.g. `2.1.0-1.0.29`); for Kotlin 2.3.x the KSP version is standalone (e.g. `2.3.7`).
3. Confirm the AGP version supports your Kotlin version on the [AGP / Kotlin compatibility table](https://developer.android.com/build/releases/gradle-plugin#compatibility).
4. Update `kotlin`, `ksp`, and `agp` in `gradle/libs.versions.toml` in the **same commit**.
5. Run `./gradlew assembleDebug` locally — verify there are no version-mismatch warnings from KSP or the Compose Compiler.

Renovate is configured to group bumps to Kotlin, KSP, the Compose Compiler plugin, Compose BOM, and AGP into a single PR (`kotlin-ksp-compose-agp toolchain`) and to never auto-merge them. See the rule in [`renovate.json`](../../renovate.json).

---

## Renovate Automerge Rules (S13)

The repo uses `renovate.json` with targeted automerge:

| Category | Automerge? | Example |
|---|---|---|

| GitHub Actions — minor/patch | ✅ Yes | `actions/checkout@v3` → `v4` |
| Test libraries — minor/patch | ✅ Yes | `junit:junit:4.13` → `4.13.2` |
| kotlinx libraries — patch only | ✅ Yes | `kotlinx-coroutines` patch bumps |
| Major version bumps | ❌ Manual review | `v2` → `v3` |
| Kotlin / KSP / Compose plugin / Compose BOM / AGP toolchain | ❌ Manual review (grouped) | Always bumped together in one PR |
| Security-sensitive (networking/crypto) | ❌ Manual review | OkHttp, BouncyCastle, etc. |

If Renovate opens a PR and it matches automerge rules, CI must pass before it auto-merges. If a PR is blocked, it means a required check failed — investigate, don't override.

---

## Ktlint (S12)

Style checks run via Gradle, not a downloaded script:

```bash
./gradlew ktlintCheck
```

The `ktlint` job in `ci.yml` runs this on every PR. If you want auto-format:

```bash
./gradlew ktlintFormat
```

---

## Required CI Checks

Five checks **must** pass before any PR merges to `main`:

1. **Security Check** — scans `BuildConfig` for hardcoded credentials
2. **Detekt** — zero-tolerance static analysis
3. **Ktlint** — code style enforcement (`./gradlew ktlintCheck`)
4. **Unit Tests** — `./gradlew testDebugUnitTest`
5. **Assemble** — `./gradlew assembleDebug` must succeed

See `branch-protection.md` for how to add a new required check to the branch protection settings.

---

## Adding a New Required CI Check

1. Add the job to `.github/workflows/ci.yml`
2. Update `docs/contributing/branch-protection.md` — add the check to the table
3. In GitHub UI: Settings → Branches → `main` → Add the check name to required status checks
4. Open a PR with both the workflow change and the doc update

---

## Release Artifacts

On every release, the following are attached automatically:

- Debug APK
- `docs/DEPENDENCY_LICENSES.md`
- `docs/sbom.json`

No manual attachment needed — the `release.yml` workflow handles it.
