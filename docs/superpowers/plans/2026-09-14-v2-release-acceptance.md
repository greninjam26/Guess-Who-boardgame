# v2.0 Release Acceptance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the already-deployed online game into a verified, installable, documented v2.0 release candidate.

**Architecture:** Treat release completion as a sequence of evidence-producing gates: automated preflight, human two-client acceptance against AWS, non-empty backup restoration, installer validation, recovery-runbook closure, and permanent documentation. A failed gate stops the sequence and produces the smallest possible repair; Phase 11 work does not begin until every v2.0 gate passes.

**Tech Stack:** Java 17, Swing, Maven, Spring Boot 4.1.1, PostgreSQL 15, Caddy, AWS EC2/S3/SSM/CloudFormation, GitHub Actions, `jpackage`

**Spec:** `docs/ROADMAP.md`, Phase 10; exact operational procedures and evidence live in `deploy/aws/README.md`

**Execution order:** Task 5 is a one-time prerequisite because it changes the repository. Complete it first, review it, and obtain explicit authorization for its commit. Then freeze that resulting commit as the candidate in Task 1 and continue with Tasks 2–4, 6 and 7. Once `package-bootstrap.sh` exists in the candidate, future runs proceed numerically from Task 1. Never collect release evidence against a SHA that predates a change intended for the final tag.

## Global Constraints

- The live endpoint is `https://greninja-guesswho.duckdns.org`.
- Keep the AWS account on the Free Plan; do not upgrade it, join AWS Organizations, or enable Control Tower.
- Preserve the deployed single-instance boundary: only ports 80 and 443 are public; Spring Boot and PostgreSQL stay on loopback.
- Use two different accounts, two persistent client data directories, and two real networks for the final acceptance game.
- Do not mark Phase 10 complete from configuration or automated tests alone; the visual two-client AWS session must pass.
- Do not tag v2.0 until both installers have been built and launched on their target operating systems.
- Do not create a Git commit or tag unless the maintainer explicitly authorizes that exact action. Checkpoints below prepare changes and suggest messages only.
- Before any public deployment, run `mvn --batch-mode --no-transfer-progress clean verify` and all deployment/packaging contract tests.
- Before destructive teardown, retain and restore-test an off-AWS backup. Teardown itself is outside this release plan.

## File Map

- `deploy/aws/README.md` — permanent deployment, backup, recovery, cost, acceptance procedures, and evidence log.
- `deploy/aws/bootstrap.sh` and its companion files — the runtime bundle that a replacement instance must receive reproducibly.
- `deploy/aws/tests/runtime-contract.sh` — enforces the replacement-instance bootstrap delivery contract.
- `.github/workflows/installers.yml` — builds target-native installers and injects the public endpoint.
- `README.md` — end-user installation, public service, and project overview.
- `docs/ARCHITECTURE.md` — durable module and deployed-runtime architecture.
- `docs/ROADMAP.md` — authoritative release/phase status after acceptance.

---

### Task 1: Establish a Fresh Release Baseline

**Files:**

- Modify: `deploy/aws/README.md`

**Interfaces:**

- Consumes: the release candidate checkout and the public status endpoint.
- Produces: a dated preflight record tied to the exact Git SHA used by every later gate.

- [ ] **Step 1: Record the candidate identity and repository state**

Run:

```bash
git rev-parse HEAD
git status --short --branch
```

Expected: `HEAD` is the candidate SHA. Existing untracked planning/status documents are named explicitly; there are no unexplained source changes.

- [ ] **Step 2: Run the complete Java verification suite**

Run:

```bash
mvn --batch-mode --no-transfer-progress clean verify
```

Expected: all three modules succeed. `PostgresMigrationTest` may be environment-gated locally, but the candidate's CI run must show the PostgreSQL job passing.

- [ ] **Step 3: Run every cheap release contract**

Run:

```bash
bash deploy/aws/tests/template-contract.sh
bash deploy/aws/tests/runtime-contract.sh
bash deploy/aws/tests/deploy-contract.sh
bash deploy/aws/tests/teardown-contract.sh
bash packaging/tests/build-installer-contract.sh
bash packaging/tests/server-url-validation.sh
```

Expected: every script exits zero and reports that its constraints hold.

- [ ] **Step 4: Prove the deployed boundary is responding**

