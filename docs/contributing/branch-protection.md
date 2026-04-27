# Branch Protection Rules

This document lists the branch protection settings that **must** be configured on `main` (and `develop`) for CI gates to be meaningful.

## Required status checks

The following CI jobs are required to pass before a PR can be merged:

| Check name | Workflow | Why required |
|---|---|---|
| `Security Check` | `ci.yml` → `security` job | Prevents secrets from shipping in BuildConfig |
| `Detekt` | `ci.yml` → `detekt` job | Enforces static analysis with zero tolerance |
| `Unit Tests` | `ci.yml` → `unit-tests` job | Both `full` and `foss` flavors must pass |
| `Assemble` | `ci.yml` → `assemble` job | Build must compile for both flavors |

## Settings to enable on `main`

In **Settings → Branches → Branch protection rules** for the `main` branch:

- [x] **Require a pull request before merging**
  - [x] Require approvals: 1
  - [x] Dismiss stale pull request approvals when new commits are pushed
- [x] **Require status checks to pass before merging**
  - [x] Require branches to be up to date before merging
  - Add the four check names listed above
- [x] **Require conversation resolution before merging**
- [x] **Do not allow bypassing the above settings**

## Rationale

Without enforced status checks, regressions can silently merge:

- A failing `foss` flavor test (e.g. broken `core:ai-noop` binding) goes undetected if only `full` tests run.
- A hardcoded API key slips through if the security script never runs.
- Detekt violations accumulate unchecked when the static analysis job is advisory only.

All four checks run in parallel (≈ 5–8 min wall-clock time on a typical PR).

## Adding a new required check

1. Add the job to `ci.yml`.
2. Enable it in branch protection (Settings → Branches).
3. Update this table.
