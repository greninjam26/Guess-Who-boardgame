# Guess Who — Roadmap

Fourteen phases across three releases, from a working desktop board game to an
online multiplayer app with accounts, verified answers, and leaderboards that
mean something.

Sequencing reflects the repo at `f856ba8`, immediately after the server-backed
leaderboard (PR #23) merged.

## Releases

| Release | What it is | Phases |
| --- | --- | --- |
| **v1.0** | A polished, installable single-machine game — PvE and hotseat, verified answers, per-mode leaderboards against a local server | 00–07 |
| **v2.0** | The same game with accounts and room-code online multiplayer, deployed and reachable | 08–10 |
| **Post-v2** | History, replay, chat, spectating, and scaling if it's ever measured | 11–13 |

Shipping v1.0 before the backend work matters. It is a real milestone you can
install and hand to someone, and it arrives well before the two XL phases.

## Locked decisions

| Area | Decision | Why |
| --- | --- | --- |
| Stack | Java end to end | No web client. One language, one rules engine, no port. |
| Client | Swing + FlatLaf | The work ahead is architectural, not visual. Switching toolkits would stack a rewrite on a refactor. |
| Server | One Spring Boot app | A monolith, deliberately. The load never justifies anything else. |
| Transport | Polling, not sockets | Turns take a human ten seconds. A 1–2s poll is indistinguishable from realtime. |
| Delivery | `jpackage` installers | Native `.dmg` / `.exe` with a bundled JRE. Nobody installs Java. |

## The dependency spine

```text
v1.0    00 ✔  01 ✔  02 ✔  03 ✔  04 ✔  05 ✔  06 ✔  →  07 ▸
                                   06  needs 00 only — slot in anywhere

v2.0    08  Accounts  →  09  Online PvP  →  10  Ship

post    11  Stats and replay  →  12  Chat and spectating
        13  External session storage — only if measured
```

Four phases block everything downstream:

- **01** — no schema change is possible until migration tooling exists, and every
  game recorded without a mode is permanently unclassifiable.
- **02** — async server state cannot be wired into a 770-line method.
- **04** — committing the character changes the core `Game` API, so it must land
  before networking freezes it.
- **08** — both the leaderboard rework and online play need identity to hang off.

Phases **00** and **01** are independent of each other; either order works.

Sizes (S / M / L / XL) compare phases to each other. They are not calendar
estimates.

## What already helps

Five things in the current repo do real work for what's coming, which is why
this is a build-out rather than a rewrite.

- `Game` is already a state machine with `requireTurn()` and
  `requireInProgress()` guards — server-side turn validation is largely written.
- The `game_result_question_answers` table already stores complete question
  histories, so history and replay are mostly a UI.
- `GameResultSubmissionService` establishes the server-first-with-fallback
  pattern the online client will reuse.
- `HttpGameResultClient` and `HttpLeaderboardClient` are where the polling client
  starts — the HTTP plumbing and async handling already exist.
- `LeaderboardPanel` and `LeaderboardPanelTest` prove the Swing layer *can* be
  separated and unit-tested. That's the pattern Phase 02 applies to everything
  else.

---

# v1.0 — Playable desktop

## Phase 00 — Repair the engine · S — done

**Blocks:** 06 **Needs:** nothing
**Branch:** `fix/repair-computer-engine`

Small, self-contained, and everything downstream builds on the rules engine.
Fixing it after the client and server depend on it means re-testing all of them.

- [x] Fix the `qIndex` elimination bug in `ComputerPlayer.chooseQuestion()` —
      derive the index from the chosen question in `askQuestion()` and delete the
      field. Add a regression test asserting the chosen question and the
      eliminated column always match.
- [x] Fix the same root cause in the baseline: `answerCount[0]` is hardcoded
      against `unAskedQuestions.get(0)`, which drift apart once question 0 has
      been asked.
- [x] `ComputerPlayer`'s constructor calls `super(..., new Random())`, discarding
      the injected source. The AI's own character is nondeterministic even in
      tests — thread the real one through.
- [x] Stop aliasing `Board.getCharacters()` and `getPeopleCount()`; elimination
      currently mutates the board's own state.
- [x] Delete the dead `persistence/Leaderboard` class. It is unreferenced, reads a
      `Leaderboard.csv` that does not exist, and sorts ascending so the worst
      score lands first. It is *not* related to the working server-backed
      leaderboard added in PR #23 — that one stays.

## Phase 01 — Migrations, modes, and API limits · L — done

**Blocks:** every later schema change **Needs:** nothing

Everything here is urgent for one reason: data is accruing in a shape you cannot
change, classify, or bound later.

**Migration tooling first.** `application.properties` sets
`spring.sql.init.mode=always` and every statement in `schema.sql` is
`CREATE TABLE IF NOT EXISTS`. That is fine for a fresh database and silently
useless for adding a column — against an existing `guess-who-data.mv.db` the
statement is a no-op, the column never appears, and inserts referencing it fail
at runtime.

Suggested as three branches:

**`chore/adopt-flyway`**

- [x] Adopt Flyway (or Liquibase). Convert `schema.sql` into `V1__baseline.sql`
      and drop `spring.sql.init.mode`. Do this while there are three tables and
      almost no data.

**`feat/record-game-mode`**

- [x] Add the mode columns to `game_results` as `V2`:
      `mode VARCHAR(20) NOT NULL` (`PVE` / `PVP_LOCAL` / `PVP_ONLINE`),
      `difficulty VARCHAR(20)` (`EASY` / `HARD`, null for PvP), and
      `question_mode VARCHAR(20)` (`PRESET` / `FREE_FORM`) — the UI already makes
      that distinction and it is otherwise lost the same way.
- [x] Thread mode through the stack: `GameResult`, `Game.getGameResult()`,
      `JdbcGameResultRepository.save()`, `HttpGameResultClient.toJson()`,
      `GameResultController.validate()`, plus tests.
- [x] Backfill existing rows. PvE is inferable from a participant named `AI`;
      difficulty is not recoverable, so leave it null.
- [x] Split the leaderboard by mode: `GET /api/leaderboard?mode=PVE`, with the
      parameter optional so the endpoint keeps working unchanged.
- [x] Two boards in the UI, not four — **vs Computer** and **vs Player** as tabs
      in `LeaderboardDialog`, with difficulty as a *column* inside the PvE board.
      Four boards means four nearly-empty tables at your player count.

**`feat/bound-history-apis`**

- [x] Add limits and pagination to `GET /api/game-results`. It currently returns
      every game joined with every participant and every question answer, with no
      bound — the worst of the two endpoints and already shipped.
- [x] Add a result limit to `GET /api/leaderboard`.
- [x] Decide and implement offline synchronization. Games written to `test.csv`
      when the server is down are never uploaded, so they silently never appear
      in history or standings. Pick one: auto-upload on reconnect, manual import,
      or permanently local and excluded — and say which in the README.

> **Why splitting the leaderboard beats excluding modes.** Beating easy AI and
> beating a human are not comparable achievements, and one combined number
> averages them into nonsense. Per-mode boards make each one internally fair
> without hiding any data — and an `AI` row stops being strange, because on a
> vs-Computer board *"AI has won 40 of 60"* is a genuinely interesting stat about
> how well the AI plays.

> **Hotseat stays farmable.** You control both sides of a local PvP game, so that
> board is self-refereed no matter how it's sliced. Either leave it unranked or
> label it casual — just don't pretend it's competitive.

## Phase 02 — Split the client · L

**Blocks:** 03, 04, 05, 08 **Needs:** 00

`GUI.java` was ~1,160 lines with one 770-line method holding roughly 35
anonymous listeners and mutable fields like `curPlayer`, `modeChoice`, and
`AIQuestion`. Async state updates could not be threaded into that safely.

Branches 1 and 2 landed together and took it to 831 lines, a 468-line
`gameGUI()`, 70 fields, and 17 listeners.

**Five branches, state model first.** The order matters: you cannot extract the
setup screen cleanly while `username1`, `birthday1`, and `modeChoice` live as
`GUI` fields — you would either pass the god-object into each extracted class or
invent throwaway state holders, then undo that at the end. Introduce a thin state
model first and extract onto it.

1. ~~**`refactor/ui-state-model`**~~ — done. `GameSetup` replaced eight loose
   fields and the stringly-typed mode flags; `GameController` translates one
   `OpeningTurn` into whichever start call the mode needs. `curPlayer` is
   derived from `Game` rather than cached.
2. ~~**`refactor/extract-setup-screen`**~~ — done. `SetupScreens` owns welcome,
   mode, names, birthdays, and who goes first on a `CardLayout`, so the frame no
   longer swaps panels to move between setup steps. Merged with branch 1, since
   the two read as one change.
3. **`refactor/extract-board-view`** — the character grids and flip-down
   behavior.
4. **`refactor/extract-question-controls`** — asking, answering, and guessing.
5. **`refactor/extract-ending-screen`** — result, reveal, and answer review.

- [x] Replace the stringly-typed mode flags with an explicit enum-backed state
      model.
- [ ] Every branch leaves the app working and the suite green.
- [ ] Give the controller a view-update path. It currently starts games and holds
      the setup, but the screens still read state directly rather than being
      pushed to — that push is what online play needs.
- [ ] Establish the EDT discipline: every state change arrives through one
      `SwingUtilities.invokeLater` boundary rather than scattered through
      listeners. Deferred from branch 1, which had no async source yet.
- [ ] Bring each extracted piece under test as it lands. `LeaderboardPanel` is the
      model to copy — its test covers loading, success, empty, error, and retry,
      and the README lists untested Swing as a known limitation.

> **Do this before it gets harder.** Every feature added ahead of this phase gets
> built into the structure that has to be dismantled, then rebuilt. It's the
> least fun phase and the one most worth front-loading.

## Phase 03 — Split the build · S

**Blocks:** 07 **Needs:** 02
**Branch:** `chore/multi-module-build`

Everything currently lives in one Maven module, so `jpackage` would bundle Spring
Boot, H2, HikariCP, and Jackson into the desktop installer — shipping a web
server and a database engine to every player.

Done here rather than at packaging time because Phase 02 has just finished
drawing these boundaries; doing both in one pass beats discovering it later.

- [ ] Parent pom with three modules: `game-core`, `desktop-client`, `server`.
- [ ] `game-core` — the `game` package plus the CSV, image, and audio resources,
      and the shared types both sides need (`GameResult`, `LeaderboardEntry`).
- [ ] `desktop-client` — `ui`, `client`, and the CSV-writing half of
      `persistence` (`CsvGameResultRepository`, `StoreResult`). Depends on
      `game-core` only.
- [ ] `server` — `web`, `leaderboard`, and the JDBC half of `persistence`.
      Depends on `game-core` only.
- [ ] Split the `persistence` package deliberately: the `GameResultRepository`
      interface is used by both sides and belongs in core; the JDBC and CSV
      implementations do not.
- [ ] Verify the desktop artifact resolves no Spring dependencies.

## Phase 04 — Commit the character · M — done

**Blocks:** 09 **Needs:** 02

Storing the chosen character and verifying honest answers turned out to be two
mechanisms, not one. Both are in place.

- [x] Move character selection to game start. `Player` stops auto-assigning;
      `Game.selectCharacter()` moves from `FINISHED` to setup.
- [x] Add the store-my-character setting, as a choice about *when* rather than
      *whether*: name your character before playing, or keep it to yourself and
      say at the end. The answer review runs either way.
- [x] Add `CharacterCommitment` — `SHA-256(character, nonce)` in `game-core`,
      recorded at the moment of choosing, carried on `GameResult.Participant`,
      stored in `V4` and sent over the wire.
- [x] Rework the answer review to run against the character committed to.

> **The commitment is not what closed the local cheat.** Changing a character
> after seeing how the questions are going is prevented by
> `Game.selectCharacter()` refusing a second call — the game holds the character,
> so a choice is final because nothing will change it. That is worth knowing
> before Phase 09, because it means the crypto is not load-bearing today and
> nothing local depends on it.
>
> What the commitment buys is verification *without disclosure*. In an online
> game the opponent's own client answers questions about their character, so the
> server can record a whole game without ever learning either character, and
> still check both at the end. It cannot leak what it never held.
>
> It does not defend against a modified client that commits to one character and
> answers as another. Say so in the README at Phase 10.

> **A missing commitment is a signal, not an omission.** A promise is only
> recorded while the game is in progress; naming a character afterwards is a
> claim, not a commitment, and storing one would make the two indistinguishable.
> So a human participant with a null commitment is one who kept their character
> to themselves and named it at the end, and their review shows only that their
> answers were consistent — not that they were fixed in advance. Phase 09 needs
> no extra field to tell those apart.

## Phase 05 — Make it feel finished · L — done

**Blocks:** 07 **Needs:** 02

- [x] **Replace absolute positioning with layout managers.** Twenty-one
      `setBounds` calls against an assumed 1350x1200 window are gone. The board
      is a `GridLayout`, the turn panels are `BorderLayout`, and the frame gives
      the board `CENTER` and the controls `SOUTH` — which also removes the
      double-`CENTER` trap that broke once in Phase 02.
- [x] FlatLaf, installed before any component exists.
- [x] A real ending screen: both characters side by side with the player's name
      under each, the answer review below, and **Play again**.
- [x] Music controls: volume, mute, and pause, persisted through
      `java.util.prefs`, all working whether or not there is anything to play.
- [x] A settings window holding the music controls, **Quit**, **Restart**, and
      **Leaderboard**, which used to sit across the top of every screen.
- [x] The rules moved from a label inside the welcome screen into their own
      scrollable window, and were rewritten — the old text described three
      difficulties, promised score validation, and never mentioned choosing a
      character.
- [x] **Save a game in progress and offer it back on the next launch.** A
      `GameSnapshot` describes the rules' half; the flipped cards and the
      transcripts are kept beside it. Written on every turn and every flipped
      card, cleared when the game ends, and discarded rather than fatal if it
      cannot be read. Design in [ARCHITECTURE.md](ARCHITECTURE.md).
- [x] **Background music**, generated by `tools/BackgroundTrack.java` rather
      than licensed. A recording carries rights in both the composition and
      the performance, neither of which could be put inside an installer.

> **Restoring is replaying, not assigning.** A resumed game is started the
> ordinary way and then replayed onto: every question through the method that
> records one during play, every elimination through the one the computer uses
> to rule a character out. Anything derived — which questions remain, how many
> characters each still splits — is rebuilt by making it happen. Copying the
> derived state across instead would leave a computer that answers correctly
> while choosing its questions against a board it no longer has, which reads as
> the AI simply playing badly.

> **A rematch is not just a button.** Three things survive a finished game and
> have to be cleared, each of which would look like a bug rather than stale
> state: the cards a player flipped, the transcripts down either side, and the
> panels showing how it ended. `GameController` also held a `final Game`, and a
> finished game cannot be replayed, so it builds a new one and reuses the
> opening turn. Characters are deliberately not carried over.

> **Adding music needs more than a file.** `GameResources` looks for
> `/audio/Bloom of Youth.wav` by that exact name, and Java Sound reads PCM WAV,
> AIFF, and AU — an MP3 fails silently, because the exception is caught and
> turns into "no music" with nothing on screen. It also ships inside
> `game-core.jar` and therefore inside every installer, which makes it the same
> licensing question as the artwork.

> **Volume is in decibels.** `FloatControl.MASTER_GAIN` is logarithmic, not a
> 0–100 linear scale. A slider wired straight to it feels broken across the
> bottom half of its travel. Map it logarithmically and treat the minimum as
> mute.

## Phase 06 — Give the AI a brain · M — done

**Blocks:** nothing **Needs:** 00

- [x] Move the elimination state off `Character`. It lived on the objects the
      board owns, so two players sharing a board shared their eliminations and
      neither could be stored separately. It is now a `boolean[]` inside
      `ComputerPlayer`. **This is what Phase 05's save-and-resume was waiting
      for.**
- [x] Teach the AI to take a risk. `ComputerDifficulty` now decides when to
      guess rather than ask again — the harder level gambles on a coin flip
      between two rather than spend a turn, because a game is a race and waiting
      can lose one that guessing would have won.
- [x] Expand easy and hard into real tiers. `ComputerDifficulty` describes how
      the computer plays instead of carrying a string for `mode.equals("easy")`,
      and owns both decisions: which question to ask, and when to stop asking.
- [x] Teach the computer to answer a typed question. `TypedQuestion` resolves
      text to a board question, so free questions work against the computer and
      the mode matrix has no empty cell.
- [ ] Consider making the AI answer imperfectly on the easiest tier, so
      beginners can win. Not attempted: an opponent that lies is hard to tell
      from one that is broken, and the answer review would flag its own game.

> **Information gain and closest-to-half are the same rule.** The plan called
> for replacing one with the other. Maximising the entropy of a two-way split
> *is* getting closest to half, and every board question is two-way, so the
> change would not alter a single decision the AI makes.
>
> Checked rather than assumed: comparing both rules pairwise across every board
> size from two to twenty-four, the only apparent disagreements were exact ties,
> where a k/n−k split scores identically both ways.

> **A computer that misreads a question is worse than one that refuses.** An
> unplaceable question is declined: nothing is recorded, and the turn does not
> pass, so it costs a retype rather than a move. Recording it would corrupt the
> history and the answer review; guessing at it would make the player rule out
> the wrong characters and lose a game they should have won.
>
> The board question is stored, not the words typed, so a transcript stays
> comparable across games however the question was phrased.

## Phase 07 — Ship v1.0 · S

**Needs:** 03, 04, 05

- [x] **Original artwork and music.** The character images came from the
      printed board game, so the repository was distributing them under an MIT
      licence claiming rights it did not have — a problem that an installer
      turns from hosting into distribution. Twenty-four portraits are now
      generated from prompts derived from `GuessWhoDB.csv`, with names stamped
      on from the same data, and the music is generated too. Tooling in
      `tools/`.
- [x] **Write to the user's application directory.** The upload queue used a
      bare file name, resolved against the working directory. Installed and
      launched from the Finder that is the root of the disk, where the write
      fails: every result finished while the server was unreachable would have
      been lost, silently, on exactly the builds this phase produces.
- [x] **`jpackage` installers for macOS and Windows with a bundled JRE.** One
      script for both, since `jpackage` only produces the format of the machine
      it runs on and CI therefore needs a runner of each. The bundled runtime
      is trimmed to the modules `jdeps` finds at build time — 69 MB down to 43 —
      rather than a hand-written list that would fail when somebody ran the
      game rather than when the installer was built.
- [x] **README pass** covering installing the game rather than only building
      it, including the unsigned-app step each platform needs.
- [ ] Release notes and the `v1.0.0` tag.

> **The version is 1.0.0 because it had to be.** `jpackage` refuses an
> `--app-version` whose first number is zero on macOS: *"the first number in an
> app-version cannot be zero or negative"*. Shipping this as v0.5 would have
> meant the installer reporting a version the tag disagreed with, so the
> release milestones moved up one — online play is v2.0.

> **Neither installer is signed.** macOS shows "the developer cannot be
> verified" and Windows shows SmartScreen, both fixable by the user in one
> extra click, both documented in the README. Signing costs $99 a year for
> Apple alone, which is not worth it for this.

---

# v2.0 — Online

## Phase 08 — Accounts · L

**Blocks:** 09, 11 **Needs:** 02

Identity is the hook both the leaderboard rework and online play hang from.
Local PvP stays exactly as it is: one account, one computer, two people — the
account owns the record and player two is just a name.

- [ ] Registration and login, Spring Security with BCrypt. Token storage on the
      desktop side, since there's no browser to hold a session.
- [ ] Guest mode. People should be able to try the game without registering —
      this matters more for "usable" than any other single decision here.
- [ ] Persistent login so the app doesn't ask on every launch.
- [ ] Move production storage to Postgres, keeping H2 in-memory for tests. By now
      this is a Flyway migration, not a schema rewrite.
- [ ] Re-key leaderboard entries from typed names to account IDs. Until this
      lands, two people typing `Gavin` share a row and anyone can claim any name.
- [ ] Decide what happens to pre-accounts standings — most likely archive or
      discard rather than trying to attribute them.
- [ ] Fix ranking while you're in there: ties currently break alphabetically, so
      `Aaron` permanently outranks `Zoe` at equal wins. Sort on win rate, or on
      wins then fewer games.
- [ ] Server-side length and content validation on usernames and questions.
      `question` is `VARCHAR(2000)` with nothing enforcing it client-side.

## Phase 09 — Online PvP · XL

**Blocks:** 12 **Needs:** 04, 08

The headline feature. The server holds the authoritative `Game`; clients stay
thin and never decide anything.

- [ ] A `GameSession` registry mapping room codes to games and connected players.
      Six-character codes, no public matchmaking.
- [ ] Endpoints wrapping the moves that already exist: ask, answer, guess, plus
      `GET /api/rooms/{code}/state` for polling.
- [ ] **Server-side state filtering.** Never send the opponent's character to a
      client. Push full state to both players and anyone can read it off the wire
      — and the entire verification story collapses with it.
- [ ] Idempotency keys on every move. A request times out, the client retries,
      and the question gets recorded twice. This is the bug you will hit first.
- [ ] Persist session state **in Postgres**, on the single instance you already
      run. Not for scale — in-memory sessions die on every restart, so deploying
      while two friends are mid-game destroys their game. Redis is a Phase 13
      upgrade, not a starting point.
- [ ] Turn timers, forfeit, and opponent-left handling, so an abandoned game
      doesn't hang forever.
- [ ] Expire sessions on three clocks: an unjoined room after ~10 minutes, an
      idle game after ~30, and any game after 24 hours regardless. The cheapest
      abuse is creating a room and never joining it — one request holding a row
      and a code forever — so that case gets the shortest life.
- [ ] Cap concurrent open rooms per account at a small number. A rate limit
      bounds how *fast* rooms appear; only a cap bounds how many exist at once.
- [ ] A scheduled sweep that deletes expired sessions. Expiring lazily on read
      never reaches the rows that actually accumulate, which are the unread
      ones.
- [ ] Reconnect: both the plumbing and the screen states — reconnecting, opponent
      reconnecting, game expired.
- [ ] API versioning with a clear rejection message for stale clients. Installers
      live on disk and will fall behind the server.
- [ ] Wire up the Phase 04 commitment reveal so PvP verification runs at game
      end.
- [ ] Rate-limit move and room-creation endpoints before this is reachable from
      outside your network. Rate limiting alone does not bound storage: creation
      that stays within the limit, sustained, still fills the database. It is the
      expiry above that bounds the total.

> **Deliberately a monolith.** One instance handles tens of thousands of
> concurrent games at these state sizes and turn rates. The distributed-systems
> work worth doing here is durable sessions, idempotent moves, and reconnect —
> not microservices or a message queue.
>
> Put that reasoning in the README. Explaining why you *didn't* distribute reads
> better than having distributed something that didn't need it.

## Phase 10 — Ship v2.0 · M

**Needs:** everything above

- [ ] Deploy the server — Railway, Fly, or Render with managed Postgres.
- [ ] Structured logging and a database-aware health endpoint. `/api/status`
      returns a hardcoded string and says nothing about connectivity.
- [ ] Error responses that don't leak stack traces to clients.
- [ ] Rebuild installers against the deployed server.
- [ ] Rewrite the README around what it became: architecture, the commitment
      scheme, why it's a monolith, and screenshots.
- [ ] Tag `v2.0`.

---

# Post-v2

## Phase 11 — Stats and replay · M

**Needs:** 08

Cheap — the question logs have been accumulating since the history endpoint
shipped. Almost all of this is presentation.

- [ ] Game history browser over the question logs already in the database.
- [ ] Post-game replay — step through a finished game question by question. This
      is the cheap 80% of spectating, with none of its problems.
- [ ] Per-mode statistics beyond wins and losses: win rate, average questions to
      a correct guess, most-asked questions.

## Phase 12 — Chat and spectating · L

**Needs:** 09

- [ ] Chat as its own channel, strictly separate from questions. The question
      channel is game state — recorded, verified, replayed — and free-form
      chatter mixed into it would corrupt the log that verification runs against.
- [ ] Chat rides the existing state-update channel: a message list on the
      session, no new transport.
- [ ] Live spectating needs a *third* projection of game state alongside each
      player's view, since a spectator sees both characters.
- [ ] Invite-only spectating, and block self-spectating.

> **Spectating is a cheat vector.** A spectator sees both characters, so a player
> who opens a spectator connection to their own game learns the opponent's.
> Blocking yourself is easy; blocking a second account you also control isn't.
> Invite-only contains it — public spectating doesn't.
>
> Room-code-only games are also what keeps chat cheap. Open it to strangers and
> you inherit moderation and reporting, which is not a small feature.

## Phase 13 — Scale, only if measured · S

**Needs:** a real reason

- [ ] Move session state from Postgres to Redis, if session reads ever show up in
      profiling.
- [ ] Run more than one instance, if a single one is ever saturated.

Do not start this phase speculatively. "We measured X and it hurt" is the entry
condition.

---

## The mode matrix

`Game.askComputer()` routes to `ComputerPlayer.answerQuestion()`, which looks the
question up on the board and throws on anything else, so free questions are
impossible against the computer today. Phase 06 closes that cell by teaching the
computer to resolve typed text to a board attribute.

| Mode | Preset questions | Free questions | Verification |
| --- | --- | --- | --- |
| PvE | Yes | Phase 06 | Automatic |
| PvP local (hotseat) | Yes | Yes | Optional |
| PvP online | Yes | Yes | Via commitment |

Until then `GameSetup.againstComputer()` forces preset questions, so the mode
cannot be selected rather than failing partway through a game.

## Deliberately not doing

- **A web client** — the whole reason this plan is one language and one rules
  engine. Reconsider only if reach beats maintenance cost.
- **Custom character packs** — uploads, storage, and moderation. A large surface
  for a nice-to-have. Revisit after v2.0.
- **Public matchmaking** — room codes cover playing with friends and keep chat
  moderation entirely out of scope.
- **Microservices and message queues** — no load problem exists to solve.
  Building one anyway is the negative signal, not the positive one.
- **WebSockets** — polling is indistinguishable for a turn-based game. Add them
  only as a deliberate exercise, not a requirement.

## Still to decide

1. How long is a turn timer, and does it forfeit the game or just pass the turn?
2. Do guests get to play online at all, or only PvE and hotseat? Letting them
   online means unranked rooms and throwaway identities.
3. Does the store-my-character setting belong to the account or the machine?
4. Does the hotseat board stay on the leaderboard as a casual tier, or come off
   entirely because you referee both sides?

## Decided

- **Offline results upload themselves.** Games queued while the server is
  unreachable are uploaded on the next successful submission. The write-only CSV
  fallback was replaced by `pending-game-results.jsonl`, which can be read back.
- **Indentation is four spaces**, enforced by `.editorconfig`.
- **Free questions against the computer become a fourth game mode**, on the
  condition that the computer declines a question it cannot resolve rather than
  guessing at it. See Phase 06.
- **Closing the app mid-game must not lose it.** Local games are saved and
  offered back on the next launch. See Phase 05.

---

Phases 00 and 01 are done. Of what remains, 02 and 04 get more expensive the
longer they wait — 02 because every feature added first has to be dismantled,
04 because it changes an API that Phase 09 will freeze. Everything after 05 can
be reordered as interest dictates.

**Next branch:** the release itself. Everything Phase 07 needs is built; what
remains is running the installer workflow once to check the Windows job, which
has never executed, then writing the notes and tagging `v1.0.0`.