Run:

```bash
curl -fsS https://greninja-guesswho.duckdns.org/api/status
bash deploy/aws/smoke-test.sh https://greninja-guesswho.duckdns.org
```

Expected: the first command prints `{"status":"online"}`; the smoke test accepts HTTPS and finds no leaked implementation details.

- [ ] **Step 5: Record the evidence**

Add one row to `deploy/aws/README.md`'s deployment log containing the UTC time, candidate SHA, local OS/JDK, Maven result, contract-test result, CI URL, and public smoke-test result. Do not mark any Phase 10 gate complete from this preflight alone.

**Checkpoint:** Review the evidence against the exact candidate SHA. Suggested commit message after all release documentation is ready: `docs: record v2 release acceptance`

---

### Task 2: Verify the Live Host's Operational Claims

**Files:**

- Modify: `deploy/aws/README.md`
- Modify: `docs/ROADMAP.md`

**Interfaces:**

- Consumes: the live stack, Session Manager access, the candidate SHA from Task 1, and the AWS CLI identity that owns `guess-who-demo`.
- Produces: deployment-log evidence for rollback, bootstrap idempotency, the proxy boundary, CloudWatch delivery, budget notifications, and teardown rehearsal.

- [ ] **Step 1: Confirm the installed proxy and CloudWatch agent**

In Session Manager, run:

```bash
sudo grep -n 'request_header -X-Real-IP' /etc/caddy/Caddyfile
sudo systemctl is-active caddy
sudo systemctl is-active amazon-cloudwatch-agent
sudo tail -n 1 /var/log/guesswho/server.log
```

Expected: the Caddy directive is present, both services are active, and the server log's last complete line is one ECS JSON object.

From an authenticated maintainer shell, run:

```bash
aws logs describe-log-streams \
  --region us-east-1 \
  --log-group-name /guess-who/demo/server \
  --order-by LastEventTime \
  --descending \
  --max-items 1
```

Expected: at least one stream has a recent `lastEventTimestamp`. Open that stream and confirm one deployed server event is present before recording the gate.

- [ ] **Step 2: Verify both budget email recipients**

In AWS Billing and Cost Management, open Budgets → `guess-who-demo` and inspect its subscribers.

Expected: both the 80% and 100% actual-cost notifications name the intended direct email recipient. Direct `EMAIL` subscribers expose no confirmation state; only an Amazon SNS subscription needs separate confirmation. Record that the recipients match, never the email address.

- [ ] **Step 3: Run bootstrap a second time on the live host**

In Session Manager, locate the checked eight-file source directory and validate every input before running anything. On the first execution of this plan, Task 5 must already have produced and delivered that exact bundle; do not reconstruct it from the files installed around the host:

```bash
BOOTSTRAP_PATH="$(sudo find /opt/guesswho -maxdepth 4 -type f -name bootstrap.sh -print -quit)"
test -n "$BOOTSTRAP_PATH"
BOOTSTRAP_DIR="$(dirname "$BOOTSTRAP_PATH")"
for file in bootstrap.sh set-db-password.sh backup.sh guesswho.service \
  guesswho-backup.service guesswho-backup.timer Caddyfile cloudwatch-agent.json; do
  test -f "$BOOTSTRAP_DIR/$file"
done
ARTIFACT_BUCKET="$(sudo sed -n 's/^ARTIFACT_BUCKET=//p' /etc/guesswho/backup.env)"
AWS_REGION="$(sudo sed -n 's/^AWS_REGION=//p' /etc/guesswho/backup.env)"
test -n "$ARTIFACT_BUCKET" && test -n "$AWS_REGION"
sudo PUBLIC_HOSTNAME=greninja-guesswho.duckdns.org \
  ARTIFACT_BUCKET="$ARTIFACT_BUCKET" \
  AWS_REGION="$AWS_REGION" \
  bash "$BOOTSTRAP_PATH"
sudo systemctl is-active postgresql caddy guesswho.service amazon-cloudwatch-agent
curl -fsS http://127.0.0.1:8080/api/status
```

Expected: bootstrap exits zero, the four services are active, and the local status endpoint returns `{"status":"online"}`. Record whether the run changed any rendered file or service state; any unexpected change is a release blocker.

- [ ] **Step 4: Rehearse rejection before installation**

