#!/usr/bin/env bash
#
# Checks the files that configure the host: the bootstrap script, the systemd
# units, the Caddyfile, the backup script and the smoke test.
#
# None of these run anywhere but the instance, which means the usual way to find
# a mistake in them is to deploy and watch. This reads them instead. Everything
# asserted here is either a door left open, a promise the application makes that
# the host has to keep, or a backup that silently is not one.
#
# Reads files, runs nothing, needs no AWS.

set -uo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
aws_dir="$here/.."
failures=0

fail() {
    echo "FAIL: $1"
    failures=$((failures + 1))
}

need_file() {
    [ -f "$aws_dir/$1" ] || { fail "missing $1"; return 1; }
}

# A file may be missing; every later check on it is then skipped rather than
# reporting a cascade of confusing failures about a file that is not there.
has() {
    [ -f "$aws_dir/$1" ] && grep -qF -- "$2" "$aws_dir/$1"
}

hasnt() {
    [ -f "$aws_dir/$1" ] && ! grep -qF -- "$2" "$aws_dir/$1"
}

scripts="bootstrap.sh backup.sh smoke-test.sh"
units="guesswho.service guesswho-backup.service guesswho-backup.timer"
others="Caddyfile cloudwatch-agent.json"

for f in $scripts $units $others; do
    need_file "$f"
done

# --- every script must at least parse ------------------------------------
for f in $scripts; do
    if [ -f "$aws_dir/$f" ] && ! bash -n "$aws_dir/$f" 2>/dev/null; then
        fail "$f is not valid bash"
    fi
done

# --- and stop at the first error rather than carrying on ------------------
# A bootstrap that continues past a failed step leaves a half-configured host
# that looks deployed. A backup that continues past a failed pg_dump uploads
# an empty file over a good one.
for f in bootstrap.sh backup.sh; do
    has "$f" "set -euo pipefail" || fail "$f does not set -euo pipefail"
done

# --- the application stays behind the proxy -------------------------------
has guesswho.service "SPRING_PROFILES_ACTIVE=aws" \
    || fail "the service does not activate the aws profile, so it would bind to 0.0.0.0"
has guesswho.service "User=guesswho" || fail "the service does not run as guesswho"
hasnt guesswho.service "User=root" || fail "the service runs as root"
has guesswho.service "Restart=on-failure" || fail "the service does not restart on failure"
has guesswho.service "-Xms128m -Xmx384m" || fail "the heap is not capped for a 1 GB host"
has guesswho.service "NoNewPrivileges=true" || fail "the service does not set NoNewPrivileges"
has guesswho.service "ProtectSystem=strict" || fail "the service does not set ProtectSystem"

# --- Caddy is the only thing that can name a caller -----------------------
has Caddyfile "reverse_proxy 127.0.0.1:8080" \
    || fail "Caddy does not proxy to loopback"
has Caddyfile "request_header -Forwarded" \
    || fail "Caddy does not strip a forged Forwarded header"
has Caddyfile "header_up X-Forwarded-For {http.request.remote.host}" \
    || fail "Caddy appends to X-Forwarded-For instead of replacing it, so any caller can forge an address and dodge the sign-in limit"

# --- PostgreSQL is not on the internet ------------------------------------
has bootstrap.sh "listen_addresses = '127.0.0.1'" \
    || fail "bootstrap does not restrict PostgreSQL to localhost"
has bootstrap.sh "max_connections" || fail "bootstrap does not cap PostgreSQL connections"

# --- the password is generated, stored, and never printed ------------------
has bootstrap.sh "openssl rand" || fail "bootstrap does not generate a database password"
has bootstrap.sh "SecureString" || fail "the password is not stored as a SecureString"
if [ -f "$aws_dir/bootstrap.sh" ] && grep -nE '^[^#]*echo[^#]*(PASSWORD|password)' "$aws_dir/bootstrap.sh" >/dev/null; then
    fail "bootstrap echoes something password-shaped"
fi

# --- backups go where the lifecycle rule expects ---------------------------
has backup.sh 's3://$ARTIFACT_BUCKET/backups/' \
    || fail "backups do not go to the backups/ prefix the bucket expires on"
has backup.sh "pg_dump" || fail "backup.sh does not dump anything"
has backup.sh "gzip -t" || fail "backup.sh does not verify the dump it uploaded"
has backup.sh "mktemp -d" || fail "backup.sh does not use a private temporary directory"
has backup.sh "trap " || fail "backup.sh does not clean up after itself"

has guesswho-backup.timer "OnCalendar=daily" || fail "the backup timer is not daily"
has guesswho-backup.timer "Persistent=true" \
    || fail "the backup timer does not catch up after downtime"

# --- the smoke test refuses to check an insecure endpoint ------------------
has smoke-test.sh "https://" || fail "smoke-test.sh does not require HTTPS"

if [ "$failures" -eq 0 ]; then
    echo "runtime contract: $(echo "$scripts $units $others" | wc -w | tr -d ' ') files checked, all constraints hold"
    exit 0
fi
echo "$failures failure(s)"
exit 1
