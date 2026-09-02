# Guess Who Board Game

A desktop adaptation of the classic Guess Who board game, written in Java with a Swing user interface. Players narrow down a board of 24 characters by asking yes-or-no questions and making a final guess.

## Features

- Player-versus-computer games with easy and hard AI modes
- Local player-versus-player games
- Online games against a friend, using a six-character code — no matchmaking
- A three-minute turn timer that forfeits only once the player who owes the move
  has gone quiet for a minute and a half, so neither thinking hard nor a dropped
  connection loses a game somebody is still playing
- Online games recorded against both players' accounts when they finish
- A dropped connection recovers on its own, and closing the app offers the game
  back on the next launch
- Accounts, with guest play for anyone who would rather not have one
- Rate limits on signing in, registering, opening rooms and moving, so a server
  on the open internet cannot be used to guess passwords
- Preset-question and free-question game modes
- Interactive character boards for tracking eliminated characters
- Character and question data loaded from CSV files
- Answers checked against the character a player committed to before playing —
  and in an online game, both characters revealed at the end with a review of
  whether the opponent answered as who they promised to be
- HTTP submission of completed game results
- Paginated HTTP history of completed games
- Three leaderboards — vs Computer, vs Player (online), vs Player (same
  machine) — kept apart because a game refereed by the server and a game
  refereed by whoever holds the keyboard are not the same achievement
- Leaderboard window available from the Swing application
- Completed games queued locally and uploaded once the server is reachable again
- Leaderboard rows that belong to an account, rather than to whoever typed a name
- A game in progress saved automatically and offered back on the next launch
- Background music with volume, mute, and pause, kept between sessions
- Settings, rules, and the leaderboard reachable from one button

## Install

