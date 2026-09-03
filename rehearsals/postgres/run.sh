#!/usr/bin/env bash
#
# The PostgreSQL rehearsal: the server running on the database it will be
# deployed to, in the profile it will be deployed under.
#
# Everything here is disposable. The cluster is created by this script in its
# own directory, listens on loopback only on a non-default port, and is removed
# at the end. It never touches an existing PostgreSQL installation, and the
# server under test is pointed at it explicitly so the developer's H2 database
# is not opened at all.

set -uo pipefail

repo="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
work="$here/work"
pgbin="${GUESSWHO_PGBIN:-/opt/homebrew/opt/postgresql@15/bin}"

pgport=55432
apiport=18081
db=guesswho_rehearsal
dbuser=guesswho
dbpass=rehearsal_only_not_a_secret

failures=0
checks=0
server_pid=""

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
require "$pgbin/initdb" "Install it with: brew install postgresql@15 (or set GUESSWHO_PGBIN)"

psql_q() {
    PGPASSWORD="$dbpass" "$pgbin/psql" -h 127.0.0.1 -p "$pgport" -U "$dbuser" \
        -d "$db" -tAc "$1" 2>/dev/null
}

cleanup() {
    step "Cleanup"
    if [ -n "$server_pid" ] && kill -0 "$server_pid" 2>/dev/null; then
        kill "$server_pid" 2>/dev/null
        wait "$server_pid" 2>/dev/null
        say "stopped the Spring server (pid $server_pid)"
    fi
    if [ -d "$work/data" ]; then
        "$pgbin/pg_ctl" -D "$work/data" -m fast stop >/dev/null 2>&1 \
            && say "stopped the disposable PostgreSQL cluster"
        rm -rf "$work/data"
        say "removed the disposable cluster directory"
    fi
    [ -n "${sockdir:-}" ] && rm -rf "$sockdir" && say "removed the socket directory"
    say "kept for inspection: $work/server.log, $work/postgres.log"
}
trap cleanup EXIT

rm -rf "$work"
mkdir -p "$work"
# PostgreSQL's Unix socket path cannot exceed 103 bytes, which a checkout
# nested a few directories deep exceeds on its own, so the socket directory
# lives somewhere short. Everything here connects over TCP on loopback anyway.
sockdir="$(mktemp -d /tmp/gwpg.XXXXXX)"

# --- what the developer's own database looks like before any of this -------
h2_before="$(ls "$repo"/guess-who-data* 2>/dev/null | wc -l | tr -d ' ')"
h2_sum_before="$(shasum "$repo"/guess-who-data.mv.db 2>/dev/null | awk '{print $1}')"

step "0. A disposable PostgreSQL 15 cluster"
LC_ALL=C LANG=C "$pgbin/initdb" -D "$work/data" -U postgres --auth=trust -E UTF8 \
    > "$work/initdb.log" 2>&1
initdb_status=$?
check "$([ "$initdb_status" -eq 0 ] && echo true || echo false)" "initdb created a cluster"
LC_ALL=C LANG=C "$pgbin/pg_ctl" -D "$work/data" -l "$work/postgres.log" \
    -o "-p $pgport -k $sockdir -c listen_addresses=127.0.0.1" -w start >/dev/null 2>&1
start_status=$?
if [ "$start_status" -ne 0 ]; then
    fail "the cluster did not start on 127.0.0.1:$pgport"
    tail -5 "$work/postgres.log" 2>/dev/null | sed 's/^/      /'
    exit 1
fi
pass "the cluster started on 127.0.0.1:$pgport"
say "$("$pgbin/postgres" --version)"

"$pgbin/psql" -h 127.0.0.1 -p "$pgport" -U postgres -d postgres -q \
    -c "CREATE ROLE $dbuser LOGIN PASSWORD '$dbpass';" >/dev/null 2>&1
"$pgbin/createdb" -h 127.0.0.1 -p "$pgport" -U postgres -O "$dbuser" "$db" >/dev/null 2>&1
check "$([ "$(psql_q 'SELECT 1')" = "1" ] && echo true || echo false)" \
    "the rehearsal database answers as the application role"

