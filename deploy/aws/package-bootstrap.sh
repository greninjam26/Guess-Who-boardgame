#!/usr/bin/env bash

set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$here/../.." && pwd)"
output_dir="$repo_root/target/aws-bootstrap"
archive="$output_dir/guesswho-bootstrap.tar.gz"
checksum="$archive.sha256"
runtime_files=(
    bootstrap.sh
    set-db-password.sh
    backup.sh
    guesswho.service
    guesswho-backup.service
    guesswho-backup.timer
    Caddyfile
    cloudwatch-agent.json
)

staging="$(mktemp -d)"
trap 'rm -rf "$staging"' EXIT

for file in "${runtime_files[@]}"; do
    source_file="$here/$file"
    if [ ! -f "$source_file" ]; then
        echo "Missing bootstrap input: $file" >&2
        exit 1
    fi
    case "$file" in
        *.sh) install -m 0755 "$source_file" "$staging/$file" ;;
        *)    install -m 0644 "$source_file" "$staging/$file" ;;
    esac
done

mkdir -p "$output_dir"
tar -C "$staging" -czf "$archive" "${runtime_files[@]}"
(
    cd "$output_dir"
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$(basename "$archive")" > "$(basename "$checksum")"
    else
        shasum -a 256 "$(basename "$archive")" > "$(basename "$checksum")"
    fi
)

echo "Created $archive"
echo "Created $checksum"
