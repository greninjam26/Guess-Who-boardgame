#!/usr/bin/env bash
#
# Turns a fresh Amazon Linux 2023 instance into the Guess Who host.
#
# Run through SSM Session Manager, not SSH — port 22 is closed. Safe to run
# again: every step checks whether it has already been done, because the way
# this script is actually used is "run it again and see if that fixes it".
#
# Needs PUBLIC_HOSTNAME, ARTIFACT_BUCKET and AWS_REGION, which come from the
# CloudFormation stack's outputs.
#
#   sudo PUBLIC_HOSTNAME=... ARTIFACT_BUCKET=... AWS_REGION=us-east-1 \
#       bash /opt/guesswho/bootstrap.sh

set -euo pipefail

: "${PUBLIC_HOSTNAME:?PUBLIC_HOSTNAME is not set}"
: "${ARTIFACT_BUCKET:?ARTIFACT_BUCKET is not set}"
: "${AWS_REGION:?AWS_REGION is not set}"

PARAMETER_NAME="/guesswho/demo/db-password"
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

say() { printf '\n== %s\n' "$1"; }

# ---------------------------------------------------------------- packages
say "packages"
dnf install -y \
    java-17-amazon-corretto-headless \
    postgresql15-server postgresql15 \
    amazon-cloudwatch-agent \
    jq gzip openssl

if ! command -v caddy >/dev/null 2>&1; then
    # Caddy is not in the Amazon Linux repositories.
    dnf install -y 'dnf-command(copr)'
    dnf copr enable -y @caddy/caddy epel-9-x86_64
    dnf install -y caddy
fi

# ------------------------------------------------------------------- user
say "user and directories"
# No shell and no home: this account exists to own a process and its log file.
# Anything that can run as guesswho should not be able to log in as it.
if ! id -u guesswho >/dev/null 2>&1; then
    useradd --system --no-create-home --shell /sbin/nologin guesswho
fi

install -d -o guesswho -g guesswho -m 0755 /opt/guesswho
install -d -o guesswho -g guesswho -m 0755 /opt/guesswho/releases
install -d -o guesswho -g guesswho -m 0755 /opt/guesswho/current
install -d -o guesswho -g guesswho -m 0750 /var/log/guesswho
# root-owned, guesswho-readable: the application reads its credentials here and
# must not be able to rewrite them.
install -d -o root -g guesswho -m 0750 /etc/guesswho

# ------------------------------------------------------------------- swap
say "swap"
# 1 GB of RAM shared by a JVM, PostgreSQL and Caddy. Swap does not make that
# fast, but it turns "the kernel killed PostgreSQL" into "the host got slow for
# a moment", which is a much better failure on a demo nobody is watching.
if [ "$(swapon --show --noheadings | wc -l)" -eq 0 ]; then
    fallocate -l 1G /swapfile
    chmod 600 /swapfile
    mkswap /swapfile
    swapon /swapfile
    grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

# -------------------------------------------------------------- postgresql
say "postgresql"
PGDATA=/var/lib/pgsql/data
if [ ! -f "$PGDATA/PG_VERSION" ]; then
    # Only when empty. Running initdb over an existing cluster is how a database
    # gets replaced by a new one that looks fine and contains nothing.
    postgresql-setup --initdb
fi

# Localhost only. The security group already closes 5432, but a host should not
# depend on a firewall for something it can simply not do.
sed -i "s/^#\?listen_addresses.*/listen_addresses = '127.0.0.1'/" "$PGDATA/postgresql.conf"
sed -i "s/^#\?shared_buffers.*/shared_buffers = 128MB/" "$PGDATA/postgresql.conf"
sed -i "s/^#\?work_mem.*/work_mem = 4MB/" "$PGDATA/postgresql.conf"
sed -i "s/^#\?maintenance_work_mem.*/maintenance_work_mem = 32MB/" "$PGDATA/postgresql.conf"
sed -i "s/^#\?max_connections.*/max_connections = 30/" "$PGDATA/postgresql.conf"

systemctl enable --now postgresql
until sudo -u postgres psql -c 'SELECT 1' >/dev/null 2>&1; do sleep 1; done

# ---------------------------------------------------------------- password
say "database password"
# Generated here and stored in Parameter Store, never passed in and never
# printed. Nothing outside this instance and that parameter ever holds it —
# which is why it is not a CloudFormation parameter, where describe-stacks
# would hand it to anyone with read access.
if ! aws ssm get-parameter --name "$PARAMETER_NAME" --region "$AWS_REGION" >/dev/null 2>&1; then
    generated="$(openssl rand -base64 32)"
    aws ssm put-parameter \
        --name "$PARAMETER_NAME" \
        --value "$generated" \
        --type SecureString \
        --region "$AWS_REGION" \
        --no-overwrite >/dev/null
    unset generated
fi

db_password="$(aws ssm get-parameter \
    --name "$PARAMETER_NAME" \
    --with-decryption \
    --region "$AWS_REGION" \
    --query 'Parameter.Value' \
    --output text)"

