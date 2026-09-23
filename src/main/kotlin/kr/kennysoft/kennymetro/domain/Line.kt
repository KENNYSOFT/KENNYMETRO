package kr.kennysoft.kennymetro.domain

/**
 * 실시간 위치를 어디서 받는지.
 *
 * 서울시 API 는 인증키당 하루 1,000회 제한이 있고 용인경전철은 제한이 없다. 예산 계산이
 * 갈리므로 소스를 노선의 속성으로 둔다.
 */
enum class LineSource {
    SEOUL,
    EVERLINE,
}

/**
 * 노선과 그 역 순서.
 *
 * <p>
 * 역 순서를 아는 것이 방향 판정의 근거다. 실시간 API 의 `updnLine` 은 종착역에서 회차를
 * 기다리는 열차에서 진행 방향과 어긋나므로, 현재역과 종착역의 자리를 견주어 방향을 정한다.
 * 다만 순환선은 그 비교가 성립하지 않아 `updnLine` 을 그대로 쓴다.
 */
data class Line(
    val slug: String,
    val name: String,
    /**
     * 실시간 API 에 넘길 노선명.
     *
     * <p>
     * 화면에 세우는 이름과 가르는 것은 분기 때문이다. 1호선은 구로에서 인천과 신창으로
     * 갈려 역을 한 줄로 세울 수 없어 계통마다 뷰를 따로 두는데, API 는 그것을 구분하지
     * 않고 "1호선" 하나로 전체 열차를 준다. 이 값이 같은 뷰끼리는 받아 온 것을 나눠 쓴다.
     */
    val apiName: String,
    val color: String,
    val source: LineSource,
    val circular: Boolean,
    val upLabel: String,
    val downLabel: String,
    val stations: List<String>,
    /** 화면이 기본으로 보여줄 구간. 전 구간이면 null. */
    val focusFrom: Int?,
    val focusTo: Int?,
    val keyStations: List<String>,
    val fleet: Fleet?,
    /**
     * 이 노선 열차가 평소 가는 종착역. 여기 없는 목록 안의 종착역은 그 사이면 단축, 바깥이면
     * 연장이다. 수인분당선은 왕십리가 평소 종착이라 청량리행이 연장으로 잡힌다. 순환선은 바깥이
     * 없어 여기 없는 종착역이 모두 단축이다.
     */
    val termini: Set<String>,
    /**
     * 목록에 없는 종착역이 이 뷰에서 닿는 자리.
     *
     * <p>
     * 1호선 경인 뷰에서 신창행은 구로까지 같은 선로를 달린다. 그 열차를 버리면 종각에서
     * 영등포로 가려는 사람이 탈 수 있는 열차의 절반을 못 본다. 그래서 갈라지는 역을 종착처럼
     * 두고 방향을 정한다. 형제 뷰(같은 API 노선)의 역에서 저절로 계산되고, 어느 뷰에도 없는
     * 종착역(서동탄, 광명)은 lines.yml 의 beyond 로 적는다.
     */
    val anchors: Map<String, Destination>,
    /**
     * 열차가 down 쪽으로만 지나는 역.
     *
     * <p>
     * 6호선 응암 순환 구간(역촌에서 구산까지)은 한 방향으로만 돌고, 이 목록 순서로 세우면 그
     * 방향이 down 이다. 원문이 방면 없이 적은 환승 문(불광, 연신내)을 이 역들에서는 화면의 오른쪽
     * 칸에만 둔다. 방면 없는 줄은 보통 양쪽에 두지만 이 역에는 왼쪽으로 가는 열차가 들어오지 않는다.
     */
    val downOnly: Set<String>,
    /**
     * 열차가 서지도 지나지도 않는 역.
     *
     * <p>
     * GTX-A 삼성역이 열리지 않아 운정중앙에서 서울역까지와 수서에서 동탄까지가 따로 다닌다. 두
     * 구간을 한 줄로 세우려고 목록에 두고 화면은 흐리게 세운다. 그 바로 옆 역(서울역, 수서)은 한쪽
     * 구간의 끝이라 목록의 끝 역처럼 열차가 한쪽에서만 들어온다.
     */
    val noService: Set<String>,
    /** 환승 문 파일 이름(`transfer/<이름>-doors.csv`). 지선마다 뷰를 둔 노선은 한 파일을 나눠 쓴다. */
    val transferFile: String,
) {

    /** 실시간 위치를 공유하는 단위. 이 값이 같으면 호출도 캐시도 한 벌이다. */
    val cacheKey: String get() = "$source:$apiName"

    private val terminiIndices: List<Int> = termini.mapNotNull { indexOf(it) }

    /** 역이 노선에서 몇 번째인지. 모르는 역이면 null. */
    fun indexOf(station: String): Int? = stations.indexOf(station).takeIf { it >= 0 }

    /** 그 자리에 열차가 서는 역이 있는지. 목록 밖이거나 열차가 다니지 않는 역이면 false. */
    fun servedAt(index: Int): Boolean = index in stations.indices && stations[index] !in noService

    /** 그 종착역으로 가는 열차가 이 뷰에서 어디를 향하고 어떤 운행인지. 모르는 종착역이면 null. */
    fun destinationOf(name: String): Destination? {
        val own = indexOf(name) ?: return anchors[name]
        return Destination(own, serviceAt(own))
    }

    /** 목록 안의 역을 종착으로 하는 열차가 어떤 운행인지. */
    fun serviceAt(index: Int): ServiceKind {
        if (stations[index] in termini) return ServiceKind.NORMAL
        if (circular) return ServiceKind.SHORT
        return if (index < terminiIndices.min() || index > terminiIndices.max()) ServiceKind.EXTENSION else ServiceKind.SHORT
    }
}

/**
 * 열차가 그 뷰에서 어떻게 달리는지. 화면이 색으로 가른다.
 */
enum class ServiceKind {
    /** 평소 가는 종착역까지 간다. */
    NORMAL,

    /** 평소 종착역보다 먼저 끝난다. 타기 전에 알아야 한다. */
    SHORT,

    /** 평소 종착역을 지나 더 간다. 드물게 있는 운행이라 알아볼 수 있어야 한다. */
    EXTENSION,

    /** 이 뷰의 역을 지나다 다른 계통으로 갈라져 나간다. */
    BRANCH,
}

/** 종착역이 이 뷰의 몇 번째 역에 닿는지와 그 운행의 종류. */
data class Destination(val index: Int, val service: ServiceKind)

/**
 * 편성번호로 차량을 특정할 수 있는 노선의 도입 차수.
 *
 * <p>
 * 신분당선만 열차번호가 편성번호다. 회차 순간에 번호가 유지되는 것으로 확인했다
 * (DESIGN.md 2.2). 다른 노선은 4자리 운행번호라 어느 차량인지 알 수 없다.
 */
data class Fleet(
    val sourceDocument: String,
    val sourceRevision: String,
    val sourceUrl: String,
    val generations: List<FleetGeneration>,
) {

    /** 그 열차가 몇 차분인지. 범위 밖이거나 숫자가 아니면 null. */
    fun labelFor(trainNo: String): String? {
        val no = trainNo.toIntOrNull() ?: return null
        return generations.find { no >= it.from && no <= it.to }?.label
    }
}

data class FleetGeneration(
    val label: String,
    val color: String,
    val from: Int,
    val to: Int,
    val note: String,
    val url: String,
)
