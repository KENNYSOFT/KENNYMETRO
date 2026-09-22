package kr.kennysoft.kennymetro.config

import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar

/**
 * 환승 데이터 CSV 를 native image 에 싣는다.
 *
 * <p>
 * `static/` 아래와 달리 임의 경로의 리소스는 Spring Boot 가 자동으로 등록해 주지 않는다.
 * 빠뜨리면 JVM 에서는 멀쩡히 읽히고 native 바이너리에서만 파일이 없는 것처럼 동작한다.
 * `scripts/smoke-test.sh` 가 실제 바이너리로 환승 엔드포인트를 왕복해 이것을 확인한다.
 */
class TransferDataRuntimeHints : RuntimeHintsRegistrar {

    override fun registerHints(hints: RuntimeHints, classLoader: ClassLoader?) {
        hints.resources().registerPattern("transfer/*.csv")
    }
}
