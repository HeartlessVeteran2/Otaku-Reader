#!/usr/bin/env bash
# Generate the developer-screen passphrase hash for DeveloperUnlock.kt.
#
# Usage:  tools/devcode/devcode.sh "your passphrase"
#         tools/devcode/devcode.sh            # prompts without echoing
#
# Paste the printed value into `passphraseSha256` in
# core/common/src/main/java/app/otakureader/core/common/developer/DeveloperUnlock.kt
#
# The passphrase itself is never written to disk by this script. Note that passing it as an
# argument leaves it in your shell history — prefer the prompting form.

set -euo pipefail

SALT="otaku-reader/developer/v1:"

if [ "$#" -gt 1 ]; then
    # `devcode.sh my secret phrase` would otherwise hash only "my" and print a perfectly valid
    # digest for the wrong passphrase — you would paste it, and then never be able to unlock with
    # what you typed. There is no way to tell that apart from a correct run afterwards, so refuse.
    echo "Too many arguments. Quote the passphrase, or pass none to be prompted:" >&2
    echo "    tools/devcode/devcode.sh \"my secret phrase\"" >&2
    exit 1
fi

if [ "$#" -eq 1 ]; then
    PASSPHRASE="$1"
else
    read -r -s -p "Passphrase: " PASSPHRASE
    echo
    read -r -s -p "Confirm:    " CONFIRM
    echo
    if [ "$PASSPHRASE" != "$CONFIRM" ]; then
        echo "Passphrases do not match." >&2
        exit 1
    fi
fi

if [ -z "$PASSPHRASE" ]; then
    echo "Refusing to hash an empty passphrase — DeveloperUnlock treats blank as unconfigured." >&2
    exit 1
fi

# printf %s rather than echo: no trailing newline, so the hash matches what Kotlin computes over
# SALT + passphrase exactly.
#
# sha256sum is GNU coreutils and is absent on macOS, which ships `shasum` instead. Without this
# the documented generator would fail with "command not found" on a Mac before printing anything.
if command -v sha256sum >/dev/null 2>&1; then
    HASH=$(printf '%s' "${SALT}${PASSPHRASE}" | sha256sum | cut -d' ' -f1)
elif command -v shasum >/dev/null 2>&1; then
    HASH=$(printf '%s' "${SALT}${PASSPHRASE}" | shasum -a 256 | cut -d' ' -f1)
else
    echo "Need sha256sum or shasum to generate the digest; found neither." >&2
    exit 1
fi

echo
echo "Paste this into DeveloperUnlock.passphraseSha256:"
echo
echo "    private const val passphraseSha256: String = \"${HASH}\""
echo
