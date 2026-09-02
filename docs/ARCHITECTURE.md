# Guess Who — Architecture

This began as a design document describing a system that did not exist. Most of
it is now built, so it describes what is there, and says plainly where it does
not.

Two kinds of note appear throughout:

- **Still missing.** Something named here that has not been written. What is
  left is narrow: API versioning. `docs/ROADMAP.md` has it in Phase 09.
- **What was actually built differs.** Somewhere the design was tried and
  something else turned out to be right. These are the interesting ones, and
  they are kept rather than tidied away: the reasoning that changed is worth
  more than a document that pretends it never did.

`docs/ROADMAP.md` has the order of work and what remains.

---

## Principles

1. **One rules engine.** The game's rules exist in exactly one place, in Java,
   and both the desktop client and the server run the same code. No second
   implementation in another language, ever.
2. **The server decides.** In online games the server holds the authoritative
   `Game`. Clients render state and submit intents; they never decide outcomes.
3. **A client is told only what its player may know.** Never "send everything and
   hide it in the UI" — the opponent's character must not cross the wire.
4. **A monolith until measured otherwise.** One Spring Boot instance, one
   database. Complexity is added in response to evidence, not anticipation.
5. **Turn-based, not realtime.** A turn takes a human ten seconds. Polling is
   sufficient, and choosing it removes an entire category of infrastructure.

---

## Module structure

Done in Phase 03. Before it, everything was one Maven module, so `jpackage`
would have bundled Spring Boot, H2 and HikariCP into the desktop installer.

As built, the desktop installer carries five runtime jars — `game-core`,
FlatLaf and three Jackson jars, about 15 MB — and no Spring. `game-core` has no
runtime dependencies at all, which is what lets it be shared by a Swing client
and a Spring server without dragging either into the other.

```text
guess-who-boardgame  (parent pom)
│
├── game-core         no Spring, no Swing, no HTTP
│   ├── game/         Game, Board, Player, ComputerPlayer, Character, Question
│   ├── resources/    character + question CSV, images, audio
│   └── shared types  GameResult, LeaderboardEntry, GameResultRepository
│
├── desktop-client    depends on game-core only
│   ├── ui/           Swing screens, controller, state model
│   ├── client/       HTTP clients, offline fallback
│   └── persistence/  CsvGameResultRepository, StoreResult
│
└── server            depends on game-core only
    ├── web/          REST controllers
    ├── session/      online game sessions
    ├── leaderboard/  standings queries
    └── persistence/  JDBC repositories
```

Dependencies point inward only. `game-core` knows nothing about either side, and
`desktop-client` and `server` never reference each other — they communicate over
HTTP and share types through core.

**The `persistence` package splits.** The `GameResultRepository` interface is used
by both sides and belongs in core; the JDBC implementation is server-only and the
CSV implementation is client-only.

---

## The rules engine

`game-core` is the centre of the system and the reason this stays a Java project.

`Game` is a lifecycle state machine:

```text
STARTING ──startComputerGame()──▶ IN_PROGRESS ──finish()──▶ FINISHED
         └─startPlayerGame()────▶
```

Every mutating method guards on state and turn ownership (`requireInProgress()`,
`requireTurn()`, `requireNoPendingComputerQuestion()`). This is what makes the
same class usable as a server-side referee without modification — the guards that
protect a local game from UI bugs are exactly the guards that protect an online
game from a hostile client.

**Rules for this module:** no Swing imports, no Spring imports, no HTTP, no
logging framework. Randomness is injected, never constructed inline, so games are
reproducible in tests.

---

## Desktop client

Done in Phase 02, which took `GUI.java` from ~1,160 lines and a single
770-line method to a frame that swaps screens. It is about 500 lines now, and
the growth since has been the online flow's screen swapping rather than logic:
`OnlineGameController` holds the room and the poll, `OnlineGameScreens` holds
what an online game shows, and `GUI` shows one or the other.

