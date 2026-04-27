# CI & Build Directions

How to use the CI tooling introduced in sprint milestones S3–S15.

---

## Quick Reference

| Task | Command | Output |
|---|---|---|
| Run all unit tests (full) | `./gradlew testFullDebugUnitTest` | `**/build/test-results/**/*.xml` |
| Run all unit tests (FOSS) | `./gradlew testFossDebugUnitTest` | same |
| Run Detekt | `./gradlew detekt` | `build/reports/detekt/detekt.html` |
| Generate license report | `./gradlew :app:generateLicenseReport` | `docs/DEPENDENCY_LICENSES.md` |
| Generate SBOM | `./gradlew :app:cyclonedxBom` | `docs/sbom.json` |
| Security scan (BuildConfig) | `bash scripts/check-buildconfig-security.sh` | stdout |
| Assemble debug APK | `./gradlew assembleFullDebug` | `app/build/outputs/apk/full/debug/` |

---

## License Report (S14)

The license report uses `com.github.jk1.dependency-license-report` (v2.9).

```bash
./gradlew :app:generateLicenseReport
```

- Reads the `fullReleaseRuntimeClasspath configuration
- Writes `docs/DEPENDENCY_LICENSES.md`
- CI runs this on every push via the `license-report` job and uploads the artifact

If a dependency is missing a recognized license, the report flags it with `UNKNOWN`. Fix by adding the license to `gradle/libs.versions.toml` or the dependency's POM.

---

## SBOM (S15)

CycloneDX 1.6 JSON SBOM generation via `org.cyclonedx.bom` (v1.10.0).

```bash
./gradlew :app:cyclonefxBom
```

- Writes `docs/sbom.json`
- Attached to every GitHub release automatically
- Format: CycloneDX 1.6 JSON with full component tree and hashes

---

## Renovate Automerge Rules (S13)

The repo uses `renovate.json` with targeted automerge:

| Category | Automerge? | Example |
|---|---|---|

| GitHub Actions — minor/patch | ✅ Yes | `actions/checkout@v3` → `v4` |
| Test libraries — minor/patch | ✅ Yes | `junit:junit:4.13` → `4.13.2` |
| kotlinx libraries — patch only | ✅ Yes | `kotlinx-coroutines` patch bumps |
| Major version bumps | ❌ Manual review | `v2` → `v3` |
| Security-sensitive (networking/crypto) | ❌ Manual review | OkHttp, BouncyCastle, etc. |

If Renovate opens a PR and it matches automerge rules, CI must pass before it auto-merges. If a PR is blocked, it means a required check failed — investigate, don't override.

---

## Ktlint (S12)

Style checks run via Gradle, not a downloaded script:

```bash
./gradlew ktlintCheck
```

The `review-on-mention.yml` workflow runs this on comment-triggered reviews. If you want auto-format:

```bash
./gradlew ktlintFormat
```

---

## Required CI Checks

Four checks **must** pass before any PR merges to `main`:

1. **Security Check** — scans `BuildConfig` for hardcoded credentials
2. **Detekt** — zero-tolerance static analysis
3. **Unit Tests** — both `full` and `foss` flavors
4. **Assemble** — must compile for both flavors

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

- Full debug APK
- FOSS debug APK
- `docs/DEPENDENCY_LICENSES.md`
- `docs/sbom.json`

No manual attachment needed — the `release.yml` workflow handles it.
