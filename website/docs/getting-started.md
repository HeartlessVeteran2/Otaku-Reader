# Getting Started

Otaku Reader ships empty on purpose — it's a reader, not a content provider. Content comes from **sources** you choose to install: the same community APK extensions used by Tachiyomi, Mihon and Komikku, and JavaScript sources from the Mangayomi ecosystem. Both are added the same way.

## 1. Install the app

Grab the APK from the [Download](/download) page and install it. On first launch a short wizard covers:

- **Notifications** — so new-chapter updates and download progress can reach you.
- **Battery optimization** — exempting the app keeps background library updates reliable on aggressive OEMs.
- **Appearance** — pick system/light/dark right away; you can fine-tune themes later in Settings.
- **Extensions** — a pointer to the step below.

Everything in the wizard is skippable; nothing requires an account.

## 2. Add an extension repository

This is the one step that makes the app useful:

1. Go to **Browse → Extensions** and open the repository management screen.
2. Add the URL of an extension repository (an `index.min.json` endpoint). If you already use Komikku or Mihon, the same repo URLs work here — the formats are identical.
3. The repo's extensions appear in the available list, grouped by language.

If a repository fails to load, the app tells you *which* repo failed and keeps the others working — repos are isolated from each other.

## 3. Install a source

1. Pick an extension and tap install.
2. The first time you install from a new signer, Otaku Reader shows a **trust prompt** with the signing certificate hash. Extensions run real code, so the app never silently executes an unknown signer — approve it once and it's remembered.
3. The source now appears under **Browse**.

## 4. Find something to read

- **Browse** a source's popular/latest lists, or use **global search** to query every installed source at once.
- Open a title and tap the heart to add it to your **library**.
- Tap a chapter to start reading — swipe, tap the screen edges, or use volume keys to turn pages. Tap the center for the menu.

## 5. Make it yours

From here, the guides cover the rest:

- [Extensions & Repositories](/docs/guides/extensions) — trust, updates, NSFW gating
- [Library](/docs/guides/library) — categories, filters, custom covers
- [Reader](/docs/guides/reader) — modes, presets, color filters, comments
- [Tracking](/docs/guides/tracking) — connect MAL, AniList, and friends
- [Backups & Sync](/docs/guides/backups) — never lose your library