The shape below was the target. What was actually built keeps the split but not
the names: `GameController` for local play, `OnlineGameController` for online,
and screens that render a state rather than a `UiState` enum.

```text
      user input                     server events
          │                                │
          ▼                                ▼
   ┌──────────────────────────────────────────────┐
   │            GameController                    │
   │  owns UiState · applies moves · one EDT hop  │
   └──────────────────────────────────────────────┘
          │ renders                    ▲ intents
          ▼                            │
   ┌──────────────────────────────────────────────┐
   │  SetupScreen  BoardView  QuestionControls    │
   │  GuessControls  EndingScreen                 │
   └──────────────────────────────────────────────┘
```

- **`UiState`** is an explicit, enum-backed model. The stringly-typed flags
  (`modeChoice.endsWith("preset questions")`) become types.
- **`GameController`** is the only component that mutates state. A local click
  and an incoming server update take the identical path, which is what makes
  online play possible without rewriting the screens.
- **One EDT boundary.** Every state change reaches Swing through a single
  `SwingUtilities.invokeLater` in the controller, rather than being scattered
  through listeners.
- **Screens are dumb and testable.** They render a state and emit intents.
  `LeaderboardPanel` is the existing proof — its test covers loading, success,
  empty, error, and retry without a running server.

---

## Saving a game in progress

Phase 05. A half-finished game is spread across both modules, and leaving any
part of it behind produces a game that looks resumed but plays wrong.

```text
  GameSetup      mode, difficulty, question mode, names, birthdays
  Game           status, winner, pending computer question, whose turn
  User x2        selected character, commitment, answers given
  ComputerPlayer stillPossible[] — the whole of what the computer knows
  CharacterBoard faceDown[] per player — the cards a player has flipped
  QuestionHistory the transcript down either side
```

**The flipped cards are not decoration.** They are the player's working notes,
and restoring a game to twenty-four face-up cards hands back a position they
can no longer reason about. That is worse than not offering the resume, so a
save either captures them or is not worth writing.

- **Written after every turn**, not on exit. A crash or a force-quit is
  precisely when someone wants their game back, and an on-exit save is the one
  that misses those.
- **Deleted when the game finishes.** A completed game offered back as
  resumable would read as a bug.
- **One slot**, `saved-game.json`, in the same application directory as the
  upload queue.
- **Versioned, and never fatal.** An unreadable or unrecognised save is
  discarded and the game starts normally. A resume is a convenience; failing to
  read one must not stop the application launching.

### Crossing the module boundary

`game-core` has no runtime dependencies, which is worth keeping, so nothing in
it knows about JSON. `Game` instead describes itself as a `GameSnapshot` — a
plain record — and rebuilds from one, keeping its invariants rather than
exposing its fields to a serialiser. The client turns that into JSON using the
Jackson it already carries for the upload queue.

Derived state is recomputed rather than stored: the computer's `answerCount[]`
and `possibleCharactersCount` both follow from `stillPossible[]`, and storing
them would create two sources of truth that can disagree.

Online games are excluded. A server-side session is not this file's business.

---

## Server

```text
  HTTP ──▶ Controllers ──▶ Services / SessionRegistry ──▶ Repositories ──▶ DB
                                      │
                                      └──▶ game-core Game (authoritative)
```

Controllers do request shaping and validation only. Game rules live in
`game-core`; persistence lives behind repository interfaces. No business logic in
the web layer.

---

## Online game sessions

Built in Phase 09. The endpoint names below differ slightly from what was
planned — each move posts to a plural resource — and two things turned out
differently from the sketch, both recorded under the headings that follow.

### Lifecycle

```text
  POST /api/rooms                    → opens a room, returns a 6-char code
  POST /api/rooms/{code}/players     → second player joins, game starts
  GET  /api/rooms/{code}/state       → polled every 2s by both clients
  POST /api/rooms/{code}/character   → the character you will be guessed at
  POST /api/rooms/{code}/questions   ┐
  POST /api/rooms/{code}/answers     ├ moves, each carrying an idempotency key
  POST /api/rooms/{code}/guesses     ┘
```

