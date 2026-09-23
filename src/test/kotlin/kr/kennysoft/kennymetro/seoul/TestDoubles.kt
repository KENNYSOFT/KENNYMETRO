package kr.kennysoft.kennymetro.seoul

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** 가짜 클라이언트가 부모 생성자에 넘기는 접속 설정. 실제로 부르지 않으므로 값은 쓰이지 않는다. */
internal val UNUSED_PROPERTIES =
    SeoulSubwayProperties("k", "http://localhost", "http://localhost", Duration.ofSeconds(70), Duration.ofSeconds(5), 1000, "unused")

/**
 * 정해 둔 역과 시간표를 돌려주고, 무엇을 불렀는지 순서대로 남긴다. 정하지 않은 것은 비어 있다.
 * `failure` 를 두면 시간표를 부를 때 그것을 던진다.
 */
internal class FakeTimetableClient : SeoulTimetableClient(UNUSED_PROPERTIES) {
    val stations = mutableMapOf<String, List<StationRow>>()
    val rows = mutableMapOf<Triple<String, String, String>, List<TimetableRow>>()
    val calls = mutableListOf<String>()
    var failure: Exception? = null

    override fun findStations(name: String): List<StationRow> {
        calls += "역 $name"
        return stations[name].orEmpty()
    }

    override fun findTimetable(stationCode: String, weekTag: String, inoutTag: String): List<TimetableRow> {
        calls += "시간표 $stationCode/$weekTag/$inoutTag"
        failure?.let { throw it }
        return rows[Triple(stationCode, weekTag, inoutTag)].orEmpty()
    }
}

/** 캐시 주기와 빈 응답 유예, 실패 뒤 다시 부르기까지를 넘겨 보려면 시계를 앞으로 보낼 수 있어야 한다. */
internal class AdvancingClock : Clock() {
    private var now: Instant = Instant.parse("2026-09-23T01:50:00Z")
    fun advance(by: Duration) {
        now = now.plus(by)
    }

    override fun getZone(): ZoneId = ZoneId.of("Asia/Seoul")
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = now
}
