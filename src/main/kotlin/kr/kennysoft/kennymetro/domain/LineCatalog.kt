package kr.kennysoft.kennymetro.domain

import kr.kennysoft.kennymetro.config.FleetConfig
import kr.kennysoft.kennymetro.config.LineConfig
import kr.kennysoft.kennymetro.config.MetroProperties
import org.springframework.stereotype.Component
import kotlin.math.abs

/**
 * 설정에 적힌 노선을 검증해 들고 있는다.
 *
 * <p>
 * 역명이 서울시 API 표기와 한 글자라도 다르면 그 역의 열차가 화면에서 조용히 사라진다.
 * 그런 실수는 배포 전에 드러나야 하므로, 노선에 없는 역을 관심 구간이나 주요 역으로
 * 가리키면 그 자리에서 기동을 실패시킨다.
 */
@Component
class LineCatalog(properties: MetroProperties) {

    private val lines: List<Line> = withSiblingAnchors(properties.lines.map { it.toLine() })
    private val bySlug: Map<String, Line> = lines.associateBy { it.slug }
    private val colors: Map<String, String> = properties.transferColors
    private val preset: List<String> = properties.preset
    private val aliases: Map<String, String> = properties.stationAliases
    private val transferNames: Map<String, String> = properties.transferNames

    /** API 역명을 우리 목록의 표기로 옮긴다. */
    val stationNames: StationNames = StationNames(aliases)

    /** API 노선마다 우리가 아는 역명. 모르는 이름이 오면 로그로 남기는 데 쓴다. */
    private val known: Map<String, Set<String>> = lines.groupBy { it.cacheKey }
        .mapValues { (_, views) -> views.flatMap { it.stations + it.anchors.keys }.toSet() }

    init {
        require(lines.isNotEmpty()) { "노선이 하나도 없다. lines.yml 이 실리지 않았다" }
        require(bySlug.size == lines.size) { "slug 가 겹친다: ${lines.map { it.slug }}" }
        preset.forEach { require(it in bySlug) { "preset 이 없는 노선을 가리킨다: $it" } }
        require(preset.size == preset.distinct().size) { "preset 에 같은 노선이 두 번 있다: $preset" }

        val everyStation = lines.flatMap { it.stations }.toSet()
        aliases.forEach { (from, to) ->
            // 대괄호를 빠뜨리면 relaxed binding 이 한글 키를 지워 빈 키가 된다.
            require(from.isNotBlank()) { "station-aliases 의 키가 비었다. 키를 대괄호로 감쌌는지 볼 것: $aliases" }
            require(from !in everyStation) { "station-aliases 가 실제 역 $from 을 다른 이름으로 바꾼다" }
            require(to in everyStation) { "station-aliases 가 어느 노선에도 없는 역을 가리킨다: $from -> $to" }
        }
        transferNames.forEach { (one, other) ->
            require(one.isNotBlank()) { "transfer-names 의 키가 비었다. 키를 대괄호로 감쌌는지 볼 것: $transferNames" }
            require(one in everyStation && other in everyStation) { "transfer-names 가 어느 노선에도 없는 역을 가리킨다: $one -> $other" }
        }
    }

    fun all(): List<Line> = lines

    /** 아직 아무것도 고르지 않은 브라우저에 보여줄 노선과 그 순서. */
    fun preset(): List<String> = preset.ifEmpty { lines.map { it.slug } }

    /** 환승 대상 노선의 색. 우리가 담지 않는 노선도 들어 있다. */
    fun transferColors(): Map<String, String> = colors

    fun stationAliases(): Map<String, String> = aliases

    /** 그 API 노선의 열차가 가리킬 수 있는 역명 전부. 여기 없는 이름이 오면 열차가 버려진다. */
    fun knownNames(cacheKey: String): Set<String> = known[cacheKey].orEmpty()

    /**
     * 환승 대상 이름이 가리키는 우리 뷰. 우리가 담지 않은 노선이면 비어 있다.
     *
     * <p>
     * 뷰 이름과 API 노선명 양쪽으로 찾는다. 지선마다 뷰를 둔 노선은 뷰 이름이 "1호선 (경인)"
     * 이라 환승 대상의 "1호선" 과 맞지 않으므로 API 노선명으로 두 뷰를 함께 가리킨다. 지선은
     * 괄호 없이 적혀 오므로("2호선 신정지선") 괄호와 공백을 빼고 견주고, 방면이나 운행 종류를
     * 괄호로 덧붙인 이름("5호선 (방화 방면)")은 괄호 앞 이름으로 찾는다.
     */
    fun viewsNamed(name: String): List<Line> {
        val base = name.substringBefore(" (").trim()
        return lines.filter { compact(it.name) == compact(name) || it.apiName == base }
    }

    private fun compact(name: String) = name.filterNot { it == '(' || it == ')' || it.isWhitespace() }

    /** 그 역과, 다른 노선에서 그 역을 부르는 이름. 환승 문을 대조할 때 쓴다. */
    fun sameStations(station: String): Set<String> =
        setOf(station) + transferNames.filter { (one, other) -> station == one || station == other }.flatMap { listOf(it.key, it.value) }

