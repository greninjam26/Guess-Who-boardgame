#!/usr/bin/env bash
#
# Checks the files that configure the host: the bootstrap script, the systemd
# units, the Caddyfile, the backup script, the smoke test and the bootstrap
# bundle packager.
#
# None of these run anywhere but the instance, which means the usual way to find
# a mistake in them is to deploy and watch. This reads them instead. Everything
# asserted here is either a door left open, a promise the application makes that
# the host has to keep, or a backup that silently is not one.
#
# Reads files and runs the local-only bundle packager, needs no AWS.

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

scripts="bootstrap.sh backup.sh smoke-test.sh set-db-password.sh package-bootstrap.sh"
units="guesswho.service guesswho-backup.service guesswho-backup.timer"
others="Caddyfile cloudwatch-agent.json"

for f in $scripts $units $others; do
    need_file "$f"
done

# --- replacement hosts receive one exact, checksummed bundle -------------
runtime_files="bootstrap.sh set-db-password.sh backup.sh guesswho.service guesswho-backup.service guesswho-backup.timer Caddyfile cloudwatch-agent.json"
bundle_test_root="$(mktemp -d)"
trap 'rm -rf "$bundle_test_root"' EXIT
bundle_aws_dir="$bundle_test_root/deploy/aws"
bundle_output_dir="$bundle_test_root/target/aws-bootstrap"
bundle_archive="$bundle_output_dir/guesswho-bootstrap.tar.gz"
bundle_checksum="$bundle_archive.sha256"

if [ -f "$aws_dir/package-bootstrap.sh" ]; then
    mkdir -p "$bundle_aws_dir"
    cp "$aws_dir/package-bootstrap.sh" "$bundle_aws_dir/"
    for f in $runtime_files; do
        cp "$aws_dir/$f" "$bundle_aws_dir/"
    done

    # Files beside the runtime inputs must not enter the archive. In the real
    # checkout these names can hold deployment values or generated data.
    touch "$bundle_aws_dir/parameters.json" \
        "$bundle_aws_dir/server.env" \
        "$bundle_aws_dir/database.dump" \
        "$bundle_aws_dir/access.token"

    if ! bash "$bundle_aws_dir/package-bootstrap.sh" >/dev/null; then
        fail "package-bootstrap.sh could not build the runtime bundle"
    elif [ ! -f "$bundle_archive" ] || [ ! -f "$bundle_checksum" ]; then
        fail "package-bootstrap.sh did not create the archive and checksum"
    else
        expected_entries="$(printf '%s\n' $runtime_files)"
        actual_entries="$(tar -tzf "$bundle_archive")"
        [ "$actual_entries" = "$expected_entries" ] \
            || fail "the bootstrap archive does not contain exactly the runtime files"

        if command -v sha256sum >/dev/null 2>&1; then
            (cd "$bundle_output_dir" && sha256sum -c "$(basename "$bundle_checksum")" >/dev/null) \
                || fail "the bootstrap archive checksum does not verify"
        else
            (cd "$bundle_output_dir" && shasum -a 256 -c "$(basename "$bundle_checksum")" >/dev/null) \
                || fail "the bootstrap archive checksum does not verify"
        fi
    fi

    rm "$bundle_aws_dir/cloudwatch-agent.json"
    if bash "$bundle_aws_dir/package-bootstrap.sh" >"$bundle_test_root/missing.out" 2>&1; then
        fail "package-bootstrap.sh accepts a missing runtime input"
    elif ! grep -qF "Missing bootstrap input: cloudwatch-agent.json" "$bundle_test_root/missing.out"; then
        fail "package-bootstrap.sh does not identify a missing runtime input"
    fi
fi

# --- every script must at least parse ------------------------------------
for f in $scripts; do
    if [ -f "$aws_dir/$f" ] && ! bash -n "$aws_dir/$f" 2>/dev/null; then
        fail "$f is not valid bash"
    fi
done

# deploy.sh validates each downloaded artifact with `jar tf`, so the host
# bootstrap must install the JDK package that supplies that executable.
has bootstrap.sh "java-17-amazon-corretto-devel" \
    || fail "bootstrap does not install the jar utility required by deploy.sh"

# --- and stop at the first error rather than carrying on ------------------
# A bootstrap that continues past a failed step leaves a half-configured host
# that looks deployed. A backup that continues past a failed pg_dump uploads
# an empty file over a good one.
for f in bootstrap.sh backup.sh set-db-password.sh; do
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
# Caddy sets no X-Real-IP of its own, so one arriving upstream can only be the
# caller's. Nothing reads it yet, which is the reason to remove it now rather
# than after something does.
has Caddyfile "request_header -X-Real-IP" \
    || fail "Caddy passes a client-supplied X-Real-IP through, which is an address any caller can choose"

# --- PostgreSQL is not on the internet ------------------------------------
has bootstrap.sh "listen_addresses = '127.0.0.1'" \
    || fail "bootstrap does not restrict PostgreSQL to localhost"
has bootstrap.sh "max_connections" || fail "bootstrap does not cap PostgreSQL connections"
has bootstrap.sh "pg_hba.conf" || fail "bootstrap does not configure PostgreSQL client authentication"
has bootstrap.sh "scram-sha-256" \
    || fail "bootstrap does not require password authentication for localhost PostgreSQL clients"

# --- the password is generated, stored, and never printed ------------------
has bootstrap.sh "openssl rand" || fail "bootstrap does not generate a database password"
has bootstrap.sh "SecureString" || fail "the password is not stored as a SecureString"
has bootstrap.sh "set-db-password.sh" || fail "bootstrap does not use the tested password setter"
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
