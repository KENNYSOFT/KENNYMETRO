#!/bin/sh
# native 바이너리를 실제로 띄워 열차 목록을 왕복한다.
#
# JVM 테스트로는 잡히지 않는 native 전용 결함을 배포 전에 거르는 것이 목적이다. 특히 Kotlin 의
# 빈 컬렉션 싱글톤은 JVM 에서 멀쩡하고 native 에서만 직렬화가 깨지므로, 열차가 있는 응답과
# 0대인 응답을 모두 확인한다 (DESIGN.md 4.1).
#
# 사용법: BINARY=build/native/nativeCompile/kennymetro ./scripts/smoke-test.sh
set -eu

BINARY="${BINARY:-build/native/nativeCompile/kennymetro}"
PORT="${PORT:-18080}"
STUB_PORT="${STUB_PORT:-18090}"
BASE="http://127.0.0.1:$PORT"
LOG=$(mktemp)
STUB_LOG=$(mktemp)

cleanup() {
  status=$?
  for pid in ${APP_PID:-} ${STUB_PID:-}; do
    if kill -0 "$pid" 2>/dev/null; then
      kill "$pid" 2>/dev/null || true
      wait "$pid" 2>/dev/null || true
    fi
  done
  if [ "$status" -ne 0 ]; then
    echo "--- 애플리케이션 로그 ---" >&2
    cat "$LOG" >&2
    echo "--- stub 로그 ---" >&2
    cat "$STUB_LOG" >&2
  fi
  rm -f "$LOG" "$STUB_LOG"
}
trap cleanup EXIT

fail() {
  echo "스모크 테스트 실패: $1" >&2
  exit 1
}

start_stub() {
  STUB_MODE="$1" STUB_PORT="$STUB_PORT" python3 scripts/stub-seoul-api.py > "$STUB_LOG" 2>&1 &
  STUB_PID=$!
  i=0
  until curl -sf "http://127.0.0.1:$STUB_PORT/" > /dev/null 2>&1; do
    i=$((i + 1))
    [ "$i" -gt 20 ] && fail "stub 이 기동하지 못했다"
    sleep 1
  done
}

start_app() {
  SERVER_PORT="$PORT" \
  SEOUL_SUBWAY_API_KEY=smoke-key \
  SEOUL_SUBWAY_BASE_URL="http://127.0.0.1:$STUB_PORT" \
    "$BINARY" > "$LOG" 2>&1 &
  APP_PID=$!
  i=0
  until curl -sf "$BASE/actuator/health" > /dev/null 2>&1; do
    i=$((i + 1))
    [ "$i" -gt 60 ] && fail "60초 안에 기동하지 못했다"
    kill -0 "$APP_PID" 2>/dev/null || fail "프로세스가 죽었다"
    sleep 1
  done
}

stop_all() {
  for pid in ${APP_PID:-} ${STUB_PID:-}; do
    kill "$pid" 2>/dev/null || true
    wait "$pid" 2>/dev/null || true
  done
  APP_PID=""
  STUB_PID=""
}

# 1라운드: 열차가 있는 응답. 파싱, 종착 처리 열차 제외, 회차 방향 판정까지 본다.
echo "1라운드: 열차 있는 응답"
start_stub trains
start_app

BODY=$(curl -sf "$BASE/api/lines/shinbundang/trains") || fail "열차 목록을 받지 못했다"
echo "$BODY" | grep -q '"trainNo":"11"' || fail "11번 열차가 응답에 없다: $BODY"
echo "$BODY" | grep -q '"trainNo":"13"' && fail "종착 처리 중인 13번이 응답에 들어갔다: $BODY"
echo "$BODY" | grep -q '"trainNo":"12","currentStation":"광교","destination":"신사","direction":"UP"' \
  || fail "회차 대기 열차의 방향이 UP 이 아니다: $BODY"
echo "  통과"

stop_all

# 2라운드: 운행 종료 시간대. 빈 목록이 native 에서 직렬화되는지 본다.
# 이 경로가 깨지면 매일 밤 500 이 난다.
echo "2라운드: 열차 0대 응답"
start_stub empty
start_app

BODY=$(curl -sf "$BASE/api/lines/shinbundang/trains") || fail "빈 목록 응답이 실패했다 (native 직렬화 확인)"
echo "$BODY" | grep -q '"trains":\[\]' || fail "빈 목록이 아니다: $BODY"
echo "$BODY" | grep -q '"stations":\[' || fail "역 목록이 빠졌다: $BODY"
echo "  통과"

echo "스모크 테스트 통과"
