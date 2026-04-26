# Source API Module

This module defines the public source-api surface used by Otaku Reader to
describe a manga source and its data model. It is the lightweight,
Android-free contract that domain and data layers depend on.

## Purpose

`source-api` provides the abstract types that both first-party adapters and
Tachiyomi-compatible loaders implement:

- `Source` / `HttpSource` — entry points for a manga source
- `MangaSource` — registry/identity of a source
- `SManga`, `SChapter`, `Page` — DTO-shaped source models
- `MangasPage` — paginated browse results
- `Filter` — search / filter primitives

## Module type

Pure `kotlin-library` (no Android dependencies). Depends only on:

- `kotlinx.coroutines.core`
- `kotlinx.serialization.json`
- `okhttp` (for `HttpSource`)

This keeps the contract usable from any layer (domain, data, core,
tachiyomi-compat) without dragging in the Android framework.

## Relationship to Tachiyomi compatibility

The shapes here mirror the equivalent Tachiyomi `source` package types so that
existing Tachiyomi/Mihon extension APKs can be adapted at runtime by
`core/tachiyomi-compat` without modification. **Do not change the public
signatures in this module without verifying against the Tachiyomi extension API
spec** — breaking them breaks the extension ecosystem the app depends on.

See [`core/tachiyomi-compat/README.md`](../core/tachiyomi-compat/README.md) for
how these types are bridged to loaded extension APKs.