Follow `deploy/aws/README.md` → **Rehearsing a rollback** → **A corrupt artifact** using the bucket resolved from `/etc/guesswho/backup.env`.

Expected: `jar tf` rejects the candidate before the current symlink or service changes, and the public status endpoint remains online.

- [ ] **Step 5: Rehearse automatic rollback after a failed health check**

Follow the runbook's unhealthy-candidate procedure. Capture `readlink -f /opt/guesswho/current/server.jar` before and after the attempt.

Expected: the candidate is installed but never becomes healthy, and the symlink returns to the exact previous release. The public endpoint recovers. Remove only the two named `rollback-test-1` and `rollback-test-2` S3 prefixes and the named `/tmp` rehearsal files.

- [ ] **Step 6: Rehearse teardown without deleting anything**

From the repository root on an authenticated maintainer machine, run:

```bash
bash deploy/aws/teardown.sh --dry-run
```

Expected: the script resolves `guess-who-demo`, downloads and verifies the newest backup, prints the exact resources it would remove, and explicitly reports that nothing was deleted. Retain the exported backup outside AWS.

- [ ] **Step 7: Record and close the operational gates**

Add one deployment-log row per gate with UTC time, candidate SHA, command result, and non-secret evidence. Mark the matching Phase 10 checkbox only after its evidence row exists.

**Checkpoint:** Every live-host operational claim has direct evidence and the public service is healthy. Suggested commit message after all release documentation is ready: `docs: record v2 release acceptance`

---

### Task 3: Run the Real Two-Client AWS Acceptance Session

**Files:**

- Modify: `deploy/aws/README.md`

**Interfaces:**

- Consumes: the candidate SHA from Task 1, the live endpoint, two devices, two networks, and Session Manager access.
- Produces: human-visible proof that online play, presence, reconnect, persistence, reveal, and leaderboard behavior work together in production.

- [ ] **Step 1: Prepare persistent, isolated client identities**

On source-run clients, build once and create named homes:

```bash
mvn install -DskipTests
mkdir -p /tmp/guesswho-aws-client-a /tmp/guesswho-aws-client-b
```

Start client A:

```bash
java -Duser.home=/tmp/guesswho-aws-client-a \
  -Dguesswho.server.url=https://greninja-guesswho.duckdns.org \
  -cp "desktop-client/target/desktop-client-1.0.0.jar:desktop-client/target/lib/*" \
  com.guesswho.ui.GUI
```

Start client B on the second device/network with its own data directory. If it is also a source-run macOS/Linux client, use `/tmp/guesswho-aws-client-b` in the same command. Never reuse A's directory for B.

- [ ] **Step 2: Exercise account and room setup**

Create a fresh account on each client. From A, create a room. From B, join after typing the code in lowercase with a space after its third character.

Expected: both sessions remain distinct, the normalized code is accepted, and neither client sees the other player's character.

- [ ] **Step 3: Exercise visible game controls**

Choose characters, ask and answer at least one question in each direction, press Guess with no cards flipped, then flip several cards and press Guess again.

Expected: transcripts agree; the first guess attempt explains what selection is required; eliminated cards remain visibly faded and clickable.

- [ ] **Step 4: Verify the wall-clock presence rule**

Leave both windows open and untouched for four full minutes.

Expected: neither player forfeits because polling continues to prove presence. Any forfeit is a release blocker; stop and diagnose before continuing.

- [ ] **Step 5: Capture the active database row before restart**

In Session Manager, read the room code without embedding it in shell history and query the row:

```bash
read -r -p 'Room code: ' ROOM_CODE
NORMALIZED_ROOM_CODE="$(printf '%s' "$ROOM_CODE" | tr -d '[:space:]-' | tr '[:lower:]' '[:upper:]')"
case "$NORMALIZED_ROOM_CODE" in
  ??????) ;;
  *) echo 'Room code must normalize to six characters' >&2; exit 1 ;;
esac
sudo -u postgres psql -d guesswho -v room_code="$ROOM_CODE" <<'SQL'
SELECT status, version, length(game_state)
FROM game_rooms
WHERE code = upper(regexp_replace(:'room_code', '[[:space:]-]', '', 'g'));
SQL
```

Record `status`, `version`, and `length` in the deployment log.

