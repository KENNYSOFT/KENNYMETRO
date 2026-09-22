package kr.kennysoft.kennymetro.web

import kr.kennysoft.kennymetro.domain.LineCatalog
import kr.kennysoft.kennymetro.domain.StationTransfer
import kr.kennysoft.kennymetro.domain.TransferSource
import kr.kennysoft.kennymetro.transfer.TransferDoorRepository
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/**
 * 환승 문 위치. 데이터가 정적이라 열차 위치와 달리 캐시 만료가 없다.
 */
@RestController
class TransferController(
    private val catalog: LineCatalog,
    private val repository: TransferDoorRepository,
) {

    @GetMapping("/api/lines/{slug}/transfers")
    fun transfers(@PathVariable slug: String): TransfersResponse {
        val line = catalog.bySlug(slug)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "모르는 노선이다: $slug")
        val transfers = repository.findByLine(line)
        // 노선에 환승 데이터가 아직 없으면 빈 목록이 된다. Kotlin 의 빈 리스트는 EmptyList
        // 싱글톤이라 native image 에서 직렬화가 깨지므로 ArrayList 로 옮긴다 (DESIGN.md 4.1).
        return TransfersResponse(
            line = line.name,
            source = transfers.source,
            stations = ArrayList(transfers.stations),
        )
    }
}

data class TransfersResponse(
    val line: String,
    /** 어느 문서의 몇 번째 판에서 옮겼는지. 화면에 그대로 표시한다. */
    val source: TransferSource?,
    val stations: List<StationTransfer>,
)
