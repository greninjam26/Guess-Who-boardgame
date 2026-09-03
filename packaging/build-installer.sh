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
    # Every check below refuses rather than warns, and refuses before the build
    # rather than after it. This value is written into the installer with
    # --java-options and cannot be corrected afterwards: a wrong one is not a
    # failed build, it is a download that talks to the wrong place on somebody
    # else's machine. Nothing downstream re-checks it — the client takes the
    # property as given and joins "/api/..." onto it.
    #
    # The refusal names the rule and never quotes the value back. A rejected
    # endpoint can carry a password in its userinfo, and this output goes to a
    # CI log that outlives the build and is readable by anybody who can see the
    # repository. Whoever set the variable can already see what they set.
    refuse() {
        echo "GUESSWHO_SERVER_URL $1" >&2
        echo "(the value is not shown here: a rejected endpoint can carry credentials)" >&2
        exit 1
    }

    # Checked first, because none of the checks after it can see whitespace for
    # what it is — a leading space would shift the scheme out of position and be
    # reported as the wrong problem. A copy-paste out of a runbook or a browser
    # brings a space or a newline with it, a repository variable keeps it, and
    # the Actions UI displays the value with the whitespace invisible.
    case "$SERVER_URL" in
        *[[:space:]]*) refuse "must not contain whitespace" ;;
    esac

    # Refused rather than warned about. Accounts are created and signed into
    # over this connection, so a plain-http installer would send passwords in
    # clear to everybody between the player and the server — and it would work,
    # which is what makes it dangerous.
    #
    # The scheme is compared case-insensitively because RFC 3986 says schemes
    # are, and java.net.URI agrees; the host and everything after it are left
    # exactly as given.
    scheme="$(printf '%s' "${SERVER_URL:0:8}" | tr 'A-Z' 'a-z')"
    if [ "$scheme" != "https://" ]; then
        refuse "must use the https scheme"
    fi
    origin="${SERVER_URL:8}"

    # Nothing after the host survives being joined to. The client builds
    # "$url/api/rooms", so a trailing slash produces //api/rooms and a path,
    # query or fragment produces a URL no route on the server matches. Both fail
    # only on an installed game, and both look like the server being down.
    case "$origin" in
        */)  refuse "must not end with a slash" ;;
        */*) refuse "must be a bare origin, with no path" ;;
    esac
    case "$origin" in
        *"?"*) refuse "must not contain a query string" ;;
        *"#"*) refuse "must not contain a fragment" ;;
        # The one that ships a secret rather than breaking a feature. Userinfo
        # baked in here is readable by anybody who downloads the installer, and
        # is sent to the host on every request the game makes.
        *@*)   refuse "must not contain credentials" ;;
    esac

    host="$origin"
    port=""
    case "$origin" in
        *:*)
            host="${origin%%:*}"
            port="${origin#*:}"
            ;;
    esac

    # Dot-separated labels of letters, digits and internal hyphens: a hostname,
    # and deliberately not an IPv6 literal or anything else this deployment has
    # no way to serve. The underscore is the near-miss worth naming — legal in
    # DNS, refused by the certificate authority, so an installer built with one
    # would fail at TLS on a player's machine rather than here.
    if ! printf '%s' "$host" \
        | grep -qE '^[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?(\.[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?)*$'
    then
        refuse "must name a host of dot-separated letters, digits and hyphens"
    fi

    # DNS limits, which are the reason these are not merely long: a name over
    # 253 characters or a label over 63 cannot be resolved on a player's machine
    # and cannot be issued a certificate, so an installer carrying one is broken
    # before it is downloaded.
    if [ "${#host}" -gt 253 ]; then
        refuse "must name a host of at most 253 characters"
    fi
    remaining="$host"
    while [ -n "$remaining" ]; do
        label="${remaining%%.*}"
        case "$remaining" in
            *.*) remaining="${remaining#*.}" ;;
            *)   remaining="" ;;
        esac
        if [ "${#label}" -gt 63 ]; then
            refuse "must name a host whose every label is at most 63 characters"
        fi
    done

    # A colon with nothing usable after it is how a mistyped ":8443" survives.
    case "$origin" in
        *:*)
            if ! printf '%s' "$port" | grep -qE '^[0-9]{1,5}$'; then
                refuse "must have a numeric port after the colon"
            fi
            if [ "$port" -lt 1 ] || [ "$port" -gt 65535 ]; then
                refuse "must have a port between 1 and 65535"
            fi
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
