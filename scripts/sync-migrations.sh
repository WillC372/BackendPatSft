#!/usr/bin/env bash
# The tests build their H2 schema with the migrations of DatabasePatSft, the repository that owns the schema.
# Run this after a new migration there: ./scripts/sync-migrations.sh ../DatabasePatSft
set -euo pipefail
src="${1:-../DatabasePatSft}/migrations"
dest="$(dirname "$0")/../src/test/resources/db/migration"
[ -d "$src" ] || { echo "No encuentro $src"; exit 1; }
rm -f "$dest"/V*.sql
cp "$src"/V*.sql "$dest"/
echo "Copiadas $(ls "$dest"/V*.sql | wc -l) migraciones desde $src"
