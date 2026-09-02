#!/usr/bin/env bash
#
# Installs a release on the instance, and puts the previous one back if the new
# one does not come up.
#
# Fetched from S3 by the SSM command that runs it, after its checksum has been
# verified — this executes as root on the public host, and the instance role can
# write to the same bucket it came from.
#
#   bash deploy.sh <artifact-bucket> <git-sha>

set -euo pipefail

bucket="${1:?usage: deploy.sh <bucket> <sha>}"
sha="${2:?usage: deploy.sh <bucket> <sha>}"
region="${AWS_REGION:-us-east-1}"

releases=/opt/guesswho/releases
current=/opt/guesswho/current/server.jar
candidate="$releases/server-$sha.jar"

say() { printf '\n== %s\n' "$1"; }

# ------------------------------------------------------------- the artifact
say "fetching release $sha"
# Downloaded to .tmp and only renamed once it is known to be a JAR. A truncated
# download named server-<sha>.jar is a file that looks like a release for ever.
aws s3 cp "s3://$bucket/releases/$sha/server.jar" "$candidate.tmp" \
    --region "$region" --only-show-errors

# The cheapest question that distinguishes a JAR from an HTML error page, an
# empty file, or half a download.
if ! jar tf "$candidate.tmp" >/dev/null 2>&1; then
    rm -f "$candidate.tmp"
    echo "deploy: the downloaded artifact is not a readable JAR; nothing was changed" >&2
    exit 1
fi
mv "$candidate.tmp" "$candidate"
chown guesswho:guesswho "$candidate"

# -------------------------------------------------------------- the switch
# Remembered before anything moves. Without this there is nothing to go back to
# and a bad release is a manual recovery on a live host.
previous=""
if [ -L "$current" ] || [ -f "$current" ]; then
    previous="$(readlink -f "$current" || true)"
fi
say "previous release: ${previous:-none}"

install -d -o guesswho -g guesswho /opt/guesswho/current
ln -sfn "$candidate" "$current"
systemctl restart guesswho

# -------------------------------------------------------------- the check
say "waiting for the server to come up"
healthy=false
for _ in $(seq 1 60); do
    # Loopback, not the public hostname. This is asking whether *this JAR*
    # started, and going through Caddy would also be testing DNS, the
    # certificate and the proxy — so a DNS problem would roll back a good
    # release.
    if curl -fsS --max-time 2 http://127.0.0.1:8080/api/status 2>/dev/null \
            | grep -q '"status"[[:space:]]*:[[:space:]]*"online"'; then
        healthy=true
        break
    fi
    sleep 1
done

if [ "$healthy" != true ]; then
    say "the new release did not become healthy; rolling back"
    if [ -n "$previous" ] && [ -f "$previous" ]; then
        ln -sfn "$previous" "$current"
        systemctl restart guesswho
        # Reported, not assumed. A rollback that also fails is the state most
        # worth knowing about and the easiest to miss.
        for _ in $(seq 1 60); do
            if curl -fsS --max-time 2 http://127.0.0.1:8080/api/status 2>/dev/null \
                    | grep -q '"status"'; then
                echo "deploy: rolled back to $previous, which is answering" >&2
                exit 1
            fi
            sleep 1
        done
        echo "deploy: rolled back to $previous and it is NOT answering" >&2
        exit 1
    fi
    echo "deploy: no previous release to roll back to; the service is down" >&2
    exit 1
fi

# ------------------------------------------------------------- tidying up
# The newest two, so the thing being rolled back to still exists. Anything older
# is reproducible from its commit.
say "keeping the two newest releases"
ls -1t "$releases"/server-*.jar 2>/dev/null | tail -n +3 | while read -r old; do
    [ "$old" = "$candidate" ] && continue
    [ "$old" = "$previous" ] && continue
    rm -f "$old"
done

say "deployed $sha"
