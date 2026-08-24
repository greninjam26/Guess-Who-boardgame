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
    └── main/
        ├── java/                  # Application source code
        │   ├── GUI.java           # Swing interface and entry point
        │   ├── Game.java          # Game flow and mode coordination
        │   ├── Board.java         # Character/question data and answers
        │   ├── Player.java        # Shared player state and behavior
        │   ├── User.java          # Human-player information
        │   ├── ComputerPlayer.java # Computer opponent logic
        │   ├── Character.java     # Character model
        │   ├── Question.java      # Question model
        │   ├── StoreResult.java   # Result persistence
        │   └── Leaderboard.java   # Leaderboard data and sorting
        └── resources/
            ├── audio/             # Background music
            ├── data/              # Character and question CSV files
            └── images/            # Character-card artwork
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

## Main Classes

| Class | Responsibility |
| --- | --- |
| `GUI` | Builds the Swing interface, handles user interaction, and starts the application. |
| `Game` | Coordinates game modes, turns, questions, guesses, and results. |
| `Board` | Loads the character/question databases and builds the answer matrix. |
| `Player` | Stores behavior and state shared by human and computer players. |
| `ComputerPlayer` | Selects questions and narrows possible characters for the AI. |
| `User` | Stores a human player's username and birthday. |
| `Character` | Represents a character and their visual attributes. |
| `Question` | Represents a yes-or-no character question. |
| `StoreResult` | Writes completed game information to a CSV file. |
| `Leaderboard` | Loads, updates, and sorts leaderboard entries. |

## Current Limitations

This repository has been reorganized before changing the application code. The source compiles, but the GUI is not yet launch-ready from the Maven layout:

- The Java classes still use their original resource paths instead of loading files from the new resource folders.
- `Bloom of Youth.wav` contains no audio data. Startup currently attempts to use the audio clip even when loading it fails.
- There is no automated test suite yet.
- The classes still use the default Java package.

These constraints are documented rather than hidden so the next improvement can address resource loading without mixing that behavioral change into the structural cleanup.

## Suggested Next Steps

1. Load CSV, image, and audio resources consistently from the classpath.
2. Replace the empty music asset or make background music optional.
3. Add unit tests for board loading, question matching, turn selection, and AI behavior.
4. Introduce a named Java package.
5. Split the large Swing class into smaller view and controller components.

## Data and Assets

- `GuessWhoDB.csv` defines the 24 characters and their attributes.
- `QuestionDB.csv` defines the preset yes-or-no questions.
- Character artwork is stored under `src/main/resources/images`.
- Audio is stored under `src/main/resources/audio`.

Ensure that any redistributed images or audio are used in accordance with their original licenses.
