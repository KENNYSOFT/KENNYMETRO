# KENNYMETRO

수도권 전철 실시간 운행 정보와 시간표를 한 화면에 모으는 개인용 웹페이지.

필요한 정보가 앱과 홈페이지 다섯 군데에 흩어져 있었다. 실시간 위치는 또타지하철 앱, 용인경전철은 그 홈페이지, 빠른 환승 문 위치는 나무위키, 신분당선 시간표는 신분당선 홈페이지, 수인분당선 행선지는 코레일 홈페이지를 각각 열어야 했다. 이 페이지는 그것을 한 곳에서 본다.

설계 문서: [DESIGN.md](./DESIGN.md)

## 지금 되는 것

**수도권 전철 실시간 열차 위치.** 서울시 실시간 API 가 담는 노선과 용인경전철을 담는다. 노선도 가운데에 역을 세우고 좌우로 방면별 열차를 얹는다.

행선지를 함께 보여주는 것이 요점이다. 신분당선에는 광교까지 가지 않는 정자행이 섞여 있어서, 시각만 알면 헛걸음한다. 그래서 열차를 평소 종착역 기준으로 **단축**, **연장**, **다른 계통**으로 갈라 색을 다르게 칠한다. 수인분당선은 왕십리가 평소 종착이라 왕십리행은 그대로 두고 청량리행을 연장으로 표시한다. 급행과 막차는 따로 붙인다.

**분기 노선은 계통마다 뷰를 나눈다.** 1호선은 구로에서 인천 방면과 신창 방면으로 갈려 역을 한 줄로 세울 수 없다. `1호선 (경인)` 과 `1호선 (경부)` 를 따로 두되 API 로는 한 노선이라 실시간 위치는 한 번만 받는다. 다른 계통 열차도 갈라지는 역까지는 함께 그린다. 5호선 하남과 마천, 경의중앙선 용산과 서울역, 2호선 성수지선과 신정지선, GTX-A 의 두 구간도 같다.

**보여줄 노선과 관심 구간을 고른다.** 전 노선을 다 그리면 800역에 가까워 정작 볼 곳을 스크롤로 찾게 된다. 노선을 골라 순서를 정하고, 노선마다 관심 구간과 굵게 세울 역을 정한다. 구간 밖 열차는 세어서 개수만 알린다. 고른 값은 서버가 아니라 그 브라우저에 남고, 아무것도 고르지 않았으면 `lines.yml` 의 프리셋으로 시작한다.

**신분당선은 몇 차분 차량인지 색으로 갈린다.** 이 노선만 열차번호가 편성번호라 차량을 특정할 수 있다 - 회차 순간에도 번호가 유지되는 것으로 확인했다. 다른 노선은 4자리 운행번호라 어느 차량인지 알 수 없어 차수를 매기지 않는다.

**빠른 환승 문 위치.** 환승역 이름을 누르면 몇 번째 칸 몇 번 문에서 내려야 하는지가 역 이름 양옆에 나온다. 왼쪽 칸은 왼쪽 열차, 오른쪽 칸은 오른쪽 열차에서 내릴 때의 문이고, 같은 문으로 가는 노선은 한 줄로 합친다. 종착역처럼 열차가 한쪽으로만 들어오는 역은 그쪽에만 나온다. 담은 노선의 환승역을 모두 담는다. 같은 승강장 건너편에서 갈아타는 역은 "모든 문" 이나 `1-1 ~ 6-4` 처럼 나오고, 출퇴근 때 돌아가는 길 같은 것은 그 역의 비고로 붙는다.

지금 탄 열차가 어느 쪽으로 가는지에 따라 내릴 문이 갈린다는 것이 요점이다. 양재에서 3호선으로 갈아탄다면 신사행은 1-1, 광교행은 6-4 다. 국가철도공단 공식 데이터에는 이 구분이 없어 나무위키를 쓴다 ([DESIGN.md](./DESIGN.md) 2.3). 어느 문서의 몇 번째 판에서 옮겼는지는 노선마다 화면에 적는다.

## 스택

| 구분 | 사용 |
|---|---|
| 백엔드 | Kotlin 2.3.21, Spring Boot 4.1.0 |
| 저장소 | 없음 (외부 데이터를 받아 메모리에 캐시) |
| 런타임 | GraalVM native image (aarch64) |
| 프론트 | 빌드 없는 단일 HTML, vanilla JS |