Download the installer for your system from the
[releases page](https://github.com/greninjam26/Guess-Who-boardgame/releases).
Java is bundled, so nothing else needs installing.

**macOS (Apple silicon)** — open the `.dmg` and drag the app to Applications. The
first time you open it, **right-click the app and choose Open**, then confirm.
Double-clicking shows _"cannot be opened because the developer cannot be
verified"_ instead: the app is not signed with an Apple developer certificate,
which costs $99 a year and this project does not have one. Right-clicking the
first time is the whole workaround, and macOS stops asking afterwards.

**Windows** — run the `.msi`. Windows SmartScreen shows a blue warning for
installers it has not seen before; choose **More info**, then **Run anyway**.
Same reason: no paid code-signing certificate.

**Linux** — no installer is built. `jpackage` only produces the format of the
system it runs on, and neither of the above can be built on Linux. Run it from
source instead, as below.

The game keeps its saved game, its queued results, and its settings in one
place, which is also what to delete to remove every trace of it:

| System  | Location                                  |
| ------- | ----------------------------------------- |
| macOS   | `~/Library/Application Support/Guess Who` |
| Windows | `%APPDATA%\Guess Who`                     |
| Linux   | `~/.local/share/guess-who`                |

## Technology

- Java 17
- Java Swing and AWT
- Spring Boot 4.1.1 and Spring MVC
- Spring JDBC, Flyway, and H2
- Maven
- CSV-based game data

## Project Structure

```text
.
├── pom.xml                          # parent, holds the three modules together
├── game-core/                       # the rules, the data, the artwork
│   └── src/main/
│       ├── java/com/guesswho/
│       │   ├── game/                # game flow, models, and resources
│       │   └── leaderboard/         # standings types shared by both sides
│       └── resources/
│           ├── audio/               # background music
│           ├── data/                # character and question CSV files
│           └── images/              # character-card artwork
├── desktop-client/                  # the Swing game
│   └── src/main/java/com/guesswho/
│       ├── client/                  # HTTP clients and the pending-upload queue
│       └── ui/                      # Swing interface and entry point
└── server/                          # the HTTP API
    └── src/main/
        ├── java/com/guesswho/
        │   ├── GuessWhoServerApplication.java
        │   ├── persistence/         # database persistence
        │   └── web/                 # HTTP controllers and responses
        └── resources/
            ├── application.properties
            └── db/migration/        # Flyway schema migrations
```

`game-core` depends on nothing — no Spring, no Swing, no HTTP — and both other
modules depend only on it. That is what keeps a web server and a database engine
out of the desktop installer.

## Prerequisites

Install the following tools:

- [JDK 17 or newer](https://adoptium.net/)
- [Apache Maven](https://maven.apache.org/)

Confirm that they are available:

```bash
java -version
mvn -version
```

## Build

From the repository root, run:

```bash
mvn clean package
```

Maven compiles the application, copies its resources, and creates the build output under `target/`.

## Run the Desktop App

Start the Swing application with:

```bash
mvn -pl desktop-client exec:java
```

On a fresh clone, run `mvn install -DskipTests` first so `game-core` is available
to the other modules.

The bundled background music starts automatically. Open Settings to adjust its
volume, mute it, or pause it.

Completed games are submitted asynchronously to `http://localhost:8080` by
default. If the server is unavailable, the result is queued in
`pending-game-results.jsonl` and uploaded automatically the next time a
submission succeeds. Point the desktop app at another server with the
`guesswho.server.url` system property:

```bash
mvn -pl desktop-client exec:java -Dexec.args="" \
  -Dguesswho.server.url=https://games.example
```

## Build the Installers

Building an installer requires the system it targets: `jpackage` produces only
the native format of the machine it runs on.

```bash
./packaging/build-installer.sh
```

That writes a `.dmg` on macOS or an `.msi` on Windows into `target/installer`.
The bundled Java runtime is trimmed to the modules the application actually
reaches, which `jdeps` works out during the build rather than a list in the
script going stale.

The same script runs in CI on tagged releases; see
[.github/workflows/installers.yml](.github/workflows/installers.yml). Icons come
from [packaging/](packaging/README.md).

## Play Online

Online games need the server running and both players signed in — an online
game has to know who is on each side, to attribute the result and to stop a
stranger acting as either player. Local play stays open to guests.

Start the server, then a client on each machine. One player chooses **play
online against a friend**, then **Start a game and get a code**; the other
chooses **Join with a code** and types it in. The code can be typed in any case
and with a space in the middle — it is read off one screen and typed into
another, so the server tidies it.

Both clients must point at the same server. Anywhere other than the machine
running it, set:

```bash
mvn -pl desktop-client exec:java -Dexec.args="" \
    -Dguesswho.server.url=http://the-servers-address:8080
```

Rooms expire: ten minutes if nobody joins, thirty idle, and twenty-four hours
regardless.

## Run the Server

Start the Spring Boot server during development with:

```bash
mvn -pl server spring-boot:run
```

Alternatively, run the executable JAR after building:

```bash
java -jar server/target/server-1.0.0.jar
```

Rate limits are on by default: signing in and registering are held per address,
opening rooms and moving per account. Reading the game is deliberately not
limited, because presence is measured by requests and throttling a poll would
make a player look absent and eventually forfeit their game. Turn the limits off
for local experimentation with:

```bash
java -jar server/target/server-1.0.0.jar --guesswho.rate-limits.enabled=false
```

The server listens on port `8080` by default. Verify it from another terminal:

```bash
curl http://localhost:8080/api/status
```

The response is `{"status":"online"}`. The server is available only on the local machine until it is deployed to a host.

The server stores submitted games in the file-backed H2 database
`guess-who-data.mv.db`. Flyway applies the migrations under
`src/main/resources/db/migration` at startup, and the data remains available
after the server restarts. A database created before Flyway was adopted is
baselined rather than rejected.

### Submit a Game Result

Submit a completed game to `POST /api/game-results`:

```bash
curl -X POST http://localhost:8080/api/game-results \
  -H "Content-Type: application/json" \
  -d '{
    "participants": [
      {
        "name": "Player 1",
        "selectedCharacter": "Olivia",
        "questionAnswers": [
          {"question": "Does your character wear glasses?", "answer": true}
        ]
      },
      {
        "name": "Player 2",
        "selectedCharacter": "Nick",
        "questionAnswers": []
      }
    ],
    "winner": "Player 1",
    "mode": "PVP_LOCAL",
    "questionMode": "PRESET"
  }'
```

A valid result returns HTTP `201 Created` and is stored transactionally in the
H2 database. The winner must match a participant; names, selected characters,
and questions cannot be blank; and both `mode` and `questionMode` are
required. `mode` is `PVE`, `PVP_LOCAL`, or `PVP_ONLINE`, and `questionMode` is
`PRESET` or `FREE_FORM`. Add `difficulty` (`EASY` or `HARD`) for games against
the computer. Database connection settings can be
overridden with standard `spring.datasource.*` Spring Boot properties.

### View Game Result History

Retrieve stored games from newest to oldest with `GET /api/game-results`. The
response is paginated: `limit` defaults to 50 and caps at 200, and `offset`
skips whole games.

```bash
curl http://localhost:8080/api/game-results
```

Each result includes its database ID, creation time, winner, participants, and
question histories:

```json
[
    {
        "id": 1,
        "createdAt": "2026-08-28T15:30:00",
        "participants": [
            {
                "name": "Player 1",
                "selectedCharacter": "Olivia",
                "questionAnswers": [
                    {
                        "question": "Does your character wear glasses?",
                        "answer": true
                    }
                ],
                "commitment": {
                    "hash": "9f2c…",
                    "nonce": "4a1b…"
                }
            }
        ],
        "winner": "Player 1",
        "mode": "PVP_LOCAL",
        "difficulty": null,
        "questionMode": "PRESET"
    }
]
```

A participant's `commitment` is the promise they made about their character
before play began: `SHA-256` of the character name and a random nonce.
Recomputing it from the revealed `selectedCharacter` and the `nonce` shows the
character was not swapped once the questions started.

It is absent for the computer opponent, which makes no promise, and for a player
who chose to keep their character to themselves and name it at the end. In that
case the stored answers are only known to be consistent with the character
named, not fixed in advance.

A modified client can still commit to one character and answer as though it held
another — the answering client is the only thing that knows, so nothing on the
wire can prevent it. What happens instead is that the lie shows up: at the end of
an online game every answer is checked against the character committed to, and
answers that character contradicts are listed for both players. A cheat can win
the game; they cannot win it unnoticed. Only the questions actually asked can
catch anything, so a lie nobody probed leaves no trace.

When no results have been stored, the endpoint returns an empty JSON array.

Request a specific page with `limit` and `offset`:

```bash
curl "http://localhost:8080/api/game-results?limit=10&offset=10"
```

### View the Leaderboard

Retrieve standings calculated from saved games with `GET /api/leaderboard`:

```bash
curl http://localhost:8080/api/leaderboard
```

The response is ordered by wins from highest to lowest, then by participant
name when wins are tied:

```json
[
    {
        "name": "Player 1",
        "gamesPlayed": 3,
        "wins": 2
    },
    {
        "name": "AI",
        "gamesPlayed": 3,
        "wins": 1
    }
]
```

Standings include every participant name stored by the server, including the
AI. When no results have been stored, the endpoint returns an empty JSON array.

Restrict standings to one game mode with `mode`, and bound the response with
`limit`, which defaults to 100 and caps at 500:

```bash
curl "http://localhost:8080/api/leaderboard?mode=PVE&limit=10"
```

Standings are never combined across modes in the desktop client, because beating
the computer and beating another player are not comparable results.

The desktop app's **Leaderboard** button opens the same standings in a separate
window without blocking the game. The window shows loading, empty, and
server-unavailable states, and its **Refresh** button retries the request.

## Test

Run the JUnit suite with:

```bash
mvn test
```

The tests cover packaged resources, board data, starting-turn rules, core
computer-player behavior, HTTP result submission and history, leaderboard
aggregation, normalized database storage, and transactional rollback.

## Main Classes

| Class                         | Responsibility                                                                         |
| ----------------------------- | -------------------------------------------------------------------------------------- |
| `GuessWhoServerApplication`   | Starts the Spring Boot HTTP server.                                                    |
| `StatusController`            | Reports whether the server is online through `/api/status`.                            |
| `GameResultController`        | Accepts completed games and returns saved history through `/api/game-results`.         |
| `LeaderboardController`       | Returns standings calculated from saved games through `/api/leaderboard`.              |
| `HttpGameResultClient`        | Submits completed games to the configured server without blocking Swing.               |
| `HttpLeaderboardClient`       | Retrieves leaderboard standings without blocking Swing.                                |
| `GameResultSubmissionService` | Queues results while the server is unreachable and uploads them on the next success.   |
| `LeaderboardPanel`            | Displays remote standings and handles loading, empty, error, and retry states.         |
| `GUI`                         | Builds the Swing interface, handles user interaction, and starts the application.      |
| `Game`                        | Coordinates game modes, turns, questions, guesses, and results.                        |
| `GameResult`                  | Provides an immutable completed-game snapshot for external consumers.                  |
| `Board`                       | Loads the character/question databases and builds the answer matrix.                   |
| `GameResources`               | Loads packaged CSV files and images and treats background music as optional.           |
| `Player`                      | Stores behavior and state shared by human and computer players.                        |
| `ComputerPlayer`              | Selects questions and narrows possible characters for the AI.                          |
| `User`                        | Stores a human player's username and birthday.                                         |
| `Character`                   | Represents a character and their visual attributes.                                    |
| `Question`                    | Represents a yes-or-no character question.                                             |
| `JdbcGameResultRepository`    | Stores and reconstructs game results from relational tables.                           |
| `JdbcLeaderboardRepository`   | Aggregates games played and wins from relational tables.                               |
| `FilePendingGameResultStore`  | Queues results locally while the server is unreachable, so they can be uploaded later. |
| `RoomService`                 | Opens and joins online rooms, and applies every move through the rules.                |
| `RoomProjection`              | Turns a stored game into what one player is allowed to see of it.                      |
| `RoomState`                   | That projection. It has no field that could hold the opponent's character.             |
| `SessionService`              | Issues and resolves bearer tokens, storing only their hashes.                          |
| `OnlineGameController`        | Holds the room, the poll and the last state an online game was in.                     |
| `RoomPoller`                  | Asks the server what has happened, and delivers it on the Swing thread.                |

## Current Limitations

- Background music is generated by `tools/BackgroundTrack.java` rather than
  recorded, so there is no track to license. It is a short loop, and
  deliberately plain.
- Online play needs the server running and both players signed in. It is not
  deployed anywhere, so today that means one machine on your network running
  the server. See [docs/ROADMAP.md](docs/ROADMAP.md).
- Online play has not been tried by two people on two machines. Every layer has
  tests and the whole chain runs end to end against a real server in
  `LiveOnlineGameTest`, but nobody has yet sat down at two computers and played
  a game with a friend — and no connection has actually been dropped to watch
  the reconnecting banner appear or the turn timer decide anything.
- Neither installer is code-signed, so both platforms warn the first time. See
  [Install](#install) for the one extra step each needs.

## What Is Next

[docs/ROADMAP.md](docs/ROADMAP.md) is the plan of record: fourteen phases across
three releases, with each one marked as it lands.

## Data and Assets

- `GuessWhoDB.csv` defines the 24 characters and their attributes.
- `QuestionDB.csv` defines the preset yes-or-no questions.
- `server/src/main/resources/db/migration` holds the Flyway migrations that
  build the game-result tables.
- Character artwork is stored under `game-core/src/main/resources/images`, named
  by board position rather than by character, so the pictures can be swapped
  without touching any code. `tools/` holds the prompts they were generated
  from and the tool that adds the name bands; see
  [tools/portraits/README.md](tools/portraits/README.md) to rebuild them.
- Background music lives in `game-core/src/main/resources/audio` and is written
  by `tools/BackgroundTrack.java`.

## License

The source code in this repository is released under the MIT License. See
[LICENSE](LICENSE) for the full text.

The character artwork is original to this project. It was generated from the
prompts in [tools/character-prompts.md](tools/character-prompts.md), which are
themselves written from `GuessWhoDB.csv`, and no part of it comes from the
printed board game. Note that the legal status of machine-generated images is
unsettled in several jurisdictions, and they may not attract copyright at all.

"Guess Who?" is a trademark of Hasbro. This is an unaffiliated personal exercise,
not endorsed by or associated with the trademark holder.
