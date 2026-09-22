# KENNYMETRO

수도권 전철 실시간 운행 정보와 시간표를 한 화면에 모으는 개인용 웹페이지.

필요한 정보가 앱과 홈페이지 다섯 군데에 흩어져 있었다. 실시간 위치는 또타지하철 앱, 용인경전철은 그 홈페이지, 빠른 환승 문 위치는 나무위키, 신분당선 시간표는 신분당선 홈페이지, 수인분당선 행선지는 코레일 홈페이지를 각각 열어야 했다. 이 페이지는 그것을 한 곳에서 본다.

설계 문서: [DESIGN.md](./DESIGN.md)

## 지금 되는 것

신분당선 실시간 열차 위치. 노선도 가운데에 역을 세우고 왼쪽에 신사 방면, 오른쪽에 광교 방면 열차를 얹는다.

**행선지를 함께 보여주는 것이 요점이다.** 신분당선에는 광교까지 가지 않는 정자행이 섞여 있어서, 시각만 알면 헛걸음한다. 노선 끝까지 가지 않는 열차와 막차는 따로 표시한다.

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

http://localhost:8080 에서 열린다. 헬스체크는 `/actuator/health`, 열차 목록은 `/api/lines/shinbundang/trains` 다.

## 호출 예산

실시간 지하철 API 는 **인증키당 하루 1,000회**다. 운행시간 19시간을 나누면 70초에 한 번이라, 방문자마다 API 를 부르면 예산이 몇 분 만에 마른다. 그래서 서버가 노선당 한 벌만 받아 70초간 캐시하고 모든 방문자가 그것을 나눠 본다. 캐시가 낡았을 때 이미 다른 요청이 받아오는 중이면 기다리지 않고 직전 값을 준다.

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
podman pull ghcr.io/kennysoft/kennymetro:latest
podman rm -f kennymetro
podman run -d --name kennymetro --network=host --restart=always \
  --env-file ~/.config/kennymetro/env \
  ghcr.io/kennysoft/kennymetro:latest
```

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

## 노선 커버리지 확인

노선을 추가하기 전에 그 노선이 실시간 API 에 실제로 들어오는지 확인한다.

```sh
SEOUL_SUBWAY_API_KEY='<키>' ./scripts/probe-coverage.sh
```

**운행 시간대에 돌려야 한다.** 이 API 는 "지원하지 않는 노선" 과 "지금 열차가 없음" 을 같은 응답으로 돌려주므로, 막차 뒤에 돌리면 둘을 구분할 수 없다.