# ------------------------------------------------------------ role and db
say "role and database"
# Both created only if absent, and the password set every run so that rotating
# the parameter and re-running is how you rotate the credential.
if ! sudo -u postgres psql -tAc "SELECT 1 FROM pg_roles WHERE rolname='guesswho'" | grep -q 1; then
    sudo -u postgres psql -c "CREATE ROLE guesswho LOGIN" >/dev/null
fi
# Passed through a variable rather than interpolated into the SQL string, so the
# password never reaches ps output or the shell history.
sudo -u postgres psql -v pw="$db_password" \
    -c "ALTER ROLE guesswho WITH PASSWORD :'pw'" >/dev/null

if ! sudo -u postgres psql -tAc "SELECT 1 FROM pg_database WHERE datname='guesswho'" | grep -q 1; then
    sudo -u postgres createdb --owner guesswho guesswho
fi

# ------------------------------------------------------------ environment
say "service environment"
umask 077
cat > /etc/guesswho/server.env <<EOF
SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5432/guesswho
SPRING_DATASOURCE_USERNAME=guesswho
SPRING_DATASOURCE_PASSWORD=${db_password}
EOF
chown root:guesswho /etc/guesswho/server.env
chmod 0640 /etc/guesswho/server.env

cat > /etc/guesswho/backup.env <<EOF
ARTIFACT_BUCKET=${ARTIFACT_BUCKET}
AWS_REGION=${AWS_REGION}
EOF
chown root:postgres /etc/guesswho/backup.env
chmod 0640 /etc/guesswho/backup.env
unset db_password
umask 022

# -------------------------------------------------------------- configure
say "services"
install -m 0755 "$here/backup.sh" /opt/guesswho/backup.sh
install -m 0644 "$here/guesswho.service" /etc/systemd/system/guesswho.service
install -m 0644 "$here/guesswho-backup.service" /etc/systemd/system/guesswho-backup.service
install -m 0644 "$here/guesswho-backup.timer" /etc/systemd/system/guesswho-backup.timer

install -d -m 0755 /etc/caddy
# Rendered with sed rather than left as Caddy's own {$PUBLIC_HOSTNAME}. Caddy
# would resolve that itself, but only if the variable reaches its process
# through systemd — and a Caddyfile on the host that names the real hostname is
# far easier to debug than one that names a variable whose value you then have
# to go and find.
sed "s|{\$PUBLIC_HOSTNAME}|$PUBLIC_HOSTNAME|g" \
    "$here/Caddyfile" > /etc/caddy/Caddyfile
chmod 0644 /etc/caddy/Caddyfile
# Refuse to start with a Caddyfile that does not parse, rather than finding out
# when the certificate never arrives.
caddy validate --config /etc/caddy/Caddyfile

install -m 0644 "$here/cloudwatch-agent.json" \
    /opt/aws/amazon-cloudwatch-agent/etc/guesswho.json
/opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl \
    -a fetch-config -m ec2 -s \
    -c file:/opt/aws/amazon-cloudwatch-agent/etc/guesswho.json

systemctl daemon-reload
systemctl enable --now caddy
systemctl enable --now guesswho-backup.timer
# Not guesswho.service: there is no JAR until the first deployment, and starting
# it here would only produce a restart loop for somebody to misread.
systemctl enable guesswho

# ----------------------------------------------------------------- verify
say "verify"
# Asserted rather than assumed. Everything above is a step that can quietly not
# have worked, and a host that looks bootstrapped is worse than one that failed.
failures=0
check() {
    if eval "$2" >/dev/null 2>&1; then
        printf '  ok    %s\n' "$1"
    else
        printf '  FAIL  %s\n' "$1"
        failures=$((failures + 1))
    fi
}

check "postgresql is running"        "systemctl is-active --quiet postgresql"
check "caddy is running"             "systemctl is-active --quiet caddy"
check "backup timer is enabled"      "systemctl is-enabled --quiet guesswho-backup.timer"
check "guesswho database exists"     "sudo -u postgres psql -tAc \"SELECT 1 FROM pg_database WHERE datname='guesswho'\" | grep -q 1"
check "server.env is not world-readable" "[ \"\$(stat -c '%a' /etc/guesswho/server.env)\" = '640' ]"

# The two that matter most: neither may be reachable from anywhere but here.
check "postgresql listens on loopback only" \
    "! ss -ltn | awk '{print \$4}' | grep -qE '^(0\.0\.0\.0|\[::\]):5432$'"
check "nothing else is listening on 8080 publicly" \
    "! ss -ltn | awk '{print \$4}' | grep -qE '^(0\.0\.0\.0|\[::\]):8080$'"

if [ "$failures" -ne 0 ]; then
    echo
    echo "bootstrap finished with $failures problem(s). The host is not ready."
    exit 1
fi

echo
echo "bootstrap complete. Deploy a JAR, then run smoke-test.sh against https://$PUBLIC_HOSTNAME"
