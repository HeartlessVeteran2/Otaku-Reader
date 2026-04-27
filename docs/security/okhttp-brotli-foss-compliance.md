# okhttp-brotli FOSS Compliance

## Summary

`com.squareup.okhttp3:okhttp-brotli` is used in `core/network` to enable
[Brotli](https://github.com/google/brotli) HTTP content-encoding alongside the default gzip.

This document records the licence and binary-blob review required for F-Droid submission.

## Licence

`okhttp-brotli` is published under the **Apache License 2.0**.
Source: https://github.com/square/okhttp/blob/master/LICENSE.txt

The native Brotli decoder bundled inside `okhttp-brotli` is
[google/brotli](https://github.com/google/brotli), also **MIT-licensed**.
Source: https://github.com/google/brotli/blob/master/LICENSE

Both licences are permissive and compatible with F-Droid's
[inclusion policy](https://f-droid.org/docs/Inclusion_Policy/).

## Native code review

`okhttp-brotli` ships a pre-compiled `.so` native library
(`libbrotli.so`) that decodes Brotli-compressed HTTP responses.

| Property | Value |
|---|---|
| Library name | `libbrotli.so` |
| Source project | https://github.com/google/brotli |
| Licence | MIT |
| Proprietary blobs | None – built entirely from Apache/MIT source |
| Non-free dependencies | None |

F-Droid's [scanner](https://gitlab.com/fdroid/fdroidserver) checks for
non-free blobs by examining embedded strings and ELF metadata.
`libbrotli.so` passes this check because it is compiled from fully
open-source code with no proprietary components.

## FOSS flavor

The FOSS build flavor does **not** disable `okhttp-brotli`. Brotli support
is a network-layer optimisation with no privacy or tracking implications,
and using it does not introduce any non-free dependencies.

If F-Droid's binary scanner raises a concern in the future, the mitigation
is to replace `BrotliInterceptor` in the FOSS flavor with a no-op interceptor
and accept the minor bandwidth increase. A placeholder is kept in
`core/network/src/foss/` for that purpose.

## Review date

First reviewed: 2026-04-27. Re-review recommended whenever `okhttp-brotli`
is updated to a new major version.
