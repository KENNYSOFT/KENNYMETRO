#!/usr/bin/env python3
"""서울시 실시간 지하철 API 를 흉내내는 stub.

스모크 테스트가 실제 API 를 부르면 하루 1,000회 예산을 깎고 외부 네트워크에 의존하게 된다.

STUB_MODE 로 두 응답을 가른다.
  trains : 열차 3대 (그중 1대는 현재역과 종착역이 같아 응답에서 빠져야 한다)
  empty  : INFO-200 (운행 종료 시간대. 빈 목록 직렬화를 검증한다)
"""

import json
import os
from http.server import BaseHTTPRequestHandler, HTTPServer

MODE = os.environ.get("STUB_MODE", "trains")
PORT = int(os.environ.get("STUB_PORT", "18090"))


def position(train_no, current, terminal, updn):
    return {
        "subwayId": "1077",
        "subwayNm": "신분당선",
        "statnId": "1077000000",
        "statnNm": current,
        "trainNo": train_no,
        "statnTid": "1077000001",
        "statnTnm": terminal,
        "updnLine": updn,
        "trainSttus": "1",
        "directAt": "0",
        "lstcarAt": "0",
        "recptnDt": "2026-09-22 21:00:00",
    }


TRAINS = {
    "errorMessage": {"status": 200, "code": "INFO-000", "message": "정상 처리되었습니다.", "total": 3},
    "realtimePositionList": [
        position("11", "청계산입구", "광교", "1"),
        # 광교에 있으면서 종착이 신사다. updnLine 은 직전 운행의 값이라 상행으로 뒤집혀야 한다.
        position("12", "광교", "신사", "1"),
        # 현재역과 종착역이 같다. 운행을 마치는 중이라 응답에서 빠져야 한다.
        position("13", "정자", "정자", "1"),
    ],
}

EMPTY = {
    "status": 500,
    "code": "INFO-200",
    "message": "해당하는 데이터가 없습니다.",
    "link": "",
    "developerMessage": "",
    "total": 0,
}


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        body = json.dumps(TRAINS if MODE == "trains" else EMPTY, ensure_ascii=False).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *args):
        pass


if __name__ == "__main__":
    HTTPServer(("127.0.0.1", PORT), Handler).serve_forever()
