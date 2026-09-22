package kr.kennysoft.kennymetro.config

import org.springframework.aot.hint.BindingReflectionHintsRegistrar
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar

/**
 * 노선 설정의 중첩 타입을 native image 에 등록한다.
 *
 * <p>
 * `@ConfigurationProperties` 는 AOT 가 알아서 처리하지만 최상위 타입에서 멈춘다. 리스트
 * 안에 든 데이터 클래스까지는 따라가지 않아, native 바이너리에서
 * `KotlinReflectionInternalError: Class not found` 로 기동이 실패한다.
 *
 * <p>
 * `BindingReflectionHintsRegistrar` 는 타입 그래프를 재귀로 훑으므로 중첩이 더 깊어져도
 * 여기를 고칠 일이 없다. 클래스를 하나씩 세면 새 타입을 더할 때마다 빠뜨리게 된다.
 *
 * <p>
 * <b>JVM 테스트로는 잡히지 않는다.</b> 메타데이터가 그대로 있어 전부 통과하고 native
 * 바이너리에서만 터진다. `scripts/smoke-test.sh` 의 노선 목록 검사가 이것을 확인한다.
 */
class ConfigBindingRuntimeHints : RuntimeHintsRegistrar {

    private val binding = BindingReflectionHintsRegistrar()

    override fun registerHints(hints: RuntimeHints, classLoader: ClassLoader?) {
        binding.registerReflectionHints(hints.reflection(), MetroProperties::class.java)
    }
}
