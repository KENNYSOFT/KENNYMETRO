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
FAVICON=$(mktemp)
# 호출 원장. 하위 디렉터리를 일부러 끼워, 없는 경로를 앱이 만들어 쓰는지까지 본다.
LEDGER_DIR=$(mktemp -d)
LEDGER="$LEDGER_DIR/nested/api-calls.log"
# 화면만 올리는 배포의 볼륨. 2라운드에서만 앱에 넘긴다.
STATIC_ROOT=$(mktemp -d)

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
  rm -f "$LOG" "$STUB_LOG" "$FAVICON"
  rm -rf "$LEDGER_DIR" "$STATIC_ROOT"
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
  KENNYMETRO_CALL_LOG="$LEDGER" \
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

# 2라운드에서 이어받기를 볼 때 쓴다. 그 사이 자정을 넘기면 원장의 날짜가 갈린다.
DAY_BEFORE=$(date '+%Y-%m-%d')

# 1라운드: 열차가 있는 응답. 파싱, 종착 처리 열차 제외, 회차 방향 판정까지 본다.
echo "1라운드: 열차 있는 응답"
start_stub trains
start_app

BODY=$(curl -sf "$BASE/api/lines/shinbundang/trains") || fail "열차 목록을 받지 못했다"
echo "$BODY" | grep -q '"trainNo":"11"' || fail "11번 열차가 응답에 없다: $BODY"
echo "$BODY" | grep -q '"trainNo":"13"' && fail "종착 처리 중인 13번이 응답에 들어갔다: $BODY"
echo "$BODY" | grep -q '"trainNo":"12","currentStation":"광교","destination":"신사","direction":"UP"' \
  || fail "회차 대기 열차의 방향이 UP 이 아니다: $BODY"

# 환승 데이터는 classpath 리소스라 native image 에 자동으로 실리지 않는다. 힌트를 빠뜨리면
# JVM 에서는 멀쩡히 읽히고 바이너리에서만 파일이 없는 것처럼 빈 목록이 된다.
TRANSFERS=$(curl -sf "$BASE/api/lines/shinbundang/transfers") || fail "환승 정보를 받지 못했다"
echo "$TRANSFERS" | grep -q '"stations":\[\]' && fail "환승 데이터가 비었다. CSV 가 native image 에 실리지 않았다"
echo "$TRANSFERS" | grep -q '"car":6,"door":4' || fail "환승 문 위치가 응답에 없다: $TRANSFERS"
# 화면은 줄마다 서버가 정한 칸(side)에 둔다. 이 값이 빠지면 모든 줄이 양쪽 칸에 나온다.
echo "$TRANSFERS" | grep -q '"trainDirection":"신사","side":"UP"' || fail "환승 줄의 칸이 응답에 없다: $TRANSFERS"
# 출처 표기는 CC BY 조건이라 화면에서 뺄 수 없다. 문서명과 판은 CSV 머리의 주석에서
# 읽으므로, 그 파싱이 깨지면 여기가 빈다.
echo "$TRANSFERS" | grep -q '"license":"CC BY-NC-SA 2.0 KR"' || fail "라이선스 표기가 빠졌다: $TRANSFERS"
echo "$TRANSFERS" | grep -q '"revision":"r' || fail "출처 판이 빠졌다: $TRANSFERS"
echo "$TRANSFERS" | grep -q '"document":"수도권' || fail "출처 문서명이 빠졌다: $TRANSFERS"
# 지선 뷰가 나눠 쓰는 파일과, 문 하나가 아닌 자리(이어진 범위, 모든 문)도 바이너리에서 읽혀야 한다.
LINE4=$(curl -sf "$BASE/api/lines/line4/transfers") || fail "4호선 환승 정보를 받지 못했다"
echo "$LINE4" | grep -q '"car":1,"door":1,"toCar":6,"toDoor":4' || fail "이어진 문 범위가 응답에 없다: $LINE4"
echo "$LINE4" | grep -q '"car":null,"door":null' || fail "모든 문 자리가 응답에 없다: $LINE4"
GYEONGBU=$(curl -sf "$BASE/api/lines/line1-gyeongbu/transfers") || fail "1호선 경부 환승 정보를 받지 못했다"
echo "$GYEONGBU" | grep -q '"station":"수원"' || fail "나눠 쓰는 환승 파일이 경부 뷰에 실리지 않았다: $GYEONGBU"

# 노선 목록. lines.yml 이 native image 에 실리지 않으면 여기가 빈다.
LINES=$(curl -sf "$BASE/api/lines") || fail "노선 목록을 받지 못했다"
echo "$LINES" | grep -q '"lines":\[\]' && fail "노선이 비었다. lines.yml 이 native image 에 실리지 않았다"
for slug in shinbundang line2 line9 suinbundang everline; do
  echo "$LINES" | grep -q "\"slug\":\"$slug\"" || fail "$slug 노선이 목록에 없다: $LINES"
