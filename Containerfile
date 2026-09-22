# native 바이너리는 CI 에서 빌드해 컨텍스트에 놓인 것을 담는다.
#
# glibc 정렬: 바이너리는 동적 링크이므로 빌드 환경의 glibc 가 실행 환경보다 낮거나 같아야
# 한다. CI runner 가 ubuntu-24.04-arm 이므로 런타임도 ubuntu:24.04 로 맞춘다. Alpine 은
# musl 이라 glibc 바이너리가 실행되지 않으므로 쓰지 않는다.
FROM ubuntu:24.04

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates tzdata \
    && rm -rf /var/lib/apt/lists/*

# 시각을 다루는 서비스라 컨테이너 기본 UTC 를 그대로 두지 않는다.
ENV TZ=Asia/Seoul

COPY kennymetro /app/kennymetro

# 호스트에서 httpd 가 8080, ledger-memo 가 8081 을 쓰므로 8082 로 띄운다.
# --network=host 로 실행하면 이 값이 곧 호스트 포트다.
EXPOSE 8082

ENTRYPOINT ["/app/kennymetro"]