- [ ] **Step 6: Restart the deployed application during the active game**

Run through Session Manager:

```bash
sudo systemctl restart guesswho.service
sudo systemctl is-active guesswho.service
```

Expected: both clients show the reconnecting state and recover without input; neither game nor login is lost.

- [ ] **Step 7: Prove the restart did not rewrite the game**

Repeat the query from Step 5 with the same room code.

Expected: `status`, `version`, and `length(game_state)` exactly match the pre-restart values. The player who owed the move can still make it.

- [ ] **Step 8: Exercise disconnect, rejoin, finish, reveal, and leaderboard**

Close B completely. Confirm A reports the absence after roughly 15 seconds. Reopen B with the same client directory and accept the rejoin offer. Continue through at least one incorrect guess, then finish with a correct guess.

Expected: B rejoins the same game; both characters are revealed; both commitment and answer-review verdicts pass; the result appears under both accounts in **vs Player (online)** on both clients.

- [ ] **Step 9: Verify independent network limits**

Keep A and B on different public networks. Perform ordinary sign-in and room operations from both after one network has recently exercised those endpoints.

Expected: one network's allowance does not cause the other network to receive `429`. Do not deliberately hammer account endpoints beyond the documented test allowance.

- [ ] **Step 10: Record the session**

Add to `deploy/aws/README.md`'s deployment log: UTC time, release SHA, client OS versions, the two network types, room code, before/after row values, and the pass/fail outcome of every acceptance row. Do not record passwords, tokens, public IP addresses, or character nonces.

**Checkpoint:** Every manual row has an explicit pass/fail and supporting observation. Suggested commit message after all release documentation is ready: `docs: record v2 release acceptance`

---

### Task 4: Restore a Backup Containing Real Acceptance Data

**Files:**

- Modify: `deploy/aws/README.md`

**Interfaces:**

- Consumes: the accounts and completed online result produced by Task 3.
- Produces: an off-AWS archive whose integrity and restored row counts match production.

- [ ] **Step 1: Record live counts and trigger a new backup**

In Session Manager, run:

```bash
sudo -u postgres psql -d guesswho -c \
  'SELECT (SELECT COUNT(*) FROM accounts) AS accounts,
          (SELECT COUNT(*) FROM game_results) AS results,
          (SELECT COUNT(*) FROM game_result_question_answers) AS answers;'
sudo systemctl start guesswho-backup.service
sudo systemctl status guesswho-backup.service --no-pager
```

Expected: all three counts are non-zero and the one-shot backup service exits successfully.

- [ ] **Step 2: Resolve and download the newest object without guessing names**

On an authenticated maintainer machine, run:

```bash
AWS_REGION=us-east-1
STACK_NAME=guess-who-demo
BUCKET="$(aws cloudformation describe-stacks --region "$AWS_REGION" --stack-name "$STACK_NAME" \
  --query "Stacks[0].Outputs[?OutputKey=='ArtifactBucketName'].OutputValue | [0]" --output text)"
LATEST_KEY="$(aws s3api list-objects-v2 --region "$AWS_REGION" --bucket "$BUCKET" --prefix backups/ \
  --query 'sort_by(Contents,&LastModified)[-1].Key' --output text)"
test -n "$BUCKET" && test "$BUCKET" != None
test -n "$LATEST_KEY" && test "$LATEST_KEY" != None
mkdir -p /Users/greninja/Documents/Guess-Who-backups
ARCHIVE_PATH="/Users/greninja/Documents/Guess-Who-backups/$(basename "$LATEST_KEY")"
aws s3 cp "s3://$BUCKET/$LATEST_KEY" "$ARCHIVE_PATH" --region "$AWS_REGION"
printf 'Downloaded %s\n' "$ARCHIVE_PATH"
```

Expected: both resolved identifiers are non-empty and one new `.dump.gz` exists outside the repository and outside AWS.

- [ ] **Step 3: Verify archive integrity and checksum**

In the same shell, run against the exact downloaded filename resolved in Step 2:

```bash
test -f "$ARCHIVE_PATH"
gzip -t "$ARCHIVE_PATH"
shasum -a 256 "$ARCHIVE_PATH"
```

Expected: `gzip -t` exits zero and the checksum names only the archive downloaded in Step 2.

- [ ] **Step 4: Restore into one explicitly named temporary database**

