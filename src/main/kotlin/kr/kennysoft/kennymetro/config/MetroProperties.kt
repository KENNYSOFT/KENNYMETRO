package kr.kennysoft.kennymetro.config

import kr.kennysoft.kennymetro.domain.LineSource
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * `lines.yml` 의 노선 정의.
 *
 * <p>
 * 역 목록이 175개라 코드에 박지 않고 설정으로 뺐다. 여기 담긴 값은 원본일 뿐이고
 * 검증과 인덱스 계산은 `LineCatalog` 가 기동 시 한 번 한다.
 */
@ConfigurationProperties(prefix = "metro")
data class MetroProperties(
    val lines: List<LineConfig>,
)

data class LineConfig(
    val slug: String,
    val name: String,
    val color: String,
    val source: LineSource,
    /** 순환선은 종착역으로 방향을 정할 수 없어 `updnLine` 을 그대로 쓴다. 2호선만 해당한다. */
    val circular: Boolean = false,
    /** `updnLine` 0 과 1 에 붙일 이름. 화면에서 up 이 왼쪽이다. */
    val upLabel: String,
    val downLabel: String,
    val stations: List<String>,
    /** [시작역, 끝역]. 화면이 기본으로 보여줄 구간이고 비어 있으면 전 구간이다. */
    val focus: List<String> = emptyList(),
    val keyStations: List<String> = emptyList(),
    /** 편성번호로 차량을 특정할 수 있는 노선에만 있다. */
    val fleet: FleetConfig? = null,
)

data class FleetConfig(
    /** 편성 범위를 옮겨 온 문서와 그 판. 위키는 계속 고쳐지므로 판까지 적는다. */
    val sourceDocument: String,
    val sourceRevision: String,
    val sourceUrl: String,
    val generations: List<FleetGenerationConfig>,
)

data class FleetGenerationConfig(
    val label: String,
    val color: String,
    val from: Int,
    val to: Int,
    val note: String,
    /** 그 차수를 다루는 문단. 차수마다 따로 건다. */
    val url: String,
)
