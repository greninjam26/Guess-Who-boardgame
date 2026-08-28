# Guess Who Board Game

A desktop adaptation of the classic Guess Who board game, written in Java with a Swing user interface. Players narrow down a board of 24 characters by asking yes-or-no questions and making a final guess.

## Features

- Player-versus-computer games with easy and hard AI modes
- Local player-versus-player games
- Preset-question and free-question game modes
- Interactive character boards for tracking eliminated characters
- Character and question data loaded from CSV files
- Game-result recording and leaderboard foundations

## Technology

- Java 17
- Java Swing and AWT
- Maven
- CSV-based game data

The project has no external runtime dependencies.

## Project Structure

```text
.
├── pom.xml
├── README.md
└── src/
    ├── main/
    │   ├── java/                   # Application source code
    │   │   ├── GUI.java            # Swing interface and entry point
    │   │   ├── Game.java           # Game flow and mode coordination
    │   │   ├── GameResult.java     # Immutable completed-game snapshot
    │   │   ├── Board.java          # Character/question data and answers
    │   │   ├── GameResources.java  # Classpath resource loading
    │   │   ├── Player.java         # Shared player state and behavior
    │   │   ├── User.java           # Human-player information
    │   │   ├── ComputerPlayer.java # Computer opponent logic
    │   │   ├── Character.java      # Character model
    │   │   ├── Question.java       # Question model
    │   │   ├── StoreResult.java    # Result persistence
    │   │   └── Leaderboard.java    # Leaderboard data and sorting
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

## Run

After building, start the Swing application with:

```bash
java -cp target/classes GUI
```

The bundled music file currently contains no audio data, so the game starts without background music.

## Test

Run the JUnit suite with:

```bash
mvn test
```

The tests cover packaged resources, board data, starting-turn rules, and core computer-player behavior.

## Main Classes

| Class | Responsibility |
| --- | --- |
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
| `StoreResult` | Writes completed game information to a CSV file. |
| `Leaderboard` | Loads, updates, and sorts leaderboard entries. |

## Current Limitations

The application now loads its CSV and image assets from the Maven classpath. The remaining limitations are:

- `Bloom of Youth.wav` contains no audio data, so background music is disabled automatically.
- Swing interactions and complete game sessions are not yet covered by automated tests.
- The classes still use the default Java package.

## Suggested Next Steps

1. Replace the empty music asset.
2. Expand tests for question elimination, guessing, and result storage.
3. Introduce a named Java package.
4. Improve input validation and naming consistency.
5. Split the large Swing class into smaller view and controller modules.

## Data and Assets

- `GuessWhoDB.csv` defines the 24 characters and their attributes.
- `QuestionDB.csv` defines the preset yes-or-no questions.
- Character artwork is stored under `src/main/resources/images`.
- Audio is stored under `src/main/resources/audio`.

Ensure that any redistributed images or audio are used in accordance with their original licenses.
