#!/usr/bin/env bash
#
# The forwarding-boundary rehearsal, through a real Caddy.
#
# Spring is configured to trust X-Forwarded-For because it binds to loopback and
# only Caddy can reach it. That trust is safe only if Caddy is also the only
# thing that can write the header — and Caddy appends by default, which would
# leave every address-keyed limit bypassable by anyone who sends one. The
# Caddyfile replaces instead. This checks that it does, against the real proxy.
#
# HTTP only, on temporary ports. No certificate is involved: the header handling
# under test is the same either way, and a local TLS setup would test Caddy's
# certificate machinery rather than this boundary.

set -uo pipefail

repo="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
work="$here/work"
springport=18083
caddyport=18443
echoport=18444

failures=0; checks=0
fail() { echo "   FAIL $1"; failures=$((failures + 1)); checks=$((checks + 1)); }
pass() { echo "   PASS $1"; checks=$((checks + 1)); }
check() { if [ "$1" = "true" ]; then pass "$2"; else fail "$2"; fi; }
say()  { echo "   . $1"; }
step() { echo; echo "== $1"; }

jar="$repo/server/target/server-1.0.0.jar"

if [ ! -f "$jar" ]; then
    echo "This rehearsal needs a built server jar at $jar."
    echo "Build it with: mvn install -DskipTests"
    exit 2
fi

require() {
    if [ ! -x "$1" ] && ! command -v "$1" >/dev/null 2>&1; then
        echo "This rehearsal needs $1."
        echo "$2"
        exit 2
    fi
}
require caddy "Install it with: brew install caddy"

spring_pid=""; caddy_pid=""; echo_pid=""
cleanup() {
    step "Cleanup"
    for pid in $caddy_pid $echo_pid $spring_pid; do
        if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
            kill "$pid" 2>/dev/null
        fi
    done
    sleep 1
    say "stopped caddy, the echo upstream and the server"
    say "kept: $work/caddy.log, $work/server.log, $work/Caddyfile"
}
trap cleanup EXIT

rm -rf "$work"; mkdir -p "$work"

step "0. A Caddy config built from the production one"
python3 "$here/make-caddyfile.py" "$repo/deploy/aws/Caddyfile" "$work/Caddyfile" \
    | sed 's/^/   . /'
check "$([ -s "$work/Caddyfile" ] && echo true || echo false)" "the config was generated"
caddy validate --config "$work/Caddyfile" --adapter caddyfile >/dev/null 2>&1
check "$([ $? -eq 0 ] && echo true || echo false)" "caddy validates it"
say "$(caddy version | head -1)"

step "1. The pieces, all on loopback"
SPRING_PROFILES_ACTIVE=aws \
SPRING_DATASOURCE_URL="jdbc:h2:file:$work/boundary-db;DB_CLOSE_ON_EXIT=FALSE" \
SPRING_DATASOURCE_USERNAME=sa SPRING_DATASOURCE_PASSWORD= \
nohup java -jar "$repo/server/target/server-1.0.0.jar" --server.port="$springport" \
    --spring.flyway.baseline-on-migrate=true > "$work/server.log" 2>&1 &
spring_pid=$!
nohup python3 "$here/echo-upstream.py" > "$work/echo.log" 2>&1 &
echo_pid=$!
nohup caddy run --config "$work/Caddyfile" --adapter caddyfile > "$work/caddy.log" 2>&1 &
caddy_pid=$!

up=false
for _ in $(seq 1 90); do
    [ "$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$springport/api/status")" = "200" ] \
        && { up=true; break; }
    sleep 1
done
check "$up" "the server is up on 127.0.0.1:$springport"
proxied="$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$caddyport/api/status")"
check "$([ "$proxied" = "200" ] && echo true || echo false)" \
    "Caddy proxies /api/status through to it (got $proxied)"

lan="$(ipconfig getifaddr en0 2>/dev/null)"
if [ -n "$lan" ]; then
    direct_lan="$(curl -s -m 3 -o /dev/null -w '%{http_code}' "http://$lan:$springport/api/status")"
    check "$([ "$direct_lan" = "000" ] && echo true || echo false)" \
        "the application itself is unreachable on the public interface ($lan:$springport)"
fi

step "2. What Caddy actually forwards, read from the upstream"
forged='203.0.113.9'
seen="$(curl -s -H "X-Forwarded-For: $forged" -H "Forwarded: for=$forged" \
    -H 'X-Real-IP: 203.0.113.99' "http://127.0.0.1:$echoport/")"
