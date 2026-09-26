package kr.kennysoft.kennymetro.seoul

import kr.kennysoft.kennymetro.domain.Line
import kr.kennysoft.kennymetro.domain.LineCatalog
import kr.kennysoft.kennymetro.domain.LineSource
import kr.kennysoft.kennymetro.domain.Recheck
import kr.kennysoft.kennymetro.domain.Train
import kr.kennysoft.kennymetro.domain.needsRecheck
import kr.kennysoft.kennymetro.domain.tidy
import kr.kennysoft.kennymetro.domain.toTrain
import kr.kennysoft.kennymetro.everline.EverlineClient
import kr.kennysoft.kennymetro.everline.toTrain
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

/**
 * 노선별 실시간 위치를 캐시해 돌려준다.
 * <p>
 * 실시간 지하철 API 는 인증키당 하루 1,000회다. 방문자마다 API 를 부르면 예산이
 * 몇 분 만에 마르므로, API 노선당 한 벌만 받아 모든 방문자가 나눠 본다. 1호선 경인과
 * 경부처럼 화면에서 갈라 놓은 뷰도 API 로는 한 노선이라 함께 쓴다. 캐시가 낡았을
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
    private val timetable: TimetableLookup,
    private val properties: SeoulSubwayProperties,
    private val ledger: ApiCallLedger,
    private val catalog: LineCatalog,
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val entries = ConcurrentHashMap<String, CacheEntry>()
    private val reported = ConcurrentHashMap.newKeySet<String>()

    /** 오늘 서울시 API 로 실제로 나간 호출 수. 일일 예산을 얼마나 썼는지 본다. */
    fun apiCallCount(): Long = ledger.today()

    fun snapshot(line: Line): TrainsSnapshot {
        val entry = entries.computeIfAbsent(line.cacheKey) { CacheEntry() }
        val now = clock.instant()

        if (entry.isFresh(now, properties.cacheTtl.toMillis())) {
            return entry.snapshot(line)
        }

        // 값이 아직 없으면 받아올 때까지 기다리고, 있으면 낡은 값을 주고 넘어간다.
        val acquired = if (entry.fetchedAt == null) {
            entry.lock.lock()
            true
        } else {
            entry.lock.tryLock()
        }
        if (!acquired) {
            return entry.snapshot(line)
        }

        try {
            // 기다리는 사이 다른 요청이 갱신했을 수 있다.
            if (entry.isFresh(clock.instant(), properties.cacheTtl.toMillis())) {
                return entry.snapshot(line)
            }
            return refresh(line, entry)
        } finally {
            entry.lock.unlock()
        }
    }

    private fun refresh(line: Line, entry: CacheEntry): TrainsSnapshot {
        val now = clock.instant()
        try {
            val fresh = fetch(line)
            entry.attemptedAt = now
            val previous = entry.fetchedAt
            // 운행 중인 노선이 잠깐 빈 응답으로 오는 일이 있다. 7호선이 34대에서 0대로 왔다가 5분 뒤
            // 돌아왔다. 그대로 받으면 그 사이 화면이 "열차 0대" 가 되므로 직전 값을 잠시 더 둔다.
            // 받은 시각은 옛 값의 것 그대로라 화면에 "N분 전 기준" 으로 드러난다.
            if (fresh.count == 0 && entry.positions.count > 0 && previous != null &&
                Duration.between(previous, now) < EMPTY_GRACE
            ) {
                log.info("빈 응답이 와서 직전 값을 유지한다. line={} previous={}", line.apiName, previous)
            } else {
                entry.positions = fresh
                entry.fetchedAt = now
            }
        } catch (e: Exception) {
            if (entry.fetchedAt == null) throw e
            // 다음 시도는 캐시 주기가 지난 뒤에 한다. 장애가 이어질 때 요청마다 다시 부르면
            // 예산만 깎인다.
            entry.attemptedAt = now
            // 직전 값이 있으면 그것을 계속 쓴다. 잠깐의 장애로 화면이 비지 않게 한다.
            log.warn("실시간 위치 갱신 실패, 직전 값을 유지한다. line={} error={}", line.name, e.message, e)
        }
        return entry.snapshot(line)
    }

    private fun fetch(line: Line): Positions = when (line.source) {
        // 호출이 실패해도 예산은 깎인다. 그래서 응답을 받기 전에 센다.
        LineSource.SEOUL -> {
            ledger.record()
            val raw = seoulClient.findPositions(line.apiName).tidy(catalog.stationNames)
            reportUnknown(line, raw.flatMap { listOf(it.statnNm, it.statnTnm) })
            val rechecks = recheck(line, raw)
            Positions(raw.size) { view -> raw.mapNotNull { it.toTrain(view, rechecks[it.trainNo]) } }
        }

        LineSource.EVERLINE -> {
            val raw = everlineClient.findPositions()
            Positions(raw.size) { view -> raw.mapNotNull { it.toTrain(view) } }
        }

        // 받을 곳이 없다. 화면은 이런 노선을 부르지 않지만, 불려도 아무 데도 호출하지 않는다.
        LineSource.NONE -> Positions(0) { emptyList() }
    }

    /**
     * 노선 중간에서 종착역과 `updnLine` 이 어긋나는 열차를 시간표로 다시 본다(DESIGN.md 2.2.2).
     *
     * <p>
     * 받아 올 때 한 번 정해 둔다. 뷰로 옮기는 것은 요청마다 다시 하므로 거기서 시간표를 부르지
     * 않는다. 같은 API 노선의 뷰 가운데 하나라도 어긋나 보이면 다시 본다.
     */
    private fun recheck(line: Line, raw: List<TrainPositionDto>): Map<String, Recheck> {
        val views = catalog.all().filter { it.cacheKey == line.cacheKey }
        return raw.filter { train -> views.any { train.needsRecheck(it) } }
            .associate { it.trainNo to timetable.recheck(line, it) }
    }

    /**
     * 우리가 모르는 역명을 한 번씩 로그로 남긴다.
     *
     * <p>
     * 모르는 역에 선 열차와 모르는 역으로 가는 열차는 화면에서 조용히 빠진다. 새 역이
     * 개통하거나 API 표기가 바뀌면 이렇게 드러난다. 같은 이름을 호출마다 남기지 않는다.
     */
    private fun reportUnknown(line: Line, names: List<String>) {
        val known = catalog.knownNames(line.cacheKey)
        names.filter { it !in known }.toSet().forEach { name ->
            if (reported.add("${line.cacheKey}:$name")) {
                log.warn("모르는 역명이라 그 열차가 화면에서 빠진다. lines.yml 에 반영할 것. line={} name={}", line.apiName, name)
            }
        }
    }

    /**
     * 받아 온 응답 한 벌.
     *
     * <p>
     * 도메인 열차가 아니라 변환 함수를 두는 것은 뷰마다 역 목록이 달라서다. 1호선 경인과
     * 경부는 같은 응답을 나눠 쓰지만 인천행을 담는 쪽과 신창행을 담는 쪽이 갈리므로, 변환은
     * 받아 올 때가 아니라 뷰가 물어볼 때 해야 한다.
     */
    private class Positions(val count: Int, private val convert: (Line) -> List<Train>) {
        fun trainsFor(line: Line): List<Train> = convert(line)
    }

    private class CacheEntry {
        val lock = ReentrantLock()

        @Volatile
        var positions: Positions = Positions(0) { emptyList() }

        /** 지금 보여 주는 값을 받은 시각. 화면에 "N초 전 기준" 으로 나간다. */
        @Volatile
        var fetchedAt: Instant? = null

        /** 마지막으로 받아 보려 한 시각. 캐시 주기는 이것으로 잰다. */
        @Volatile
        var attemptedAt: Instant? = null

        fun isFresh(now: Instant, ttlMillis: Long): Boolean {
            val at = attemptedAt ?: return false
            return now.toEpochMilli() - at.toEpochMilli() < ttlMillis
        }

        fun snapshot(line: Line): TrainsSnapshot = TrainsSnapshot(positions.trainsFor(line), fetchedAt)
    }

    private companion object {
        /** 빈 응답을 잠깐의 공백으로 보고 직전 값을 더 둘 시간. 막차 뒤라면 그만큼 늦게 빈다. */
        private val EMPTY_GRACE: Duration = Duration.ofMinutes(3)
    }
}

data class TrainsSnapshot(
    val trains: List<Train>,
    val fetchedAt: Instant?,
)
