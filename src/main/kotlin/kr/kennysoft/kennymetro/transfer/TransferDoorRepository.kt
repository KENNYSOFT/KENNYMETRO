package kr.kennysoft.kennymetro.transfer

import kr.kennysoft.kennymetro.domain.Direction
import kr.kennysoft.kennymetro.domain.Line
import kr.kennysoft.kennymetro.domain.LineCatalog
import kr.kennysoft.kennymetro.domain.LineTransfers
import kr.kennysoft.kennymetro.domain.StationTransfer
import kr.kennysoft.kennymetro.domain.TransferDoor
import kr.kennysoft.kennymetro.domain.TransferSource
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

/**
 * 환승 문 위치를 기동 시 한 번 읽어 메모리에 둔다.
 *
 * <p>
 * 원본이 사람이 옮겨 적은 CSV 라 실수는 배포 전에 드러나야 한다. 노선에 없는 역명이나
 * 숫자가 아닌 칸 번호를 만나면 그 자리에서 기동을 실패시킨다. 빈 목록으로 넘어가면 화면에
 * 환승 정보만 조용히 빠진 채 뜬다.
 *
 * <p>
 * 출처는 파일 머리의 주석에서 읽는다. 노선마다 가져온 문서와 판이 달라 코드에 둘 수 없고,
 * 데이터와 같은 파일에 있어야 한쪽만 고쳐지는 일이 없다. CC BY 조건이라 화면에서 뺄 수 없다.
 *
 * <p>
 * 지선마다 뷰를 둔 노선(1호선, 5호선, 경의중앙선)은 원본 문서가 하나라 파일도 하나를
 * 나눠 쓴다. 각 뷰는 자기 목록에 있는 역만 가져가고, 나눠 쓰는 뷰 어디에도 없는 역은 오타로
 * 보고 기동을 실패시킨다.
 *
 * <p>
 * 리소스는 native image 에 자동으로 실리지 않는다. `TransferDataRuntimeHints` 가 등록하고
 * `scripts/smoke-test.sh` 가 실제 바이너리로 왕복해 확인한다.
 */
@Component
class TransferDoorRepository(private val catalog: LineCatalog) {

    private val byLine: Map<String, LineTransfers> = catalog.all().associate { it.slug to load(it) }

    /** 그 노선의 환승역 정보. 역 순서대로 온다. */
    fun findByLine(line: Line): LineTransfers = byLine[line.slug] ?: LineTransfers(null, emptyList())

    private fun load(line: Line): LineTransfers {
        val doorFile = readFile("transfer/${line.transferFile}-doors.csv", DOOR_COLUMNS)
        if (doorFile == null) return LineTransfers(null, emptyList())
        val sharers = catalog.all().filter { it.transferFile == line.transferFile }

        val notes = readFile("transfer/${line.transferFile}-notes.csv", NOTE_COLUMNS)
            ?.rows.orEmpty()
            .associate { (lineNo, columns) ->
                require(columns.size == NOTE_COLUMNS) { "비고 ${lineNo}번 줄의 컬럼이 ${NOTE_COLUMNS}개가 아니다: $columns" }
                requireKnown(columns[0], sharers, "transfer/${line.transferFile}-notes.csv:$lineNo")
                columns[0] to columns[1]
            }

        val doors = parseDoors(doorFile, line, sharers)

        // 여기서 정렬해 버리면 파일이 흐트러져도 조용히 바로잡혀 사람이 모른다. 순서가 곧
        // 화면 순서이므로 어긋난 것을 알려주는 편이 낫다.
        val indexes = doors.keys.map { line.indexOf(it)!! }
        require(indexes == indexes.sorted()) {
            "${doorFile.path} 의 역을 ${line.name} 순서대로 적어야 한다: ${doors.keys}"
        }

        verifyTargets(line, doors, doorFile.path)

        return LineTransfers(
            source = doorFile.source(line.slug),
            stations = doors.map { (station, stationDoors) ->
                StationTransfer(station, notes[station], stationDoors)
            },
        )
    }

