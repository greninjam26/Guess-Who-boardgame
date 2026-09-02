#!/usr/bin/env bash
#
# Removes the Guess Who demo from AWS, after getting the data out.
#
# The most dangerous script here. It deletes things that cannot be recovered,
# and it will be run once, months from now, by somebody who has not read it
# recently — so it refuses by default, exports and verifies before destroying
# anything, and checks afterwards that the deletion actually happened.
#
#   bash teardown.sh --dry-run                     # what would happen
#   bash teardown.sh --confirm-delete ~/guess-who-final-export
#
# The export directory is outside AWS and is never deleted by this script.

set -euo pipefail

STACK="${STACK_NAME:-guess-who-demo}"
REGION="${AWS_REGION:-us-east-1}"

dry_run=true
confirmed=false
export_dir="${HOME}/guess-who-final-export"

while [ $# -gt 0 ]; do
    case "$1" in
        --dry-run)
            dry_run=true
            ;;
        --confirm-delete)
            # The exact argument, spelled out. Nothing shorter, and no flag that
            # could be reached by a typo in another one.
            confirmed=true
            dry_run=false
            ;;
        -*)
            echo "unknown option: $1" >&2
            exit 2
            ;;
        *)
            export_dir="$1"
            ;;
    esac
    shift
done

# Unreachable today, and kept on purpose. Nothing in the parsing above can
# produce confirmed=false with dry_run=false, so mutation testing cannot make
# this branch fail — which is worth stating rather than leaving as a check that
# looks tested and is not. It is here for the next flag somebody adds: a
# --force that clears dry_run without setting confirmed would otherwise walk
# straight into the deletions.
#
# What actually protects the script is the default of dry_run=true and the
# early exit before any deletion. Both of those are covered.
if [ "$confirmed" != true ] && [ "$dry_run" != true ]; then
    echo "Refusing to delete anything without --confirm-delete." >&2
    exit 2
fi

say() { printf '\n== %s\n' "$1"; }
note() { printf '   %s\n' "$1"; }

if [ "$dry_run" = true ]; then
    say "DRY RUN — discovery and backup verification only, nothing will be deleted"
else
    say "DELETING the $STACK stack in $REGION"
fi

# ------------------------------------------------------------- who and what
say "checking identity and stack"
identity="$(aws sts get-caller-identity --query 'Arn' --output text)"
note "acting as $identity"

status="$(aws cloudformation describe-stacks \
    --stack-name "$STACK" --region "$REGION" \
    --query 'Stacks[0].StackStatus' --output text 2>/dev/null || true)"
if [ -z "$status" ] || [ "$status" = "None" ]; then
    echo "No stack named $STACK in $REGION. Nothing to tear down." >&2
    exit 1
fi
note "stack status: $status"

stack_id="$(aws cloudformation describe-stacks \
    --stack-name "$STACK" --region "$REGION" \
    --query 'Stacks[0].StackId' --output text)"

BUCKET="$(aws cloudformation describe-stacks \
    --stack-name "$STACK" --region "$REGION" \
    --query "Stacks[0].Outputs[?OutputKey=='ArtifactBucketName'].OutputValue" \
    --output text)"

# An empty name here would turn every s3 command below into one against
# "s3:///", which is the shape of accident this whole script exists to avoid.
if [ -z "$BUCKET" ] || [ "$BUCKET" = "None" ]; then
    echo "Could not resolve the artifact bucket from the stack outputs." >&2
    echo "Refusing to continue: a deletion loop with no bucket name is not safe." >&2
    exit 1
fi
note "bucket: $BUCKET"

# --------------------------------------------------------- get the data out
say "exporting the newest database backup"
mkdir -p "$export_dir"

newest="$(aws s3api list-objects-v2 \
    --bucket "$BUCKET" --prefix "backups/" --region "$REGION" \
    --query 'sort_by(Contents, &LastModified)[-1].Key' --output text 2>/dev/null || true)"

if [ -z "$newest" ] || [ "$newest" = "None" ]; then
    echo "There is no backup in s3://$BUCKET/backups/." >&2
    echo "Refusing to tear down a stack whose data has never been exported." >&2
    exit 1
fi
note "newest backup: $newest"

export_file="$export_dir/$(basename "$newest")"
aws s3 cp "s3://$BUCKET/$newest" "$export_file" --region "$REGION" --only-show-errors

# Verified here, before anything is deleted. An export checked afterwards has
# nothing left to go back to when the check fails.
gzip -t "$export_file"
note "gzip integrity: ok"

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
gunzip -c "$export_file" > "$tmp/dump"
# Reads the dump's table of contents. Proves it is a pg_dump archive rather than
# a well-formed gzip of something useless.
pg_restore --list "$tmp/dump" > "$tmp/toc"
note "archive table of contents: $(wc -l < "$tmp/toc" | tr -d ' ') entries"

