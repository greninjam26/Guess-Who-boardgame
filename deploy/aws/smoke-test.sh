#!/usr/bin/env bash
#
# Checks a deployed Guess Who server from outside it.
#
# What this covers: that HTTPS works, that the server says online only when its
# database answers, and that failures reveal nothing about the inside. It runs
# on every deployment and needs nothing but curl.
#
# What this does not cover: whether the game is playable. Two people creating
# accounts, opening a room and finishing a game is a separate manual acceptance
# session — it needs two installed clients and somebody watching, and no script
# can stand in for it.
#
# Usage: bash smoke-test.sh https://your-host.duckdns.org

set -euo pipefail

base="${1:-}"
if [ -z "$base" ]; then
    echo "usage: $0 https://host" >&2
    exit 2
fi

case "$base" in
    https://*) ;;
    *)
        # Refused rather than warned about. Every request this makes carries no
        # secret, but a smoke test that quietly accepted http:// would report a
        # healthy deployment while proving nothing about the certificate the
        # installers are about to be built against.
        echo "FAIL: $base is not https, and this only tests a public HTTPS endpoint" >&2
        exit 2
        ;;
esac

failures=0
check() {
    if [ "$1" = "pass" ]; then
        printf '  ok    %s\n' "$2"
    else
        printf '  FAIL  %s\n' "$2"
        failures=$((failures + 1))
    fi
}

echo "smoke test: $base"

# --- the certificate is real ---------------------------------------------
# No -k. A self-signed or expired certificate has to fail here, because the
# desktop clients will not accept one either and this is the check standing in
# for them.
if curl -fsS --max-time 15 "$base/api/status" >/dev/null 2>&1; then
    check pass "HTTPS certificate is valid and the server answered"
else
    check fail "could not reach $base/api/status over HTTPS"
fi

# --- online means the database answered -----------------------------------
body="$(curl -fsS --max-time 15 "$base/api/status" 2>/dev/null || echo '{}')"
if printf '%s' "$body" | grep -q '"status"[[:space:]]*:[[:space:]]*"online"'; then
    check pass "status is online, which means PostgreSQL answered SELECT 1"
else
    check fail "status did not report online: $body"
fi

# --- the API says what version it speaks ----------------------------------
if curl -fsSI --max-time 15 "$base/api/status" 2>/dev/null | grep -qi '^x-api-version:'; then
    check pass "the server states its API version"
else
    check fail "no X-Api-Version header, so a stale client cannot be told to update"
fi

# --- failures reveal nothing ----------------------------------------------
# A deliberately malformed sign-in. The response must be a refusal, not a tour
# of the stack behind it.
error_body="$(curl -sS --max-time 15 -X POST "$base/api/sessions" \
    -H 'Content-Type: application/json' \
    -d '{"username":' 2>/dev/null || true)"
leaked=""
for word in Exception "at org.springframework" "at com.guesswho" jdbc postgresql Caused; do
    if printf '%s' "$error_body" | grep -qi -- "$word"; then
        leaked="$leaked $word"
    fi
done
if [ -z "$leaked" ]; then
    check pass "a malformed request reveals no implementation detail"
else
    check fail "the error body leaked:$leaked"
fi

# --- nothing else is open -------------------------------------------------
host="${base#https://}"
host="${host%%/*}"
for port in 22 8080 5432; do
    # A refused or filtered connection is what should happen. Anything that
    # completes means the security group or a binding is wrong.
    if timeout 5 bash -c "</dev/tcp/$host/$port" 2>/dev/null; then
        check fail "port $port is reachable from the internet"
    else
        check pass "port $port is closed"
    fi
done

echo
if [ "$failures" -eq 0 ]; then
    echo "smoke test passed. This says the server is up and safe to talk to."
    echo "It does not say the game is playable — that is the manual two-client session."
    exit 0
fi
echo "smoke test failed with $failures problem(s)"
exit 1
