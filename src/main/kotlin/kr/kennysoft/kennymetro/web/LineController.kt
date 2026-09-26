package kr.kennysoft.kennymetro.web

import kr.kennysoft.kennymetro.domain.Fleet
import kr.kennysoft.kennymetro.domain.Line
import kr.kennysoft.kennymetro.domain.LineCatalog
import kr.kennysoft.kennymetro.domain.LineSource
import kr.kennysoft.kennymetro.seoul.SeoulSubwayProperties
import kr.kennysoft.kennymetro.seoul.TrainPositionService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 노선 목록과 메타. 역 목록과 관심 구간처럼 바뀌지 않는 것을 한 번에 내려보내,
 * 열차 위치 폴링은 가벼운 응답만 주고받게 한다.
 */
@RestController
class LineController(
    private val catalog: LineCatalog,
    private val trainPositionService: TrainPositionService,
    private val properties: SeoulSubwayProperties,
) {

    @GetMapping("/api/lines")
    fun lines(): LinesResponse = LinesResponse(
        lines = ArrayList(catalog.all().map { it.toView() }),
        preset = ArrayList(catalog.preset()),
        transferColors = LinkedHashMap(catalog.transferColors()),
        stationAliases = LinkedHashMap(catalog.stationAliases()),
        apiCallBudget = properties.dailyCallBudget,
        apiCallCount = trainPositionService.apiCallCount(),
    )

    private fun Line.toView() = LineView(
        slug = slug,
        name = name,
        // 화면이 호출 수를 셀 때 쓴다. 이 값이 같은 뷰를 여럿 펼쳐도 호출은 한 번이다.
        apiName = apiName,
        color = color,
        circular = circular,
        upLabel = upLabel,
        downLabel = downLabel,
        stations = ArrayList(stations),
        focusFrom = focusFrom,
        focusTo = focusTo,
        keyStations = ArrayList(keyStations),
        fleet = fleet,
        // 용인경전철은 다른 엔드포인트라 하루 1,000회 예산에 들지 않는다.
        budgeted = source == LineSource.SEOUL,
        live = source != LineSource.NONE,
        extraDestinations = ArrayList(anchors.keys),
        downOnly = ArrayList(downOnly),
        noService = ArrayList(noService),
    )
}

data class LinesResponse(
    val lines: List<LineView>,
    /** 아직 아무것도 고르지 않은 브라우저가 보여줄 노선과 그 순서. */
    val preset: List<String>,
    /** 환승 문 위치에 노선 동그라미를 붙이는 데 쓴다. */
    val transferColors: Map<String, String>,
    /** API 가 우리와 다르게 적는 역명. `scripts/probe-coverage.sh` 가 역명을 대조할 때 쓴다. */
    val stationAliases: Map<String, String>,
    val apiCallBudget: Int,
    val apiCallCount: Long,
)

data class LineView(
    val slug: String,
    val name: String,
    val apiName: String,
    val color: String,
    val circular: Boolean,
    val upLabel: String,
    val downLabel: String,
    val stations: List<String>,
    val focusFrom: Int?,
    val focusTo: Int?,
    val keyStations: List<String>,
    val fleet: Fleet?,
    val budgeted: Boolean,
    /** 실시간 위치를 받는 노선인지. 아니면 화면이 열차를 부르지 않고 추후 지원 예정이라고 적는다. */
    val live: Boolean,
    /**
     * 역 목록에는 없지만 이 뷰가 알아듣는 종착역. 다른 계통으로 갈라지거나 목록 밖으로 더 가는
     * 열차의 종착이다. `scripts/probe-coverage.sh` 가 역명을 대조할 때 오탐을 막는 데 쓴다.
     */
    val extraDestinations: List<String>,
    /** 열차가 down 쪽으로만 지나는 역. 화면은 그 역의 왼쪽 화살표를 그리지 않는다. */
    val downOnly: List<String>,
    /** 열차가 서지도 지나지도 않는 역. 화면은 흐리게 세우고 화살표를 그리지 않는다. */
    val noService: List<String>,
)
