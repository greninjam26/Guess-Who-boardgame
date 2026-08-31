# Guess Who — Target Architecture

This describes the system as it is **intended to be**, not as it currently
stands. It is a design document that the phases in `docs/ROADMAP.md` implement.

Where the current code differs, that difference is noted — those gaps are the
work.

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

Target of Phase 03. Today everything is a single Maven module, which means
`jpackage` would bundle Spring Boot, H2, HikariCP, and Jackson into the desktop
installer.

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

Target of Phase 02. Today `GUI.java` is ~1,160 lines with a single 770-line
method holding ~35 anonymous listeners and mutable fields (`curPlayer`,
`modeChoice`, `AIQuestion`) acting as implicit state.

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

Target of Phase 09 — the most consequential part of the design.

### Lifecycle

```text
  POST /api/rooms                 → creates session, returns 6-char code
  POST /api/rooms/{code}/join     → second player joins, game starts
  GET  /api/rooms/{code}/state    → polled every 1–2s by both clients
  POST /api/rooms/{code}/ask      ┐
  POST /api/rooms/{code}/answer   ├ intents, each carrying an idempotency key
  POST /api/rooms/{code}/guess    ┘
```

Room codes only. No public matchmaking — that decision is what keeps chat free of
moderation obligations later.

### State projection — the critical rule

The same session produces **different payloads per viewer**:

| Field | Player A sees | Player B sees | Spectator (Phase 12) |
| --- | --- | --- | --- |
| Own character | yes | yes | — |
| Opponent's character | **no** | **no** | both |
| Own question history | yes | yes | yes |
| Opponent's questions + answers given | yes | yes | yes |
| Whose turn, turn number, timer | yes | yes | yes |
| Commitment hashes | yes | yes | yes |

A spectator view is a *third* projection, not a player view with a flag. Building
it as "player view plus extra" is how the opponent's character leaks.

### Durability

Sessions persist in **Postgres**, on the single instance already running. This is
not a scaling decision — in-memory sessions die on restart, so deploying while two
friends are mid-game destroys their game. Redis is an upgrade to consider only if
session reads ever appear in profiling.

### Idempotency and concurrency

Every intent carries a client-generated idempotency key. The server records
applied keys per session and returns the existing result on a repeat, so a
timeout-and-retry cannot record a question twice.

Session state carries a monotonic `version`. Intents declare the version they were
composed against; a mismatch is rejected with the current state rather than
applied blindly. This covers both simultaneous submission and a client acting on
a stale poll.

### Disconnection

Last-seen timestamps per player drive the states the UI must render:
*reconnecting*, *opponent reconnecting*, *game expired*. A turn timer bounds
abandonment — on expiry the turn passes or the game forfeits, per the decision
still open in the roadmap.

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

Its value is verification *without disclosure*, which Phase 09 needs. An online
opponent's own client answers questions about their character, so the server can
record a whole game without ever learning either character and still check both
at the end. It cannot leak what it never held.

It does **not** defend against a modified client that commits to one character
and answers as though it held another. State that plainly in the README rather
than overclaiming.

### Choosing when to say

A player may keep their character to themselves and name it once the game is
over. The answer review runs either way, but proves different things, and the
interface says which:

| | commitment recorded | the review shows |
| --- | --- | --- |
| Named before playing | yes | the character was fixed before any question |
| Named at the end | no | the answers were consistent with the character named |

A promise is only recorded while the game is in progress, because one made after
the answers are known proves nothing and would be indistinguishable from a real
commitment. So **a human participant with no commitment is one who named their
character at the end** — the signal a verifier needs, with no extra field.

---

## Data model

Target shape. Bold rows are additions to what exists today.

| Table | Purpose |
| --- | --- |
| **`accounts`** | id, username, password hash, created_at |
| `game_results` | id, winner, created_at, **mode**, **difficulty**, **question_mode** |
| `game_result_participants` | id, game_result_id, play_order, **account_id** (nullable for guests), name, selected_character, commitment_hash, commitment_nonce |
| `game_result_question_answers` | id, participant_id, question_order, question, answer |
| **`game_sessions`** | code, state, version, created_at, last_activity |
| **`game_session_players`** | session_id, account_id, commitment_hash, last_seen |
| **`game_session_moves`** | session_id, idempotency_key, applied_at |

Schema changes go through **Flyway** migrations, adopted in Phase 01. Bold rows
are still to come; everything else exists. Phase 01
replaces it before any other schema work.

Participants keep a denormalized `name` alongside `account_id` so historical
results stay readable after a rename, and so guest games record something
meaningful.

---

## Distribution

```text
  desktop-client + game-core ──jpackage──▶  .dmg / .exe  (bundled JRE)
  server + game-core         ──deploy───▶  Railway / Fly / Render + Postgres
```

Installers live on user disks and will fall behind the server, so the API carries
a version and rejects incompatible clients with a message telling the player to
update — never with undefined behavior.

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
