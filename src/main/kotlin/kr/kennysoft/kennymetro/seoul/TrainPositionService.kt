package kr.kennysoft.kennymetro.seoul

import kr.kennysoft.kennymetro.domain.Line
import kr.kennysoft.kennymetro.domain.Train
import kr.kennysoft.kennymetro.domain.toTrain
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock

/**
 * 노선별 실시간 위치를 캐시해 돌려준다.
 * <p>
 * 실시간 지하철 API 는 인증키당 하루 1,000회다. 방문자마다 API 를 부르면 예산이
 * 몇 분 만에 마르므로, 노선당 한 벌만 받아 모든 방문자가 나눠 본다. 캐시가 낡았을
 * 때 이미 다른 요청이 받아오는 중이면 기다리지 않고 직전 값을 준다 - 같은 노선을
 * 동시에 두 번 부르지 않기 위한 것이다.
 */
@Service
class TrainPositionService(
    private val client: SeoulSubwayClient,
    private val properties: SeoulSubwayProperties,
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val entries = ConcurrentHashMap<Line, CacheEntry>()
    private val apiCallCount = AtomicLong()

    /** 지금까지 실제로 나간 API 호출 수. 일일 예산을 얼마나 썼는지 본다. */
    fun apiCallCount(): Long = apiCallCount.get()

    fun snapshot(line: Line): TrainsSnapshot {
        val entry = entries.computeIfAbsent(line) { CacheEntry() }
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
            val dtos = client.findPositions(line.lineName)
            apiCallCount.incrementAndGet()
            entry.trains = dtos.mapNotNull { it.toTrain(line) }
            entry.fetchedAt = clock.instant()
        } catch (e: Exception) {
            apiCallCount.incrementAndGet()
            if (entry.fetchedAt == null) throw e
            // 직전 값이 있으면 그것을 계속 쓴다. 잠깐의 장애로 화면이 비지 않게 한다.
            log.warn("실시간 위치 갱신 실패, 직전 값을 유지한다. line={} error={}", line.lineName, e.message, e)
        }
        return entry.snapshot()
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
