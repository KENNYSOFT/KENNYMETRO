package kr.kennysoft.kennymetro.seoul

import kr.kennysoft.kennymetro.domain.Line
import kr.kennysoft.kennymetro.domain.LineSource
import kr.kennysoft.kennymetro.domain.Train
import kr.kennysoft.kennymetro.domain.toTrain
import kr.kennysoft.kennymetro.everline.EverlineClient
import kr.kennysoft.kennymetro.everline.toTrain
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

/**
 * 노선별 실시간 위치를 캐시해 돌려준다.
 * <p>
 * 실시간 지하철 API 는 인증키당 하루 1,000회다. 방문자마다 API 를 부르면 예산이
 * 몇 분 만에 마르므로, 노선당 한 벌만 받아 모든 방문자가 나눠 본다. 캐시가 낡았을
 * 때 이미 다른 요청이 받아오는 중이면 기다리지 않고 직전 값을 준다 - 같은 노선을
 * 동시에 두 번 부르지 않기 위한 것이다.
 * <p>
 * <b>예산은 노선 수만큼 나뉜다.</b> 화면이 다섯 노선을 한꺼번에 폴링하면 한 노선일
 * 때의 다섯 배가 나간다. 그래서 화면은 펼친 노선만 요청하고, 서버는 나간 호출 수를
 * 세어 응답에 실어 보낸다. 용인경전철은 다른 엔드포인트라 이 예산에 들지 않는다.
 */
@Service
class TrainPositionService(
    private val seoulClient: SeoulSubwayClient,
    private val everlineClient: EverlineClient,
    private val properties: SeoulSubwayProperties,
    private val ledger: ApiCallLedger,
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val entries = ConcurrentHashMap<String, CacheEntry>()

    /** 오늘 서울시 API 로 실제로 나간 호출 수. 일일 예산을 얼마나 썼는지 본다. */
    fun apiCallCount(): Long = ledger.today()

    fun snapshot(line: Line): TrainsSnapshot {
        val entry = entries.computeIfAbsent(line.slug) { CacheEntry() }
        val now = clock.instant()

        if (entry.isFresh(now, properties.cacheTtl.toMillis())) {
            return entry.snapshot()
        }

        // 값이 아직 없으면 받아올 때까지 기다리고, 있으면 낡은 값을 주고 넘어간다.
        val acquired = if (entry.fetchedAt == null) {
            entry.lock.lock()
            true
        } else {
            entry.lock.tryLock()
        }
        if (!acquired) {
            return entry.snapshot()
        }

        try {
            // 기다리는 사이 다른 요청이 갱신했을 수 있다.
            if (entry.isFresh(clock.instant(), properties.cacheTtl.toMillis())) {
                return entry.snapshot()
            }
            return refresh(line, entry)
        } finally {
            entry.lock.unlock()
        }
    }

    private fun refresh(line: Line, entry: CacheEntry): TrainsSnapshot {
        try {
            entry.trains = fetch(line)
            entry.fetchedAt = clock.instant()
        } catch (e: Exception) {
            if (entry.fetchedAt == null) throw e
            // 직전 값이 있으면 그것을 계속 쓴다. 잠깐의 장애로 화면이 비지 않게 한다.
            log.warn("실시간 위치 갱신 실패, 직전 값을 유지한다. line={} error={}", line.name, e.message, e)
        }
        return entry.snapshot()
    }

    private fun fetch(line: Line): List<Train> = when (line.source) {
        // 호출이 실패해도 예산은 깎인다. 그래서 응답을 받기 전에 센다.
        LineSource.SEOUL -> {
            ledger.record()
            seoulClient.findPositions(line.name).mapNotNull { it.toTrain(line) }
        }

        LineSource.EVERLINE -> everlineClient.findPositions().mapNotNull { it.toTrain(line) }
    }

    private class CacheEntry {
        val lock = ReentrantLock()

        @Volatile
        var trains: List<Train> = emptyList()

        @Volatile
        var fetchedAt: Instant? = null

        fun isFresh(now: Instant, ttlMillis: Long): Boolean {
            val at = fetchedAt ?: return false
            return now.toEpochMilli() - at.toEpochMilli() < ttlMillis
        }

        fun snapshot(): TrainsSnapshot = TrainsSnapshot(trains, fetchedAt)
    }
}

data class TrainsSnapshot(
    val trains: List<Train>,
    val fetchedAt: Instant?,
)
