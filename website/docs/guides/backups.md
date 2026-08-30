# Backups & Sync

Your library is yours — back it up like it.

## What's in a backup

A backup includes essentially everything you've done in the app:

- Library entries, categories (including per-category update schedules and locks), and reading progress
- Chapter read states, bookmarks, and your **chapter notes**
- **All your customizations** — edited titles/authors/descriptions/genres, per-manga reader settings (direction, mode, color filters, preload), completed/dropped flags
- **Tracker links** — your MyAnimeList, AniList, Kitsu, MangaUpdates and Shikimori entries, with their scores, statuses and progress — plus reading history and settings

The one deliberate exception: custom cover *image files* are device-local (the paths wouldn't exist on a new phone), so re-pick covers after restoring to a different device.

::: warning Tracker links needed backup format v5
Backups written before format **v5** did not contain tracker links at all, so restoring one leaves your library untracked and the tracker chips empty. That was a real bug, not a limitation — it is fixed, but a backup file made by an older build cannot have data in it that the older build never wrote. If you have an old backup you rely on, take a fresh one.
:::

## Local backup & restore

**Settings → Backup** creates a backup file wherever you point it (SAF — so SD cards and USB drives work). Restore from the same screen.

You can also pick exactly **which sections** go into a backup, or get applied from one, on both the backup and restore dialogs — library entries, chapters, categories, tracking, preferences, OPDS servers, feed, and tracker sync settings.

Backups are plain versioned JSON (currently **v5**), which is also why they are the app's data-portability story: anything that wants to read your library outside this app reads a backup, not the internal database. Newer app versions restore older backups cleanly.

## Scheduled cloud backup (WebDAV)

Point the app at any WebDAV server — Nextcloud, ownCloud, a NAS — and it uploads backups on a schedule. Credentials are stored encrypted. This is the recommended way to survive a lost phone.

## Coming from Tachiyomi?

The app imports **Tachiyomi/Mihon backup files** directly — library, categories, progress, and tracking links carry over. Combined with the shared extension ecosystem, switching takes minutes.

One thing to expect: an imported backup names *Tachiyomi's* sources. Entries whose source you haven't installed here will show as unavailable until you install the matching extension, or point them somewhere else with **Migration**.

## Reading-position sync

Mid-chapter positions queue locally and ride along with backups, so picking up on another device puts you close to where you left off.

## An honest note on privacy

Backups contain your full library and reading history. They're your data and they only go where you send them — but treat a backup file with the same care as the library itself.
