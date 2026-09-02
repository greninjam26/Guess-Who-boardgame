# Guess Who — Roadmap

Fourteen phases across three releases, from a working desktop board game to an
online multiplayer app with accounts, verified answers, and leaderboards that
mean something.

Sequencing reflects the repo at `f856ba8`, immediately after the server-backed
leaderboard (PR #23) merged.

## Releases

| Release     | What it is                                                                                                                    | Phases |
| ----------- | ----------------------------------------------------------------------------------------------------------------------------- | ------ |
| **v1.0**    | A polished, installable single-machine game — PvE and hotseat, verified answers, per-mode leaderboards against a local server | 00–07  |
| **v2.0**    | The same game with accounts and room-code online multiplayer, deployed and reachable                                          | 08–10  |
| **Post-v2** | History, replay, chat, spectating, and scaling if it's ever measured                                                          | 11–13  |

Shipping v1.0 before the backend work matters. It is a real milestone you can
install and hand to someone, and it arrives well before the two XL phases.

## Locked decisions

| Area      | Decision              | Why                                                                                                  |
| --------- | --------------------- | ---------------------------------------------------------------------------------------------------- |
| Stack     | Java end to end       | No web client. One language, one rules engine, no port.                                              |
| Client    | Swing + FlatLaf       | The work ahead is architectural, not visual. Switching toolkits would stack a rewrite on a refactor. |
| Server    | One Spring Boot app   | A monolith, deliberately. The load never justifies anything else.                                    |
| Transport | Polling, not sockets  | Turns take a human ten seconds. A 1–2s poll is indistinguishable from realtime.                      |
| Delivery  | `jpackage` installers | Native `.dmg` / `.exe` with a bundled JRE. Nobody installs Java.                                     |

## The dependency spine

```text
v1.0    00 ✔  01 ✔  02 ✔  03 ✔  04 ✔  05 ✔  06 ✔  07 ✔   shipped
                                   06  needs 00 only — slot in anywhere

v2.0    08 ✔  →  09 ▸  Online PvP — playable  →  10  Ship (and Postgres)

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
- `LeaderboardPanel` and `LeaderboardPanelTest` prove the Swing layer _can_ be
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
      score lands first. It is _not_ related to the working server-backed
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
      in `LeaderboardDialog`, with difficulty as a _column_ inside the PvE board.
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
> vs-Computer board _"AI has won 40 of 60"_ is a genuinely interesting stat about
> how well the AI plays.

> **Hotseat stays farmable.** You control both sides of a local PvP game, so that
> board is self-refereed no matter how it's sliced. Either leave it unranked or
> label it casual — just don't pretend it's competitive.

## Phase 02 — Split the client · L — done

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
- [x] Every branch leaves the app working and the suite green.
- [x] Give the controller a view-update path. `OnlineGameController.View` is
      that push, and online play is what needed it: the screens are told what to
      show rather than reading state for themselves.
- [x] Establish the EDT discipline: every state change arrives through one
      `SwingUtilities.invokeLater` boundary rather than scattered through
      listeners. `RoomPoller` hands everything to `onInterfaceThread`, which is
      the single boundary the polling thread crosses.
- [x] Bring each extracted piece under test as it lands. Every extracted screen
      has one, in the shape `LeaderboardPanelTest` set.

> **Do this before it gets harder.** Every feature added ahead of this phase gets
> built into the structure that has to be dismantled, then rebuilt. It's the
> least fun phase and the one most worth front-loading.

## Phase 03 — Split the build · S — done

**Blocks:** 07 **Needs:** 02
**Branch:** `chore/multi-module-build`

Everything currently lives in one Maven module, so `jpackage` would bundle Spring
Boot, H2, HikariCP, and Jackson into the desktop installer — shipping a web
server and a database engine to every player.

Done here rather than at packaging time because Phase 02 has just finished
drawing these boundaries; doing both in one pass beats discovering it later.

- [x] Parent pom with three modules: `game-core`, `desktop-client`, `server`.
- [x] `game-core` — the `game` package plus the CSV, image, and audio resources,
      and the shared types both sides need (`GameResult`, `LeaderboardEntry`).
- [x] `desktop-client` — `ui`, `client`, and the CSV-writing half of
      `persistence` (`CsvGameResultRepository`, `StoreResult`). Depends on
      `game-core` only.
- [x] `server` — `web`, `leaderboard`, and the JDBC half of `persistence`.
      Depends on `game-core` only.
- [x] Split the `persistence` package deliberately: the `GameResultRepository`
      interface is used by both sides and belongs in core; the JDBC and CSV
      implementations do not.
- [x] Verify the desktop artifact resolves no Spring dependencies.

## Phase 04 — Commit the character · M — done

**Blocks:** 09 **Needs:** 02

Storing the chosen character and verifying honest answers turned out to be two
mechanisms, not one. Both are in place.

- [x] Move character selection to game start. `Player` stops auto-assigning;
      `Game.selectCharacter()` moves from `FINISHED` to setup.
- [x] Add the store-my-character setting, as a choice about _when_ rather than
      _whether_: name your character before playing, or keep it to yourself and
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
> What the commitment buys is verification _without disclosure_. In an online
> game the opponent's own client answers questions about their character, so the
> server can record a whole game without ever learning either character, and
> still check both at the end. It cannot leak what it never held.
>
> It does not prevent a modified client committing to one character and
> answering as another — the answering client is the only thing that knows. The
> reveal at game end makes it detectable instead, by checking every answer
> against the committed character. Said plainly in the README.

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

> **Adding music needed more than a file.** `GameResources` looks for
> `/audio/Guessing Game.wav` by that exact name, and Java Sound reads PCM WAV,
> AIFF, and AU — an MP3 fails silently, because the exception is caught and
> turns into "no music" with nothing on screen. It also ships inside
> `game-core.jar` and therefore inside every installer, which is why the track
> is generated rather than licensed: a recording carries rights in both the
> composition and the performance, and neither was ours to redistribute.

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
> _is_ getting closest to half, and every board question is two-way, so the
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

## Phase 07 — Ship v1.0 · S — done

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
- [x] **Release notes and the `v1.0.0` tag.** Tagging is what builds the
      installers and drafts the release, so the whole path was rehearsed on a
      `v1.0.0-rc1` tag first: the release job only runs on tags, and there is no
      earlier point at which it can be tested.

> **The version is 1.0.0 because it had to be.** `jpackage` refuses an
> `--app-version` whose first number is zero on macOS: _"the first number in an
> app-version cannot be zero or negative"_. Shipping this as v0.5 would have
> meant the installer reporting a version the tag disagreed with, so the
> release milestones moved up one — online play is v2.0.

> **Neither installer is signed.** macOS shows "the developer cannot be
> verified" and Windows shows SmartScreen, both fixable by the user in one
> extra click, both documented in the README. Signing costs $99 a year for
> Apple alone, which is not worth it for this.

---

# v2.0 — Online

## Phase 08 — Accounts · L — done

**Blocks:** 09, 11 **Needs:** 02

Identity is the hook both the leaderboard rework and online play hang from.
Local PvP stays exactly as it is: one account, one computer, two people — the
account owns the record and player two is just a name.

- [x] **Registration and login**, Spring Security for BCrypt only. Sessions are
      opaque tokens in a table, not JWTs: revocable, no key management, and
      obvious failure modes. The server stores each token's SHA-256 hash, so a
      copy of the database yields nothing anyone can present.
- [x] **Guest mode**, on the first screen beside Sign in and Create an account.
      No confirmation and no nagging: the whole game works without an account,
      and signing in is offered rather than demanded.
- [x] **Persistent login.** The token lives in the application directory as an
      owner-only file rather than in preferences, which on macOS are
      world-readable. Resumed before the window is shown, so nobody types into
      a screen that then vanishes.
- [ ] **Moved to Phase 10.** Postgres matters when something is deployed, and
      nothing is yet. The migrations turned out to be portable already — no H2
      syntax anywhere — so switching engines is configuration, not a rewrite,
      and doing it early would only oblige everyone to run a database.
- [x] **Leaderboard keyed on accounts.** Standings group by account where there
      is one and by typed name where there is not. The account comes from the
      bearer token and never from the request body — a body that could name an
      account is a body that could claim one.
- [x] **Pre-accounts standings stay unattributed.** Every row from before the
      migration keeps a null account and drops off the registered side.
      Guessing which account an old `Player 1` belonged to would put somebody
      else's games on somebody's record.
- [x] **Ranking fixed**: wins, then fewer games played, then the name as a last
      resort. The name has to stay in as a tiebreak, or two identical records
      order differently between calls and a paginated board shows one player
      twice — but it must not be the second key, which is what made `Aaron`
      outrank `Zoe`.
- [x] **Server-side validation.** Field lengths are checked against the column
      widths before the insert, so an oversized value is a 400 rather than a
      constraint violation surfacing as a 500. Usernames are limited to letters,
      digits, underscores and hyphens: a name that cannot be typed back
      reliably is a name that can impersonate another.

## Phase 09 — Online PvP · XL — done

**Blocks:** 12 **Needs:** 04, 08

The headline feature. The server holds the authoritative `Game`; clients stay
thin and never decide anything.

Two people can now play a game: create a room, share the code, join, choose,
ask, answer, guess, and a turn nobody comes back to forfeits. What remains is
what a deployment needs rather than what a game needs — reconnect, rate limits
and API versioning.

> **Automated end to end, not yet played.** `LiveOnlineGameTest` runs the real
> client against a real server over a real socket, through a whole game and
> through a forfeit, which is where the contract bugs between layers surface.
> What has still never happened is two people at two machines. That is the next
> thing to do, not the next thing to build.

- [x] **Rooms, not a registry.** Six-character codes from an alphabet without
      the characters people mishear reading one screen and typing into another.
      Kept in the database rather than in memory: sessions held in memory die
      on restart, so deploying while two people are mid-game would destroy it.
- [x] **Endpoints for ask, answer, guess and character choice**, plus
      `GET /api/rooms/{code}/state`. Asking and answering had to become two
      moves: local play records both at once because the opponent answers out
      loud, which two people on two machines cannot do. A guess is settled by
      the server against the real character — asking the player who just lost
      to confirm it is not a check worth having.
- [x] **Server-side state filtering.** `RoomState` has no field that could
      hold the opponent's character, so the guarantee is in the shape of the
      type rather than in everyone remembering. The test asserts the character
      appears nowhere in the response body, and was checked by deliberately
      adding a field that leaked it — it failed, and was reverted.
- [x] **Idempotency keys on every move.** Claimed inside the move's
      transaction, so a move the rules refuse releases its key instead of
      consuming it — otherwise one out-of-turn attempt would silently disable
      that key for ever. The client retries a failed move with the same key,
      which is the whole reason the keys exist.
- [x] **Session state persisted**, as a `GameSnapshot` in the rooms table —
      the same shape a saved local game uses. The engine is Phase 10's
      business: the migrations are portable, so which database holds it is
      configuration rather than a rewrite.
- [x] **Turn timers, forfeit, and opponent-left handling.** Presence is measured
      by requests, polling included, so a client that is open keeps its player
      present without them doing anything — which separates somebody thinking
      from somebody who closed their laptop. A turn that runs out forfeits to
      whoever stayed, blaming whoever owed the move rather than whose turn it
      is: an unanswered question is held up by the answerer, and getting that
      backwards would forfeit the game of the player who did their part.
      Running out is necessary but not sufficient — the player who owes the
      move must also have gone quiet for six presence windows, not one, so a
      single dropped connection cannot end a game somebody is sitting in front
      of. The fifteen-second window still decides what their opponent is told;
      only the forfeit waits. A room already past its
      expiry is left to the sweep rather than forfeited, since settling it
      would also carry a deadline that brings the room back to life.
- [x] **Finished online games recorded**, against both accounts rather than
      whichever client noticed. `GameMode.PVP_ONLINE` existed and nothing
      produced it, so every online game — forfeits included — was invisible to
      the leaderboard the accounts exist for. Written by the server on the
      version-checked write that ended the game, which is what makes it happen
      exactly once when both players are polling.

- [x] **Three clocks.** Ten minutes unjoined, thirty idle, twenty-four hours
      absolute. The unjoined room dies soonest because creating one and walking
      away is the cheapest abuse there is, and the ceiling is measured from when
      the room opened so playing cannot extend it.
- [x] **Five open rooms per account.** A rate limit bounds how fast rooms
      appear; only a cap bounds how many exist at once.
- [x] **A scheduled sweep**, every five minutes. Expiring lazily on read never
      reaches the rows that accumulate, which are exactly the abandoned ones
      nobody will read again. Failure is logged and the next run tries again.
- [x] **Reconnect, the screen states.** A dropped connection is a banner over
      the board and the client keeps polling; a room that has gone ends the game
      instead of being retried for ever. Reported on the transition, because
      polling every two seconds had been raising a modal dialog per failed
      attempt. An opponent coming back needed nothing new — presence already
      flips back on their next request.
- [x] **Reconnect, across a restart.** The room code is remembered in its own
      file beside the saved game and the token, and the next launch offers to
      pick the game back up. Its own file rather than a field on `SavedGame`,
      because a player can have both a half-finished game against the computer
      and an open room with a friend, and only one of those lives on this
      machine. Rejoining is not joining — the server has held the room and both
      accounts all along, so it only starts polling again, and whether the room
      survived is answered by the first poll rather than by a request on every
      launch.
- [x] **API versioning.** A header rather than a path, since there is one client
      and it ships from this repository — `/api/v1/` would rewrite every URL to
      buy a property nobody outside relies on. The server declares a minimum it
      will answer and turns away anything below it with 426 and a sentence a
      player can act on.

> **The minimum is zero, and shipping it that way is the point.** Every
> installer already released sends no version header at all. Rejecting them on
> the first deployed server would be the check doing harm rather than good:
> nothing is incompatible yet, and those builds map an unrecognised status to
> "the server could not be reached" — which, since reconnect landed, means a
> reconnecting banner and a retry every two seconds. A player told to wait for
> ever instead of to update.
>
> So the mechanism ships now and turns nobody away. By the time raising the
> minimum matters, the clients in the wild send a version and know what a 426
> means.
- [x] **The commitment reveal at game end.** Its own type and its own endpoint,
      refused until the game is finished. `RoomState` stays shaped so that no
      field on it can carry the opponent's character; a nullable one for the
      endgame would put that field on every response of every game and turn a
      structural guarantee back into something the server has to remember.
      Two verdicts per player, because they fail independently: whether the
      revealed character matches the promise made before play, and whether their
      answers match that character. Committing honestly and then answering as
      somebody else passes the first and fails the second.
- [x] **Rate limits**, on signing in and registering per address, and on
      opening rooms and moving per account. Rate limiting alone does not bound
      storage: creation that stays within the limit, sustained, still fills the
      database. It is the expiry above that bounds the total.

> **The endpoint this plan did not name was the one that mattered.** It listed
> moves and room creation — both already bounded by something else, the rules
> refusing a move out of turn and the five-open-rooms cap. Signing in is bounded
> by nothing, takes a password, and costs a BCrypt hash per attempt, so leaving
> it open was both a way to guess passwords and a way to burn the server's CPU
> with a few hundred requests. It is now held ten times tighter than playing.
>
> Held per address rather than per username, because limiting per username lets
> anybody lock a player out of their own account by failing to sign in as them.
>
> **Reading the game is deliberately not limited**, and it is the busiest
> endpoint by far. Presence is measured by requests, so a poll answered with 429
> is a poll that did not mark the player present — throttling it would forfeit
> games as a side effect of protecting the server. The poll interval is the
> lever there, not a limit.

> **Deliberately a monolith.** One instance handles tens of thousands of
> concurrent games at these state sizes and turn rates. The distributed-systems
> work worth doing here is durable sessions, idempotent moves, and reconnect —
> not microservices or a message queue.
>
> Put that reasoning in the README. Explaining why you _didn't_ distribute reads
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
- [ ] Live spectating needs a _third_ projection of game state alongside each
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

| Mode                | Preset questions | Free questions | Verification   |
| ------------------- | ---------------- | -------------- | -------------- |
| PvE                 | Yes              | Phase 06       | Automatic      |
| PvP local (hotseat) | Yes              | Yes            | Optional       |
| PvP online          | Yes              | Yes            | Via commitment |

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

1. Do guests get to play online at all, or only PvE and hotseat? Letting them
   online means unranked rooms and throwaway identities.
2. Does the store-my-character setting belong to the account or the machine?

## Decided

- **Offline results upload themselves.** Games queued while the server is
  unreachable are uploaded on the next successful submission. The write-only CSV
  fallback was replaced by `pending-game-results.jsonl`, which can be read back.
- **A turn is three minutes, and running out forfeits the game** — but only to
  a player who is still there. Passing the turn instead would move the stall
  along and still need the sweep to end it; forfeiting against somebody who is
  watching the board would lose them a game for thinking. Presence decides
  which of those it is.
- **Hotseat keeps a board, and it is not the same board as online.** Three
  tables: vs Computer, vs Player (online), vs Player (same machine). An online
  game is refereed by the server and answered by each player's own client
  against a character committed to before play; a same-machine game is refereed
  by whoever is holding the keyboard for both sides. Ranking those together
  would put a result anybody can manufacture next to one they had to earn, so
  the hotseat board stays as its own casual tier rather than coming off or
  being merged in.
- **Indentation is four spaces**, enforced by `.editorconfig`.
- **Free questions against the computer become a fourth game mode**, on the
  condition that the computer declines a question it cannot resolve rather than
  guessing at it. See Phase 06.
- **Closing the app mid-game must not lose it.** Local games are saved and
  offered back on the next launch. See Phase 05.

---

Everything through Phase 08 is done, and Phase 09 is playable. v1.0 shipped as
installers anyone can download; two people can now open a room and play each
other. What remains in Phase 09 is what a deployment needs rather than what a
game needs.

**Next: not a branch.** Play a game against a second client with the server
running.

The chain now runs end to end under test, which caught what that kind of test
catches. What it cannot catch is the part that needs two people: whether the
polling feels like a game, whether a three-minute timer is generous or mean in
practice, whether the room code is readable down a phone. Finding that now costs
an afternoon; finding it after reconnect and rate limits are layered on top
costs considerably more.

Three things carried forward and not forgotten:

- Nobody has run the Windows installer. CI proves it builds; the `.msi` has only
  ever been a file.
- Postgres moved to Phase 10, to sit with the deployment that needs it.
- The four open Phase 09 items bound abuse and handle reconnection. Two people
  on one network can play without them.
