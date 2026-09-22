package kr.kennysoft.kennymetro.transfer

import kr.kennysoft.kennymetro.domain.Line
import kr.kennysoft.kennymetro.domain.StationTransfer
import kr.kennysoft.kennymetro.domain.TransferDoor
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
 * 리소스는 native image 에 자동으로 실리지 않는다. `TransferDataRuntimeHints` 가 등록하고
 * `scripts/smoke-test.sh` 가 실제 바이너리로 왕복해 확인한다.
 */
@Component
class TransferDoorRepository {

    private val byLine: Map<Line, List<StationTransfer>> = Line.entries.associateWith(::load)

    /** 그 노선의 환승역 정보. 역 순서대로 온다. */
    fun findByLine(line: Line): List<StationTransfer> = byLine[line].orEmpty()

    private fun load(line: Line): List<StationTransfer> {
        val slug = line.name.lowercase()
        val notes = loadNotes("transfer/$slug-notes.csv")
        val doors = loadDoors("transfer/$slug-doors.csv", line)

        // 여기서 정렬해 버리면 파일이 흐트러져도 조용히 바로잡혀 사람이 모른다. 순서가 곧
        // 화면 순서이므로 어긋난 것을 알려주는 편이 낫다.
        val indexes = doors.keys.map { line.indexOf(it)!! }
        require(indexes == indexes.sorted()) {
            "transfer/$slug-doors.csv 의 역을 ${line.lineName} 순서대로 적어야 한다: ${doors.keys}"
        }

        return doors.map { (station, stationDoors) ->
            StationTransfer(station, notes[station], stationDoors)
        }
    }

    private fun loadDoors(path: String, line: Line): Map<String, List<TransferDoor>> {
        // 파싱 순서가 곧 화면 표시 순서라 LinkedHashMap 으로 원본 줄 순서를 지킨다.
        val result = LinkedHashMap<String, MutableList<TransferDoor>>()
        readRows(path, DOOR_COLUMNS).forEach { (lineNo, columns) ->
            require(columns.size == DOOR_COLUMNS) {
                "$path:$lineNo 컬럼이 ${DOOR_COLUMNS}개여야 한다: $columns"
            }
            val station = columns[0]
            require(line.indexOf(station) != null) { "$path:$lineNo ${line.lineName}에 없는 역이다: $station" }
            result.getOrPut(station) { mutableListOf() } += TransferDoor(
                trainDirection = columns[1].ifBlank { null },
                targetLine = columns[2],
                targetDirection = columns[3].ifBlank { null },
                car = columns[4].toIntOrNull() ?: error("$path:$lineNo 칸이 숫자가 아니다: ${columns[4]}"),
                door = columns[5].toIntOrNull() ?: error("$path:$lineNo 문이 숫자가 아니다: ${columns[5]}"),
            )
        }
        return result
    }

    private fun loadNotes(path: String): Map<String, String> =
        readRows(path, NOTE_COLUMNS).associate { (lineNo, columns) ->
            require(columns.size == NOTE_COLUMNS) {
                "$path:$lineNo 컬럼이 ${NOTE_COLUMNS}개여야 한다: $columns"
            }
            columns[0] to columns[1]
        }

    /**
     * 주석과 헤더를 걷어낸 데이터 줄을 원본 줄 번호와 함께 돌려준다.
     *
     * <p>
     * `columns` 는 split 상한이다. 마지막 컬럼이 문장이라 쉼표를 담을 수 있는 비고 파일은
     * 이 상한 덕에 첫 쉼표 뒤가 통째로 남는다. 따옴표로 감싼 필드는 다루지 않는다.
     */
    private fun readRows(path: String, columns: Int): List<Pair<Int, List<String>>> {
        val resource = ClassPathResource(path)
        if (!resource.exists()) return emptyList()
        return resource.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.withIndex()
                .map { (index, raw) -> index + 1 to raw.trim() }
                .filter { (_, text) -> text.isNotEmpty() && !text.startsWith("#") }
                .drop(1)
                .map { (lineNo, text) -> lineNo to text.split(",", limit = columns) }
                .toList()
        }
    }

    private companion object {
        private const val DOOR_COLUMNS = 6
        private const val NOTE_COLUMNS = 2
    }
}
