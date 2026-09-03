#!/usr/bin/env bash
#
# Checks what build-installer.sh accepts as GUESSWHO_SERVER_URL.
#
# The contract test beside this one reads the build script and asserts the
# endpoint reaches jpackage. That is a check on the plumbing. This is a check on
# the value that goes through it, and it runs the real script rather than
# grepping it, because the only thing that matters here is what the script does
# when it is handed a bad URL.
#
# What makes this worth its own file: the value is baked into every installer
# with --java-options and cannot be changed afterwards. A wrong one is not a
# build failure, it is a download that talks to the wrong place, or to the right
# place over a broken address, on somebody else's machine. Nothing downstream
# validates it — the client takes the property and joins paths onto it.
#
# Safe to run: the script is invoked with a stub mvn on PATH that prints no
# version, so every run stops at the version check, which is before the build
# and before anything is removed. Nothing is compiled, packaged or deleted.

set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
script="$here/../build-installer.sh"
failures=0

fail() { echo "FAIL: $1"; failures=$((failures + 1)); }

[ -f "$script" ] || { echo "FAIL: missing packaging/build-installer.sh"; exit 1; }

# The stub. mvn prints nothing, so the script cannot read a version and stops
# there; jdeps and jpackage are stubbed too so that a future reordering of the
# script cannot reach a real build through this test.
stubs="$(mktemp -d)"
trap 'rm -rf "$stubs"' EXIT
for tool in mvn jdeps jpackage; do
    printf '#!/bin/sh\nexit 0\n' > "$stubs/$tool"
    chmod +x "$stubs/$tool"
done

# Checked before anything is run, because a stub that is not on PATH means every
# case below quietly exercises the real build instead. A test whose safety rests
# on a stub has to prove the stub is the thing being used.
for tool in mvn jdeps jpackage; do
    resolved="$(PATH="$stubs:$PATH" command -v "$tool" || true)"
    if [ "$resolved" != "$stubs/$tool" ]; then
        echo "FAIL: $tool resolves to '$resolved', not the stub at $stubs/$tool"
        exit 1
    fi
    [ -x "$stubs/$tool" ] || { echo "FAIL: the $tool stub is not executable"; exit 1; }
done

# Where an accepted endpoint must stop: past every validation rule, at the
# stubbed version read. Asserting this is what distinguishes "the URL was
# accepted" from "the script fell over somewhere else for its own reasons".
stopped_at_maven="Could not read the project version from Maven"
accepted_marker="Installers will connect to"

# Runs the build script with one candidate endpoint. Both an accepted and a
# rejected endpoint exit 1 here — the first at the stubbed version read, the
# second at validation — so anything else is a failure this test must not
# swallow.
attempt() {
    local status=0
    attempt_output="$(GUESSWHO_SERVER_URL="$1" PATH="$stubs:$PATH" bash "$script" 2>&1)" \
        || status=$?
    if [ "$status" -ne 1 ]; then
        fail "build-installer.sh exited $status, which is neither validation nor the stubbed build: $1"
        printf '      %s\n' "$attempt_output"
        return 1
    fi
    return 0
}

accepts() {
    attempt "$1" || return 0
    if ! printf '%s' "$attempt_output" | grep -qF -- "$accepted_marker $1"; then
        fail "rejected a valid endpoint: $1"
        printf '      %s\n' "$(printf '%s' "$attempt_output" | head -2)"
        return 0
    fi
    if ! printf '%s' "$attempt_output" | grep -qF -- "$stopped_at_maven"; then
        fail "an accepted endpoint did not reach the build step: $1"
    fi
}

rejects() {
    attempt "$1" || return 0
    if printf '%s' "$attempt_output" | grep -qF -- "$accepted_marker"; then
        fail "accepted $2: $1"
        return 0
    fi
    if printf '%s' "$attempt_output" | grep -qF -- "$stopped_at_maven"; then
        fail "$2 was not refused before the build began: $1"
    fi
    if ! printf '%s' "$attempt_output" | grep -qF -- "GUESSWHO_SERVER_URL"; then
        fail "rejected $2 without saying which variable was wrong: $1"
    fi
    # The refusal must not quote the value back. A rejected endpoint can carry a
    # password in its userinfo, and this output goes to a CI log that outlives
    # the build and is readable by anybody who can see the repository.
    if printf '%s' "$attempt_output" | grep -qF -- "$1"; then
        fail "echoed the rejected value back into the log: $2"
    fi
}

