package kr.kennysoft.kennymetro.web

import kr.kennysoft.kennymetro.domain.Line
import kr.kennysoft.kennymetro.domain.Train
import kr.kennysoft.kennymetro.seoul.TrainPositionService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@RestController
class TrainController(private val trainPositionService: TrainPositionService) {

    @GetMapping("/api/lines/{slug}/trains")
    fun trains(@PathVariable slug: String): TrainsResponse {
        val line = findLine(slug) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "모르는 노선이다: $slug")
        val snapshot = trainPositionService.snapshot(line)
        return TrainsResponse(
            line = line.lineName,
            stations = line.stations,
            fetchedAt = snapshot.fetchedAt,
            trains = snapshot.trains,
        )
    }

    private fun findLine(slug: String): Line? =
        Line.entries.find { it.name.equals(slug, ignoreCase = true) }
}

data class TrainsResponse(
    val line: String,
    val stations: List<String>,
    val fetchedAt: Instant?,
    val trains: List<Train>,
)
