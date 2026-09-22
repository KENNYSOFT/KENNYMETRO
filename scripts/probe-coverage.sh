#!/usr/bin/env bash
# 서울시 실시간 지하철 API 가 어느 노선을 담고 있는지 확인한다.
#
# 이 API 는 "지원하지 않는 노선" 과 "지금 운행 중인 열차가 없음" 을 같은 응답
# (INFO-200)으로 돌려주므로, 반드시 운행 시간대에 실행해야 판정이 갈린다.
# 첫차 이후부터 막차 전까지 돌릴 것.
#
# 사용법: SEOUL_SUBWAY_KEY=... ./scripts/probe-coverage.sh
set -euo pipefail

: "${SEOUL_SUBWAY_KEY:?실시간 지하철 인증키를 SEOUL_SUBWAY_KEY 환경변수로 넘길 것}"

BASE="http://swopenapi.seoul.go.kr/api/subway/${SEOUL_SUBWAY_KEY}/json"
CALLS=0

# 확인할 노선. 앞의 둘이 목적이고 나머지는 대조군 겸 환승 대상이다.
LINES=("신분당선" "수인분당선" "경강선" "2호선" "3호선" "경의중앙선")

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

printf '실행 시각: %s\n\n' "$(date '+%Y-%m-%d %H:%M:%S')"
printf '%-12s %-6s %s\n' "노선" "열차수" "표본 (열차번호 / 현재역 / 종착)"
printf '%s\n' "------------------------------------------------------------------"

for line in "${LINES[@]}"; do
  resp=$(curl -sS --max-time 20 "${BASE}/realtimePosition/0/999/$(urlencode "$line")")
  CALLS=$((CALLS + 1))

  n=$(printf '%s' "$resp" | jq -r 'if .realtimePositionList then (.realtimePositionList | length) else -1 end' 2>/dev/null || echo -1)

  if [ "$n" = "-1" ]; then
    msg=$(printf '%s' "$resp" | jq -r '.message // "파싱 실패"' 2>/dev/null || echo "파싱 실패")
    printf '%-12s %-6s %s\n' "$line" "-" "$msg"
    continue
  fi

  sample=$(printf '%s' "$resp" | jq -r '.realtimePositionList[0] | "\(.trainNo) / \(.statnNm) / \(.statnTnm)"' 2>/dev/null || echo "-")
  printf '%-12s %-6s %s\n' "$line" "$n" "$sample"
done

# 용인경전철은 공식 실시간 페이지가 쓰는 엔드포인트. 인증과 호출 제한이 없다.
printf '\n%s\n' "용인경전철 (everlinecu):"
ever=$(curl -sSL --max-time 20 "https://everlinecu.com/api/api009.json" || echo '{}')
printf '  열차수: %s\n' "$(printf '%s' "$ever" | jq -r '(.data // []) | length')"
printf '  표본: %s\n' "$(printf '%s' "$ever" | jq -rc '(.data // [])[0] // "없음"')"

printf '\n서울시 API 호출: %d회 (일일 한도 1000)\n' "$CALLS"
printf '판정: 위 표에서 열차수 0 인 노선은 이 시간대에 운행이 없거나 API 가 담지 않는다.\n'
printf '      같은 시간대에 다른 노선이 열차를 반환하는데 특정 노선만 0 이면 미수록 쪽이다.\n'
