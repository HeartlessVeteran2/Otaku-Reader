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

if [ "$#" -ge 1 ]; then
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
HASH=$(printf '%s' "${SALT}${PASSPHRASE}" | sha256sum | cut -d' ' -f1)

echo
echo "Paste this into DeveloperUnlock.passphraseSha256:"
echo
echo "    private val passphraseSha256: String = \"${HASH}\""
echo
