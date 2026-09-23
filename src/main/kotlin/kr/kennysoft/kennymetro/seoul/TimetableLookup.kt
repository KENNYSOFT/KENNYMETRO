package kr.kennysoft.kennymetro.seoul

import kr.kennysoft.kennymetro.domain.Line
import kr.kennysoft.kennymetro.domain.LineCatalog
import kr.kennysoft.kennymetro.domain.Recheck
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * 종착역을 믿을 수 없는 열차를 그 역의 시간표에서 찾는다(DESIGN.md 2.2.2).
 *
 * <p>
 * 어긋나는 열차가 나올 때만 부른다. 한 번 받은 역 코드와 시간표는 하루 동안 다시 부르지 않고,
 * 부르다 실패하면 10분 동안 다시 부르지 않는다 - 실시간 위치를 받는 요청이 이 호출을 기다리므로,
 * 장애가 이어질 때 갱신마다 다시 부르면 화면이 그만큼 늦어진다. 같은 인증키로 부르므로 호출은
 * 실시간 API 와 함께 원장에 센다.
 */
@Service
class TimetableLookup(
    private val client: SeoulTimetableClient,
    private val ledger: ApiCallLedger,
    private val catalog: LineCatalog,
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val stations = ConcurrentHashMap<String, Cached<List<StationRow>>>()
    private val timetables = ConcurrentHashMap<TimetableKey, Cached<List<TimetableRow>>>()

    /** 그 열차를 지금 선 역의 시간표에서 찾아 이 운행의 종착역을 돌려준다. */
    fun recheck(line: Line, train: TrainPositionDto): Recheck {
        val found = find(line, train)
        val result = if (found == null) Recheck.Unknown else Recheck.Terminal(catalog.stationNames.normalize(found.terminal))
        log.info(
            "종착역과 updnLine 이 어긋나 시간표로 다시 봤다. line={} train={} station={} terminal={} updnLine={} result={}",
            line.apiName, train.trainNo, train.statnNm, train.statnTnm, train.updnLine, result,
        )
        return result
    }

    private fun find(line: Line, train: TrainPositionDto): TimetableRow? {
        val timetableLine = line.timetableLine ?: return null
        val receivedAt = runCatching { LocalDateTime.parse(train.recptnDt, RECEIVED_AT) }.getOrNull() ?: return null
        val code = stationCode(timetableLine, train.statnNm) ?: return null
        val time = ServiceTime.of(receivedAt)
        for (weekTag in time.weekTags) {
            // 어느 쪽으로 가는지를 가리려는 것이라 상행과 하행을 모두 본다.
            val rows = INOUT_TAGS.flatMap { inoutTag -> timetable(code, weekTag, inoutTag) ?: return null }
            rows.findTrain(train.trainNo, time.seconds)?.let { return it }
        }
        return null
    }

    private fun stationCode(timetableLine: String, station: String): String? =
        stations.load(station, "역 코드") { client.findStations(station) }
            ?.firstOrNull { it.lineNum == timetableLine }
            ?.stationCode

    private fun timetable(code: String, weekTag: String, inoutTag: String): List<TimetableRow>? =
        timetables.load(TimetableKey(code, weekTag, inoutTag), "시간표") {
            client.findTimetable(code, weekTag, inoutTag).also {
                log.info("시간표를 받았다. station={} week={} inout={} rows={}", code, weekTag, inoutTag, it.size)
            }
        }

    /** 기억해 둔 값이 있으면 그것을, 없거나 낡았으면 새로 받아 돌려준다. 실패하면 null 이다. */
    private fun <K : Any, V : Any> ConcurrentHashMap<K, Cached<V>>.load(key: K, what: String, fetch: () -> V): V? {
        val now = clock.instant()
        val cached = get(key)
        if (cached != null) {
            val keep = if (cached.value == null) RETRY_AFTER else KEEP_FOR
            if (Duration.between(cached.at, now) < keep) return cached.value
        }
        val value = try {
            // 실패해도 호출은 나간 것이라 받기 전에 센다.
            ledger.record()
            fetch()
        } catch (e: Exception) {
            log.warn("{} 조회 실패. {} 동안 다시 부르지 않는다. key={} error={}", what, RETRY_AFTER, key, e.message, e)
            null
        }
        put(key, Cached(value, now))
        return value
    }

    private data class TimetableKey(val stationCode: String, val weekTag: String, val inoutTag: String)

    /** `value` 가 null 이면 실패를 기억해 둔 것이다. */
    private class Cached<V>(val value: V?, val at: Instant)

    private companion object {
        private val RECEIVED_AT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        private val KEEP_FOR: Duration = Duration.ofDays(1)
        private val RETRY_AFTER: Duration = Duration.ofMinutes(10)
        private val INOUT_TAGS = listOf("1", "2")
    }
}

