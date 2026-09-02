#!/usr/bin/env bash
#
# Builds a desktop installer for whichever system this is running on.
#
# jpackage cannot cross-compile: a .dmg can only be built on macOS and an .msi
# only on Windows, which is why release builds need a runner of each rather
# than one Linux machine.
#
# Run from the repository root:
#
#     ./packaging/build-installer.sh
#
set -euo pipefail

# macOS ships bash 3.2, where an empty array counts as unset under `set -u`,
# so the platform options below are expanded with a guard rather than plainly.

cd "$(dirname "$0")/.."

NAME="Guess Who"
MAIN_CLASS="com.guesswho.ui.GUI"
VENDOR="greninjam26"
DESCRIPTION="The Guess Who board game, for one or two players"

# Where the installed game looks for the server.
#
# Unset means localhost, which is what a developer building on their own machine
# wants and what every run before this produced. Release builds set it, so a
# downloaded game talks to the deployed server while nothing about running from
# an IDE or a test changes.
#
# Baked in with jpackage rather than read from a file the player could edit:
# there is nothing here worth configuring, and a game that silently talked to
# somewhere else because a file was edited would be a worse problem than one
# that cannot be repointed.
SERVER_OPTIONS=()
SERVER_URL="${GUESSWHO_SERVER_URL:-}"
if [ -n "$SERVER_URL" ]; then
    case "$SERVER_URL" in
        https://*) ;;
        *)
            # Refused rather than warned about. Accounts are created and signed
            # into over this connection, so a plain-http installer would send
            # passwords in clear to everybody between the player and the server
            # — and it would work, which is what makes it dangerous.
            echo "GUESSWHO_SERVER_URL must be an https:// URL, got: $SERVER_URL" >&2
            exit 1
            ;;
    esac
    case "$SERVER_URL" in
        */)
            # The client joins paths onto this, so a trailing slash produces
            # //api/rooms. Some servers forgive that and this one need not.
            echo "GUESSWHO_SERVER_URL must not end with a slash, got: $SERVER_URL" >&2
            exit 1
            ;;
    esac
    echo "Installers will connect to $SERVER_URL"
    SERVER_OPTIONS=(--java-options "-Dguesswho.server.url=$SERVER_URL")
else
    echo "GUESSWHO_SERVER_URL is not set; this installer will use localhost."
fi

VERSION="$(mvn -q -Dexec.executable=echo -Dexec.args='${project.version}' \
    --non-recursive exec:exec 2>/dev/null | tail -1 | tr -d '[:space:]')"
if [[ -z "$VERSION" ]]; then
    echo "Could not read the project version from Maven." >&2
    exit 1
fi
# jpackage refuses a version whose first number is zero, on macOS in particular.
if [[ "$VERSION" == 0.* ]]; then
    echo "jpackage will not accept version $VERSION: the first number cannot be zero." >&2
    exit 1
fi
# A -SNAPSHOT suffix is not a number and jpackage rejects it too.
VERSION="${VERSION%%-*}"

OUT="target/installer"
INPUT="target/installer-input"

echo "Building $NAME $VERSION"

mvn -q clean install -DskipTests

# jpackage takes one directory and starts one jar out of it, so the application
# jar and everything it depends on are gathered together first.
rm -rf "$INPUT" "$OUT"
mkdir -p "$INPUT" "$OUT"
cp desktop-client/target/desktop-client-*.jar "$INPUT/"
cp desktop-client/target/lib/*.jar "$INPUT/"
MAIN_JAR="$(basename desktop-client/target/desktop-client-*.jar)"

# Ask jdeps which parts of Java the application actually reaches, rather than
# carrying a hand-written list that quietly goes stale when a dependency
# changes. A runtime missing a module fails when somebody runs the game, not
# when the installer is built, so the list is worth deriving.
MODULES="$(jdeps --print-module-deps --ignore-missing-deps --multi-release 17 \
    --class-path "$INPUT/*" "$INPUT/$MAIN_JAR")"
# Not reachable by inspection: TLS picks its cipher suite at runtime, and the
# leaderboard cannot be reached over HTTPS without this one.
MODULES="$MODULES,jdk.crypto.ec"
echo "Bundling: $MODULES"

case "$(uname -s)" in
    Darwin)
        TYPE="dmg"
        ICON="packaging/GuessWho.icns"
        EXTRA=()
        ;;
    MINGW* | MSYS* | CYGWIN* | Windows_NT)
        TYPE="msi"
        ICON="packaging/GuessWho.ico"
        # Without these the installer offers no choice of location and leaves
        # nothing to launch the game from.
        EXTRA=(--win-dir-chooser --win-menu --win-shortcut)
        ;;
    *)
        echo "No installer is built for $(uname -s); jpackage only produces" >&2
        echo "the native format of the system it runs on." >&2
        exit 1
        ;;
esac

jpackage \
    --type "$TYPE" \
    --name "$NAME" \
    --app-version "$VERSION" \
    --vendor "$VENDOR" \
    --description "$DESCRIPTION" \
    --icon "$ICON" \
    --input "$INPUT" \
    --main-jar "$MAIN_JAR" \
    --main-class "$MAIN_CLASS" \
    --add-modules "$MODULES" \
    --dest "$OUT" \
    ${SERVER_OPTIONS[@]+"${SERVER_OPTIONS[@]}"} \
    ${EXTRA[@]+"${EXTRA[@]}"}

echo
echo "Built:"
ls -la "$OUT"
