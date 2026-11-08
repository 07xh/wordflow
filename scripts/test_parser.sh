#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
out="$(mktemp -d)"
trap 'rm -rf "$out"' EXIT
javac -encoding UTF-8 -d "$out" \
  app/src/main/java/dev/wordflow/app/WordParser.java \
  app/src/main/java/dev/wordflow/app/WordCsv.java \
  app/src/main/java/dev/wordflow/app/WordReview.java \
  scripts/WordParserCliTest.java scripts/WordReviewCliTest.java
java -cp "$out" WordParserCliTest
java -cp "$out" WordReviewCliTest
