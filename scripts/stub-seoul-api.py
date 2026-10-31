#!/usr/bin/env python3
"""서울시 실시간 지하철 API 와 역별 시간표 API 를 흉내내는 stub.

스모크 테스트가 실제 API 를 부르면 하루 1,000회 예산을 깎고 외부 네트워크에 의존하게 된다.

STUB_MODE 로 두 응답을 가른다.
  trains : 노선마다 열차 몇 대. 종착 처리 열차, 회차 대기 열차, 종착역이 다음 운행 것으로 앞서
           바뀐 열차가 섞여 있다. 실시간 위치가 빠지는 광명셔틀의 시간표도 지금 시각에 맞춰 준다.
  empty  : INFO-200 (운행 종료 시간대. 빈 목록 직렬화를 검증한다)

경로로 서비스를 가른다. 실시간 위치는 노선명으로, 시간표는 역 코드로 고른다.
"""

import json
import os
from datetime import datetime, timedelta
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import unquote

MODE = os.environ.get("STUB_MODE", "trains")
PORT = int(os.environ.get("STUB_PORT", "18090"))


def position(train_no, current, terminal, updn, recptn="2026-09-22 21:00:00", last="0", sttus="1"):
    return {
        "subwayId": "1077",
        "subwayNm": "신분당선",
        "statnId": "1077000000",
        "statnNm": current,
        "trainNo": train_no,
        "statnTid": "1077000001",
        "statnTnm": terminal,
        "updnLine": updn,
        "trainSttus": sttus,
        "directAt": "0",
        "lstcarAt": last,
        "recptnDt": recptn,
    }


def positions(*trains):
    return {
        "errorMessage": {"status": 200, "code": "INFO-000", "message": "정상 처리되었습니다.", "total": len(trains)},
        "realtimePositionList": list(trains),
    }


POSITIONS = {
    "신분당선": positions(
        # 청계산입구를 떠나 판교로 가는 중이다. 화면은 두 역 사이에 그린다.
        position("11", "청계산입구", "광교", "1", sttus="2"),
        # 광교에 있으면서 종착이 신사다. updnLine 은 직전 운행의 값이라 상행으로 뒤집혀야 한다.
        position("12", "광교", "신사", "1"),
        # 현재역과 종착역이 같다. 운행을 마치는 중이라 응답에서 빠져야 한다.
        position("13", "정자", "정자", "1"),
    ),
    # 2026-09-24 00:59 에 실제로 받은 모습이다. 약수로 가는 막차인데 종착이 다음 운행의 구파발로 앞서
    # 바뀌었다. 시간표(동대입구 하행)가 약수행으로 되돌려야 한다.
    "3호선": positions(
        position("3423", "동대입구", "구파발", "1", recptn="2026-09-24 00:59:24", last="1"),
    ),
    # 2026-09-23 10:50 에 받은 모습이다. 북한산우이로 가는 열차인데 종착이 다음 운행의 신설동으로 왔다.
    # 이 노선은 시간표가 없어 updnLine 으로 방향만 정하고 종착역은 비운다.
    "우이신설선": positions(
        position("1147", "가오리", "신설동", "1", recptn="2026-09-23 10:50:48"),
    ),
    # 광명셔틀은 금천구청과 광명 사이를 달리는 동안 실시간 위치에 오지 않는다(DESIGN.md 2.2.3). 그 때를
    # 흉내내 셔틀 없이 같은 선로를 지나는 다른 1호선 열차만 준다.
    "1호선": positions(
        position("0707", "영등포", "천안", "1"),
    ),
}

STATIONS = {
    "동대입구": [{"STATION_CD": "0322", "STATION_NM": "동대입구", "LINE_NUM": "03호선", "FR_CODE": "332"}],
}

# 광명셔틀 뷰의 역. 역 코드는 시간표를 고르는 데만 쓰므로 실제 값이 아니어도 된다.
SHUTTLE_STATIONS = ["영등포", "신도림", "구로", "가산디지털단지", "독산", "금천구청", "광명"]
SHUTTLE_CODES = {name: "GM%d" % i for i, name in enumerate(SHUTTLE_STATIONS)}
for _name, _code in SHUTTLE_CODES.items():
    STATIONS[_name] = [{"STATION_CD": _code, "STATION_NM": _name, "LINE_NUM": "01호선", "FR_CODE": _code}]


