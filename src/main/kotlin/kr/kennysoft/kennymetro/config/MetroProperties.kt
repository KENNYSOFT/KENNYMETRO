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
    /**
     * 처음 방문한 브라우저에 보여줄 노선과 그 순서.
     *
     * <p>
     * 노선을 다 담고 나면 무엇을 보여줄지 정해야 하는데, 그것은 사람마다 다르고 서버에
     * 계정을 두지 않으므로(DESIGN.md 1.3) 브라우저가 기억한다. 여기 값은 그 브라우저가
     * 아직 아무것도 고르지 않았을 때의 출발점이다. 각 노선의 관심 구간과 주요 역도 같은
     * 뜻의 기본값이다.
     */
    val preset: List<String> = emptyList(),
    /** 환승 대상 노선의 색. 우리가 담지 않는 노선까지 들어 있어 노선 정의와 따로 둔다. */
    val transferColors: Map<String, String> = emptyMap(),
)

data class LineConfig(
    val slug: String,
    val name: String,
    /** 실시간 API 에 넘길 노선명. 표시 이름과 같으면 적지 않는다. */
    val apiName: String? = null,
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
