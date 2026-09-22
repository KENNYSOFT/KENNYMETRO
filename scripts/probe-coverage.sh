#!/usr/bin/env bash
# 서울시 실시간 지하철 API 가 우리 노선을 담고 있는지, 그리고 역명 표기가 우리 것과
# 같은지 확인한다.
#
# 확인할 것이 둘이다.
#
# 1. 그 노선이 API 에 있는가. 이 API 는 "지원하지 않는 노선" 과 "지금 운행 중인 열차가
#    없음" 을 같은 응답(INFO-200)으로 돌려주므로 반드시 운행 시간대에 실행해야 판정이
#    갈린다. 다른 노선이 열차를 주는데 특정 노선만 0 이면 그 노선이 없는 것이다.
#
# 2. 역명이 우리 목록과 같은가. 한 글자라도 다르면 그 역의 열차가 화면에서 조용히
#    사라진다. API 가 준 역명 중 우리가 모르는 것을 찍어 준다 - 그 목록이 비어야 한다.
#
# 노선과 역 목록은 앱에서 받아 온다. lines.yml 을 여기서 다시 파싱하면 두 곳이 어긋난다.
#
# 사용법: SEOUL_SUBWAY_KEY=... [APP=http://localhost:8080] ./scripts/probe-coverage.sh
set -euo pipefail

: "${SEOUL_SUBWAY_KEY:?실시간 지하철 인증키를 SEOUL_SUBWAY_KEY 환경변수로 넘길 것}"
APP="${APP:-https://metro.kennysoft.kr}"

BASE="http://swopenapi.seoul.go.kr/api/subway/${SEOUL_SUBWAY_KEY}/json"
CALLS=0

urlencode() {
  local s="$1" i c out=""
  for ((i = 0; i < ${#s}; i++)); do
    c="${s:i:1}"
    case "$c" in
      [a-zA-Z0-9.~_-]) out+="$c" ;;
      *) out+=$(printf '%s' "$c" | od -An -tx1 | tr -d ' \n' | sed 's/../%&/g') ;;
    esac
  done
  printf '%s' "$out"
}

printf '실행 시각: %s\n앱: %s\n\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$APP"

lines=$(curl -sS --max-time 20 "$APP/api/lines") || { echo "앱에서 노선 목록을 받지 못했다: $APP" >&2; exit 1; }

# 화면에서 갈라 놓은 뷰도 API 로는 한 노선이다. api-name 으로 묶고 역 목록은 합친다.
targets=$(printf '%s' "$lines" | jq -r '
  [.lines[] | select(.budgeted)]
  | group_by(.apiName)[]
  | (.[0].apiName) + "\t" + ((map(.stations) | add | unique) | join(","))')

printf '%-14s %-6s %s\n' "노선" "열차수" "표본 (열차번호 / 현재역 / 종착)"
printf '%s\n' "----------------------------------------------------------------------"

unknown_total=0
while IFS=$'\t' read -r name stations; do
  [ -n "$name" ] || continue
  resp=$(curl -sS --max-time 20 "${BASE}/realtimePosition/0/999/$(urlencode "$name")")
  CALLS=$((CALLS + 1))

  count=$(printf '%s' "$resp" | jq -r 'if .realtimePositionList then (.realtimePositionList | length) else -1 end' 2>/dev/null || echo -1)
  if [ "$count" = "-1" ]; then
    message=$(printf '%s' "$resp" | jq -r '.message // "파싱 실패"' 2>/dev/null || echo "파싱 실패")
    printf '%-14s %-6s %s\n' "$name" "-" "$message"
    continue
  fi

  sample=$(printf '%s' "$resp" | jq -r '.realtimePositionList[0] | "\(.trainNo) / \(.statnNm) / \(.statnTnm)"' 2>/dev/null || echo "-")
  printf '%-14s %-6s %s\n' "$name" "$count" "$sample"

  # API 가 준 역명 중 우리 목록에 없는 것. 여기 찍히면 lines.yml 을 그 표기로 고친다.
  unknown=$(comm -23 \
    <(printf '%s' "$resp" | jq -r '.realtimePositionList[]?.statnNm' | sort -u) \
    <(printf '%s' "$stations" | tr ',' '\n' | sort -u))
  if [ -n "$unknown" ]; then
    unknown_total=$((unknown_total + 1))
    printf '  모르는 역: %s\n' "$(printf '%s' "$unknown" | tr '\n' ' ')"
  fi
done <<< "$targets"

# 용인경전철은 공식 실시간 페이지가 쓰는 엔드포인트. 인증과 호출 제한이 없다.
printf '\n%s\n' "용인경전철 (everlinecu):"
ever=$(curl -sSL --max-time 20 "https://everlinecu.com/api/api009.json" || echo '{}')
printf '  열차수: %s\n' "$(printf '%s' "$ever" | jq -r '(.data // []) | length')"
printf '  표본: %s\n' "$(printf '%s' "$ever" | jq -rc '(.data // [])[0] // "없음"')"

printf '\n서울시 API 호출: %d회 (일일 한도 1000)\n' "$CALLS"
printf '판정: 열차수 0 인 노선은 이 시간대에 운행이 없거나 API 가 담지 않는다.\n'
printf '      같은 시간대에 다른 노선이 열차를 반환하는데 특정 노선만 0 이면 미수록 쪽이다.\n'
if [ "$unknown_total" -gt 0 ]; then
  printf '      역명이 어긋난 노선이 %d개 있다. 위 "모르는 역" 을 lines.yml 에 반영할 것.\n' "$unknown_total"
fi