In the Session Manager shell, resolve the newest production backup independently and download that exact object, then restore it:

```bash
ARTIFACT_BUCKET="$(sudo sed -n 's/^ARTIFACT_BUCKET=//p' /etc/guesswho/backup.env)"
AWS_REGION="$(sudo sed -n 's/^AWS_REGION=//p' /etc/guesswho/backup.env)"
test -n "$ARTIFACT_BUCKET" && test -n "$AWS_REGION"
LATEST_KEY="$(aws s3api list-objects-v2 --region "$AWS_REGION" --bucket "$ARTIFACT_BUCKET" \
  --prefix backups/ --query 'sort_by(Contents,&LastModified)[-1].Key' --output text)"
test -n "$LATEST_KEY" && test "$LATEST_KEY" != None
aws s3 cp "s3://$ARTIFACT_BUCKET/$LATEST_KEY" /tmp/guesswho-acceptance.dump.gz \
  --region "$AWS_REGION"
gzip -t /tmp/guesswho-acceptance.dump.gz
gunzip -c /tmp/guesswho-acceptance.dump.gz > /tmp/guesswho-acceptance.dump
sudo -u postgres dropdb --if-exists guesswho_restore_acceptance
sudo -u postgres createdb guesswho_restore_acceptance
sudo -u postgres pg_restore --no-owner --no-acl \
  -d guesswho_restore_acceptance /tmp/guesswho-acceptance.dump
sudo -u postgres psql -d guesswho_restore_acceptance -c \
  'SELECT (SELECT COUNT(*) FROM accounts) AS accounts,
          (SELECT COUNT(*) FROM game_results) AS results,
          (SELECT COUNT(*) FROM game_result_question_answers) AS answers;'
```

Expected: all three restored counts are non-zero and exactly match Step 1.

- [ ] **Step 5: Remove only the named restore artifacts and record evidence**

Run:

```bash
sudo -u postgres dropdb guesswho_restore_acceptance
rm -f /tmp/guesswho-acceptance.dump.gz /tmp/guesswho-acceptance.dump
```

Record the object key, UTC time, live/restored counts, checksum, and off-AWS path in `deploy/aws/README.md`. Keep the off-AWS archive.

**Checkpoint:** The retained archive has a checksum and restored non-zero data. Suggested commit message after all release documentation is ready: `docs: record v2 release acceptance`

---

### Task 5: Close Replacement-Instance Bootstrap Delivery

**Files:**

- Create: `deploy/aws/package-bootstrap.sh`
- Modify: `deploy/aws/tests/runtime-contract.sh`
- Modify: `deploy/aws/README.md`

**Interfaces:**

- Consumes: the runtime files already installed by `deploy/aws/bootstrap.sh`.
- Produces: one checksummed archive plus exact S3/SSM commands that can bootstrap a clean replacement instance.

- [ ] **Step 1: Add a failing bundle contract**

Extend `deploy/aws/tests/runtime-contract.sh` to require `package-bootstrap.sh` and assert that the archive includes exactly:

```text
bootstrap.sh
set-db-password.sh
backup.sh
guesswho.service
guesswho-backup.service
guesswho-backup.timer
Caddyfile
cloudwatch-agent.json
```

Also assert that packaging emits a SHA-256 file, refuses missing inputs, and never includes `parameters.json`, `server.env`, database dumps, or tokens.

Add this check after the existing `scripts`, `units`, and `others` declarations, and update the final file count to include the packaging script:

```bash
need_file "package-bootstrap.sh"

repo_root="$(cd "$aws_dir/../.." && pwd)"
bundle_dir="$repo_root/target/aws-bootstrap"
archive="$bundle_dir/guesswho-bootstrap.tar.gz"
checksum="$archive.sha256"
expected_entries="$(printf '%s\n' \
    bootstrap.sh \
    set-db-password.sh \
    backup.sh \
    guesswho.service \
    guesswho-backup.service \
    guesswho-backup.timer \
    Caddyfile \
    cloudwatch-agent.json)"

if ! bash "$aws_dir/package-bootstrap.sh" >/dev/null; then
    fail "package-bootstrap.sh could not build the runtime bundle"
elif [ ! -f "$archive" ] || [ ! -f "$checksum" ]; then
    fail "package-bootstrap.sh did not create the archive and checksum"
else
    actual_entries="$(tar -tzf "$archive")"
    [ "$actual_entries" = "$expected_entries" ] \
        || fail "the bootstrap archive does not contain exactly the runtime files"
    (cd "$bundle_dir" && shasum -a 256 -c "$(basename "$checksum")" >/dev/null) \
        || fail "the bootstrap archive checksum does not verify"
fi
```