# --- what a release actually sets -----------------------------------------
accepts "https://guess-who.duckdns.org"
accepts "https://guess-who.duckdns.org:8443"
accepts "https://203.0.113.5"
accepts "https://a-b.c-d.example"

# --- the scheme is case-insensitive, as it is everywhere else -------------
# RFC 3986 says schemes match case-insensitively and java.net.URI agrees, so a
# capitalised one is a valid endpoint, not a mistake to refuse.
accepts "HTTPS://guess-who.duckdns.org"
accepts "HttpS://guess-who.duckdns.org"

# --- length limits --------------------------------------------------------
# DNS: 63 octets per label, 253 for the name. A longer one cannot resolve on a
# player's machine, and the certificate authority will not issue for it either.
label63="$(printf '%063d' 0 | tr '0' 'a')"
label64="$(printf '%064d' 0 | tr '0' 'a')"
accepts "https://$label63.example"
rejects "https://$label64.example" "a label longer than 63 characters"
rejects "https://$label63.$label63.$label63.$label63" "a host longer than 253 characters"

# --- not https ------------------------------------------------------------
# The original check. Passwords cross this connection on every sign-in.
rejects "http://guess-who.duckdns.org" "a plain-http endpoint"
rejects "ftp://guess-who.duckdns.org" "a non-http scheme"
rejects "guess-who.duckdns.org" "an endpoint with no scheme"
rejects "//guess-who.duckdns.org" "a scheme-relative endpoint"

# --- credentials ----------------------------------------------------------
# This is the one that ships a secret. Userinfo in the baked-in URL is readable
# by anybody who downloads the installer, and it is sent to the host on every
# request.
rejects "https://user:hunter2@guess-who.duckdns.org" "an endpoint carrying credentials"
rejects "https://user@guess-who.duckdns.org" "an endpoint carrying a username"

# --- anything after the host ----------------------------------------------
# The client joins "/api/..." onto this value. A path, query or fragment
# survives the join and produces a URL no route matches, which surfaces as every
# online feature failing on an installed game and nowhere else.
rejects "https://guess-who.duckdns.org/" "a trailing slash"
rejects "https://guess-who.duckdns.org/api" "an endpoint with a path"
rejects "https://guess-who.duckdns.org?retry=1" "an endpoint with a query string"
rejects "https://guess-who.duckdns.org#fragment" "an endpoint with a fragment"

# --- whitespace -----------------------------------------------------------
# What a copy-paste out of a browser or a runbook adds. A repository variable
# keeps it, and it is invisible in the Actions UI that displays the value.
rejects "https://guess who.duckdns.org" "an endpoint containing a space"
rejects " https://guess-who.duckdns.org" "a leading space"
rejects "https://guess-who.duckdns.org " "a trailing space"
rejects "$(printf 'https://guess-who.duckdns.org\nhttps://elsewhere.example')" "an embedded newline"

# --- malformed hosts ------------------------------------------------------
rejects "https://" "an endpoint with no host at all"
rejects "https://-guess-who.duckdns.org" "a host label starting with a hyphen"
rejects "https://guess-who-.duckdns.org" "a host label ending with a hyphen"
rejects "https://guess-who..duckdns.org" "a host with an empty label"
rejects "https://.duckdns.org" "a host starting with a dot"
rejects "https://guess_who.duckdns.org" "a host containing an underscore"
rejects "https://[::1]" "an IPv6 literal, which nothing here is set up to serve"

# --- ports ----------------------------------------------------------------
rejects "https://guess-who.duckdns.org:" "an empty port"
rejects "https://guess-who.duckdns.org:https" "a non-numeric port"
rejects "https://guess-who.duckdns.org:0" "port zero"
rejects "https://guess-who.duckdns.org:70000" "a port above 65535"
rejects "https://guess-who.duckdns.org:8443:9" "two ports"

# --- and an unset variable is still the developer default -----------------
# Not a validation case but the one that must never become one: requiring the
# variable would make every local build ceremony, and this test would be the
# reason.
unset_status=0
unset_output="$(PATH="$stubs:$PATH" bash "$script" 2>&1)" || unset_status=$?
if [ "$unset_status" -ne 1 ]; then
    fail "with no endpoint set, build-installer.sh exited $unset_status rather than reaching the build"
fi
if ! printf '%s' "$unset_output" | grep -qF -- "GUESSWHO_SERVER_URL is not set"; then
    fail "an unset GUESSWHO_SERVER_URL no longer falls back to localhost"
fi

if [ "$failures" -eq 0 ]; then
    echo "server url validation: every endpoint case behaves as intended"
    exit 0
fi
echo "$failures failure(s)"
exit 1
