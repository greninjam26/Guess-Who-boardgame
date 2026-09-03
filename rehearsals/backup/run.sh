#!/usr/bin/env bash
#
# The backup and restore rehearsal.
#
# A backup nobody has restored is a belief, not a backup, and the moment to find
# that out is not the moment you need it. This creates a database the way the
# application creates one — through the running server, with accounts, sessions,
# a finished game and a game still in progress — dumps it with the options
# production uses, restores it into a different database, and compares the two.
#
# It never touches S3: the upload is the one step of backup.sh that cannot be
# rehearsed without an AWS account.

set -uo pipefail

repo="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
work="$here/work"
pgbin="${GUESSWHO_PGBIN:-/opt/homebrew/opt/postgresql@15/bin}"
harness="$here/../two-client/TwoClientRehearsal.java"
pgjar="$(find "$HOME/.m2/repository/org/postgresql/postgresql" -name "postgresql-*.jar" 2>/dev/null | sort -V | tail -1)"

pgport=55433
apiport=18082
srcdb=guesswho_backup_src
restoredb=guesswho_restore_check
dbuser=guesswho
dbpass=rehearsal_only_not_a_secret

failures=0
checks=0
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
require "$pgbin/pg_dump" "Install it with: brew install postgresql@15 (or set GUESSWHO_PGBIN)"

q() { PGPASSWORD="$dbpass" "$pgbin/psql" -h 127.0.0.1 -p "$pgport" -U "$dbuser" -d "$1" -tAc "$2" 2>/dev/null; }

cleanup() {
    step "Cleanup"
    if [ -n "${sockdir:-}" ] && [ -d "$work/data" ]; then
        # Only the database this rehearsal created is dropped, and only if it exists.
        if [ "$(q postgres "SELECT 1 FROM pg_database WHERE datname = '$restoredb'")" = "1" ]; then
            PGPASSWORD="$dbpass" "$pgbin/dropdb" -h 127.0.0.1 -p "$pgport" -U "$dbuser" \
                "$restoredb" 2>/dev/null && say "dropped the temporary restore database"
        fi
        LC_ALL=C "$pgbin/pg_ctl" -D "$work/data" -m fast stop >/dev/null 2>&1 \
            && say "stopped the disposable cluster"
        rm -rf "$work/data"
        say "removed the disposable cluster directory"
    fi
    [ -n "${sockdir:-}" ] && rm -rf "$sockdir"
    say "kept as evidence: $work/*.gz, $work/server.log"
}
trap cleanup EXIT

rm -rf "$work"
mkdir -p "$work"
sockdir="$(mktemp -d /tmp/gwpg.XXXXXX)"

step "0. A disposable cluster and an empty database"
LC_ALL=C LANG=C "$pgbin/initdb" -D "$work/data" -U postgres --auth=trust -E UTF8 \
    > "$work/initdb.log" 2>&1
LC_ALL=C LANG=C "$pgbin/pg_ctl" -D "$work/data" -l "$work/postgres.log" \
    -o "-p $pgport -k $sockdir -c listen_addresses=127.0.0.1" -w start >/dev/null 2>&1
if [ $? -ne 0 ]; then
    fail "the cluster did not start"
    tail -5 "$work/postgres.log" | sed 's/^/      /'
    exit 1
fi
pass "the cluster started on 127.0.0.1:$pgport"
"$pgbin/psql" -h 127.0.0.1 -p "$pgport" -U postgres -d postgres -q \
    -c "CREATE ROLE $dbuser LOGIN SUPERUSER PASSWORD '$dbpass';" >/dev/null 2>&1
"$pgbin/createdb" -h 127.0.0.1 -p "$pgport" -U postgres -O "$dbuser" "$srcdb" >/dev/null 2>&1
check "$([ "$(q "$srcdb" 'SELECT 1')" = "1" ] && echo true || echo false)" \
    "the source database answers"

step "1. Data created the way the application creates it"
cp="$repo/desktop-client/target/desktop-client-1.0.0.jar:$repo/desktop-client/target/lib/*:$pgjar"
java -cp "$cp" "$harness" \
    "$repo/server/target/server-1.0.0.jar" \
    "jdbc:postgresql://127.0.0.1:$pgport/$srcdb" \
    "$apiport" "$work/server.log" "$work/queue.jsonl" \
    "$dbuser" "$dbpass" populate > "$work/populate.log" 2>&1
populate_status=$?
check "$([ $populate_status -eq 0 ] && echo true || echo false)" \
    "the server populated the database through its own API"
live_room="$(grep -o 'live room [A-Z0-9]*' "$work/populate.log" | tail -1 | awk '{print $3}')"
say "live room still in progress: ${live_room:-<none>}"
grep -E '^   \. (LIVE ROOM|accounts|game_results)' "$work/populate.log" | sed 's/^   \. /      /'

step "2. What the source database holds"
tables="$(q "$srcdb" "SELECT tablename FROM pg_tables WHERE schemaname = 'public' ORDER BY tablename")"
say "tables: $(echo "$tables" | tr '\n' ' ')"
: > "$work/counts-source.txt"
for t in $tables; do
    printf '%s %s\n' "$t" "$(q "$srcdb" "SELECT count(*) FROM $t")" >> "$work/counts-source.txt"
done
sed 's/^/      /' "$work/counts-source.txt"
populated="$(awk '$2 > 0 {n++} END {print n+0}' "$work/counts-source.txt")"
check "$([ "$populated" -ge 5 ] && echo true || echo false)" \
    "the backup will contain real data ($populated tables have rows)"

