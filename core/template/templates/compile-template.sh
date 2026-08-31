#!/usr/bin/env bash
# Compiles a canonical template's .textproto to the .pb the app bakes in.
#
# WHY THIS IS A SCRIPT AND NOT A GRADLE TASK (#675)
#
# Wire owns codegen here and has no protobuf text-format parser — it turns a .proto SCHEMA into
# Kotlin classes, and cannot read a .textproto DATA file. `protoc --encode` is the only off-the-shelf
# tool that does text -> binary, so protoc is needed purely as a data compiler, never for codegen.
#
# Running it inside Gradle would put a native binary download and an unzip on every developer's
# configuration path, for an asset that changes about once per preset. Instead the .pb is committed
# and this script regenerates it, so a fresh clone and CI need no protoc at all. The publishing
# script (#725) needs protoc regardless, which makes that the one place it lives.
#
# The compiled bytes are verified by AirplaneTemplateAssetTest — if you edit the .textproto and
# forget to re-run this, that test is what tells you.
#
# Usage:  ./compile-template.sh airplane.v1
set -euo pipefail

PROTOC_VERSION="4.28.2"
PROTOBUF_JAVA_VERSION="3.25.5"

name="${1:-airplane.v1}"
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo="$(cd "$here/../../.." && pwd)"
proto_path="$repo/core/model/src/commonMain/proto"

case "$(uname -s)-$(uname -m)" in
  Darwin-arm64)  classifier="osx-aarch_64" ;;
  Darwin-x86_64) classifier="osx-x86_64" ;;
  Linux-aarch64) classifier="linux-aarch_64" ;;
  Linux-x86_64)  classifier="linux-x86_64" ;;
  *) echo "unsupported host: $(uname -s)-$(uname -m)" >&2; exit 1 ;;
esac

mkdir -p "$here/binary"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

fetch() { # group/artifact/version/file -> stdout path
  local url="$1" out="$2"
  [ -f "$out" ] || curl -fsSL "$url" -o "$out"
  echo "$out"
}

base="https://repo1.maven.org/maven2/com/google/protobuf"
cache="${HOME}/.cache/squawkit-protoc"
mkdir -p "$cache"

protoc="$(fetch "$base/protoc/$PROTOC_VERSION/protoc-$PROTOC_VERSION-$classifier.exe" \
  "$cache/protoc-$PROTOC_VERSION-$classifier.exe")"
chmod +x "$protoc"

# The Maven protoc artifact is the BARE BINARY — it does not carry the include/ directory the
# release zip ships, so the well-known types have to come from somewhere. thing/template.proto does
# not import Timestamp itself, but protoc compiles the whole --proto_path and siblings
# (attachment.proto, technician.proto) do, so without these the encode fails on those imports.
jar="$(fetch "$base/protobuf-java/$PROTOBUF_JAVA_VERSION/protobuf-java-$PROTOBUF_JAVA_VERSION.jar" \
  "$cache/protobuf-java-$PROTOBUF_JAVA_VERSION.jar")"
mkdir -p "$work/wkt"
(cd "$work/wkt" && unzip -oq "$jar" 'google/protobuf/*.proto')

"$protoc" \
  --proto_path="$proto_path" \
  --proto_path="$work/wkt" \
  --encode=ThingTemplate thing/template.proto \
  < "$here/$name.textproto" \
  > "$here/binary/$name.pb"

echo "wrote $here/binary/$name.pb ($(wc -c < "$here/binary/$name.pb" | tr -d ' ') bytes)"