    /**
     * 환승 대상이 우리가 담은 노선이면 그 역이 상대 노선에도 있어야 한다.
     *
     * <p>
     * 원본 문서에서 표를 옮길 때 역 제목을 놓치면 그 표가 앞 역에 붙는다. 그렇게 생긴
     * 행은 역명도 노선도 각각은 멀쩡해 다른 검사에 걸리지 않으므로, 두 노선이 실제로
     * 그 역에서 만나는지를 따로 본다. 우리가 담지 않은 노선은 대조할 것이 없어 넘어간다.
     * 목록 밖이어도 그 노선 열차가 닿는 역(beyond)이면 갈아탈 수 있다 - 1호선 광운대에서
     * 경춘선으로 갈아타는 것이 그렇다. 노선마다 이름이 다른 역(총신대입구와 이수)은 lines.yml 의
     * transfer-names 로 같은 역임을 안다.
     */
    private fun verifyTargets(line: Line, doors: Map<String, List<TransferDoor>>, path: String) {
        doors.forEach { (station, stationDoors) ->
            val names = catalog.sameStations(station)
            stationDoors.forEach { door ->
                door.targetLine.split("/").forEach { name ->
                    val others = catalog.viewsNamed(name.trim())
                    if (others.isEmpty()) return@forEach
                    require(others.any { other -> names.any { other.indexOf(it) != null || it in other.anchors } }) {
                        "$path: ${line.name} $station 역은 ${others.joinToString("/") { it.name }} 에 없어 환승할 수 없다"
                    }
                }
            }
        }
    }

    private fun parseDoors(file: ParsedFile, line: Line, sharers: List<Line>): Map<String, List<TransferDoor>> {
        // 파싱 순서가 곧 화면 표시 순서라 LinkedHashMap 으로 원본 줄 순서를 지킨다.
        val result = LinkedHashMap<String, MutableList<TransferDoor>>()
        file.rows.forEach { (lineNo, columns) ->
            val where = "${file.path}:$lineNo"
            require(columns.size == DOOR_COLUMNS) { "$where 컬럼이 ${DOOR_COLUMNS}개여야 한다: $columns" }
            val station = columns[0]
            requireKnown(station, sharers, where)
            val spot = spot(columns[4], columns[5], where)
            if (line.indexOf(station) == null) return@forEach
            val trainDirection = columns[1].ifBlank { null }
            val targetDirection = columns[3].ifBlank { null }
            result.getOrPut(station) { mutableListOf() } += TransferDoor(
                trainDirection = trainDirection,
                side = sideOf(trainDirection, station, line, where),
                targetLine = columns[2],
                targetDirection = targetDirection,
                targetNext = targetNextOf(columns[2], targetDirection, station),
                car = spot.car,
                door = spot.door,
                toCar = spot.toCar,
                toDoor = spot.toDoor,
            )
        }
        return result
    }

    /**
     * 그 줄을 화면의 어느 칸에 둘지. null 이면 양쪽이다.
     *
     * <p>
     * 열차 방면은 원문의 열 제목이라 화면의 좌우 이름과 글자가 다르다("연천/광운대", "용산 급행",
     * "광명셔틀 광명"). 그래서 이름이 아니라 자리로 가린다. 방면에 적힌 역이 역 목록에서 지금
     * 역보다 앞이면 열차가 앞쪽으로 가는 것이라 왼쪽 칸이고, 뒤면 오른쪽 칸이다. 급행이나 순환처럼
     * 역이 아닌 낱말은 건너뛴다. 가릴 역이 하나도 없거나 낱말끼리 쪽이 어긋나면 기동을 실패시킨다.
     *
     * <p>
     * 방면이 비어 있으면 어느 쪽으로 가든 같은 문이라 양쪽에 둔다. 다만 열차가 한쪽으로만 들어오는
     * 역은 그쪽에만 둔다. 구간의 첫 역에는 앞쪽으로 가는 열차만, 끝 역에는 뒤쪽으로 가는 열차만
     * 들어온다. 목록의 양 끝(수인분당선 청량리와 인천)이 그렇고, 열차가 다니지 않는 역 바로 옆의
     * 역(GTX-A 수서)도 그렇다. 6호선 순환 구간은 lines.yml 의 down-only 로 안다. 순환선은 끝이
     * 없고 방면을 외선과 내선으로 적으므로 좌우 이름과 그대로 견준다.
     */
    private fun sideOf(trainDirection: String?, station: String, line: Line, where: String): Direction? {
        if (trainDirection == null) {
            val index = line.indexOf(station)!!
            return when {
                line.circular -> null
                // 6호선 역촌은 목록의 첫 역이지만 순환 구간이라 열차가 오른쪽으로만 지난다.
                station in line.downOnly -> Direction.DOWN
                !line.servedAt(index - 1) -> Direction.UP
                !line.servedAt(index + 1) -> Direction.DOWN
                else -> null
            }
        }
        if (line.circular) {
            return when (trainDirection) {
                line.upLabel -> Direction.UP
                line.downLabel -> Direction.DOWN
                else -> error("$where 순환선의 열차 방면이 ${line.upLabel}, ${line.downLabel} 중 하나가 아니다: $trainDirection")
            }
        }
        val sides = trainDirection.split('/', ' ').mapNotNull { towards(it, station, line) }.toSet()
        require(sides.isNotEmpty()) { "$where 열차 방면에 ${line.name} 의 어느 쪽인지 가릴 역이 없다: $trainDirection" }
        require(sides.size == 1) { "$where 열차 방면의 역들이 서로 다른 쪽을 가리킨다: $trainDirection" }
        return sides.single()
    }

