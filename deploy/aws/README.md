# Deploying the Guess Who server to AWS

A six-month demo on the AWS Free Plan: one `t3.micro` in `us-east-1` running
PostgreSQL, the Spring Boot server and Caddy, reachable over HTTPS at a DuckDNS
hostname. Everything is in one CloudFormation stack, so the teardown is a stack
deletion rather than a hunt.

**Live since 2026-09-14** at <https://greninja-guesswho.duckdns.org>. **Tear down
by 2027-02-26.** What has happened to it is in the [deployment log](#deployment-log)
at the bottom of this file; why it is built this way is in
[docs/ARCHITECTURE.md](../../docs/ARCHITECTURE.md#deployment).

> **Nothing here creates AWS resources until you run it.** The template and the
> contract test are files; the contract test reads the template and never calls
> AWS, so it runs with no credentials configured.

## The rules this deployment lives by

These are not preferences. Each one is a way the demo stops being free.

- **Stay on the Free Plan.** Never select *Upgrade to Paid Plan*, and never join
  AWS Organizations or Control Tower — either can upgrade the account.
- **`us-east-1` only**, one `t3.micro`, one 12 GB gp3 volume.
- **CPU credits stay `standard`.** On `unlimited`, a busy hour buys surplus
  credits billed in real money. It is the one instance setting that can produce
  a charge by itself.
- **Never add** a NAT gateway, load balancer, RDS, WAF, Secrets Manager, a Route
  53 hosted zone, Redis, Kubernetes, or a second instance.
- **Tear down by day 165.** Record the date below on the day you create the
  account.

The AWS Budget in this stack **alerts; it does not cap.** AWS Budgets cannot
stop spending. It is configured to exclude credits, so it reports gross
consumption — otherwise it would read near zero while the credits lasted and go
quiet until the moment they ran out.

## Before you create anything

Check the template says what it should. Both of these are local:

```bash
bash deploy/aws/tests/template-contract.sh
```

```bash
cfn-lint deploy/aws/template.yaml
```

The contract test asserts the instance type, disk size and encryption, CPU
credit mode, that only ports 80 and 443 are open, log retention, bucket
versioning and public-access blocking, that the budget excludes credits, that no
IAM policy grants `ssm:*` or a wildcard parameter path, and that every taggable
resource is tagged. It fails on any of them.

## Creating the stack

1. **Confirm the account is on the Free Plan.** AWS Console → Billing and Cost
   Management → the plan is stated on the overview. Stop if it says Paid Plan.

2. **Register a DuckDNS hostname** at <https://www.duckdns.org>. Leave the IP as
   it is for now — it gets pointed at the Elastic IP once the stack exists.

3. **Copy the parameters and fill them in.** The example contains no real
   values:

   ```bash
   cp deploy/aws/parameters.example.json deploy/aws/parameters.json
   ```

   `deploy/aws/parameters.json` is gitignored. Set `AlertEmail` to an address you
   read and `PublicHostname` to the DuckDNS name.

4. **Validate the template against AWS.** The first command that needs
   credentials, and it still creates nothing:

   ```bash
   aws cloudformation validate-template \
     --region us-east-1 \
     --template-body file://deploy/aws/template.yaml
   ```

5. **Create a change set and read it before executing.** Do not use
   `create-stack` directly — a change set is the last point at which the
   resource list can be checked against the design:

   ```bash
   aws cloudformation create-change-set \
     --region us-east-1 \
     --stack-name guess-who-demo \
     --change-set-name initial \
     --change-set-type CREATE \
     --template-body file://deploy/aws/template.yaml \
     --parameters file://deploy/aws/parameters.json \
     --capabilities CAPABILITY_IAM
   ```

   ```bash
   aws cloudformation describe-change-set \
     --region us-east-1 \
     --stack-name guess-who-demo \
     --change-set-name initial \
     --query 'Changes[].ResourceChange.{Action:Action,Type:ResourceType,Id:LogicalResourceId}' \
     --output table
   ```

   Expected: exactly ten resources, all `Add` — security group, bucket, log
   group, two roles, instance profile, OIDC provider, instance, Elastic IP,
   budget. Anything else, stop and find out why.

   ```bash
   aws cloudformation execute-change-set \
     --region us-east-1 \
     --stack-name guess-who-demo \
     --change-set-name initial
   ```

6. **Confirm the budget email.** AWS sends a subscription confirmation; until
   you click it the alerts go nowhere. This is the easiest step to skip and the
   one whose absence you find out about last.

7. **Record the dates** in the deployment log at the bottom of this file: the
   day the Free Plan started, its expiry, and the day-165 teardown date.

8. **Read the outputs:**

   ```bash
   aws cloudformation describe-stacks \
     --region us-east-1 \
     --stack-name guess-who-demo \
     --query 'Stacks[0].Outputs' \
     --output table
   ```

9. **Point DuckDNS at `ElasticIp`.** Caddy cannot obtain a certificate until the
   hostname resolves to the instance.

10. **Verify what the stack actually built**, rather than trusting that it did:

    ```bash
    aws ec2 describe-instances \
      --region us-east-1 \
      --filters Name=tag:Project,Values=guess-who \
      --query 'Reservations[].Instances[].{Type:InstanceType,Credits:CpuOptions,State:State.Name}' \
      --output table
    ```

    ```bash
    aws ec2 describe-security-groups \
      --region us-east-1 \
      --filters Name=tag:Project,Values=guess-who \
      --query 'SecurityGroups[].IpPermissions[].{From:FromPort,To:ToPort,Proto:IpProtocol}' \
      --output table
    ```

    Expected: `t3.micro`, and exactly two rules — 80 and 443.

## Bootstrapping the host

Once DuckDNS points at the Elastic IP, open a Session Manager shell on the
instance (`InstanceId` from the stack outputs).

**The instance does not come with `bootstrap.sh`, and the script cannot run on
its own.** Nothing in the stack puts it on the host, and it installs seven files
from its own directory: `set-db-password.sh`, `backup.sh`, `guesswho.service`,
`guesswho-backup.service`, `guesswho-backup.timer`, `Caddyfile` and
`cloudwatch-agent.json`. All eight have to arrive together, from one commit. The
first host got them by packaging those files from `deploy/aws/` with a checksum,
uploading both to the artifact bucket, downloading them through Session Manager
into a directory of their own, and running bootstrap from there.

That step is manual and not yet scripted. It is the one thing standing between
this stack and a clean rebuild of the instance, and it is open in
[Phase 10 of the roadmap](../../docs/ROADMAP.md).

From the directory the files were unpacked into, run bootstrap with the stack's
values. It is written to be safe to run again if anything fails part-way, but no
second run on the live host is in the deployment log yet — do one, and record
it. The live host keeps the non-secret bucket and region values in
`/etc/guesswho/backup.env`; resolve them without assuming the Session Manager
user can read that file directly:

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
```

It ends with its own checks — PostgreSQL and Caddy running, the backup timer
enabled, `server.env` not world-readable, and neither 5432 nor 8080 listening on
anything but loopback. It exits nonzero if any of them fail, so a host that
looks bootstrapped and is not will say so.

The application service is enabled but **not started**: there is no JAR until
the first deployment, and starting it here would only produce a restart loop.

### Checking the forwarding boundary

Spring trusts `X-Forwarded-For` because only Caddy can reach it. That trust is
only sound if Caddy overwrites the header rather than appending to it, which is
not Caddy's default. Once the server is deployed, prove it:

```bash
curl -s -o /dev/null -w '%{http_code}\n' \
  -H 'X-Forwarded-For: 203.0.113.9' \
  https://your-host.duckdns.org/api/status
```

```bash
curl -s -o /dev/null -w '%{http_code}\n' \
  -H 'Forwarded: for=203.0.113.9' \
  https://your-host.duckdns.org/api/status
```

Then exhaust the sign-in allowance from one machine while sending a *different*
forged `X-Forwarded-For` on every request:

```bash
for i in $(seq 1 30); do
  curl -s -o /dev/null -w '%{http_code} ' \
    -H "X-Forwarded-For: 203.0.113.$i" \
    -H 'Content-Type: application/json' \
    -d '{"username":"nobody","password":"wrong"}' \
    https://your-host.duckdns.org/api/sessions
done; echo
```

**Expected: the responses turn into `429`.** If they stay `401` forever, Caddy is
appending rather than replacing, every per-address limit is bypassable by anyone
who sends a header, and the sign-in endpoint — which costs a BCrypt hash per
attempt — is effectively unprotected. Record the result in the deployment log.

One part of the boundary cannot be seen from outside at all. Nothing reads
`X-Real-IP`, so whether Caddy strips it changes no response. Read the rendered
configuration on the host instead:

```bash
sudo grep -n 'X-Real-IP' /etc/caddy/Caddyfile
```

Expected: the `request_header -X-Real-IP` line. A host bootstrapped from a
commit before `a1774c2` will not have it.

## Deploying

The workflow is `.github/workflows/deploy-aws.yml`, run manually from the
Actions tab. It needs four **repository variables** (Settings → Secrets and
variables → Actions → Variables), all taken from the stack outputs. None is a
secret — the point of OIDC is that there is no secret to store:

| Variable | Value |
| --- | --- |
| `AWS_DEPLOY_ROLE` | the `GitHubDeployRoleArn` output |
| `AWS_ARTIFACT_BUCKET` | the `ArtifactBucketName` output |
| `AWS_INSTANCE_ID` | the `InstanceId` output |
| `AWS_PUBLIC_URL` | `https://your-host.duckdns.org` |

What a run does: builds and runs the full test suite, requests temporary AWS
credentials through OIDC, uploads `server.jar` and `deploy.sh` under
`releases/<sha>/` with a checksum, asks SSM to run the script on the instance,
waits for it, and finishes with the public smoke test.

It refuses to run unless you type `deploy` in the confirmation box, because it
restarts the public server.

### Release installers

The installers workflow reads a fifth variable, `GUESSWHO_SERVER_URL`, which
should be `https://greninja-guesswho.duckdns.org`. A tagged release refuses to
build without it, rather than shipping installers that quietly talk to
`localhost`. It was not yet set on 2026-09-14.

### Closing the live-host release gates

Before the two-client session, close the operational gates that prove the host
can be observed, rebuilt and recovered. Record each result separately in the
[deployment log](#deployment-log), with its UTC time and release SHA:

1. Confirm the installed Caddyfile removes `X-Real-IP` using the check above.
2. Confirm `amazon-cloudwatch-agent` is active and the server log group has a
   recent event:

   ```bash
   aws logs describe-log-streams \
     --region us-east-1 \
     --log-group-name /guess-who/demo/server \
     --order-by LastEventTime \
     --descending \
     --max-items 1
   ```

3. In AWS Billing and Cost Management → Budgets → `guess-who-demo`, confirm the
   subscriber is confirmed for both the 80% and 100% actual-cost notifications.
   Record the confirmation state, never the address.
4. Run bootstrap a second time using the checked eight-file source directory
   above. All four services must remain active and both the loopback and public
   status endpoints must answer.
5. Rehearse both rollback paths below: rejection before installation and
   automatic rollback after a failed candidate health check.
6. From an authenticated maintainer checkout, run
   `bash deploy/aws/teardown.sh --dry-run`. It must verify and retain the newest
   backup, name the resources it would remove, and report that nothing was
   deleted.

Any failed item blocks the release. The log should contain the command outcome
or observed AWS state that proves each claim, not merely the intended
configuration.

### Rehearsing a rollback

Do this once, before trusting it. Two failures, and the second needs building
deliberately. No rollback rehearsal on the live host is in the deployment log
yet.

**A corrupt artifact.** Upload something that is not a JAR under a test prefix
and run `deploy.sh` against it by hand through Session Manager:

```bash
ARTIFACT_BUCKET="$(sudo sed -n 's/^ARTIFACT_BUCKET=//p' /etc/guesswho/backup.env)"
AWS_REGION="$(sudo sed -n 's/^AWS_REGION=//p' /etc/guesswho/backup.env)"
test -n "$ARTIFACT_BUCKET" && test -n "$AWS_REGION"
echo "not a jar" > /tmp/notajar
aws s3 cp /tmp/notajar \
  "s3://$ARTIFACT_BUCKET/releases/rollback-test-1/server.jar" \
  --region "$AWS_REGION" --sse AES256
sudo bash /opt/guesswho/deploy.sh "$ARTIFACT_BUCKET" rollback-test-1
```

Expected: `jar tf` rejects it, the message says nothing was changed, and
`/api/status` is still answering from the release that was already there.

**A structurally valid JAR that never becomes healthy.** The tempting version of
this — adding a bad value to `/etc/guesswho/server.env` — **does not test
rollback at all.** That file is shared by whatever the symlink points at, so the
restored previous JAR would fail for the same reason the candidate did. A
property embedded in the JAR does not solve that: the environment value has
higher Spring precedence. Failure has to belong to the candidate alone.

Build an unhealthy candidate from the real JAR by replacing only its main class
with an invalid class file. The archive still passes `jar tf`, so `deploy.sh`
installs it and reaches the health-check rollback path; only that candidate
fails to start:

```bash
mkdir -p /tmp/bad && cd /tmp/bad
cp /opt/guesswho/current/server.jar bad.jar
mkdir -p BOOT-INF/classes/com/guesswho
: > BOOT-INF/classes/com/guesswho/GuessWhoServerApplication.class
jar uf bad.jar BOOT-INF/classes/com/guesswho/GuessWhoServerApplication.class
jar tf bad.jar >/dev/null
aws s3 cp bad.jar \
  "s3://$ARTIFACT_BUCKET/releases/rollback-test-2/server.jar" \
  --region "$AWS_REGION" --sse AES256
sudo bash /opt/guesswho/deploy.sh "$ARTIFACT_BUCKET" rollback-test-2
```

Expected: the candidate cannot start, the health retry times out, the symlink
goes back to the previous release, the service restarts, and the script reports
rolling back. Then check
which JAR is actually running — recording only "health recovered" would also be
satisfied by a rollback that never happened:

```bash
readlink -f /opt/guesswho/current/server.jar
curl -fsS http://127.0.0.1:8080/api/status
```

Expected: the previous SHA, not `rollback-test-2`. Clean up:

```bash
aws s3 rm "s3://$ARTIFACT_BUCKET/releases/rollback-test-1/" \
  --region "$AWS_REGION" --recursive
aws s3 rm "s3://$ARTIFACT_BUCKET/releases/rollback-test-2/" \
  --region "$AWS_REGION" --recursive
rm -rf /tmp/bad /tmp/notajar
```

## Two-client acceptance session

The one check no script can make: two people playing a whole game on the
deployed server while somebody watches. `smoke-test.sh` says the server is up
and safe to talk to, and `rehearsals/` proves the logic — including a server
restarted mid-game. Neither shows that the reconnecting banner appears, that four
idle minutes on a real clock forfeit nothing, or that the reveal renders. Phase 10
is not complete until this passes.

**Two machines, two networks, two accounts.** Two networks, because callers
behind one public address share one sign-in allowance, and the separation the
forwarding boundary exists to give cannot be seen from a single network.

Run each client from source against the live server, each with its own **named**
home directory. Named rather than temporary, because closing a client and opening
it again only offers to rejoin if its token and its remembered room are still
there:

```bash
mvn install -DskipTests
```

```bash
mkdir -p /tmp/guesswho-client-a
```

```bash
java -Duser.home=/tmp/guesswho-client-a \
  -Dguesswho.server.url=https://greninja-guesswho.duckdns.org \
  -cp "desktop-client/target/desktop-client-1.0.0.jar:desktop-client/target/lib/*" \
  com.guesswho.ui.GUI
```

Client B is the same on the second machine, with its own directory. Reopening a
client means running its own command again, unchanged.

| # | Do this | Pass looks like |
| --- | --- | --- |
| 1 | Register a fresh account on each client and sign in | Two accounts; neither sign-in displaces the other |
| 2 | A opens a room; B joins, typing the code in lowercase with a space in it | The code is accepted |
| 3 | Choose characters; ask and answer a question each way | Transcripts agree; neither side is shown the other's character |
| 4 | Press Guess with no cards flipped, then flip several and press it again | The first explains what it needs; ruled-out cards stay faded and clickable |
| 5 | **Leave both clients untouched for four minutes** | **Nothing forfeits** |
| 6 | Read the room row, then restart the service mid-game (below) | Both clients show reconnecting and recover with nobody touching anything |
| 7 | Read the room row again | Same `status`, `version` and `game_state` length; whoever owed the move can still make it |
| 8 | Quit B entirely and watch A; then reopen B | Within about 15s A says B seems to have gone; B offers to rejoin the same game |
| 9 | Play on through a wrong guess to a correct one | Both characters revealed; the promise and answer checks both pass |
| 10 | Open the leaderboard on both | The result is under **vs Player (online)**, against both accounts |
| 11 | Sign in and open a room from both networks | Neither network's use earns the other a `429` |

Row 5 matters most: it is the failure that takes a game away from somebody who is
still playing it. A forfeit there stops the session.

The room row, in a Session Manager shell. The code is read into a variable rather
than typed into the SQL, and tidied the way the server tidies it — spaces and
hyphens dropped, case ignored — so it can be typed however the client showed it.
The SQL goes to `psql` on standard input because `psql -c` does not substitute
variables, which is the same trap `set-db-password.sh` exists to avoid:

```bash
printf 'Room code: '; read -r ROOM_CODE
```

```bash
sudo -u postgres psql -d guesswho -v room_code="$ROOM_CODE" <<'SQL'
SELECT status, version, length(game_state)
FROM game_rooms
WHERE code = upper(regexp_replace(:'room_code', '[[:space:]-]', '', 'g'));
SQL
```

The restart, between the two readings:

```bash
sudo systemctl restart guesswho.service
```

```bash
sudo systemctl is-active guesswho.service
```

Record it in the deployment log: the date, the release SHA, both client operating
systems, the two kinds of network, the row before and after, and the result of
every row. No passwords, tokens, IP addresses or nonces.

## Verifying a backup can be restored

A backup nobody has restored is a hope. Do this after the acceptance game so the
archive contains real account, result and answer rows. First record the live
counts and trigger a new backup in Session Manager:

```bash
sudo -u postgres psql -d guesswho -c \
  'SELECT (SELECT COUNT(*) FROM accounts) AS accounts,
          (SELECT COUNT(*) FROM game_results) AS results,
          (SELECT COUNT(*) FROM game_result_question_answers) AS answers;'
sudo systemctl start guesswho-backup.service
sudo systemctl status guesswho-backup.service --no-pager
```

All three counts must be non-zero. On an authenticated maintainer machine,
resolve the bucket and newest object from AWS rather than guessing either name,
then keep a checked copy outside both the repository and AWS:

```bash
AWS_REGION=us-east-1
STACK_NAME=guess-who-demo
ARTIFACT_BUCKET="$(aws cloudformation describe-stacks \
  --region "$AWS_REGION" --stack-name "$STACK_NAME" \
  --query "Stacks[0].Outputs[?OutputKey=='ArtifactBucketName'].OutputValue | [0]" \
  --output text)"
LATEST_KEY="$(aws s3api list-objects-v2 \
  --region "$AWS_REGION" --bucket "$ARTIFACT_BUCKET" --prefix backups/ \
  --query 'sort_by(Contents,&LastModified)[-1].Key' --output text)"
test -n "$ARTIFACT_BUCKET" && test "$ARTIFACT_BUCKET" != None
test -n "$LATEST_KEY" && test "$LATEST_KEY" != None
mkdir -p /Users/greninja/Documents/Guess-Who-backups
ARCHIVE_PATH="/Users/greninja/Documents/Guess-Who-backups/$(basename "$LATEST_KEY")"
aws s3 cp "s3://$ARTIFACT_BUCKET/$LATEST_KEY" "$ARCHIVE_PATH" \
  --region "$AWS_REGION"
gzip -t "$ARCHIVE_PATH"
shasum -a 256 "$ARCHIVE_PATH"
```

Restore that exact object into one explicitly named temporary database on the
host. Session Manager users cannot necessarily source `backup.env`, so read only
the two non-secret values needed here:

```bash
ARTIFACT_BUCKET="$(sudo sed -n 's/^ARTIFACT_BUCKET=//p' /etc/guesswho/backup.env)"
AWS_REGION="$(sudo sed -n 's/^AWS_REGION=//p' /etc/guesswho/backup.env)"
LATEST_KEY="$(aws s3api list-objects-v2 \
  --region "$AWS_REGION" --bucket "$ARTIFACT_BUCKET" --prefix backups/ \
  --query 'sort_by(Contents,&LastModified)[-1].Key' --output text)"
test -n "$LATEST_KEY" && test "$LATEST_KEY" != None
aws s3 cp "s3://$ARTIFACT_BUCKET/$LATEST_KEY" \
  /tmp/guesswho-acceptance.dump.gz --region "$AWS_REGION"
gzip -t /tmp/guesswho-acceptance.dump.gz
gunzip -c /tmp/guesswho-acceptance.dump.gz > /tmp/guesswho-acceptance.dump
sudo -u postgres dropdb --if-exists guesswho_restore_acceptance
sudo -u postgres createdb guesswho_restore_acceptance
sudo -u postgres pg_restore --no-owner --no-acl \
  -d guesswho_restore_acceptance /tmp/guesswho-acceptance.dump
sudo -u postgres psql -d guesswho_restore_acceptance \
  -c 'SELECT (SELECT COUNT(*) FROM accounts) AS accounts,
             (SELECT COUNT(*) FROM game_results) AS results,
             (SELECT COUNT(*) FROM game_result_question_answers) AS answers;'
```

The restored counts must exactly match the live counts. Then remove only the
named temporary database and files; keep the off-AWS archive:

```bash
sudo -u postgres dropdb guesswho_restore_acceptance
rm -f /tmp/guesswho-acceptance.dump.gz /tmp/guesswho-acceptance.dump
```

The first restore, on 2026-09-14, matched at zero accounts and zero results. It
proved the archive format, not its contents; the non-empty restore above is the
release gate. Record the object key, UTC time, live and restored counts,
checksum, and retained path in the deployment log.

## What this is expected to consume

The Free Plan gives credits for six months rather than a free-tier allowance, so
the question is not "is this free" but "does six months of this fit inside the
credits". Below is the estimate that decision was made on.

**These are figures for `us-east-1`, and they are estimates, not quotes.**
Confirm them against the pricing pages before creating the stack — AWS changes
prices, and one item here changed recently in a way that catches people out.

| Item | Basis | Per month |
| --- | --- | --- |
| `t3.micro` on-demand | ~$0.0104/hr × 730 hr | **~$7.60** |
| Elastic IP | ~$0.005/hr × 730 hr | **~$3.65** |
| 12 GB gp3 root volume | ~$0.08/GB-month | ~$0.96 |
| S3 storage | releases + 14 days of dumps, ~1–2 GB | ~$0.05 |
| CloudWatch Logs | 7-day retention, low volume | ~$0.25 |
| Data transfer out | a handful of players, well under the free 100 GB | ~$0 |
| SSM, Parameter Store (standard), Budgets | no charge at this usage | $0 |
| | **Total** | **~$12.50** |

**Six months: roughly $75.**

> **The Elastic IP is not free, and it used to be.** Since early 2024 AWS
> charges for every public IPv4 address, attached or not. It is about 29% of
> the monthly cost here and the single most surprising line — plenty of guidance
> written before that change still says an attached Elastic IP costs nothing.
>
> It also means a forgotten Elastic IP keeps billing after the instance is gone,
> which is why `teardown.sh` checks for one by tag and fails if it finds one.

### What the estimate assumes

- The instance runs continuously for six months. Stopping it when nobody is
  playing would cut the largest line roughly in proportion — but the EBS volume
  and Elastic IP bill regardless of whether the instance is running.
- CPU credits stay `standard`. On `unlimited`, sustained load buys surplus
  credits **billed in real money** and this projection stops meaning anything.
- Nothing is added. A NAT gateway (~$32/month), a load balancer (~$16/month) or
  RDS would each cost more than the whole stack above.

### How much margin there is

Against ~$75 of consumption, the credits should cover the demo with room to
spare — but not so much room that an accidental addition goes unnoticed. That is
why the budget is set at $15/month and configured to exclude credits: at these
figures, an alert at 80% means something has been added that should not have
been.

Check actual consumption weekly rather than trusting this table.

Sources to confirm against: [EC2 on-demand
pricing](https://aws.amazon.com/ec2/pricing/on-demand/), [EBS
pricing](https://aws.amazon.com/ebs/pricing/), [VPC pricing (public IPv4
addresses)](https://aws.amazon.com/vpc/pricing/), [S3
pricing](https://aws.amazon.com/s3/pricing/), [CloudWatch
pricing](https://aws.amazon.com/cloudwatch/pricing/).

## Weekly, while it is up

- Check Free Plan credit consumption in Billing and Cost Management.
- Confirm a database backup landed in `s3://<bucket>/backups/` in the last day.
- Confirm the budget has not alerted.
- Investigate any charge you did not expect, rather than assuming the credits make
  it harmless. The credits end; whatever caused the charge does not.
- Every few weeks, check when the certificate expires. Caddy renews it on its own,
  and the first renewal is due around 2026-11-13, but the smoke test only notices
  a renewal that failed once the old certificate has actually run out.

## Tearing down, by day 165

The demo has a deadline: **2027-02-26**, day 165 of a Free Plan that started on
2026-09-14 and ends on 2027-03-14. `teardown.sh` gets the data out, deletes the
stack, and then checks that the deletion actually happened.

**Rehearse it first**, well before the day — no dry run is in the deployment log
yet. The dry run does everything except delete: resolves the stack, downloads
the newest backup, verifies it, and prints what it *would* remove.

```bash
bash deploy/aws/teardown.sh --dry-run
```

Expected: a verified export path and SHA-256, and a list naming the bucket and
stack. Nothing is deleted, and the export it leaves behind is a real backup you
can keep.

**On the day**, run the dry run again, confirm the export restores (see the
section above), then:

```bash
bash deploy/aws/teardown.sh --confirm-delete ~/guess-who-final-export
```

It refuses without that exact argument. It will not proceed if the stack has
never produced a backup, and it will not proceed if it cannot resolve the bucket
name from the stack outputs — a deletion loop with an empty bucket name is the
shape of accident this script exists to avoid.

Afterwards it verifies by tag that no instance, Elastic IP, bucket or log group
remains, and exits nonzero listing anything that does. Check Billing and Cost
Explorer a day or two later: an Elastic IP left attached to nothing still bills
and is invisible unless you go looking.

The export is never deleted by this script. It is the only copy once the stack
is gone.

## Deployment log

| Date (UTC) | Release SHA | What happened | By |
| --- | --- | --- | --- |
| 2026-09-14 | | Free Plan started — expiry: **2027-03-14**, teardown by: **2027-02-26** (day 165) | greninjam26 |
| 2026-09-14 | | Stack `guess-who-demo` created in `us-east-1` with the expected ten resources, CPU credits `standard` and only ports 80 and 443 open. Budget USD 15 a month, alerting on gross consumption. DuckDNS name pointed at the Elastic IP; Caddy obtained a certificate | greninjam26 |
| 2026-09-14 | | First host bootstrapped from a hand-delivered copy of the runtime files, not yet scripted — see [Bootstrapping the host](#bootstrapping-the-host). Three fixes found on the way, merged as `83241c5`: the `jar` tool `deploy.sh` needs is in Corretto's devel package, not the headless one; the database password goes through `set-db-password.sh` on standard input, because `psql -c` does not substitute variables; loopback TCP authentication moved from `ident`, which cannot authenticate a different OS user, to `scram-sha-256` | greninjam26 |
| 2026-09-14 | `c3657c3` | The deploy role could not be assumed: the workflow declared a GitHub environment, which changes the OIDC subject away from the `main` branch the role trusts. Environment removed; the deploy contract now fails if one comes back | greninjam26 |
| 2026-09-14 | `c3657c3` | First deployment, [run 34883880272](https://github.com/greninjam26/Guess-Who-boardgame/actions/runs/34883880272): build, tests, upload, SSM install and public smoke test passed; `/api/status` online | greninjam26 |
| 2026-09-14 | `c3657c3` | Forwarding boundary on the live host: 30 sign-ins, each with a different forged `X-Forwarded-For`, went from `401` to `429` after ten | greninjam26 |
| 2026-09-14 | `c3657c3` | Backup `guesswho-20260914T191252Z.dump.gz`: passed `gzip -t`, read as a PostgreSQL 15 custom-format archive, restored into a temporary database. Counts matched at **0 accounts and 0 results** — this proves the archive, not its contents. Copy kept off AWS, SHA-256 `025db5712f94361b0404067e56944620f05695bb899dcc94f8c0063219e06da0` | greninjam26 |
| 2026-09-14 | `c3657c3` | Checked from outside: certificate valid until 2026-12-13, `http://` redirects to `https://`, a 404 names nothing inside, smoke test 7 of 7 with ports 22, 8080 and 5432 closed. Not checkable from outside: whether the host's Caddyfile strips `X-Real-IP` | Claude Code |