step "1. Every migration, from an empty database, through the existing test"
cd "$repo" || exit 1
POSTGRES_TEST_URL="jdbc:postgresql://127.0.0.1:$pgport/$db" \
POSTGRES_TEST_USER="$dbuser" \
POSTGRES_TEST_PASSWORD="$dbpass" \
mvn --batch-mode --no-transfer-progress -pl server -am \
    -Dtest=PostgresMigrationTest -DfailIfNoTests=false \
    -Dsurefire.failIfNoSpecifiedTests=false test > "$work/migration-test.log" 2>&1
migration_status=$?
check "$([ $migration_status -eq 0 ] && echo true || echo false)" \
    "PostgresMigrationTest passed against this cluster"
report="server/target/surefire-reports/com.guesswho.persistence.PostgresMigrationTest.txt"
if [ -f "$report" ]; then
    say "$(grep -o 'Tests run.*' "$report" | head -1)"
    grep -q "Skipped: 0" "$report" \
        && pass "the test actually ran rather than skipping" \
        || fail "the test skipped, so it proved nothing"
fi

step "2. What the migrations built"
applied="$(psql_q "SELECT count(*) FROM flyway_schema_history WHERE success")"
say "migrations applied: $applied"
say "versions: $(psql_q "SELECT string_agg(version, ', ' ORDER BY installed_rank) FROM flyway_schema_history WHERE version IS NOT NULL")"
check "$([ -n "$applied" ] && [ "$applied" -ge 8 ] && echo true || echo false)" \
    "every migration ran and was recorded as successful"
failed="$(psql_q "SELECT count(*) FROM flyway_schema_history WHERE NOT success")"
check "$([ "$failed" = "0" ] && echo true || echo false)" "no migration is recorded as failed"

state_type="$(psql_q "SELECT data_type FROM information_schema.columns WHERE table_name = 'game_rooms' AND column_name = 'game_state'")"
say "game_rooms.game_state is: $state_type"
check "$([ "$state_type" = "text" ] && echo true || echo false)" \
    "game_state is PostgreSQL text, not the CLOB H2 accepted"

step "3. The server, in the aws profile, on PostgreSQL"
SPRING_PROFILES_ACTIVE=aws \
SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:$pgport/$db" \
SPRING_DATASOURCE_USERNAME="$dbuser" \
SPRING_DATASOURCE_PASSWORD="$dbpass" \
nohup java -jar "$repo/server/target/server-1.0.0.jar" --server.port="$apiport" \
    --logging.level.com.zaxxer.hikari=DEBUG \
    > "$work/server.log" 2>&1 &
server_pid=$!
say "server pid $server_pid, profile aws, port $apiport"

up=false
for _ in $(seq 1 90); do
    if [ "$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$apiport/api/status")" = "200" ]; then
        up=true
        break
    fi
    sleep 1
done
check "$up" "the server started against PostgreSQL and /api/status is 200"
say "status body: $(curl -s "http://127.0.0.1:$apiport/api/status")"

step "4. The profile's settings, as the running server actually applied them"
pool="$(grep -o 'maximumPoolSize[^,}"]*' "$work/server.log" | head -1)"
say "Hikari's own configuration line: ${pool:-<not printed>}"
check "$(printf '%s' "$pool" | grep -q '4' && echo true || echo false)" \
    "Hikari reports the profile's maximum pool size of 4"

idle="$(psql_q "SELECT count(*) FROM pg_stat_activity WHERE datname = '$db' AND usename = '$dbuser' AND pid <> pg_backend_pid()")"
say "backend connections while idle: $idle"

# The cap only means something under enough concurrency to exceed it. Hikari's
# default maximum is 10, so if the profile were not applied this load would open
# more than four backends and the sampling below would see them.
say "driving 60 concurrent requests and sampling the backend count"
# Their pids are collected because a bare `wait` here would also wait on the
# server, which is a background job of this script and never exits.
load_pids=""
for _ in $(seq 1 60); do
    curl -s -o /dev/null "http://127.0.0.1:$apiport/api/status" &
    load_pids="$load_pids $!"
done
peak=0
for _ in $(seq 1 25); do
    n="$(psql_q "SELECT count(*) FROM pg_stat_activity WHERE datname = '$db' AND usename = '$dbuser' AND pid <> pg_backend_pid()")"
    if [ -n "$n" ] && [ "$n" -gt "$peak" ]; then peak="$n"; fi