Room codes only. No public matchmaking — that decision is what keeps chat free of
moderation obligations later.

The code alphabet leaves out everything misheard reading one screen and typing
into another: no `O` against `0`, no `I` or `1` against `L`, and no vowels, so
no real words appear in something people read aloud. 27 characters and six
places is about 387 million codes.

**Asking and answering had to become two moves.** Local play records a question
and its answer in one call, because the opponent answers out loud and the asker
types both halves. Two people on two machines cannot, so `Game` now holds a
question waiting for an answer, and the turn does not pass until it is
answered — otherwise one player could ask five questions while the other was
deciding how to answer the first.

**A guess is settled by the server.** Local play asks the opponent to confirm;
two people who cannot see each other have no such check, and asking the player
who just lost to agree that they lost is not one worth having.

### State projection — the critical rule

The same session produces **different payloads per viewer**:

| Field                                | Player A sees | Player B sees | Spectator (Phase 12) |
| ------------------------------------ | ------------- | ------------- | -------------------- |
| Own character                        | yes           | yes           | —                    |
| Opponent's character                 | **no**        | **no**        | both                 |
| Own question history                 | yes           | yes           | yes                  |
| Opponent's questions + answers given | yes           | yes           | yes                  |
| Whose turn, turn number, timer       | yes           | yes           | yes                  |
| Commitment hashes                    | yes           | yes           | yes                  |

A spectator view is a _third_ projection, not a player view with a flag. Building
it as "player view plus extra" is how the opponent's character leaks.

As built, the rule is enforced by the shape of the type rather than by care:
`RoomState` has no field that could hold the opponent's character, so there is
nowhere for one to be put. What it carries instead is `opponentHasChosen` — a
boolean, because a client that knows _whether_ they have chosen can show a tick,
and one that knows _what_ has already won.

The test asserts the character appears nowhere in the serialised response, not
that a named field is absent, so a field added later that leaks it fails the
same way. That was checked by deliberately adding one: it failed, printing the
whole offending payload, and was reverted.

### Durability

Sessions persist in the result database — H2 today, Postgres when there is
something deployed. The migrations use no engine-specific syntax, so that is a
configuration change rather than a rewrite, and moving it early would only
oblige everyone running the server to install a database. This is
not a scaling decision — in-memory sessions die on restart, so deploying while two
friends are mid-game destroys their game. Redis is an upgrade to consider only if
session reads ever appear in profiling.

### Idempotency and concurrency

Every move carries a client-generated key, required rather than optional: a
move without one cannot be recognised as a retry, and this project ships the
only client, so a request arriving without a key is a bug rather than somebody
else's client being awkward. Keys are scoped to their room and checked against
the column width — an oversized one is a bad request, not a constraint
violation surfacing as a 500.

The key is claimed inside the move's transaction, so a move the rules refuse
releases its key instead of consuming it. Without that, one out-of-turn attempt
would disable that key for ever and the client's retry would silently do
nothing.

Rooms carry a monotonic `version`. **The client never sees it** — an earlier
sketch had intents declaring the version they were composed against, but the
server already reads the room to apply a move, so the version it read is the
one the write is conditional on. `UPDATE ... WHERE code = ? AND version = ?`
is what decides; comparing in Java would leave the gap it is meant to close.

A losing write is rejected with a conflict rather than applied. The clearest
case is not exotic: both players choosing a character at the same moment, which
happens at the start of every game. Each reads a game where nobody has chosen,
each writes its own choice, and without this the second silently replaces the
first.

### Disconnection

Presence is measured by requests rather than moves. A client polls every couple
of seconds, so one that is open keeps its player present without them doing
anything — which is the distinction worth drawing: somebody deliberating still
has a client watching for them, and somebody who quit does not. Fifteen seconds
of silence counts as gone, which is several missed polls.

The waiting player is told, and told tentatively — _"seems to have left"_ —
because a phone that went through a tunnel looks exactly like one that was put
away.