    /**
     * 그 역으로 가는 열차가 지금 역에서 어느 쪽으로 가는지. 역이 아닌 낱말이거나 지금 역이면 null.
     *
     * <p>
     * 이 뷰의 목록에 없는 역은 같은 API 노선의 다른 뷰에서 찾아, 두 역을 함께 담은 뷰에서 자리를
     * 견준다. 그 뷰들은 앞쪽 끝을 같이 쓰므로(1호선은 연천, 5호선은 방화) 어느 뷰에서 견주든 쪽이
     * 같다. 5호선 마천 뷰의 강동에서 하남검단산행이 그렇게 풀린다 - 이 뷰에서는 하남검단산이 바로
     * 강동에서 갈라져 자리가 같다. 어느 뷰에도 없는 종착역(1호선 광명)은 갈라지는 역의 자리로 본다.
     */
    private fun towards(name: String, station: String, line: Line): Direction? {
        val views = listOf(line) + catalog.all().filter { it !== line && it.cacheKey == line.cacheKey }
        views.forEach { view ->
            val to = view.indexOf(name)
            val from = view.indexOf(station)
            if (to != null && from != null) return directionBetween(from, to)
        }
        val anchor = line.anchors[name] ?: return null
        return directionBetween(line.indexOf(station)!!, anchor.index)
    }

    /**
     * 갈아탄 뒤 그 방면으로 가면 바로 다음에 서는 역.
     *
     * <p>
     * 갈아탈 노선의 뷰마다 방면에 적힌 역 가운데 그 뷰에 있는 역으로 쪽을 가리고, 순환선은 외선과
     * 내선을 좌우 이름과 견준다. 열차 방면과 달리 형제 뷰의 역으로 가리지 않는다 - 갈라지는 역에서
     * 다른 계통의 다음 역이 나온다(5호선 강동의 마천 방면을 하남 뷰에서 풀면 길동이 된다). 뷰마다
     * 구해 하나로 모일 때만 쓴다. 1호선 종로3가의 "인천/신창/서동탄" 은 두 뷰 모두 종각이지만,
     * 강동의 "하남검단산/마천" 은 지선마다 다음 역이 달라 하나로 말할 수 없다. 우리가 담지 않은
     * 노선(인천1호선)과 여러 노선을 한 줄에 적은 줄은 풀지 않는다.
     */
    private fun targetNextOf(targetLine: String, targetDirection: String?, station: String): String? {
        if (targetDirection == null || '/' in targetLine) return null
        val names = catalog.sameStations(station)
        return catalog.viewsNamed(targetLine.trim()).mapNotNull { view ->
            val here = names.firstNotNullOfOrNull { view.indexOf(it) } ?: return@mapNotNull null
            val side = if (view.circular) {
                when (targetDirection) {
                    view.upLabel -> Direction.UP
                    view.downLabel -> Direction.DOWN
                    else -> null
                }
            } else {
                targetDirection.split('/', ' ')
                    .mapNotNull { name -> view.indexOf(name)?.let { directionBetween(here, it) } }
                    .toSet().singleOrNull()
            }
            side?.let { view.stationAfter(here, it) }
        }.toSet().singleOrNull()
    }

    /** 그 쪽으로 한 역 가면 서는 역. 순환선은 이어 돌고, 목록 끝이나 열차가 다니지 않는 역이면 null. */
    private fun Line.stationAfter(index: Int, side: Direction): String? {
        val next = if (side == Direction.UP) index - 1 else index + 1
        if (circular) return stations[Math.floorMod(next, stations.size)]
        return if (servedAt(next)) stations[next] else null
    }

    /** 목록의 from 번째 역에서 to 번째 역으로 가는 쪽. 같은 역이면 null. */
    private fun directionBetween(from: Int, to: Int): Direction? = when {
        to < from -> Direction.UP
        to > from -> Direction.DOWN
        else -> null
    }