Run:

```bash
bash deploy/aws/tests/runtime-contract.sh
```

Expected: failure because `deploy/aws/package-bootstrap.sh` does not exist yet.

- [ ] **Step 2: Implement validated bundle creation**

Create `deploy/aws/package-bootstrap.sh` with this implementation:

```bash
#!/usr/bin/env bash

set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$here/../.." && pwd)"
output_dir="$repo_root/target/aws-bootstrap"
archive="$output_dir/guesswho-bootstrap.tar.gz"
checksum="$archive.sha256"
runtime_files=(
    bootstrap.sh
    set-db-password.sh
    backup.sh
    guesswho.service
    guesswho-backup.service
    guesswho-backup.timer
    Caddyfile
    cloudwatch-agent.json
)

staging="$(mktemp -d)"
trap 'rm -rf "$staging"' EXIT

for file in "${runtime_files[@]}"; do
    source_file="$here/$file"
    if [ ! -f "$source_file" ]; then
        echo "Missing bootstrap input: $file" >&2
        exit 1
    fi
    case "$file" in
        *.sh) install -m 0755 "$source_file" "$staging/$file" ;;
        *)    install -m 0644 "$source_file" "$staging/$file" ;;
    esac
done

mkdir -p "$output_dir"
tar -C "$staging" -czf "$archive" "${runtime_files[@]}"
(
    cd "$output_dir"
    shasum -a 256 "$(basename "$archive")" > "$(basename "$checksum")"
)

echo "Created $archive"
echo "Created $checksum"
```

This script reads only the eight fixed inputs, so real parameters, generated environment files, database dumps, and tokens cannot enter the archive through directory recursion.

- [ ] **Step 3: Verify the real archive**

Run:

```bash
bash deploy/aws/package-bootstrap.sh
tar -tzf target/aws-bootstrap/guesswho-bootstrap.tar.gz
(cd target/aws-bootstrap && shasum -a 256 -c guesswho-bootstrap.tar.gz.sha256)
bash deploy/aws/tests/runtime-contract.sh
```

Expected: the archive lists exactly the eight runtime files and both checksum and contract checks pass.

- [ ] **Step 4: Document clean-host delivery**

In `deploy/aws/README.md`, document this sequence: build the archive; set `RELEASE_SHA="$(git rev-parse HEAD)"`; upload both archive and checksum under `bootstrap/$RELEASE_SHA/`; use SSM on the exact replacement instance to download both; verify the checksum; extract into a new `/opt/guesswho/bootstrap-source` directory; run `bootstrap.sh` from that directory with `PUBLIC_HOSTNAME`, `ARTIFACT_BUCKET`, and `AWS_REGION`; run bootstrap a second time to prove idempotency; then deploy the server through the existing GitHub workflow.

Always derive `RELEASE_SHA` with `git rev-parse HEAD` during execution rather than typing a branch name or mutable label.

- [ ] **Step 5: Run the full local release contracts again**

Run:

```bash
bash deploy/aws/tests/runtime-contract.sh
bash deploy/aws/tests/template-contract.sh
bash deploy/aws/tests/deploy-contract.sh
```

Expected: all pass. Do not replace the live instance merely to rehearse this release-documentation gap.

**Checkpoint:** A replacement host can receive the exact bootstrap inputs without relying on files that happen to exist on the current instance. Review the change and request explicit authorization to commit it before Task 1 freezes the candidate SHA. Suggested commit message: `ops: package the host bootstrap bundle`

---

### Task 6: Build and Launch Both Release Installers

**Files:**

- Modify only if a failure is found: `packaging/build-installer.sh`
- Modify only if a failure is found: `.github/workflows/installers.yml`
- Modify: `deploy/aws/README.md`

**Interfaces:**

- Consumes: the accepted candidate SHA and repository variable `GUESSWHO_SERVER_URL`.
- Produces: a macOS `.dmg` and Windows `.msi` proven to install, launch, and contact the deployed service.

