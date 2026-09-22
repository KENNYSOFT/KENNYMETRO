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
 * 리소스는 native image 에 자동으로 실리지 않는다. `TransferDataRuntimeHints` 가 등록하고
 * `scripts/smoke-test.sh` 가 실제 바이너리로 왕복해 확인한다.
 */
@Component
class TransferDoorRepository(catalog: LineCatalog) {

    private val byLine: Map<String, LineTransfers> = catalog.all().associate { it.slug to load(it) }

    /** 그 노선의 환승역 정보. 역 순서대로 온다. */
    fun findByLine(line: Line): LineTransfers = byLine[line.slug] ?: LineTransfers(null, emptyList())

    private fun load(line: Line): LineTransfers {
        val doorFile = readFile("transfer/${line.slug}-doors.csv", DOOR_COLUMNS)
        if (doorFile == null) return LineTransfers(null, emptyList())

        val notes = readFile("transfer/${line.slug}-notes.csv", NOTE_COLUMNS)
            ?.rows.orEmpty()
            .associate { (lineNo, columns) ->
                require(columns.size == NOTE_COLUMNS) { "비고 ${lineNo}번 줄의 컬럼이 ${NOTE_COLUMNS}개가 아니다: $columns" }
                columns[0] to columns[1]
            }

        val doors = parseDoors(doorFile, line)

        // 여기서 정렬해 버리면 파일이 흐트러져도 조용히 바로잡혀 사람이 모른다. 순서가 곧
        // 화면 순서이므로 어긋난 것을 알려주는 편이 낫다.
        val indexes = doors.keys.map { line.indexOf(it)!! }
        require(indexes == indexes.sorted()) {
            "transfer/${line.slug}-doors.csv 의 역을 ${line.name} 순서대로 적어야 한다: ${doors.keys}"
        }

        return LineTransfers(
            source = doorFile.source(line.slug),
            stations = doors.map { (station, stationDoors) ->
                StationTransfer(station, notes[station], stationDoors)
            },
        )
    }

    private fun parseDoors(file: ParsedFile, line: Line): Map<String, List<TransferDoor>> {
        // 파싱 순서가 곧 화면 표시 순서라 LinkedHashMap 으로 원본 줄 순서를 지킨다.
        val result = LinkedHashMap<String, MutableList<TransferDoor>>()
        file.rows.forEach { (lineNo, columns) ->
            val where = "${file.path}:$lineNo"
            require(columns.size == DOOR_COLUMNS) { "$where 컬럼이 ${DOOR_COLUMNS}개여야 한다: $columns" }
            val station = columns[0]
            require(line.indexOf(station) != null) { "$where ${line.name}에 없는 역이다: $station" }
            result.getOrPut(station) { mutableListOf() } += TransferDoor(
                trainDirection = columns[1].ifBlank { null },
                targetLine = columns[2],
                targetDirection = columns[3].ifBlank { null },
                car = columns[4].toIntOrNull() ?: error("$where 칸이 숫자가 아니다: ${columns[4]}"),
                door = columns[5].toIntOrNull() ?: error("$where 문이 숫자가 아니다: ${columns[5]}"),
            )
        }
        return result
    }

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
