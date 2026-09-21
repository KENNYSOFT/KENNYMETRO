package kr.kennysoft.kennymetro.domain

/**
 * 노선과 그 역 순서.
 * <p>
 * 역 순서를 아는 것이 방향 판정의 근거다. 실시간 API 의 `updnLine` 은 종착역에서
 * 회차를 기다리는 열차에서 진행 방향과 어긋나므로, 현재역과 종착역의 자리를 견주어
 * 방향을 정한다.
 */
enum class Line(
    val lineName: String,
    val stations: List<String>,
) {
    SHINBUNDANG(
        lineName = "신분당선",
        stations = listOf(
            "신사", "논현", "신논현", "강남", "양재", "양재시민의숲", "청계산입구", "판교",
            "정자", "미금", "동천", "수지구청", "성복", "상현", "광교중앙", "광교",
        ),
    ),
    ;

    /** 역이 노선에서 몇 번째인지. 모르는 역이면 null. */
    fun indexOf(station: String): Int? = stations.indexOf(station).takeIf { it >= 0 }

    companion object {
        fun byLineName(lineName: String): Line? = entries.find { it.lineName == lineName }
    }
}