- [x] **Step 1: Verify the repository variable**

In GitHub Actions repository variables, confirm `GUESSWHO_SERVER_URL` is exactly:

```text
https://greninja-guesswho.duckdns.org
```

No trailing slash, path, query, fragment, whitespace, or embedded credentials are allowed.

- [x] **Step 2: Manually run the installer workflow from the candidate SHA**

Dispatch `.github/workflows/installers.yml` from the candidate revision. Do not create a tag yet.

Expected: both matrix jobs pass and upload `installer-macos` and `installer-windows` artifacts.

- [x] **Step 3: Validate the macOS artifact**

On Apple silicon macOS, install from the `.dmg`, use the documented first-launch right-click flow, start the app, register or sign in, and reach the online-room screen without setting any JVM property.

Expected: the installed app contacts the AWS endpoint; it does not attempt `localhost:8080`.

- [x] **Step 4: Validate the Windows artifact**

On a real Windows system, install the `.msi`, pass through the documented SmartScreen flow, launch the app, register or sign in, and reach the online-room screen.

Expected: installation and launch succeed and the app contacts the AWS endpoint. A green Windows build job alone is not acceptance.

- [x] **Step 5: Record immutable evidence**

Add the workflow URL, artifact names, candidate SHA, macOS version/result, and Windows version/result to `deploy/aws/README.md`'s deployment log.

**Checkpoint:** Both native installers were launched on their target systems and used the public endpoint. Suggested commit message after all release documentation is ready: `docs: record v2 release acceptance`

---

### Task 7: Make Permanent Documentation Match Reality

**Files:**

- Modify: `README.md`
- Modify: `docs/ARCHITECTURE.md`
- Modify: `docs/ROADMAP.md`
- Modify: `deploy/aws/README.md`

**Interfaces:**

- Consumes: passing evidence from Tasks 1–6.
- Produces: durable project documentation and a release-ready, untagged working tree.

- [ ] **Step 1: Close only the Phase 10 gates with evidence**

Mark each remaining Phase 10 checkbox complete only when its deployment-log evidence exists. Keep `Tag v2.0` unchecked until the tag actually exists.

- [ ] **Step 2: Update the end-user README after installer acceptance**

Replace the current limitation saying released installers use localhost with the accepted v2.0 behavior. Keep the source-run localhost default and `guesswho.server.url` override documented for developers. Add current application screenshots only if they show the accepted build.

- [ ] **Step 3: Reconcile the architecture and runbook**

Update `docs/ARCHITECTURE.md` only if the accepted deployment or replacement-bootstrap path differs from the architecture already recorded. Make the runbook's remaining-language and every open Phase 10 checkbox agree.

- [ ] **Step 4: Finish the deployment log**

Record the final accepted candidate SHA, every operational gate, the two-client session, the non-empty restore counts and checksum, both installer results, and any corrective deployment. Never record account credentials, tokens, IP addresses, private bucket names, database passwords, or character nonces.

- [ ] **Step 5: Run the final verification gate**

Run:

```bash
mvn --batch-mode --no-transfer-progress clean verify
bash deploy/aws/tests/template-contract.sh
bash deploy/aws/tests/runtime-contract.sh
bash deploy/aws/tests/deploy-contract.sh
bash deploy/aws/tests/teardown-contract.sh
bash packaging/tests/build-installer-contract.sh
bash packaging/tests/server-url-validation.sh
bash deploy/aws/smoke-test.sh https://greninja-guesswho.duckdns.org
git diff --check
git status --short
```

Expected: all executable checks pass; the status lists only the reviewed release documentation/bootstrap changes; no credentials, tokens, database files, backups, or generated installer artifacts are tracked.

- [ ] **Step 6: Prepare the release handoff without committing or tagging**

Present the complete diff, the acceptance evidence, and these suggested messages to the maintainer:

```text
ops: package the host bootstrap bundle
docs: record v2 release acceptance
```

After those exact changes are explicitly authorized and committed, request separate explicit authorization before creating `v2.0` and publishing the draft release.

**Checkpoint:** Phase 10 is truthful, all evidence lives in permanent documentation, and the repository is ready for an explicitly authorized v2.0 commit/tag sequence.
