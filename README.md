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
- Offline CSV fallback when the game-result server is unavailable

## Technology

- Java 17
- Java Swing and AWT
- Spring Boot 4.1.1 and Spring MVC
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
    │   │   ├── client/             # Desktop HTTP client and offline fallback flow
    │   │   ├── game/               # Game flow, models, and resources
    │   │   ├── persistence/        # CSV result and leaderboard storage
    │   │   ├── ui/                 # Swing interface and entry point
    │   │   └── web/                # HTTP controllers and responses
    │   └── resources/
    │       ├── audio/               # Background music
    │       ├── data/                # Character and question CSV files
    │       └── images/              # Character-card artwork
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
default. If the server is unavailable or rejects the request, the result is
stored locally in `test.csv`. Point the desktop app at another server with the
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

A valid result returns HTTP `201 Created` and is appended to `test.csv` by
default. The winner must match a participant, and names, selected characters,
and questions cannot be blank. Override the result file when starting the
server if needed:

```bash
java -jar target/guess-who-boardgame-1.0-SNAPSHOT.jar \
  --guesswho.results.file=game-results.csv
```

## Test

Run the JUnit suite with:

```bash
mvn test
```

The tests cover packaged resources, board data, starting-turn rules, and core computer-player behavior.

## Main Classes

| Class | Responsibility |
| --- | --- |
| `GuessWhoServerApplication` | Starts the Spring Boot HTTP server. |
| `StatusController` | Reports whether the server is online through `/api/status`. |
| `GameResultController` | Validates and accepts completed games through `POST /api/game-results`. |
| `HttpGameResultClient` | Submits completed games to the configured server without blocking Swing. |
| `GameResultSubmissionService` | Falls back to local persistence when server submission fails. |
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
| `CsvGameResultRepository` | Persists server-submitted game results to a configurable CSV file. |
| `StoreResult` | Writes completed game information to a CSV file. |
| `Leaderboard` | Loads, updates, and sorts leaderboard entries. |

## Current Limitations

The application now loads its CSV and image assets from the Maven classpath. The remaining limitations are:

- `Bloom of Youth.wav` contains no audio data, so background music is disabled automatically.
- Swing interactions and complete game sessions are not yet covered by automated tests.

## Suggested Next Steps

1. Replace the empty music asset.
2. Expand tests for question elimination, guessing, and result storage.
3. Improve input validation and naming consistency.
4. Split the large Swing class into smaller view and controller modules.
5. Replace CSV result persistence with a database-backed implementation.

## Data and Assets

- `GuessWhoDB.csv` defines the 24 characters and their attributes.
- `QuestionDB.csv` defines the preset yes-or-no questions.
- Character artwork is stored under `src/main/resources/images`.
- Audio is stored under `src/main/resources/audio`.

Ensure that any redistributed images or audio are used in accordance with their original licenses.