step "3. The dump, with the options production uses"
# Read from backup.sh rather than trusting this script's memory of it: a
# rehearsal that quietly drifts from the thing it rehearses proves nothing.
backup_script="$repo/deploy/aws/backup.sh"
for opt in "--format=custom" "--no-owner" "--no-acl" "gzip -9" "gzip -t"; do
    check "$(grep -qF -- "$opt" "$backup_script" && echo true || echo false)" \
        "backup.sh still uses $opt, which is what this rehearsal uses"
done

PGPASSWORD="$dbpass" "$pgbin/pg_dump" --format=custom --no-owner --no-acl \
    -h 127.0.0.1 -p "$pgport" -U "$dbuser" "$srcdb" > "$work/dump" 2>"$work/dump.err"
check "$([ $? -eq 0 ] && [ -s "$work/dump" ] && echo true || echo false)" "pg_dump produced a dump"
gzip -9 "$work/dump"
gzip -t "$work/dump.gz"
check "$([ $? -eq 0 ] && echo true || echo false)" "gzip -t accepts the compressed dump"
size="$(wc -c < "$work/dump.gz" | tr -d ' ')"
say "size: $size bytes"
# backup.sh refuses to upload anything under 1000 bytes on the grounds that it
# is not a database. The same floor is applied here.
check "$([ "$size" -ge 1000 ] && echo true || echo false)" \
    "the dump is larger than backup.sh's 1000-byte floor"
checksum="$(shasum -a 256 "$work/dump.gz" | awk '{print $1}')"
say "sha256: $checksum"

gunzip -c "$work/dump.gz" > "$work/dump.restored-from"
"$pgbin/pg_restore" --list "$work/dump.restored-from" > "$work/toc.txt" 2>&1
check "$([ $? -eq 0 ] && echo true || echo false)" \
    "pg_restore --list reads it as a dump, which is the check teardown.sh makes"
say "table entries in the dump's table of contents: $(grep -c 'TABLE DATA' "$work/toc.txt")"

step "4. Restored into a separate database"
PGPASSWORD="$dbpass" "$pgbin/createdb" -h 127.0.0.1 -p "$pgport" -U "$dbuser" "$restoredb" 2>/dev/null
PGPASSWORD="$dbpass" "$pgbin/pg_restore" -h 127.0.0.1 -p "$pgport" -U "$dbuser" \
    -d "$restoredb" --no-owner --no-acl "$work/dump.restored-from" > "$work/restore.log" 2>&1
restore_status=$?
check "$([ $restore_status -eq 0 ] && echo true || echo false)" \
    "pg_restore loaded the dump into $restoredb without error"
[ $restore_status -ne 0 ] && tail -5 "$work/restore.log" | sed 's/^/      /'

step "5. Row counts, table by table"
mismatch=0
: > "$work/counts-compare.txt"
for t in $tables; do
    a="$(q "$srcdb" "SELECT count(*) FROM $t")"
    b="$(q "$restoredb" "SELECT count(*) FROM $t")"
    printf '%-22s source=%-5s restored=%-5s %s\n' "$t" "$a" "$b" \
        "$([ "$a" = "$b" ] && echo same || { echo DIFFERENT; })" >> "$work/counts-compare.txt"
    [ "$a" != "$b" ] && mismatch=$((mismatch + 1))
done
sed 's/^/      /' "$work/counts-compare.txt"
check "$([ "$mismatch" -eq 0 ] && [ -n "$tables" ] && echo true || echo false)" \
    "every table has the same number of rows after the restore"

step "6. Representative records, not just counts"
src_accounts="$(q "$srcdb" "SELECT string_agg(username, ',' ORDER BY username) FROM accounts")"
res_accounts="$(q "$restoredb" "SELECT string_agg(username, ',' ORDER BY username) FROM accounts")"
say "accounts: $src_accounts"
check "$([ -n "$src_accounts" ] && [ "$src_accounts" = "$res_accounts" ] && echo true || echo false)" \
    "the same accounts came back, by name"

src_results="$(q "$srcdb" "SELECT count(*) || ':' || coalesce(string_agg(DISTINCT mode::text, ','), '') FROM game_results")"
res_results="$(q "$restoredb" "SELECT count(*) || ':' || coalesce(string_agg(DISTINCT mode::text, ','), '') FROM game_results")"
say "game_results (count:modes): $src_results"
check "$([ -n "$src_results" ] && [ "${src_results%%:*}" != "0" ] \
        && [ "$src_results" = "$res_results" ] && echo true || echo false)" \
    "recorded games came back with their modes intact"

step "7. The game that was still being played"
src_room="$(q "$srcdb" "SELECT status || '|' || version || '|' || length(game_state) || '|' || md5(game_state) FROM game_rooms WHERE code = '$live_room'")"
res_room="$(q "$restoredb" "SELECT status || '|' || version || '|' || length(game_state) || '|' || md5(game_state) FROM game_rooms WHERE code = '$live_room'")"
say "source:   $src_room"
say "restored: $res_room"
check "$([ -n "$src_room" ] && echo true || echo false)" "the in-progress room was found in the source"
check "$([ -n "$src_room" ] && [ "$src_room" = "$res_room" ] && echo true || echo false)" \
    "its status, version, length and content are identical after restoration"
check "$(printf '%s' "$res_room" | grep -q '^IN_PROGRESS' && echo true || echo false)" \
    "and it is still an in-progress game, not a finished one"

echo
if [ "$failures" -eq 0 ]; then
    echo "BACKUP REHEARSAL PASSED: $checks checks"
else
    echo "BACKUP REHEARSAL FAILED: $failures of $checks checks"
fi
echo "dump: $work/dump.gz"
echo "sha256: ${checksum:-<none>}"
exit "$failures"
