package kr.kennysoft.kennymetro.config

import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar
import org.springframework.aot.hint.TypeReference
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.ImportRuntimeHints

/**
 * Kotlin 의 빈 컬렉션 싱글톤을 native image 에서 Jackson 이 다룰 수 있게 한다.
 *
 * `mapNotNull` 이나 `toList()` 는 결과가 비면 `kotlin.collections.EmptyList` 를 돌려준다.
 * 이는 Kotlin 내부 object 라서 native image 에 메타데이터가 없으면 jackson-module-kotlin 이
 * 직렬화하다 `KotlinReflectionInternalError: Unresolved class` 로 실패한다.
 *
 * <p>
 * 이 프로젝트는 매일 밤 그 상태가 된다. 운행이 끝나면 열차 목록이 비고 그 빈 목록이 그대로
 * 응답에 실린다.
 *
 * <p>
 * <b>JVM 테스트로는 잡히지 않는다.</b> JVM 에는 메타데이터가 그대로 있어 전부 통과하고 native
 * 바이너리에서만 터진다. `scripts/smoke-test.sh` 가 열차 0대 응답까지 왕복해 이것을 검증한다.
 */
class KotlinCollectionsRuntimeHints : RuntimeHintsRegistrar {

    override fun registerHints(hints: RuntimeHints, classLoader: ClassLoader?) {
        EMPTY_COLLECTION_TYPES.forEach { type ->
            hints.reflection().registerType(
                TypeReference.of(type),
                MemberCategory.INVOKE_PUBLIC_METHODS,
                MemberCategory.INVOKE_DECLARED_METHODS,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.PUBLIC_FIELDS,
                MemberCategory.DECLARED_FIELDS,
            )
        }
    }

    private companion object {
        /**
         * 빈 컬렉션 싱글톤 셋.
         *
         * 크기가 1일 때 쓰이는 `listOf(x)` 는 `java.util.Collections.singletonList` 라 Kotlin
         * 메타데이터가 필요 없어 대상이 아니다.
         */
        private val EMPTY_COLLECTION_TYPES = listOf(
            "kotlin.collections.EmptyList",
            "kotlin.collections.EmptyMap",
            "kotlin.collections.EmptySet",
        )
    }
}

@Configuration(proxyBeanMethods = false)
@ImportRuntimeHints(KotlinCollectionsRuntimeHints::class)
class NativeHintsConfig
