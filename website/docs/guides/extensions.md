# Extensions & Repositories

Otaku Reader gets its content from **sources** you install yourself. There are two kinds, and they sit side by side in the same list — you install, browse, and read from them the same way.

| | Where it comes from | What it is |
|---|---|---|
| **APK extensions** | The Tachiyomi/Mihon/Komikku ecosystem (Keiyoushi, Komikku, and any repo you add) | Signed Android packages, loaded in an isolated classloader |
| **JavaScript sources** | The Mangayomi ecosystem | Plain `.js` files, run in a sandboxed engine in a separate process |

APK extensions carry the overwhelming majority of the catalogue — several hundred sources — and are what most people will use. JavaScript sources are a smaller, newer set that needs no APK install at all.

## Repositories

A repository is a URL serving an extension index. Add as many as you like under **Browse → Extensions → Repositories**.

- Repos used in Komikku/Mihon work unchanged.
- The same screen handles both kinds. When you add a repo, the app asks it for both an APK index (`index.min.json`) and a JavaScript index (`index.json`); a repo that only serves one is normal and is not reported as an error.
- Each repo is fetched independently — one broken or unreachable repo never blocks the others, and the failure message names the repo at fault.
- Removing a repo removes its available extensions from the list; already-installed extensions keep working.

## JavaScript sources

These come from the Mangayomi ecosystem and are ordinary JavaScript files rather than installable packages.

- **They run in a separate process**, in a sandboxed engine with no access to your library, your files, or the app's own memory. Everything a source can do — HTTP requests, HTML parsing — goes through a narrow, checked bridge.
- **Requests are held to the same rules as everywhere else**: HTTPS only, private and loopback addresses refused, and every redirect hop checked *before* it is followed rather than after.
- **Published sources run unmodified.** If one misbehaves, that's the app's runtime to fix, not the source.
- **Per-source preferences** (mirrors, base-URL overrides) work as the source's author intended, including when they later change a default you never touched.

Be aware of the size difference: the JavaScript half of the Mangayomi ecosystem is around **16 working sources**, roughly ten of them English-facing. It is a useful addition, not a replacement for the APK catalogue — which is exactly why both ship.

## Trust & provenance

Extensions execute code, so the app treats signers carefully:

- **Trust prompts** — the first APK install from an unknown signing certificate shows its SHA-256 hash and asks for confirmation. Trusted signers are remembered (in encrypted storage) and can be revoked.
- **Provenance tracking** — the app records which repository each extension came from. If an update is offered from a *different* repo than the one it was installed from, you get a warning before anything changes.
- **Signer-change detection** — if an installed extension's signing certificate changes, it's flagged in the list with a warning.
- **Blocklist** — known-bad extensions are filtered out automatically via a daily-refreshed blocklist.

JavaScript sources have no signing certificate to check, which is why they are sandboxed and process-isolated instead. Different mechanism, same goal.

## Updates

Extension updates are checked in the background (WorkManager) and surfaced in the extensions list. Updating preserves your trust decisions and source settings.

## NSFW content

Sources flagged 18+ are hidden until you enable NSFW content in settings. The toggle also gates features that depend on adult sources, such as E-Hentai favorites sync.

## When a source misbehaves

- **Source health diagnostics** — Browse tracks per-source failures and shows a warning badge with a diagnostic sheet explaining what went wrong. Both kinds of source route through it.
- **WebView fallback** — sources behind Cloudflare can open a WebView challenge; the solved session cookies are shared back to the app's network stack automatically.
- **Most failures are the site, not the app** — a moved domain, an outage, or a Cloudflare interstitial. The diagnostic sheet says which.
