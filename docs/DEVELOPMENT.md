# Guess Who — Development Guide

This guide contains the detailed build, runtime, packaging, and API reference.
For the project overview and installation instructions, start with the
[README](../README.md).

## Prerequisites

- [JDK 17 or newer](https://adoptium.net/)
- [Apache Maven](https://maven.apache.org/)

Verify the tools:

```bash
java -version
mvn -version
```

## Build and Test

From the repository root:

```bash
mvn clean package
mvn test
```

Maven builds all three modules and writes their output under each module's
`target/` directory.

## Run the Desktop Client

Install the shared `game-core` module, then start Swing:

```bash
mvn install -DskipTests
mvn -pl desktop-client exec:java
```

The client submits completed games asynchronously to `http://localhost:8080` by
default. If the server is unavailable, results remain in a local pending queue
and are retried after a later successful submission.

Point the client at another server with a JVM property:

```bash
mvn -pl desktop-client exec:java -Dexec.args="" \
  -Dguesswho.server.url=https://games.example
```

## Run the Server

Start Spring Boot through Maven:

```bash
mvn -pl server spring-boot:run
```

Or run the packaged JAR:

```bash
java -jar server/target/server-2.0.0.jar
```

The server listens on port `8080`. Its health endpoint checks the database as
well as the process:

```bash
curl http://localhost:8080/api/status
```

A healthy response is:

```json
{"status":"online"}
```

Development uses a file-backed H2 database named `guess-who-data.mv.db`.
Flyway applies the migrations under `server/src/main/resources/db/migration`.
Standard `spring.datasource.*` properties override the connection.

Rate limits are enabled by default. Disable them for local experiments only:

```bash
java -jar server/target/server-2.0.0.jar \
  --guesswho.rate-limits.enabled=false
```

## Build Native Installers

`jpackage` produces only the native format of the host system:

```bash
./packaging/build-installer.sh
```

The result is written to `target/installer` as a `.dmg` on macOS or `.msi` on
Windows. The Java runtime is bundled and trimmed to the modules discovered by
`jdeps`.

Local installers use `http://localhost:8080`. A distributable installer must
receive the public HTTPS origin:

```bash
GUESSWHO_SERVER_URL=https://your-host.duckdns.org \
  ./packaging/build-installer.sh
```

The value must be a bare HTTPS origin with no credentials, path, query,
fragment, whitespace, or trailing slash. Tagged GitHub Actions builds fail when
the repository variable is missing. See [packaging/README.md](../packaging/README.md)
for the icon and packaging details.

## Project Structure

```text
.
├── pom.xml                          # parent Maven reactor
├── game-core/                       # rules, data, and artwork
│   └── src/main/
│       ├── java/com/guesswho/
│       │   ├── game/                # game flow, models, and resources
│       │   └── leaderboard/         # standings shared by client and server
│       └── resources/
│           ├── audio/               # generated background music
│           ├── data/                # character and question CSV files
│           └── images/              # character-card artwork
├── desktop-client/                  # Swing application
│   └── src/main/java/com/guesswho/
│       ├── client/                  # HTTP clients and local pending queue
│       └── ui/                      # screens, controllers, and entry point
├── server/                          # Spring Boot HTTP API
│   └── src/main/
│       ├── java/com/guesswho/
│       │   ├── persistence/         # JDBC repositories
│       │   └── web/                 # controllers, rooms, and services
│       └── resources/db/migration/  # Flyway migrations
├── deploy/aws/                      # host bootstrap and operations
├── packaging/                       # jpackage resources and scripts
└── docs/                            # architecture, roadmap, and guides
```

## API Reference

### Submit a Game Result

Send a completed game to `POST /api/game-results`:

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

A valid result returns `201 Created`. The winner must be a participant; names,
characters, and questions cannot be blank. `mode` is `PVE`, `PVP_LOCAL`, or
`PVP_ONLINE`; `questionMode` is `PRESET` or `FREE_FORM`. PVE results also carry
`difficulty` as `EASY` or `HARD`.

### View Game History

Results are returned newest first. `limit` defaults to 50 and caps at 200;
`offset` skips whole games.

```bash
curl "http://localhost:8080/api/game-results?limit=10&offset=10"
```

Each participant may include a commitment consisting of a SHA-256 hash and
nonce. Recomputing it from the revealed character shows that the player did not
switch characters after questions began. At the end of an online game, recorded
answers are also checked against the committed character and contradictions are
shown to both players.

The commitment cannot prevent a modified client from lying about an answer. It
makes lies detectable when a recorded answer contradicts the final reveal.

### View the Leaderboard

```bash
curl "http://localhost:8080/api/leaderboard?mode=PVE&limit=10"
```

Results are ordered by wins descending, then participant name. `mode` separates
`PVE`, `PVP_LOCAL`, and `PVP_ONLINE`; `limit` defaults to 100 and caps at 500.
The desktop client deliberately keeps the three modes separate.

### Play Online During Development

Both clients must point at the same server and both players must be signed in.
One player opens a room; the other joins its six-character code. Codes are
normalized for case and an optional space.

Rooms expire after ten minutes without a join, thirty minutes idle, or
twenty-four hours total.

## Main Classes

| Class | Responsibility |
| ----- | -------------- |
| `Game` | Coordinates modes, turns, questions, guesses, and results |
| `Board` | Loads character/question data and builds the answer matrix |
| `ComputerPlayer` | Selects questions and narrows AI candidates |
| `GameResources` | Loads packaged CSV, artwork, and optional music |
| `GUI` | Builds the Swing application and manages navigation |
| `OnlineGameController` | Owns an online room, polling, and its last state |
| `RoomPoller` | Polls without blocking Swing and reports connection changes |
| `GameResultSubmissionService` | Submits results and manages the pending queue |
| `GuessWhoServerApplication` | Starts the Spring Boot server |
| `RoomService` | Creates rooms and applies every online move through the rules |
| `RoomProjection` | Produces the state one specific player is allowed to see |
| `GameResultController` | Accepts results and returns paginated history |
| `LeaderboardController` | Returns mode-specific standings |
| `SessionService` | Issues bearer tokens and stores only token hashes |
| `JdbcGameResultRepository` | Stores and reconstructs normalized results |
| `JdbcLeaderboardRepository` | Aggregates games played and wins |

## Data and Generated Assets

- `game-core/src/main/resources/data/GuessWhoDB.csv` defines the characters.
- `game-core/src/main/resources/data/QuestionDB.csv` defines preset questions.
- Character cards live under `game-core/src/main/resources/images` and are named
  by board position.
- Portrait prompts and rebuilding instructions live under `tools/portraits`.
- `tools/BackgroundTrack.java` generates the packaged music loop.
- `tools/ApplicationIcon.java` generates the installer icon sources.

## Further Reading

- [Architecture](ARCHITECTURE.md)
- [Roadmap](ROADMAP.md)
- [AWS deployment](../deploy/aws/README.md)
- [Installer packaging](../packaging/README.md)
