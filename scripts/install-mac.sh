#!/bin/bash
# 일정 관리 앱을 Mac에 한 번에 설치(또는 최신 버전으로 업데이트)하는 스크립트.
# 터미널에 아래 한 줄을 붙여 넣으면 된다:
#   curl -fsSL https://raw.githubusercontent.com/kim0040/proto/main/scripts/install-mac.sh | bash
# 하는 일: 최신 릴리즈의 macOS zip을 받아 응용 프로그램 폴더에 넣고 실행한다.
# 브라우저가 아닌 curl로 받으므로 "확인되지 않은 개발자" 경고 없이 바로 열린다.
# 데이터(~/Library/Application Support/ScheduleApp)는 건드리지 않는다.
set -euo pipefail

REPO="kim0040/proto"
APP_NAME="ScheduleApp"
INSTALL_DIR="${INSTALL_DIR:-/Applications}"

say() { printf '\033[1m%s\033[0m\n' "$1"; }
fail() { printf '\033[31m%s\033[0m\n' "$1" >&2; exit 1; }

check_mac() {
    [ "$(uname -s)" = "Darwin" ] || fail "이 스크립트는 Mac 전용이에요. Windows는 설치 안내의 PowerShell 한 줄을 써 주세요."
    if [ "$(uname -m)" != "arm64" ]; then
        fail "이 앱은 Apple Silicon(M1 이후) Mac 전용이에요. Intel Mac에서는 설치 안내의 '소스로 실행하기'를 따라 주세요."
    fi
}

# 최신 릴리즈의 macOS zip 주소 (릴리즈 파일 이름에는 버전이 없어서 이 주소가 늘 최신 버전을 가리킨다)
ZIP_URL="${ZIP_URL:-https://github.com/${REPO}/releases/latest/download/${APP_NAME}-macos-arm64.zip}"

# 앱이 켜져 있으면 정상 종료를 부탁한다 (일정은 바꿀 때마다 저장되므로 잃는 것은 없다)
quit_running_app() {
    if pgrep -f "${APP_NAME}.app/Contents/MacOS/" >/dev/null 2>&1; then
        say "켜져 있는 일정 관리 앱을 닫을게요…"
        osascript -e "quit app \"${APP_NAME}\"" >/dev/null 2>&1 || true
        for _ in 1 2 3 4 5 6 7 8 9 10; do
            pgrep -f "${APP_NAME}.app/Contents/MacOS/" >/dev/null 2>&1 || return 0
            sleep 1
        done
        fail "앱이 닫히지 않았어요. 앱을 직접 끈 다음 다시 실행해 주세요."
    fi
}

# 받은 앱을 설치 폴더에 넣는다. /Applications에 쓸 권한이 없으면 내 응용 프로그램 폴더(~/Applications)에 넣는다.
# 새 앱을 먼저 같은 폴더에 다 복사해 둔 뒤 예전 앱과 바꿔치기한다. 중간에 실패하면 예전 앱을 그대로 되살린다
place_app() {
    local new_app="$1" target="$INSTALL_DIR"
    if [ ! -w "$target" ]; then
        target="$HOME/Applications"
        mkdir -p "$target"
    fi
    local final="$target/${APP_NAME}.app" staged="$target/.${APP_NAME}.app.new" backup="$target/.${APP_NAME}.app.old"
    rm -rf "$staged" "$backup"
    ditto "$new_app" "$staged" || { rm -rf "$staged"; fail "앱을 복사하지 못했어요. 디스크 공간을 확인해 주세요. 예전 앱은 그대로예요."; }
    xattr -dr com.apple.quarantine "$staged" 2>/dev/null || true
    if [ -e "$final" ]; then
        mv "$final" "$backup" || { rm -rf "$staged"; fail "예전 앱을 옮기지 못했어요. 예전 앱은 그대로예요."; }
    fi
    if ! mv "$staged" "$final"; then
        [ -e "$backup" ] && mv "$backup" "$final"
        rm -rf "$staged"
        fail "새 앱으로 바꾸지 못했어요. 예전 앱을 되살렸어요."
    fi
    rm -rf "$backup"
    INSTALLED="$final"
}

main() {
    check_mac
    TMP_DIR="$(mktemp -d)"
    trap 'rm -rf "$TMP_DIR"' EXIT
    local tmp="$TMP_DIR"
    say "최신 버전을 내려받는 중이에요…"
    curl -fL --progress-bar -o "$tmp/app.zip" "$ZIP_URL" \
        || fail "내려받지 못했어요. 인터넷 연결을 확인하고 잠시 뒤 다시 해 주세요."
    ditto -x -k "$tmp/app.zip" "$tmp/unzipped"
    [ -d "$tmp/unzipped/${APP_NAME}.app" ] || fail "받은 파일 안에 앱이 없어요. 잠시 뒤 다시 해 주세요."
    quit_running_app
    place_app "$tmp/unzipped/${APP_NAME}.app"
    say "설치했어요: $INSTALLED"
    if [ -z "${NO_OPEN:-}" ]; then
        open "$INSTALLED"
        say "앱을 열었어요. 다음부터는 ⌘ + Space로 '일정 관리'를 검색하면 돼요."
    fi
}

main "$@"
