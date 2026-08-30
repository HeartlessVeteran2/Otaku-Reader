# FAQ

## Is this another Tachiyomi fork?

No — and that's the point. Otaku Reader is a **from-scratch app** (100% Kotlin, Jetpack Compose, clean architecture) that deliberately implements the Tachiyomi extension API, so it runs the same community extensions as Tachiyomi, Mihon, and Komikku without forking a decade of legacy code. Same ecosystem, new foundation.

## Where does the content come from?

From sources **you** install. The app ships with no sources and hosts no content.

There are two kinds, side by side in the same list: community-maintained **APK extensions** (the Tachiyomi/Mihon ecosystem — this is where the great majority of sources are) and **JavaScript sources** from the Mangayomi ecosystem, which need no APK install and run in a sandboxed process. Both come from repositories you add — see [Extensions & Repositories](/docs/guides/extensions).

## Does it phone home?

No. There are no analytics, no ads, no AI features, and no account system. The only network calls the app makes on its own are the ones you configure: library updates from your sources, tracker sync you've logged into, extension repo refreshes, and update checks against GitHub. Crash reporting exists but is **opt-in** and only works with a Sentry endpoint *you* supply.

## My repo URLs from Komikku — do they work?

Yes, unchanged. Same `index.min.json` format, same extensions. The app also asks each repo for a JavaScript index; a repo that doesn't serve one is perfectly normal and isn't treated as an error.

## Why does the app ask me to "trust" an extension?

Extensions are executable code. The first time you install one from a new signing certificate, the app shows the signer's hash and asks once. This is a security feature, not friction for its own sake — see the [trust model](/docs/guides/extensions#trust--provenance).

## Can I read my own files?

Yes — the **local source** reads CBZ/ZIP/EPUB from a folder you choose, and **OPDS** support connects to self-hosted servers like Komga and Kavita.

## Is my reading hidden from prying eyes?

If you want it to be: biometric/PIN **app lock**, **secure screen** (blocks screenshots and recents thumbnails in the reader), **incognito mode** (no history, no tracker updates), and locked categories.

## What Android versions are supported?

Android 8.0 (API 26) and up.

## Where do I report a bug or request a feature?

[GitHub Issues](https://github.com/Heartless-Veteran/Otaku-Reader/issues). The full privacy policy is in [PRIVACY.md](https://github.com/Heartless-Veteran/Otaku-Reader/blob/main/PRIVACY.md).
