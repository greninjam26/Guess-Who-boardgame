# Guess Who Board Game

A desktop adaptation of the classic Guess Who board game, written in Java with a Swing user interface. Players narrow down a board of 24 characters by asking yes-or-no questions and making a final guess.

## Features

- Player-versus-computer games with easy and hard AI modes
- Local player-versus-player games
- Preset-question and free-question game modes
- Interactive character boards for tracking eliminated characters
- Character and question data loaded from CSV files
- Game-result recording and leaderboard foundations
- HTTP submission of completed game results
- Paginated HTTP history of completed games
- Database-backed leaderboard standings
- Leaderboard window available from the Swing application
- Completed games queued locally and uploaded once the server is reachable again

## Technology

- Java 17
- Java Swing and AWT
- Spring Boot 4.1.1 and Spring MVC
- Spring JDBC and H2
- Maven
- CSV-based game data

## Project Structure

```text
.
├── pom.xml
├── README.md
└── src/
    ├── main/
    │   ├── java/com/guesswho/
    │   │   ├── GuessWhoServerApplication.java # Spring Boot entry point
    │   │   ├── client/             # Desktop HTTP clients and the pending-upload queue
    │   │   ├── game/               # Game flow, models, and resources
    │   │   ├── persistence/        # Database persistence
    │   │   ├── ui/                 # Swing interface and entry point
    │   │   └── web/                # HTTP controllers and responses
    │   └── resources/
    │       ├── application.properties # Server database configuration
    │       ├── audio/               # Background music
    │       ├── data/                # Character and question CSV files
    │       ├── images/              # Character-card artwork
    │       └── schema.sql           # Game-result database schema
    └── test/
        └── java/                    # Regression checks
```

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

After building, start the Swing application with:

```bash
java -cp target/classes com.guesswho.ui.GUI
```

The bundled music file currently contains no audio data, so the game starts without background music.

Completed games are submitted asynchronously to `http://localhost:8080` by
default. If the server is unavailable, the result is queued in
`pending-game-results.jsonl` and uploaded automatically the next time a
submission succeeds. Point the desktop app at another server with the
`guesswho.server.url` system property:

```bash
java -Dguesswho.server.url=https://games.example \
  -cp target/classes com.guesswho.ui.GUI
```

## Run the Server

Start the Spring Boot server during development with:

```bash
mvn spring-boot:run
```

Alternatively, run the executable JAR after building:

```bash
java -jar target/guess-who-boardgame-1.0-SNAPSHOT.jar
```

The server listens on port `8080` by default. Verify it from another terminal:

```bash
curl http://localhost:8080/api/status
```

The response is `{"status":"online"}`. The server is available only on the local machine until it is deployed to a host.

The server stores submitted games in the file-backed H2 database
`guess-who-data.mv.db`. The schema is created automatically at startup, and
the data remains available after the server restarts.

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
    "winner": "Player 1"
  }'
```

A valid result returns HTTP `201 Created` and is stored transactionally in the
H2 database. The winner must match a participant, and names, selected
characters, and questions cannot be blank. Database connection settings can be
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
        ]
      }
    ],
    "winner": "Player 1"
  }
]
```

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

| Class | Responsibility |
| --- | --- |
| `GuessWhoServerApplication` | Starts the Spring Boot HTTP server. |
| `StatusController` | Reports whether the server is online through `/api/status`. |
| `GameResultController` | Accepts completed games and returns saved history through `/api/game-results`. |
| `LeaderboardController` | Returns standings calculated from saved games through `/api/leaderboard`. |
| `HttpGameResultClient` | Submits completed games to the configured server without blocking Swing. |
| `HttpLeaderboardClient` | Retrieves leaderboard standings without blocking Swing. |
| `GameResultSubmissionService` | Queues results while the server is unreachable and uploads them on the next success. |
| `LeaderboardPanel` | Displays remote standings and handles loading, empty, error, and retry states. |
| `GUI` | Builds the Swing interface, handles user interaction, and starts the application. |
| `Game` | Coordinates game modes, turns, questions, guesses, and results. |
| `GameResult` | Provides an immutable completed-game snapshot for external consumers. |
| `Board` | Loads the character/question databases and builds the answer matrix. |
| `GameResources` | Loads packaged CSV files and images and treats background music as optional. |
| `Player` | Stores behavior and state shared by human and computer players. |
| `ComputerPlayer` | Selects questions and narrows possible characters for the AI. |
| `User` | Stores a human player's username and birthday. |
| `Character` | Represents a character and their visual attributes. |
| `Question` | Represents a yes-or-no character question. |
| `JdbcGameResultRepository` | Stores and reconstructs game results from relational tables. |
| `JdbcLeaderboardRepository` | Aggregates games played and wins from relational tables. |
| `FilePendingGameResultStore` | Queues results locally while the server is unreachable, so they can be uploaded later. |

## Current Limitations

The application now loads its CSV and image assets from the Maven classpath. The remaining limitations are:

- `Bloom of Youth.wav` contains no audio data, so background music is disabled automatically.
- Swing interactions and complete game sessions are not yet covered by automated tests.

## Suggested Next Steps

1. Replace the empty music asset.
2. Expand tests for question elimination, guessing, and result storage.
3. Improve input validation and naming consistency.
4. Split the large Swing class into smaller view and controller modules.
5. Add PostgreSQL configuration for production deployment.

## Data and Assets

- `GuessWhoDB.csv` defines the 24 characters and their attributes.
- `QuestionDB.csv` defines the preset yes-or-no questions.
- `schema.sql` defines the game-result database tables and relationships.
- Character artwork is stored under `src/main/resources/images`.
- Audio is stored under `src/main/resources/audio`.

## License

The source code in this repository is released under the MIT License. See
[LICENSE](LICENSE) for the full text.

The MIT grant covers the source code only. The character artwork under
`src/main/resources/images` and the audio under `src/main/resources/audio` are
not covered by it and remain the property of their respective owners. Anyone
redistributing this project, or building installers from it, is responsible for
ensuring those assets are used in accordance with their original licenses.

"Guess Who?" is a trademark of Hasbro. This project is an unaffiliated personal
exercise and is not endorsed by or associated with the trademark holder.
