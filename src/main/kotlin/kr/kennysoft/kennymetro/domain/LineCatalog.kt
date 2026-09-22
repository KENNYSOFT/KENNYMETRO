package kr.kennysoft.kennymetro.domain

import kr.kennysoft.kennymetro.config.FleetConfig
import kr.kennysoft.kennymetro.config.LineConfig
import kr.kennysoft.kennymetro.config.MetroProperties
import org.springframework.stereotype.Component

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

    private val lines: List<Line> = properties.lines.map { it.toLine() }
    private val bySlug: Map<String, Line> = lines.associateBy { it.slug }

    init {
        require(lines.isNotEmpty()) { "노선이 하나도 없다. lines.yml 이 실리지 않았다" }
        require(bySlug.size == lines.size) { "slug 가 겹친다: ${lines.map { it.slug }}" }
    }

    fun all(): List<Line> = lines

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
        // 노선 순서와 반대로 적어도 되게 한다. 2호선 관심 구간이 강남에서 잠실인데
        // 역 순서로는 잠실이 앞이다.
        val bounds = focus.map { indexOrFail(it, "focus") }.sorted()

        return Line(
            slug = slug,
            name = name,
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
        )
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
