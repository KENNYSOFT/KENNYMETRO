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
    /**
     * 실시간 API 가 우리와 이름부터 다르게 적는 역. 키가 API 쪽, 값이 우리 목록의 표기다.
     * 괄호 부기와 2호선 꼬리말은 여기 적지 않아도 떼어진다(`StationNames`).
     */
    val stationAliases: Map<String, String> = emptyMap(),
    /**
     * 노선마다 이름이 다른 환승역. 4호선은 총신대입구, 7호선은 이수라고 부른다. 환승 문을
     * 대조할 때 같은 역으로 본다.
     */
    val transferNames: Map<String, String> = emptyMap(),
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
    /** 이 노선 열차가 평소 가는 종착역. 비어 있으면 역 목록의 양 끝이다. */
    val termini: List<String> = emptyList(),
    /**
     * 어느 뷰의 목록에도 없는 종착역과, 그 열차가 이 뷰를 떠나는 역. 1호선 서동탄행은
     * 병점에서 갈라진다. 형제 뷰에 있는 종착역은 적지 않아도 저절로 계산된다.
     */
    val beyond: Map<String, String> = emptyMap(),
    /**
     * 열차가 down 쪽으로만 지나는 역. 6호선 응암 순환 구간이 한 방향으로만 돈다. 방면 없이 적힌
     * 환승 문을 화면의 어느 칸에 둘지 정하는 데 쓴다.
     */
    val downOnly: List<String> = emptyList(),
    /**
     * 열차가 서지도 지나지도 않는 역. GTX-A 삼성역이 열리지 않아 서울역과 수서 사이로 열차가
     * 다니지 않는다. 끊긴 두 구간을 한 줄로 세우려고 목록에 두고, 화면은 흐리게 세운다.
     */
    val noService: List<String> = emptyList(),
    /**
     * 환승 문 파일 이름(`transfer/<이름>-doors.csv`). 적지 않으면 slug 다. 지선마다 뷰를 둔
     * 노선은 원본 문서가 하나라 파일도 하나를 나눠 쓴다.
     */
    val transferFile: String? = null,
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