> **Two thresholds, not one.** Fifteen seconds decides what the opponent is
> *told*. Ending a game needs ninety — six of those windows, written as a
> multiple so the relationship survives anyone changing the number. Saying
> somebody seems to have gone is a hint: it costs nothing when it is wrong and
> it corrects itself on the next poll. Taking their game away is irreversible
> and happens to the player who is not looking, so it demands far more evidence
> than a hint does.
>
> Between the two, a player shows as absent and keeps their game — which is
> exactly where a bad minute of wifi puts somebody sitting right in front of
> the board. Sharing one threshold would have meant choosing between a slow
> hint and a forfeit that a single lapse could trigger.
>
> A room with no sighting at all — one opened before presence was recorded —
> is left to expire rather than forfeited. Ending a game on the strength of a
> column that did not exist when it started is not evidence of anything.

A turn that runs out forfeits to whoever stayed. Passing the turn instead would
only move the stall along and still leave the sweep to end the game; a forfeit
gives the player who stayed a result. It blames whoever **owed the move**, which
is not always whose turn it is: an unanswered question is held up by the
answerer while the turn still belongs to the asker.

Checked when a player reads the game rather than only on a schedule, because the
person waiting is the one polling. Version-checked like any other write, so two
simultaneous polls cannot forfeit the same game twice.

A connection that drops is shown as a banner over the board — _"Reconnecting…
your game is safe, and this client is still trying"_ — and the client keeps
polling through it. Reported on the transition rather than per attempt: polling
every two seconds turned one outage into a run of identical failures, and the
frame was raising each as a modal dialog, so a player whose wifi blinked got a
dialog every two seconds until it came back, each titled _"Invalid game setup"_.

> **A failed poll and a failed button press are not the same news.** A poll is
> still trying, so it says reconnecting. Nothing retries a room that failed to
> open or a move that failed to send, so those still say plainly that the server
> could not be reached. Showing a reconnecting banner for those would promise a
> recovery that nothing is working towards.

A room that has gone — expired, or swept after being abandoned — ends the game
rather than being retried. More polling returns the same answer for ever. The
same 404 before joining means a mistyped code, which is why it is only terminal
once the client is in a room.

Closing the application no longer loses the game. The room's code is kept in
`active-room`, beside the saved local game and the session token, and the next
launch offers to pick it back up. Rejoining is not joining: the server has held
the room, the game and both accounts all along, so the client sets the code and
starts polling, and the first answer brings back whatever the game became.

> **Its own file, not a field on `SavedGame`.** A saved local game is the whole
> game — this client is the only place it exists. An online room is six
> characters, because the game is on the server. Sharing one slot would mean a
> field that is null for every local game, and would make a player choose
> between two things they can genuinely have at once.

> **The room is not checked before offering.** That would be a request on every
> launch to answer a question the first poll answers anyway. A room that expired
> while the application was shut arrives at the game-gone screen, which is where
> that news belongs.

One question per launch: the online room is offered ahead of a saved local game,
because it has a turn clock running and a room that expires while the local one
waits as long as it likes.

---

## Trust boundary: character commitment

Built in Phase 04. Each player names their character before playing, and
`CharacterCommitment` records `SHA-256(character, nonce)` at that moment.

```text
  game start   choose a character  ──▶  commitment recorded
                                        (hash + nonce)

  play         answers flow normally; the hash reveals nothing

  game end     character + nonce   ──▶  recompute and compare
                                        replay every answer
```

**Locally the commitment is not what prevents cheating.** The game holds the
character, so a choice is final because `Game.selectCharacter()` refuses a second
call. Nothing local depends on the hash.

Its value is verification _without disclosure_, which Phase 09 needs. An online
opponent's own client answers questions about their character, so the server can
record a whole game without ever learning either character and still check both
at the end. It cannot leak what it never held.

It does not **prevent** a modified client from committing to one character and
answering as though it held another — nothing on the wire can, because the
answering client is the only thing that knows. What it does is make that
detectable. The reveal at game end checks every answer somebody gave against the
character they committed to, so answering as Sam while holding Olivia produces a
list of answers Olivia contradicts.

