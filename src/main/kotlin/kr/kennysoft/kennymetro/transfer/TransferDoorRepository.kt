package kr.kennysoft.kennymetro.transfer

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
 * 지선마다 뷰를 둔 노선(1호선, 5호선, 경의중앙선, GTX-A)은 원본 문서가 하나라 파일도 하나를
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
            result.getOrPut(station) { mutableListOf() } += TransferDoor(
                trainDirection = columns[1].ifBlank { null },
                targetLine = columns[2],
                targetDirection = columns[3].ifBlank { null },
                car = spot.car,
                door = spot.door,
                toCar = spot.toCar,
                toDoor = spot.toDoor,
            )
        }
        return result
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
