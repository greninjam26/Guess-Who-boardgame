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

## Weekly, while it is up

- Check Free Plan credit consumption in Billing and Cost Management.
- Confirm a database backup landed in `s3://<bucket>/backups/` in the last day.
- Confirm the budget has not alerted.

## Deployment log

| Date (UTC) | Release SHA | What happened | By |
| --- | --- | --- | --- |
| | | Free Plan started — expiry: ______ , teardown by: ______ | |