## 로컬 실행

JDK 25 가 필요하다. 서울시 실시간 지하철 인증키는 기본값이 없어 주입하지 않으면 기동이 실패한다.

인증키는 [서울 열린데이터광장](https://data.seoul.go.kr/)에서 발급받는다. **일반 인증키가 아니라 "실시간 지하철 인증키" 를 따로 신청해야 한다** - 신청 화면에 버튼이 나뉘어 있고, 일반 키로는 실시간 API 가 열리지 않는다.

```sh
export SEOUL_SUBWAY_API_KEY='<발급받은 키>'
./gradlew bootRun
```

http://localhost:8080 에서 열린다. 헬스체크는 `/actuator/health`, 노선 목록은 `/api/lines`, 열차 위치는 `/api/lines/{노선}/trains`, 환승 문 위치는 `/api/lines/{노선}/transfers` 다. 노선 이름은 [lines.yml](./src/main/resources/lines.yml) 에서 정하고 `/api/lines` 가 전부 돌려준다.

## 호출 예산

실시간 지하철 API 는 **인증키당 하루 1,000회**다. 운행시간 19시간을 나누면 70초에 한 번이라, 방문자마다 API 를 부르면 예산이 몇 분 만에 마른다. 그래서 서버가 노선당 한 벌만 받아 70초간 캐시하고 모든 방문자가 그것을 나눠 본다. 캐시가 낡았을 때 이미 다른 요청이 받아오는 중이면 기다리지 않고 직전 값을 준다.

**노선이 늘면 예산도 그만큼 나뉜다.** 70초 주기는 노선이 하나일 때의 값이고, 네 노선을 한꺼번에 폴링하면 한도의 네 배가 된다. 그래서 주기를 늘리는 대신 **펼친 노선만 부른다** - 첫 방문은 한 노선만 펼친 채로 시작하고, 나머지는 눌러서 펼친다. 화면 맨 위에 오늘 나간 호출 수와, 갱신 한 번에 몇 회가 나가는지가 보인다.

**같은 API 노선을 보는 뷰끼리는 한 번만 받는다.** `1호선 (경인)` 과 `1호선 (경부)` 를 둘 다 펼쳐도 호출은 한 번이다.

용인경전철은 서울시 API 가 아니라 공식 실시간 페이지의 엔드포인트를 직접 부르므로 이 예산에 들지 않는다.

활용사례 갤러리에 인증키와 함께 콘텐츠를 등록하면 이 제한이 풀린다.

## 배포

GitHub Actions 가 ARM64 runner 에서 native 바이너리를 만들고, 스모크 테스트를 통과하면 이미지를 GHCR 에 올린다. 서버는 그 이미지를 받아 컨테이너를 재생성한다. `main` push 에서만 이미지가 올라가고, 문서만 바뀐 push 는 빌드하지 않는다.

**native 빌드는 크로스 컴파일이 불가능하다.** 서버가 aarch64 이므로 CI 도 ARM runner 여야 하고, x86 산출물은 서버에서 실행되지 않는다.

### 최초 셋업

ledger-memo 가 이미 도는 서버라 httpd 와 Podman 은 갖춰져 있다. 아래만 추가한다.

**1. 인증키 파일** (권한 600). `podman run -e` 로 넘기면 shell history 와 `ps` 에 남으므로 env 파일로만 다룬다.

```sh
mkdir -p ~/.config/kennymetro
cat > ~/.config/kennymetro/env <<'EOF'
SEOUL_SUBWAY_API_KEY=<발급받은 키>
SERVER_PORT=8082
EOF
chmod 600 ~/.config/kennymetro/env
```

**포트는 8082 다.** 같은 호스트에서 httpd 가 8080, ledger-memo 가 8081 을 쓰고 있고 `--network=host` 로 띄우므로 겹치면 기동이 실패한다.

**2. 재생성 스크립트** `/usr/local/bin/deploy-kennymetro.sh`

```sh
#!/bin/sh
set -e
mkdir -p ~/.local/share/kennymetro
podman pull ghcr.io/kennysoft/kennymetro:latest
podman rm -f kennymetro
podman run -d --name kennymetro --network=host --restart=always \
  --env-file ~/.config/kennymetro/env \
  -v ~/.local/share/kennymetro:/app/data:Z \
  ghcr.io/kennysoft/kennymetro:latest
```

**볼륨은 호출 원장(`api-calls.log`) 하나를 위한 것이다.** 날짜별로 그 날 서울시 API 에 나간 호출 수가 한 줄씩 쌓인다. 안 붙여도 앱은 뜨지만 재생성할 때마다 0 부터 다시 세어, 화면의 남은 예산이 실제 한도와 어긋난다.

**`sudo` 로 실행하지 않는다.** `~/.config/kennymetro/env` 가 `/root` 쪽으로 해석되어 인증키 파일을 못 찾고, rootless podman 에서는 컨테이너도 따로 뜬다. 셋업한 사용자 그대로 실행한다.

native 기동이 0.1초 수준이라 무중단 배포 장치는 필요 없다. Podman 은 데몬이 없어 `--restart=always` 만으로는 호스트 재부팅 후 뜨지 않으므로 `podman-restart.service` 를 한 번 켜둔다.

**3. httpd VirtualHost.** 호스트 `/httpd-data/conf/` 에 추가한다. 인증서는 기존 `*.kennysoft.kr` 와일드카드를 그대로 참조해 certbot 갱신이 자동으로 반영되게 둔다.

```apache
<VirtualHost *:8443>
    ServerName metro.kennysoft.kr

    ProxyPreserveHost On
    RequestHeader set X-Forwarded-Proto "https"
    ProxyPass        / http://localhost:8082/
    ProxyPassReverse / http://localhost:8082/

    Protocols h2 h2c http/1.1
    TraceEnable off

    ErrorLog  /usr/local/apache2/logs/metro_error.log
    CustomLog /usr/local/apache2/logs/metro_access.log combined
</VirtualHost>
```

**4. 검증**은 `GET /actuator/health` 가 `UP` 인지로 한다.

### 스모크 테스트

CI 가 native 바이너리를 실제로 띄워 열차 목록을 왕복한다. 실제 API 는 부르지 않고 `scripts/stub-seoul-api.py` 를 쓴다 - 하루 1,000회 예산을 CI 가 깎으면 안 되고, 외부 네트워크에 의존하게 된다.

**열차가 0대인 응답까지 확인하는 것이 요점이다.** Kotlin 의 빈 컬렉션은 native 에서 직렬화가 깨지는데 JVM 테스트로는 잡히지 않고, 운행이 끝나면 매일 밤 그 상태가 된다.

로컬에서 돌리려면 `BINARY` 로 바이너리 경로를 준다.

```sh
BINARY=build/native/nativeCompile/kennymetro ./scripts/smoke-test.sh
```

## 노선 커버리지와 역명 확인

노선을 추가하거나 연장 구간이 생기면 두 가지를 확인한다. 그 노선이 실시간 API 에 실제로 들어오는지, 그리고 **역명 표기가 우리 목록과 같은지**다. 역명이 한 글자라도 다르면 그 역의 열차가 화면에서 조용히 사라진다.

```bash
SEOUL_SUBWAY_KEY='<키>' ./scripts/probe-coverage.sh
```

노선과 역 목록은 배포된 앱의 `/api/lines` 에서 받아 온다. 로컬을 보려면 `APP=http://localhost:8080` 을 함께 넘긴다.

**운행 시간대에 돌려야 한다.** 이 API 는 "지원하지 않는 노선" 과 "지금 열차가 없음" 을 같은 응답으로 돌려주므로, 막차 뒤에 돌리면 둘을 구분할 수 없다.

## 라이선스

`src/main/resources/transfer/` 의 환승 데이터와 `lines.yml` 의 편성 차수는 나무위키에서 옮겨 적은 것이라 **CC BY-NC-SA 2.0 KR** 을 따른다. 그 조건 때문에 이 사이트에는 광고와 유료 기능을 넣지 않고, 화면에 문서명과 판과 라이선스를 표시한다. 위키는 계속 고쳐지므로 문서명만으로는 옮긴 시점을 가리킬 수 없어 판까지 적는다.

동일조건변경허락이 코드로 번지지 않도록 데이터 파일에만 라이선스를 걸었다. 나머지 코드는 여기에 묶이지 않는다.
