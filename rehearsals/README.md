# Rehearsals

Things that are true only when something runs.

The contract tests under `deploy/aws/tests/` and `packaging/tests/` read files
and run nothing; they are cheap, they need no credentials, and CI can run them
on every push. These are the opposite: each one starts real processes — a
server, a database, a proxy — does something to them, and reads the result.
They take minutes rather than seconds and need software installed, so nothing
runs them automatically. Run them by hand, before a deployment, when the thing
they cover changes.

All four are disposable. They create their own databases on non-default ports,
write into `rehearsals/*/work/` (git-ignored), and clean up after themselves.
None of them touches the developer database at `./guess-who-data`, and none
touches AWS.

Build first — every one of them runs the real server jar:

```bash
mvn install -DskipTests
```

## two-client

```bash
bash rehearsals/two-client/run.sh
```

Two clients playing a real game over HTTP against a server this harness owns as
a child process, so it can stop it. Covers: accounts and sessions, room creation
and joining, a question and answer, **the server restarting mid-game** and both
clients recovering with the room's version and stored state unchanged, absence
and forfeit, the offline result queue, and the 426 sent to a client too old to
play.

The restart is the reason this exists. Nothing in the test suite can stop a
server, so nothing else can show that a deployment does not destroy a game in
progress — which is what putting rooms in the database was for.

## postgres

Needs PostgreSQL 15: `brew install postgresql@15`, or set `GUESSWHO_PGBIN`.

```bash
bash rehearsals/postgres/run.sh
```

The server running under the `aws` profile on the database it is deployed to,
rather than the H2 the suite uses. Covers: every migration from empty,
`game_state` being portable `text`, the Hikari pool holding to the profile's
limit under concurrent load, ECS-structured logging, and — the part worth having
— `/api/status` returning a 503 that names no exception, driver, credential,
host or database when PostgreSQL is stopped underneath it.

## backup

Needs PostgreSQL 15, as above.

```bash
bash rehearsals/backup/run.sh
```

A database populated through the application's own API, dumped with the options
`deploy/aws/backup.sh` uses, compressed, verified, and restored into a second
database, then compared row by row. It reads those options out of `backup.sh`
and fails if they have changed, so this cannot drift into rehearsing something
production no longer does.

What it proves that a row count cannot: a game **still in progress** at backup
time comes back as a game still in progress, with the same version and the same
bytes of state.

The upload to S3 is the one step it cannot cover.

## caddy

Needs Caddy: `brew install caddy`.

```bash
bash rehearsals/caddy/run.sh
```

The forwarding boundary, through a real proxy configured from
`deploy/aws/Caddyfile` — the two header directives are copied out of it, not
retyped, and the run fails if they are missing.

Spring trusts `X-Forwarded-For` because only Caddy should be able to write it.
This checks that: the RFC `Forwarded` header is stripped, `X-Forwarded-For` is
replaced rather than appended, and a rotating forged address per request still
hits the sign-in limit through the proxy — while the same requests sent straight
to the application are never limited at all. That contrast is the point; the
second half is what the first half is protecting against.

HTTP only. Certificates, the HTTPS redirect and HSTS belong to the real host.

## What none of them cover

Certificate issuance, DNS, the deployment and rollback path on the instance,
`bootstrap.sh` being idempotent, S3, cost — and anything a person has to look
at. The visual half of acceptance is a checklist in
`docs/two-client-acceptance-checklist.md`, and no harness can do it for you.
