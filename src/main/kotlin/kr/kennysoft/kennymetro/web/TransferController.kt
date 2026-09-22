package kr.kennysoft.kennymetro.web

import kr.kennysoft.kennymetro.domain.Line
import kr.kennysoft.kennymetro.domain.StationTransfer
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
class TransferController(private val repository: TransferDoorRepository) {

    @GetMapping("/api/lines/{slug}/transfers")
    fun transfers(@PathVariable slug: String): TransfersResponse {
        val line = Line.bySlug(slug) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "모르는 노선이다: $slug")
        // 노선에 환승 데이터가 아직 없으면 빈 목록이 된다. Kotlin 의 빈 리스트는 EmptyList
        // 싱글톤이라 native image 에서 직렬화가 깨지므로 ArrayList 로 옮긴다 (DESIGN.md 4.1).
        return TransfersResponse(
            line = line.lineName,
            source = SOURCE,
            license = LICENSE,
            licenseUrl = LICENSE_URL,
            stations = ArrayList(repository.findByLine(line)),
        )
    }

    private companion object {
        /** 화면에 그대로 표시한다. CC BY 조건이라 빼면 안 된다 (DESIGN.md 2.3.1). */
        private const val SOURCE = "나무위키 수도권 전철 환승 정보"
        private const val LICENSE = "CC BY-NC-SA 2.0 KR"
        private const val LICENSE_URL = "https://creativecommons.org/licenses/by-nc-sa/2.0/kr/"
    }
}

data class TransfersResponse(
    val line: String,
    val source: String,
    val license: String,
    val licenseUrl: String,
    val stations: List<StationTransfer>,
)