    /** 파일을 나눠 쓰는 뷰 어디에도 없는 역은 오타다. 한 뷰만 쓰는 파일이면 그 뷰에 없는 역이다. */
    private fun requireKnown(station: String, sharers: List<Line>, where: String) {
        require(sharers.any { it.indexOf(station) != null }) {
            "$where ${sharers.joinToString("/") { it.name }}에 없는 역이다: $station"
        }
    }

    /**
     * 칸과 문 칸을 읽는다. 둘 다 `*` 이면 어느 문에서 내려도 되고, `4~7` 과 `1~4` 처럼 물결로
     * 이으면 4-1 부터 7-4 까지 이어진 문이다. 한쪽만 그렇게 적은 줄은 틀린 것이다.
     */
    private fun spot(car: String, door: String, where: String): Spot {
        require((car == "*") == (door == "*")) { "$where 칸과 문은 둘 다 * 여야 모든 문이다: $car, $door" }
        if (car == "*") return Spot(null, null, null, null)
        val cars = numbers(car, "칸", where)
        val doors = numbers(door, "문", where)
        require(cars.size == doors.size) { "$where 칸과 문은 둘 다 범위이거나 둘 다 한 자리여야 한다: $car, $door" }
        if (cars.size == 1) return Spot(cars[0], doors[0], null, null)
        require(cars[0] < cars[1] || (cars[0] == cars[1] && doors[0] < doors[1])) {
            "$where 범위의 앞 자리가 뒤 자리보다 뒤에 있다: $car, $door"
        }
        return Spot(cars[0], doors[0], cars[1], doors[1])
    }

    private fun numbers(text: String, what: String, where: String): List<Int> {
        val parts = text.split("~")
        require(parts.size <= 2) { "$where ${what}은 한 자리나 범위여야 한다: $text" }
        return parts.map { it.trim().toIntOrNull() ?: error("$where ${what}이 숫자가 아니다: $text") }
    }

    private data class Spot(val car: Int?, val door: Int?, val toCar: Int?, val toDoor: Int?)

    /**
     * 주석에서 읽은 출처. 하나라도 빠지면 기동을 실패시킨다 - 표기 없이 데이터만 나가는 것이
     * 라이선스 위반이므로, 빠뜨린 것을 배포 전에 알아야 한다.
     */
    private fun ParsedFile.source(slug: String): TransferSource {
        fun need(key: String) = meta[key] ?: error("$path 머리에 `# $key:` 가 없다")
        return TransferSource(
            document = need("source-document"),
            revision = need("source-revision"),
            url = need("source-url"),
            license = need("license"),
            licenseUrl = need("license-url"),
        )
    }

    /**
     * 파일 하나를 메타와 데이터 줄로 가른다. 없으면 null 이다.
     *
     * <p>
     * `columns` 는 split 상한이다. 마지막 컬럼이 문장이라 쉼표를 담을 수 있는 비고 파일은
     * 이 상한 덕에 첫 쉼표 뒤가 통째로 남는다. 따옴표로 감싼 필드는 다루지 않는다.
     */
    private fun readFile(path: String, columns: Int): ParsedFile? {
        val resource = ClassPathResource(path)
        if (!resource.exists()) return null

        val meta = LinkedHashMap<String, String>()
        val rows = mutableListOf<Pair<Int, List<String>>>()
        var headerSeen = false

        resource.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.forEachIndexed { index, raw ->
                val text = raw.trim()
                when {
                    text.isEmpty() -> Unit
                    text.startsWith("#") -> {
                        // 설명 주석과 섞여 있으므로 아는 키만 줍는다.
                        val body = text.removePrefix("#").trim()
                        val key = body.substringBefore(':', "").trim()
                        if (key in META_KEYS) meta[key] = body.substringAfter(':').trim()
                    }

                    !headerSeen -> headerSeen = true
                    else -> rows += (index + 1) to text.split(",", limit = columns)
                }
            }
        }
        return ParsedFile(path, meta, rows)
    }

    private data class ParsedFile(
        val path: String,
        val meta: Map<String, String>,
        val rows: List<Pair<Int, List<String>>>,
    )

    private companion object {
        private const val DOOR_COLUMNS = 6
        private const val NOTE_COLUMNS = 2
        private val META_KEYS = setOf(
            "source-document",
            "source-revision",
            "source-url",
            "license",
            "license-url",
        )
    }
}
