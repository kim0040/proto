#!/bin/sh
# 앱 아이콘(.icns, .ico)을 src/main/resources/proto/ui/appicon/icon-N.png 에서 다시 만든다.
# 아이콘 PNG를 바꿨을 때만 실행하고, 결과 파일(packaging/macos/ScheduleApp.icns, packaging/windows/ScheduleApp.ico)을 커밋한다.
# .icns는 macOS의 iconutil이 필요하다. .ico는 자바(java)만 있으면 어디서나 만들 수 있다.
set -eu

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/src/main/resources/proto/ui/appicon"

if [ "$(uname)" = "Darwin" ]; then
  SET="$(mktemp -d)/ScheduleApp.iconset"
  mkdir -p "$SET"
  # iconset 이름: 크기 N은 icon_NxN.png, 2배 그림은 icon_NxN@2x.png (= 2N 크기 PNG)
  cp "$SRC/icon-16.png"   "$SET/icon_16x16.png"
  cp "$SRC/icon-32.png"   "$SET/icon_16x16@2x.png"
  cp "$SRC/icon-32.png"   "$SET/icon_32x32.png"
  cp "$SRC/icon-64.png"   "$SET/icon_32x32@2x.png"
  cp "$SRC/icon-128.png"  "$SET/icon_128x128.png"
  cp "$SRC/icon-256.png"  "$SET/icon_128x128@2x.png"
  cp "$SRC/icon-256.png"  "$SET/icon_256x256.png"
  cp "$SRC/icon-512.png"  "$SET/icon_256x256@2x.png"
  cp "$SRC/icon-512.png"  "$SET/icon_512x512.png"
  cp "$SRC/icon-1024.png" "$SET/icon_512x512@2x.png"
  iconutil -c icns "$SET" -o "$ROOT/packaging/macos/ScheduleApp.icns"
  rm -rf "$(dirname "$SET")"
else
  echo "macOS가 아니라서 .icns는 건너뜁니다."
fi

java "$ROOT/packaging/MakeIco.java" "$SRC" "$ROOT/packaging/windows/ScheduleApp.ico"
echo "완료: packaging/macos/ScheduleApp.icns, packaging/windows/ScheduleApp.ico"
