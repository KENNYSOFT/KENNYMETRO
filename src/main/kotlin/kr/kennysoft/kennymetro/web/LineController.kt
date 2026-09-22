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
    )
}

data class LinesResponse(
    val lines: List<LineView>,
    /** 아직 아무것도 고르지 않은 브라우저가 보여줄 노선과 그 순서. */
    val preset: List<String>,
    /** 환승 문 위치에 노선 동그라미를 붙이는 데 쓴다. */
    val transferColors: Map<String, String>,
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
)
