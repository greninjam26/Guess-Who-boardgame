#!/usr/bin/env bash
# Runs the two-client acceptance rehearsal against a disposable database.
set -euo pipefail

repo="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
work="$here/work"
rm -rf "$work"
mkdir -p "$work"

port=18080
jar="$repo/server/target/server-1.0.0.jar"

if [ ! -f "$jar" ]; then
    echo "This rehearsal needs a built server jar at $jar."
    echo "Build it with: mvn install -DskipTests"
    exit 2
fi

h2="$(find "$HOME/.m2/repository/com/h2database/h2" -name "h2-*.jar" 2>/dev/null | sort -V | tail -1)"
cp="$repo/desktop-client/target/desktop-client-1.0.0.jar:$repo/desktop-client/target/lib/*:$h2"
jdbc="jdbc:h2:file:$work/rehearsal;AUTO_SERVER=TRUE"

echo "database: $jdbc"
echo "server log: $work/server.log"
echo

java -cp "$cp" "$here/TwoClientRehearsal.java" \
    "$jar" "$jdbc" "$port" "$work/server.log" "$work/pending-game-results.jsonl"