checksum="$(sha256sum "$export_file" | cut -d' ' -f1)"
say "verified export"
note "path:     $export_file"
note "sha256:   $checksum"
note "Keep this. It is the only copy once the stack is gone."

if [ "$dry_run" = true ]; then
    say "dry run complete"
    note "would empty:  s3://$BUCKET (all versions and delete markers)"
    note "would delete: stack $stack_id"
    note "nothing was deleted"
    exit 0
fi

# ----------------------------------------------------------------- delete
say "emptying s3://$BUCKET"
# Versioned bucket: deleting objects leaves versions, and a bucket with versions
# cannot be deleted. Both versions and delete markers have to go.
while true; do
    batch="$(aws s3api list-object-versions \
        --bucket "$BUCKET" --region "$REGION" --max-items 500 \
        --query '{Objects: Versions[].{Key:Key,VersionId:VersionId}}' \
        --output json 2>/dev/null || echo '{"Objects":null}')"
    [ "$(echo "$batch" | jq -r '.Objects | length // 0')" -eq 0 ] && break
    aws s3api delete-objects --bucket "$BUCKET" --region "$REGION" \
        --delete "$(echo "$batch" | jq -c '{Objects: .Objects, Quiet: true}')" >/dev/null
done
while true; do
    batch="$(aws s3api list-object-versions \
        --bucket "$BUCKET" --region "$REGION" --max-items 500 \
        --query '{Objects: DeleteMarkers[].{Key:Key,VersionId:VersionId}}' \
        --output json 2>/dev/null || echo '{"Objects":null}')"
    [ "$(echo "$batch" | jq -r '.Objects | length // 0')" -eq 0 ] && break
    aws s3api delete-objects --bucket "$BUCKET" --region "$REGION" \
        --delete "$(echo "$batch" | jq -c '{Objects: .Objects, Quiet: true}')" >/dev/null
done
# The bucket is DeletionPolicy: Retain, so CloudFormation leaves it behind
# deliberately — that is what stops a stack deletion destroying the last backup.
# Now that the export is verified and off AWS, it goes explicitly.
aws s3api delete-bucket --bucket "$BUCKET" --region "$REGION" || \
    note "bucket not deleted; remove it by hand once you are sure"

say "deleting the stack"
aws cloudformation delete-stack --stack-name "$STACK" --region "$REGION"
aws cloudformation wait stack-delete-complete --stack-name "$STACK" --region "$REGION"

# ----------------------------------------------------------------- verify
say "verifying nothing is left"
remaining=0

instances="$(aws ec2 describe-instances --region "$REGION" \
    --filters "Name=tag:Project,Values=guess-who" \
              "Name=instance-state-name,Values=pending,running,stopping,stopped" \
    --query 'Reservations[].Instances[].InstanceId' --output text 2>/dev/null || true)"
if [ -n "$instances" ] && [ "$instances" != "None" ]; then
    echo "  STILL THERE: instances $instances"
    remaining=$((remaining + 1))
fi

addresses="$(aws ec2 describe-addresses --region "$REGION" \
    --filters "Name=tag:Project,Values=guess-who" \
    --query 'Addresses[].AllocationId' --output text 2>/dev/null || true)"
if [ -n "$addresses" ] && [ "$addresses" != "None" ]; then
    # An Elastic IP not attached to anything is billed and is invisible unless
    # you go looking, which makes it the classic forgotten resource.
    echo "  STILL THERE: elastic IPs $addresses"
    remaining=$((remaining + 1))
fi

if aws s3api head-bucket --bucket "$BUCKET" --region "$REGION" 2>/dev/null; then
    echo "  STILL THERE: bucket $BUCKET"
    remaining=$((remaining + 1))
fi

groups="$(aws logs describe-log-groups --region "$REGION" \
    --log-group-name-prefix /guess-who/ \
    --query 'logGroups[].logGroupName' --output text 2>/dev/null || true)"
if [ -n "$groups" ] && [ "$groups" != "None" ]; then
    echo "  STILL THERE: log groups $groups"
    remaining=$((remaining + 1))
fi

echo
if [ "$remaining" -ne 0 ]; then
    echo "Teardown incomplete: $remaining kind(s) of resource remain. Remove them by hand." >&2
    echo "Your export is still at $export_file" >&2
    exit 1
fi

say "teardown complete"
note "export kept at $export_file"
note "sha256 $checksum"
note "Check Billing and Cost Explorer in a day or two for anything unexpected."
