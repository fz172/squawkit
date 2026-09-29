#!/usr/bin/env bash
# Fetches every document the cases name into docs/, from the private bucket that holds the
# licensed manuals. The repo holds only their sha256 (design §12.2).
#
#   EVAL_DOCS_BUCKET=gs://wingslog-9ca4e-ai-eval npm run eval:fetch
set -euo pipefail
cd "$(dirname "$0")"
: "${EVAL_DOCS_BUCKET:?Set EVAL_DOCS_BUCKET, e.g. gs://wingslog-9ca4e-ai-eval}"
mkdir -p docs

node -e '
const fs = require("fs");
const ext = { "application/pdf": "pdf", "image/jpeg": "jpg", "image/png": "png", "image/webp": "webp" };
const seen = new Set();
for (const id of fs.readdirSync("cases")) {
  const c = JSON.parse(fs.readFileSync(`cases/${id}/case.json`, "utf8"));
  for (const d of c.request.documents) {
    const f = `${d.sha256}.${ext[d.mimeType]}`;
    if (!seen.has(f)) { seen.add(f); console.log(f); }
  }
}' | while read -r f; do
  if [ -f "docs/$f" ]; then echo "have $f"; continue; fi
  gcloud storage cp "$EVAL_DOCS_BUCKET/docs/$f" "docs/$f"
  actual=$(shasum -a 256 "docs/$f" | cut -d' ' -f1)
  if [ "$actual.${f##*.}" != "$f" ]; then
    echo "sha256 mismatch for $f" >&2
    rm "docs/$f"
    exit 1
  fi
done