done
# 편성 차수는 신분당선에만 있다. 이 값이 빠지면 화면에서 색 구분이 통째로 사라진다.
echo "$LINES" | grep -q '"generations":\[' || fail "편성 차수가 응답에 없다: $LINES"
# 프리셋이 없으면 처음 방문한 화면이 통째로 빈다.
echo "$LINES" | grep -q '"preset":\["shinbundang"' || fail "프리셋이 응답에 없다: $LINES"
# 1호선은 계통마다 뷰를 나누되 API 노선명은 하나다. 이 값이 어긋나면 호출이 배로 나간다.
[ "$(echo "$LINES" | grep -o '"apiName":"1호선"' | wc -l)" -eq 2 ] \
  || fail "1호선 뷰 둘이 같은 API 노선을 가리키지 않는다: $LINES"

# 호출 원장. 위 trains 요청으로 서울시 API 를 한 번 불렀으니 그 한 번이 파일에 남아야 한다.
# 재배포해도 예산 표시가 이어지게 하는 장치라 실제로 파일이 생기는지까지 본다.
echo "$LINES" | grep -q '"apiCallCount":1' || fail "호출 수가 응답에 없다: $LINES"
[ -f "$LEDGER" ] || fail "호출 원장 파일이 생기지 않았다: $LEDGER"
grep -q "^$DAY_BEFORE 1$" "$LEDGER" || fail "오늘 호출 수가 원장에 없다: $(cat "$LEDGER")"

# favicon 은 XML 이라 주석에 붙임표 두 개만 들어가도 브라우저가 렌더링을 거부한다.
# 인라인으로 넣어 보면 HTML 파서가 관대해서 그냥 지나가므로 파일 그대로 파싱해 본다.
curl -sf "$BASE/favicon.svg" -o "$FAVICON" || fail "favicon 을 받지 못했다"
python3 -c 'import sys, xml.etree.ElementTree as ET; ET.parse(sys.argv[1])' "$FAVICON" \
  || fail "favicon.svg 가 XML 로 파싱되지 않는다"

# 첫 화면. 볼륨이 없으면 이미지에 담긴 화면이 나와야 한다.
curl -sf "$BASE/" | grep -q '<title>수도권 전철 실시간</title>' || fail "첫 화면이 이미지에 담긴 화면이 아니다"
# 화면만 올렸을 때 바로 보이도록 브라우저가 매번 바뀌었는지 묻게 한다.
curl -sfI "$BASE/index.html" | grep -qi '^cache-control:.*no-cache' || fail "화면에 Cache-Control: no-cache 가 없다"
echo "  통과"

stop_all

# 2라운드: 운행 종료 시간대. 빈 목록이 native 에서 직렬화되는지 본다.
# 이 경로가 깨지면 매일 밤 500 이 난다. 화면만 올리는 배포의 볼륨도 이 라운드에서 붙인다.
echo "2라운드: 열차 0대 응답"
printf '<!DOCTYPE html><title>smoke-static-override</title>\n' > "$STATIC_ROOT/index.html"
export KENNYMETRO_STATIC_ROOT="$STATIC_ROOT"
start_stub empty
start_app

BODY=$(curl -sf "$BASE/api/lines/shinbundang/trains") || fail "빈 목록 응답이 실패했다 (native 직렬화 확인)"
echo "$BODY" | grep -q '"trains":\[\]' || fail "빈 목록이 아니다: $BODY"
echo "$BODY" | grep -q '"line":"shinbundang"' || fail "노선 표시가 빠졌다: $BODY"

# 앱을 껐다 켰으므로 호출 수는 1 에서 이어져 2 여야 한다. 원장을 안 읽으면 여기서 1 이 된다.
LINES=$(curl -sf "$BASE/api/lines") || fail "노선 목록을 받지 못했다"
if [ "$(date '+%Y-%m-%d')" = "$DAY_BEFORE" ]; then
  echo "$LINES" | grep -q '"apiCallCount":2' || fail "재기동 후 호출 수를 이어받지 못했다: $LINES"
else
  echo "  자정을 넘겨 이어받기 확인은 건너뛴다"
fi

# 볼륨의 화면이 이미지에 담긴 것보다 먼저 나와야 한다. 첫 화면(`/`)은 RootPageController 가
# index.html 로 넘기는 경로라 따로 본다. 볼륨에 없는 파일은 이미지 것으로 떨어진다.
curl -sf "$BASE/" | grep -q 'smoke-static-override' || fail "첫 화면이 볼륨의 파일이 아니다"
curl -sf "$BASE/index.html" | grep -q 'smoke-static-override' || fail "index.html 이 볼륨의 파일이 아니다"
curl -sf "$BASE/favicon.svg" -o /dev/null || fail "볼륨에 없는 favicon 이 이미지 것으로 떨어지지 않는다"
echo "  통과"

echo "스모크 테스트 통과"
