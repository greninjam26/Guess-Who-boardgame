# Deploying the Guess Who server to AWS

A six-month demo on the AWS Free Plan: one `t3.micro` in `us-east-1` running
PostgreSQL, the Spring Boot server and Caddy, reachable over HTTPS at a DuckDNS
hostname. Everything is in one CloudFormation stack, so the teardown is a stack
deletion rather than a hunt.

Design and reasoning: `docs/superpowers/specs/2026-09-02-aws-free-deployment-design.md`.

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
instance (`InstanceId` from the stack outputs) and run bootstrap with the stack's
values. It is safe to run again if anything fails part-way:

```bash
sudo PUBLIC_HOSTNAME=your-host.duckdns.org \
     ARTIFACT_BUCKET=the-bucket-from-the-outputs \
     AWS_REGION=us-east-1 \
     bash /opt/guesswho/bootstrap.sh
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

### Rehearsing a rollback

Do this once, before trusting it. Two failures, and the second needs building
deliberately.

**A corrupt artifact.** Upload something that is not a JAR under a test prefix
and run `deploy.sh` against it by hand through Session Manager:

```bash
echo "not a jar" > /tmp/notajar
aws s3 cp /tmp/notajar s3://the-bucket/releases/rollback-test-1/server.jar --sse AES256
sudo bash /opt/guesswho/deploy.sh the-bucket rollback-test-1
```

Expected: `jar tf` rejects it, the message says nothing was changed, and
`/api/status` is still answering from the release that was already there.

**A JAR that starts and fails its health check.** The tempting version of this —
adding a bad value to `/etc/guesswho/server.env` — **does not test rollback at
all.** That file is shared by whatever the symlink points at, so the restored
previous JAR would fail for the same reason the candidate did; a passing run
would mean "both are broken" and a failing one would tell you nothing about the
rollback path. Failure has to belong to the candidate alone.

Build an unhealthy candidate instead — the real JAR, repackaged with its own
datasource pointing at a closed port, so it starts and reports 503:

```bash
mkdir -p /tmp/bad && cd /tmp/bad
cp /opt/guesswho/current/server.jar bad.jar
printf 'spring.datasource.url=jdbc:postgresql://127.0.0.1:1/nothing\n' > application.properties
jar uf bad.jar application.properties
aws s3 cp bad.jar s3://the-bucket/releases/rollback-test-2/server.jar --sse AES256
sudo bash /opt/guesswho/deploy.sh the-bucket rollback-test-2
```

Expected: the health retry times out, the symlink goes back to the previous
release, the service restarts, and the script reports rolling back. Then check
which JAR is actually running — recording only "health recovered" would also be
satisfied by a rollback that never happened:

```bash
readlink -f /opt/guesswho/current/server.jar
curl -fsS http://127.0.0.1:8080/api/status
```

Expected: the previous SHA, not `rollback-test-2`. Clean up:

```bash
aws s3 rm s3://the-bucket/releases/rollback-test-1/ --recursive
aws s3 rm s3://the-bucket/releases/rollback-test-2/ --recursive
rm -rf /tmp/bad /tmp/notajar
```

## Verifying a backup can be restored

A backup nobody has restored is a hope. Before calling the deployment done, run
one and put it back:

```bash
sudo systemctl start guesswho-backup.service
aws s3 ls s3://the-bucket/backups/ --region us-east-1
```

```bash
aws s3 cp s3://the-bucket/backups/the-newest.dump.gz /tmp/ --region us-east-1
gzip -t /tmp/the-newest.dump.gz && gunzip -c /tmp/the-newest.dump.gz > /tmp/restore.dump
sudo -u postgres createdb guesswho_restore_test
sudo -u postgres pg_restore --no-owner --no-acl -d guesswho_restore_test /tmp/restore.dump
```

Compare what matters, then remove only the named temporary database:

```bash
sudo -u postgres psql -d guesswho_restore_test \
  -c 'SELECT (SELECT COUNT(*) FROM accounts) AS accounts,
             (SELECT COUNT(*) FROM game_results) AS results;'
sudo -u postgres dropdb guesswho_restore_test
rm -f /tmp/the-newest.dump.gz /tmp/restore.dump
```

## Weekly, while it is up

- Check Free Plan credit consumption in Billing and Cost Management.
- Confirm a database backup landed in `s3://<bucket>/backups/` in the last day.
- Confirm the budget has not alerted.

## Deployment log

| Date (UTC) | Release SHA | What happened | By |
| --- | --- | --- | --- |
| | | Free Plan started — expiry: ______ , teardown by: ______ | |
