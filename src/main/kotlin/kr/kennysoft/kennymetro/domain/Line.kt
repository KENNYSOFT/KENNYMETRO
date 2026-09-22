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
) {

    /** 역이 노선에서 몇 번째인지. 모르는 역이면 null. */
    fun indexOf(station: String): Int? = stations.indexOf(station).takeIf { it >= 0 }
}

/**
 * 편성번호로 차량을 특정할 수 있는 노선의 도입 차수.
 *
 * <p>
 * 신분당선만 열차번호가 편성번호다. 회차 순간에 번호가 유지되는 것으로 확인했다
 * (DESIGN.md 2.2). 다른 노선은 4자리 운행번호라 어느 차량인지 알 수 없다.
 */
data class Fleet(
    val sourceName: String,
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