    fun bySlug(slug: String): Line? = bySlug[slug]

    private fun LineConfig.toLine(): Line {
        require(stations.isNotEmpty()) { "$slug: 역 목록이 비었다" }
        require(stations.size == stations.distinct().size) {
            "$slug: 역이 중복됐다: ${stations.groupBy { it }.filter { it.value.size > 1 }.keys}"
        }
        require(focus.isEmpty() || focus.size == 2) {
            "$slug: focus 는 [시작역, 끝역] 두 개여야 한다: $focus"
        }

        fun indexOrFail(station: String, where: String): Int =
            stations.indexOf(station).takeIf { it >= 0 }
                ?: error("$slug: $where 가 노선에 없는 역을 가리킨다: $station")

        keyStations.forEach { indexOrFail(it, "key-stations") }
        downOnly.forEach { indexOrFail(it, "down-only") }
        // 노선 순서와 반대로 적어도 되게 한다. 2호선 관심 구간이 강남에서 잠실인데
        // 역 순서로는 잠실이 앞이다.
        val bounds = focus.map { indexOrFail(it, "focus") }.sorted()

        val ends = termini.ifEmpty { listOf(stations.first(), stations.last()) }
        val endIndices = ends.map { indexOrFail(it, "termini") }
        val anchors = beyond.map { (destination, via) ->
            require(destination.isNotBlank()) { "$slug: beyond 의 키가 비었다. 키를 대괄호로 감쌌는지 볼 것: $beyond" }
            require(destination !in stations) { "$slug: beyond 의 $destination 은 이미 목록에 있는 역이다" }
            val at = indexOrFail(via, "beyond")
            // 가장 바깥 종착역에서 더 나아가면 연장이고, 그 안쪽에서 옆으로 빠지면 다른 계통이다.
            // 서동탄행은 병점이 평소 종착역이어도 신창 쪽이 아니라 옆으로 빠지므로 다른 계통이다.
            val service = if (at <= endIndices.min() || at >= endIndices.max()) ServiceKind.EXTENSION else ServiceKind.BRANCH
            destination to Destination(at, service)
        }.toMap()

        return Line(
            slug = slug,
            name = name,
            apiName = apiName ?: name,
            color = color,
            source = source,
            circular = circular,
            upLabel = upLabel,
            downLabel = downLabel,
            stations = stations,
            focusFrom = bounds.firstOrNull(),
            focusTo = bounds.lastOrNull(),
            keyStations = keyStations,
            fleet = fleet?.toFleet(slug),
            termini = ends.toSet(),
            anchors = anchors,
            downOnly = downOnly.toSet(),
            transferFile = transferFile ?: slug,
        )
    }

    /**
     * 형제 뷰의 종착역을 이 뷰의 갈라지는 역에 붙인다.
     *
     * <p>
     * 1호선 경인 뷰에서 신창은 목록에 없지만 경부 뷰에 있다. 경부 뷰의 목록을 신창에서부터
     * 거슬러 가다 처음 만나는 경인 뷰의 역이 구로이므로, 신창행은 경인 뷰에서 구로를 향해
     * 달리다 갈라진다. 형제 뷰가 beyond 로 적어 둔 종착역(서동탄)도 같은 식으로 따라간다.
     * 순환선은 종착역 자리로 방향을 정하지 않으므로 대상이 아니다.
     */
    private fun withSiblingAnchors(lines: List<Line>): List<Line> = lines.map { view ->
        if (view.circular) return@map view
        val found = LinkedHashMap<String, Destination>()
        lines.filter { it !== view && it.cacheKey == view.cacheKey }.forEach { sibling ->
            val shared = sibling.stations.indices.filter { view.indexOf(sibling.stations[it]) != null }
            if (shared.isEmpty()) return@forEach
            (sibling.stations + sibling.anchors.keys).forEach { name ->
                if (view.indexOf(name) != null || name in view.anchors || name in found) return@forEach
                val at = sibling.indexOf(name) ?: sibling.anchors.getValue(name).index
                val junction = shared.minBy { abs(it - at) }
                found[name] = Destination(view.indexOf(sibling.stations[junction])!!, ServiceKind.BRANCH)
            }
        }
        view.copy(anchors = view.anchors + found)
    }

    private fun FleetConfig.toFleet(slug: String): Fleet {
        require(generations.isNotEmpty()) { "$slug: fleet 에 generations 가 없다" }
        val sorted = generations.sortedBy { it.from }
        sorted.zipWithNext { a, b ->
            require(a.to < b.from) { "$slug: 편성 범위가 겹친다: ${a.label}(${a.from}~${a.to}), ${b.label}(${b.from}~${b.to})" }
        }
        sorted.forEach { require(it.from <= it.to) { "$slug: ${it.label} 의 편성 범위가 뒤집혔다" } }
        return Fleet(
            sourceDocument = sourceDocument,
            sourceRevision = sourceRevision,
            sourceUrl = sourceUrl,
            generations = sorted.map { FleetGeneration(it.label, it.color, it.from, it.to, it.note, it.url) },
        )
    }
}
