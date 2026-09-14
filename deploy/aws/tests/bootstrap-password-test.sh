#!/usr/bin/env bash

set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
password_setter="$here/../set-db-password.sh"
work="$(mktemp -d)"
data="$work/data"
socket="$work/socket"
port=55439

cleanup() {
    if [ -f "$data/postmaster.pid" ]; then
        pg_ctl -D "$data" -m fast -w stop >/dev/null
    fi
    rm -rf "$work"
}
trap cleanup EXIT

if [ ! -x "$password_setter" ]; then
    echo "FAIL: missing executable set-db-password.sh"
    exit 1
fi

mkdir -p "$socket"
initdb -D "$data" --auth-local=trust --auth-host=scram-sha-256 >/dev/null
pg_ctl -D "$data" \
    -o "-F -h 127.0.0.1 -k $socket -p $port" \
    -w start >/dev/null

PGHOST="$socket" PGPORT="$port" \
    psql -d postgres -v ON_ERROR_STOP=1 \
    -c "CREATE ROLE guesswho LOGIN PASSWORD 'old-password'" >/dev/null

new_password='new-password/+=safe'
printf '%s\n' "$new_password" | \
    PGHOST="$socket" PGPORT="$port" "$password_setter"

actual="$(PGPASSWORD="$new_password" \
    psql -h 127.0.0.1 -p "$port" -U guesswho -d postgres \
    -tAc 'SELECT current_user')"

if [ "$actual" != "guesswho" ]; then
    echo "FAIL: the updated password did not authenticate the guesswho role"
    exit 1
fi

if PGPASSWORD='old-password' \
    psql -h 127.0.0.1 -p "$port" -U guesswho -d postgres \
    -tAc 'SELECT 1' >/dev/null 2>&1; then
    echo "FAIL: the previous password still authenticates"
    exit 1
fi

echo "bootstrap password: role password changed without putting it in psql arguments"