done
for load_pid in $load_pids; do
    wait "$load_pid" 2>/dev/null
done
say "peak backend connections observed under load: $peak"
check "$([ "$peak" -le 4 ] && echo true || echo false)" \
    "the pool never exceeded 4 connections under 60 concurrent requests"
check "$([ "$peak" -ge 2 ] && echo true || echo false)" \
    "and the load was real enough to open more than one connection"

first_log_line="$(grep -m1 '^{' "$work/server.log")"
if [ -n "$first_log_line" ] && printf '%s' "$first_log_line" | python3 -c "import json,sys; json.loads(sys.stdin.read())" 2>/dev/null; then
    pass "logs are one JSON object per line, as CloudWatch needs"
    say "sample keys: $(printf '%s' "$first_log_line" | python3 -c "import json,sys; print(', '.join(list(json.loads(sys.stdin.read()).keys())[:8]))" 2>/dev/null)"
else
    fail "logs are not structured JSON in the aws profile"
fi

lan="$(ipconfig getifaddr en0 2>/dev/null)"
if [ -n "$lan" ]; then
    lan_code="$(curl -s -m 3 -o /dev/null -w '%{http_code}' "http://$lan:$apiport/api/status")"
    check "$([ "$lan_code" = "000" ] && echo true || echo false)" \
        "the server is bound to loopback and unreachable on the LAN address ($lan)"
fi

step "5. PostgreSQL goes away underneath it"
"$pgbin/pg_ctl" -D "$work/data" -m fast stop >/dev/null 2>&1
say "stopped PostgreSQL"
sleep 2
code="$(curl -s -o "$work/down-body.txt" -w '%{http_code}' "http://127.0.0.1:$apiport/api/status")"
body="$(cat "$work/down-body.txt" 2>/dev/null)"
say "status code: $code"
say "body: $body"
check "$([ "$code" = "503" ] && echo true || echo false)" \
    "the status endpoint reports 503 rather than a cheerful 200"
leaked=""
for secret in "Exception" "SQLState" "org.postgresql" "Caused by" "at com.guesswho" \
        "$dbpass" "$dbuser" "127.0.0.1:$pgport" "$db" "HikariPool" "Connection refused"; do
    if printf '%s' "$body" | grep -qF -- "$secret"; then
        leaked="$leaked $secret"
    fi
done
# A body with nothing in it would pass a leak scan for the wrong reason, so the
# check requires a real response to have been read before believing it is clean.
check "$([ -n "$body" ] && [ -z "$leaked" ] && echo true || echo false)" \
    "the failure body is a real response that names no exception, driver, credential, host or database${leaked:+ (leaked:$leaked)}"

step "6. And recovers when the database comes back"
LC_ALL=C LANG=C "$pgbin/pg_ctl" -D "$work/data" -l "$work/postgres.log" \
    -o "-p $pgport -k $sockdir -c listen_addresses=127.0.0.1" -w start >/dev/null 2>&1
back=false
for _ in $(seq 1 30); do
    if [ "$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$apiport/api/status")" = "200" ]; then
        back=true
        break
    fi
    sleep 1
done
check "$back" "the server serves 200 again without being restarted"

step "7. The developer's own database was never opened"
h2_after="$(ls "$repo"/guess-who-data* 2>/dev/null | wc -l | tr -d ' ')"
h2_sum_after="$(shasum "$repo"/guess-who-data.mv.db 2>/dev/null | awk '{print $1}')"
say "H2 files in the repository before: $h2_before, after: $h2_after"
check "$([ "$h2_before" = "$h2_after" ] && echo true || echo false)" \
    "no H2 database file was created or removed in the repository"
check "$([ "$h2_sum_before" = "$h2_sum_after" ] && echo true || echo false)" \
    "the developer H2 database is byte-for-byte unchanged"

echo
if [ "$failures" -eq 0 ]; then
    echo "POSTGRES REHEARSAL PASSED: $checks checks"
else
    echo "POSTGRES REHEARSAL FAILED: $failures of $checks checks"
fi
exit "$failures"