/**
 * 수신 시각이 어느 운행일의 몇 초째인지.
 *
 * <p>
 * 운행일은 04시에 바뀐다. 막차는 시간표에 `24:58:30` 처럼 적혀 있어, 04시 전의 시각은 전날
 * 운행일에 24시간을 더해 센다.
 */
internal data class ServiceTime(val date: LocalDate, val seconds: Int) {

    /**
     * 볼 요일 구분(1 평일, 2 토요일, 3 휴일)을 먼저 볼 것부터. 공휴일 달력을 두지 않으므로 평일과
     * 토요일은 그 요일 시간표에서 못 찾으면 휴일 시간표를 한 번 더 본다.
     */
    val weekTags: List<String>
        get() = when (date.dayOfWeek) {
            DayOfWeek.SATURDAY -> listOf("2", "3")
            DayOfWeek.SUNDAY -> listOf("3")
            else -> listOf("1", "3")
        }

    companion object {
        private const val DAY_STARTS_AT_HOUR = 4

        fun of(at: LocalDateTime): ServiceTime {
            val date = if (at.hour < DAY_STARTS_AT_HOUR) at.toLocalDate().minusDays(1) else at.toLocalDate()
            return ServiceTime(date, Duration.between(date.atStartOfDay(), at).seconds.toInt())
        }
    }
}

/**
 * 시간표에서 그 열차를 고른다.
 *
 * <p>
 * 열차번호는 노선마다 표기가 조금씩 달라 숫자만 떼어 견준다. 1호선은 `K56` 이 실시간의 `0056` 이고
 * 3호선은 `3426K` 처럼 뒤에 글자가 붙는다. 2호선은 첫 자리가 달라(실시간 `3180`, 시간표 `2180`)
 * 숫자가 통째로 같은 열차가 없으면 끝 세 자리로 견준다. 그 가운데 그 역의 도착이나 출발 시각이
 * 수신 시각에서 30분 안인 것 중 가장 가까운 것을 쓴다.
 */
internal fun List<TimetableRow>.findTrain(trainNo: String, seconds: Int): TimetableRow? {
    val near = mapNotNull { row -> row.gapFrom(seconds)?.takeIf { it <= MATCH_WINDOW_SECONDS }?.let { row to it } }
    val digits = trainNo.digits()
    val same = near.filter { (row, _) -> row.trainNo.digits() == digits }
    val sameTail = near.filter { (row, _) -> row.trainNo.digits().tail() == digits.tail() }
    return same.ifEmpty { sameTail }.minByOrNull { (_, gap) -> gap }?.first
}

private const val MATCH_WINDOW_SECONDS = 30 * 60

/** 이 역의 도착 또는 출발 시각과 벌어진 초. 두 시각이 모두 비었으면 null 이다. */
private fun TimetableRow.gapFrom(seconds: Int): Int? =
    listOf(arriveTime, leftTime).mapNotNull { it.toSeconds() }.minOfOrNull { abs(it - seconds) }

/** `24:58:30` 을 초로 옮긴다. 이 역에 그 시각이 없다는 `00:00:00` 과 알아볼 수 없는 값은 null 이다. */
private fun String.toSeconds(): Int? {
    val parts = split(":").mapNotNull { it.toIntOrNull() }
    if (parts.size != 3) return null
    return (parts[0] * 3600 + parts[1] * 60 + parts[2]).takeIf { it > 0 }
}

private fun String.digits(): String = filter { it.isDigit() }.trimStart('0')

private fun String.tail(): String = padStart(3, '0').takeLast(3)
