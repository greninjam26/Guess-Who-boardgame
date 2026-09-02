#!/usr/bin/env bash
#
# Dumps the Guess Who database and uploads it to S3.
#
# Run by guesswho-backup.timer as the postgres user, daily. The bucket expires
# backups after 14 days, so this is a rolling fortnight rather than an archive —
# long enough to notice a failure, short enough to stay inside the free tier.
#
# Reads ARTIFACT_BUCKET and AWS_REGION from /etc/guesswho/backup.env.

set -euo pipefail

: "${ARTIFACT_BUCKET:?ARTIFACT_BUCKET is not set}"
: "${AWS_REGION:?AWS_REGION is not set}"

database="${GUESSWHO_DB:-guesswho}"
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
name="guesswho-${stamp}.dump.gz"

# Mode 0700 and removed on any exit. A dump on a shared filesystem is every
# account row in the game, so it exists for as short a time as possible and is
# never readable by another user on the host.
workdir="$(mktemp -d)"
chmod 700 "$workdir"
trap 'rm -rf "$workdir"' EXIT

# No password on the command line and none in the environment: this runs as the
# postgres user over the local socket, which peer-authenticates. A password
# passed here would appear in ps output for every user on the machine.
#
# --format=custom because it restores selectively and compresses better than
# plain SQL; --no-owner and --no-acl so a restore into a differently-named role
# works, which is exactly the situation a recovery is.
pg_dump --format=custom --no-owner --no-acl "$database" > "$workdir/dump"

gzip -9 "$workdir/dump"

# Verify before uploading, not after. An unverified backup is a backup you find
# out about during a restore, which is the worst possible moment.
gzip -t "$workdir/dump.gz"

size="$(wc -c < "$workdir/dump.gz" | tr -d ' ')"
if [ "$size" -lt 1000 ]; then
    echo "backup: refusing to upload a ${size}-byte dump, which is not a database" >&2
    exit 1
fi

aws s3 cp "$workdir/dump.gz" "s3://$ARTIFACT_BUCKET/backups/$name" \
    --region "$AWS_REGION" \
    --sse AES256 \
    --only-show-errors

echo "backup: uploaded $name (${size} bytes)"