def shuttle_rows(code, inout):
    """2분 전에 광명을 떠나 영등포로 가는 셔틀 7012. 역마다 2분 30초 달리고 30초 선다.

    시각은 앱과 같은 운행일 기준으로 적는다. 04시 전이면 전날 운행일이라 24시를 더한다.
    """
    station = next((name for name, c in SHUTTLE_CODES.items() if c == code), None)
    if station is None or inout != "1":
        return []
    now = datetime.now().replace(microsecond=0)
    day = now.date() - timedelta(days=1) if now.hour < 4 else now.date()
    base = datetime.combine(day, datetime.min.time())

    def clock(t):
        s = int((t - base).total_seconds())
        return "%02d:%02d:%02d" % (s // 3600, s % 3600 // 60, s % 60)

    order = list(reversed(SHUTTLE_STATIONS))
    at = now - timedelta(minutes=2)
    for i, name in enumerate(order):
        if i == 0:
            arrive, leave = "00:00:00", clock(at)
        else:
            at += timedelta(minutes=2, seconds=30)
            arrive = clock(at)
            if i < len(order) - 1:
                at += timedelta(seconds=30)
            leave = clock(at) if i < len(order) - 1 else "00:00:00"
        if name == station:
            return [{"LINE_NUM": "01호선", "STATION_CD": code, "STATION_NM": name, "TRAIN_NO": "K7012",
                     "ARRIVETIME": arrive, "LEFTTIME": leave, "SUBWAYSNAME": "광명", "SUBWAYENAME": "영등포",
                     "WEEK_TAG": "1", "INOUT_TAG": "1", "EXPRESS_YN": "G"}]
    return []

# 역 코드와 상하행(1 상행, 2 하행)마다의 시간표. 요일 구분(WEEK_TAG)은 가리지 않고 같은 것을 준다.
TIMETABLES = {
    ("0322", "2"): [
        {
            "LINE_NUM": "03호선", "STATION_CD": "0322", "STATION_NM": "동대입구", "TRAIN_NO": "3423",
            "ARRIVETIME": "24:58:30", "LEFTTIME": "24:59:00", "SUBWAYSNAME": "대화", "SUBWAYENAME": "약수",
            "WEEK_TAG": "1", "INOUT_TAG": "2", "EXPRESS_YN": "G",
        },
    ],
}

NO_DATA = {
    "status": 500,
    "code": "INFO-200",
    "message": "해당하는 데이터가 없습니다.",
    "link": "",
    "developerMessage": "",
    "total": 0,
}

# 시간표 쪽 API 는 데이터가 없을 때 모양이 다르다. 최상위에 RESULT 만 온다.
NO_ROWS = {"RESULT": {"CODE": "INFO-200", "MESSAGE": "해당하는 데이터가 없습니다."}}


def rows(service, found):
    if not found:
        return NO_ROWS
    return {service: {"list_total_count": len(found), "RESULT": {"CODE": "INFO-000", "MESSAGE": "정상 처리되었습니다"}, "row": found}}


def respond(path):
    parts = [unquote(p) for p in path.split("/") if p]
    if MODE != "trains":
        return NO_DATA
    if "realtimePosition" in parts:
        return POSITIONS.get(parts[-1], POSITIONS["신분당선"])
    if "SearchInfoBySubwayNameService" in parts:
        return rows("SearchInfoBySubwayNameService", STATIONS.get(parts[-1], []))
    if "SearchSTNTimeTableByIDService" in parts:
        code, _week, inout = parts[-3:]
        return rows("SearchSTNTimeTableByIDService", TIMETABLES.get((code, inout)) or shuttle_rows(code, inout))
    return POSITIONS["신분당선"]


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        body = json.dumps(respond(self.path), ensure_ascii=False).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *args):
        pass


if __name__ == "__main__":
    HTTPServer(("127.0.0.1", PORT), Handler).serve_forever()