say "headers the upstream received: $seen"
xff="$(printf '%s' "$seen" | python3 -c "import json,sys; print(json.load(sys.stdin).get('X-Forwarded-For',''))")"
fwd="$(printf '%s' "$seen" | python3 -c "import json,sys; print(json.load(sys.stdin).get('Forwarded','<absent>'))")"
say "X-Forwarded-For seen upstream: '$xff'"
say "Forwarded seen upstream: '$fwd'"
check "$([ "$fwd" = "<absent>" ] && echo true || echo false)" \
    "Caddy strips a client-supplied RFC 7239 Forwarded header"
check "$([ "$xff" = "127.0.0.1" ] && echo true || echo false)" \
    "Caddy replaces X-Forwarded-For with the real remote address"
check "$(printf '%s' "$xff" | grep -qv "$forged" && echo true || echo false)" \
    "the forged address is nowhere in the forwarded header (it was not appended)"

step "3. Room limits are keyed on the account, not the address"
reg() { curl -s -o /dev/null -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -d "{\"username\":\"$1\",\"password\":\"a-good-password\"}" "http://127.0.0.1:$caddyport/api/accounts"; }
tok() { curl -s -X POST -H 'Content-Type: application/json' \
    -d "{\"username\":\"$1\",\"password\":\"a-good-password\"}" "http://127.0.0.1:$caddyport/api/sessions" \
    | python3 -c "import json,sys; print(json.load(sys.stdin).get('token',''))" 2>/dev/null; }
suffix="$(date +%s | tail -c 6)"
a="boundarya$suffix"; b="boundaryb$suffix"
say "register $a: $(reg "$a"), register $b: $(reg "$b")"
token_a="$(tok "$a")"; token_b="$(tok "$b")"
check "$([ -n "$token_a" ] && [ -n "$token_b" ] && echo true || echo false)" "both accounts signed in"

room() { curl -s -o /dev/null -w '%{http_code}' -X POST \
    -H "Authorization: Bearer $1" ${2:+-H "X-Forwarded-For: $2"} "http://127.0.0.1:$caddyport/api/rooms"; }
codes=""
for _ in $(seq 1 8); do codes="$codes $(room "$token_a")"; done
say "account A opening rooms:$codes"
check "$(printf '%s' "$codes" | grep -q 429 && echo true || echo false)" \
    "account A is eventually refused with 429"
forged_try="$(room "$token_a" 198.51.100.7)"
check "$([ "$forged_try" = "429" ] && echo true || echo false)" \
    "and a forged address does not buy account A another room (got $forged_try)"
b_try="$(room "$token_b")"
check "$([ "$b_try" = "201" ] && echo true || echo false)" \
    "while account B, from the same address, is served (got $b_try) — the limit is not address-keyed"

step "4. A rotating forged address does not defeat the sign-in limit"
seen_429=false; attempts=0
for i in $(seq 1 14); do
    code="$(curl -s -o /dev/null -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
        -H "X-Forwarded-For: 203.0.113.$i" \
        -d '{"username":"nobody","password":"wrong"}' "http://127.0.0.1:$caddyport/api/sessions")"
    attempts=$((attempts + 1))
    [ "$code" = "429" ] && { seen_429=true; break; }
done
say "through Caddy: 429 after $attempts attempts, each claiming a different address"
check "$seen_429" "the sign-in limit still bites through Caddy despite a forged address per request"

step "5. The same requests sent straight to the application"
# Not a defect: this is what the Caddyfile exists to prevent, and seeing it here
# is what makes the check above meaningful rather than a tautology.
direct_429=false
for i in $(seq 100 116); do
    code="$(curl -s -o /dev/null -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
        -H "X-Forwarded-For: 203.0.113.$i" \
        -d '{"username":"nobody","password":"wrong"}' "http://127.0.0.1:$springport/api/sessions")"
    [ "$code" = "429" ] && { direct_429=true; break; }
done
check "$([ "$direct_429" = "false" ] && echo true || echo false)" \
    "bypassing Caddy, a forged address per request is never limited — which is what Caddy is protecting"

echo
if [ "$failures" -eq 0 ]; then
    echo "CADDY BOUNDARY REHEARSAL PASSED: $checks checks"
else
    echo "CADDY BOUNDARY REHEARSAL FAILED: $failures of $checks checks"
fi
exit "$failures"