Detection, not prevention, and worth being exact about: a cheat can still win the
game. What they cannot do is win it unnoticed. And the review only sees the
questions that were actually asked — a lie about an attribute nobody asked about
leaves no trace, because there is nothing to contradict.

### Choosing when to say

A player may keep their character to themselves and name it once the game is
over. The answer review runs either way, but proves different things, and the
interface says which:

|                      | commitment recorded | the review shows                                     |
| -------------------- | ------------------- | ---------------------------------------------------- |
| Named before playing | yes                 | the character was fixed before any question          |
| Named at the end     | no                  | the answers were consistent with the character named |

A promise is only recorded while the game is in progress, because one made after
the answers are known proves nothing and would be indistinguishable from a real
commitment. So **a human participant with no commitment is one who named their
character at the end** — the signal a verifier needs, with no extra field.

---

## Data model

As built, at `V11`. Every table here exists.

| Table                          | Columns                                                                                                                                       |
| ------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------- |
| `accounts`                     | id, username, username_folded, password_hash, created_at                                                                                      |
| `account_sessions`             | id, account_id, token_hash, created_at, expires_at                                                                                            |
| `game_results`                 | id, winner, created_at, mode, difficulty, question_mode                                                                                       |
| `game_result_participants`     | id, game_result_id, play_order, account_id (nullable for guests), name, selected_character, commitment_hash, commitment_nonce                 |
| `game_result_question_answers` | id, participant_id, question_order, question, answer                                                                                          |
| `game_rooms`                   | id, code, host_account_id, guest_account_id, status, game_state, version, host_last_seen, guest_last_seen, created_at, updated_at, expires_at |
| `room_move_keys`               | id, room_code, move_key, applied_at                                                                                                           |

Schema changes go through **Flyway** migrations, adopted in Phase 01.

Participants keep a denormalized `name` alongside `account_id` so historical
results stay readable after a rename, and so guest games record something
meaningful.

> **One room table, not three.** The design called for `game_sessions`,
> `game_session_players` and `game_session_moves`. What got built is
> `game_rooms` and `room_move_keys`, because the players table had nothing to
> hold: a room has exactly two sides, so `host_*` and `guest_*` columns say the
> same thing as two rows and a join, and they let the conditional updates that
> decide joining and moving stay single statements against one row. The move
> keys did need their own table — there are many per room and they are claimed
> by a unique constraint.
>
> `game_state` is the whole `GameSnapshot` as JSON rather than columns. The
> server never queries inside a game; what it decides with — who may act,
> whether the room is open, when it dies — is what became a column.

> **Both `last_seen` columns are on the room, not the account.** Presence is
> per-game: the same person can have a room open in one window and nothing in
> another, and an account-level "last seen" would say they were present in a
> game they had closed.

---

## Distribution

```text
  desktop-client + game-core ──jpackage──▶  .dmg / .exe  (bundled JRE)
  server + game-core         ──deploy───▶  Railway / Fly / Render + Postgres
```

> **Still missing.** Installers live on user disks and will fall behind the
> server, so the API should carry a version and reject incompatible clients with
> a message telling the player to update rather than behaving undefinedly. It
> does not yet — there is no version on the wire and no rejection, so an old
> client meets a newer server and fails in whatever way the mismatch happens to
> produce. Phase 09 in `docs/ROADMAP.md`.

---

## Deliberately excluded

- **A web client.** The reason this is one language and one rules engine.
- **WebSockets.** Polling is indistinguishable for a turn-based game.
- **Microservices, message queues, multiple instances.** No load problem exists to
  solve; building for one anyway is the negative signal.
- **Public matchmaking.** Room codes cover playing with friends and keep
  moderation out of scope.

Each of these is a live option if evidence changes. None should be adopted
because it seemed more impressive.

---

See `docs/ROADMAP.md` for the phase ordering that gets from the current code to
this design.
