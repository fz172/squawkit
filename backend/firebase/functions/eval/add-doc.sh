#!/usr/bin/env bash
# Registers a local document for eval cases: copies it to docs/<sha256>.<ext> and prints the
# `documents` entry for case.json. With --upload, also copies it to $EVAL_DOCS_BUCKET so
# fetch.sh can restore it on another machine.
#
#   npm run eval:add-doc -- ~/Downloads/manual.pdf [--upload]
set -euo pipefail
cd "$(dirname "$0")"

file="${1:?usage: add-doc.sh <file> [--upload]}"
upload="${2:-}"
case "${file##*.}" in
  pdf|PDF) ext=pdf; mime=application/pdf ;;
  jpg|jpeg|JPG|JPEG) ext=jpg; mime=image/jpeg ;;
  png|PNG) ext=png; mime=image/png ;;
  webp) ext=webp; mime=image/webp ;;
  *) echo "Unsupported file type: $file" >&2; exit 1 ;;
esac

sha=$(shasum -a 256 "$file" | cut -d' ' -f1)
size=$(wc -c < "$file" | tr -d ' ')
mkdir -p docs
cp "$file" "docs/$sha.$ext"

if [ "$upload" = "--upload" ]; then
  : "${EVAL_DOCS_BUCKET:?Set EVAL_DOCS_BUCKET, e.g. gs://wingslog-9ca4e-ai-eval}"
  gcloud storage cp "docs/$sha.$ext" "$EVAL_DOCS_BUCKET/docs/$sha.$ext"
fi

name=$(basename "$file")
printf '{ "blobId": "%s", "name": "%s", "mimeType": "%s", "sha256": "%s", "sizeBytes": %s }\n' \
  "${sha:0:12}" "$name" "$mime" "$sha" "$size"
