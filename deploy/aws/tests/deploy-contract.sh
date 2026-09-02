#!/usr/bin/env bash
#
# Checks the deployment path: the GitHub workflow and the script it runs on the
# instance.
#
# This is the one part of the system that holds credentials and runs as root on
# a public host, so the things asserted here are mostly about what it must *not*
# do — no stored AWS keys, no SSH, no wider permission than it needs, and no
# executing a script it has not verified.
#
# Reads files, runs nothing, needs no AWS.

set -uo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
aws_dir="$here/.."
workflow="$aws_dir/../../.github/workflows/deploy-aws.yml"
failures=0

fail() { echo "FAIL: $1"; failures=$((failures + 1)); }
has()  { [ -f "$1" ] && grep -qF -- "$2" "$1"; }
hasnt() { [ -f "$1" ] && ! grep -qF -- "$2" "$1"; }

[ -f "$workflow" ] || fail "missing .github/workflows/deploy-aws.yml"
[ -f "$aws_dir/deploy.sh" ] || fail "missing deploy/aws/deploy.sh"

if [ -f "$aws_dir/deploy.sh" ] && ! bash -n "$aws_dir/deploy.sh" 2>/dev/null; then
    fail "deploy.sh is not valid bash"
fi
has "$aws_dir/deploy.sh" "set -euo pipefail" || fail "deploy.sh does not set -euo pipefail"

# --- the workflow is not automatic yet ------------------------------------
# Deploying on every push to main before anybody has watched a deployment work
# is a way to find out about a bad release from a player.
has "$workflow" "workflow_dispatch" || fail "the workflow is not manual"
hasnt "$workflow" "  push:" || fail "the workflow deploys on push before that has been earned"

# --- no long-lived AWS credentials ----------------------------------------
has "$workflow" "id-token: write" || fail "the workflow cannot request an OIDC token"
has "$workflow" "contents: read" || fail "the workflow does not state contents: read"
has "$workflow" "aws-actions/configure-aws-credentials" \
    || fail "the workflow does not use the AWS credentials action"
has "$workflow" "role-to-assume" || fail "the workflow does not assume a role"
for secret in "aws-access-key-id" "AWS_ACCESS_KEY_ID" "AWS_SECRET_ACCESS_KEY"; do
    hasnt "$workflow" "$secret" || fail "the workflow uses a static AWS key ($secret)"
done
hasnt "$workflow" "permissions: write-all" || fail "the workflow grants write-all"

# --- nothing reaches the host except through SSM --------------------------
for forbidden in "scp " "ssh " "ProxyCommand" "-i ~/.ssh"; do
    hasnt "$workflow" "$forbidden" || fail "the workflow uses $forbidden; port 22 is closed"
done
has "$workflow" "ssm send-command" || fail "the workflow does not deploy through SSM"
has "$workflow" "wait command-executed" \
    || fail "the workflow does not wait for the SSM command, so a failed deploy would report success"

# --- the release is built and immutable -----------------------------------
has "$workflow" "clean verify" || fail "the workflow does not run the test suite before packaging"
has "$workflow" 'releases/${GITHUB_SHA}' \
    || fail "the release is not keyed by commit, so a redeploy is not reproducible"

# --- the host verifies what it is about to run as root --------------------
# The instance role can write to this bucket. Without a checksum, anything that
# compromised the instance could replace the script the next deployment runs.
has "$workflow" "deploy.sh.sha256" \
    || fail "the workflow does not publish a checksum for deploy.sh"
has "$workflow" "sha256sum -c" \
    || fail "the instance does not verify deploy.sh before running it as root"

# --- the deployment can undo itself ---------------------------------------
has "$aws_dir/deploy.sh" "jar tf" || fail "deploy.sh does not validate the downloaded artifact"
has "$aws_dir/deploy.sh" "api/status" || fail "deploy.sh does not health-check after restarting"
has "$aws_dir/deploy.sh" "previous" || fail "deploy.sh does not remember what to roll back to"

# --- and it is checked from outside afterwards ----------------------------
has "$workflow" "smoke-test.sh" || fail "the workflow does not run the public smoke test"

if [ "$failures" -eq 0 ]; then
    echo "deploy contract: workflow and deploy.sh checked, all constraints hold"
    exit 0
fi
echo "$failures failure(s)"
exit 1
