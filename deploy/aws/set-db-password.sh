#!/usr/bin/env bash

# Reads the new database password from standard input and changes the fixed
# application role. The password is handed to psql through its environment,
# rather than as a command-line argument that can appear in a process listing.

set -euo pipefail

IFS= read -r db_password
if [ -z "$db_password" ]; then
    echo "database password is empty" >&2
    exit 1
fi

export GUESSWHO_DB_PASSWORD="$db_password"
psql -d postgres -v ON_ERROR_STOP=1 <<'SQL'
\getenv db_password GUESSWHO_DB_PASSWORD
ALTER ROLE guesswho WITH PASSWORD :'db_password';
SQL
unset GUESSWHO_DB_PASSWORD db_password
