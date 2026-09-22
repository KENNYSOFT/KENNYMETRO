package kr.kennysoft.kennymetro

import kr.kennysoft.kennymetro.config.MetroProperties
import kr.kennysoft.kennymetro.domain.Line
import kr.kennysoft.kennymetro.domain.LineCatalog
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource
import org.springframework.core.io.ClassPathResource

/**
 * 테스트가 실제 `lines.yml` 을 읽어 쓰게 한다.
 *
 * <p>
 * 역 목록을 테스트에 복제하면 설정이 바뀔 때 조용히 어긋난다. 그러면 테스트는 통과하는데
 * 실제 화면에서는 열차가 사라지는 상태가 된다. 같은 파일을 읽으면 그 어긋남이 생기지 않고,
 * 덤으로 설정 자체의 오타도 여기서 걸린다.
 *
 * <p>
 * 스프링 컨텍스트를 띄우지 않고 `Binder` 로 직접 바인딩한다. 이 검증에 필요한 것은 파일과
 * 바인딩 규칙뿐이라 컨텍스트를 띄울 값이 없다.
 */
object TestLines {

    val catalog: LineCatalog by lazy {
        val yaml = YamlPropertiesFactoryBean().apply { setResources(ClassPathResource("lines.yml")) }
        val flat = yaml.getObject() ?: error("lines.yml 을 읽지 못했다")
        val source = MapConfigurationPropertySource(flat.entries.associate { it.key.toString() to it.value })
        val properties = Binder(source).bind("metro", MetroProperties::class.java).get()
        LineCatalog(properties)
    }

    fun bySlug(slug: String): Line = catalog.bySlug(slug) ?: error("lines.yml 에 없는 노선이다: $slug")

    val shinbundang: Line get() = bySlug("shinbundang")
    val line2: Line get() = bySlug("line2")
    val everline: Line get() = bySlug("everline")
}
