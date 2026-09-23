package kr.kennysoft.kennymetro.seoul

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.FreeSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kr.kennysoft.kennymetro.TestLines
import kr.kennysoft.kennymetro.domain.Recheck
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

class TimetableLookupTest : FreeSpec({

    // 테스트가 시계를 움직이고 가짜 클라이언트의 응답을 바꾸므로, 테스트마다 스펙을 새로 만들어 섞이지 않게 한다.
    isolationMode = IsolationMode.InstancePerRoot

    val dir = tempdir().toPath()
    val clock = AdvancingClock()
    val client = FakeTimetableClient().apply {
        stations["동대입구"] = listOf(StationRow("0322", "동대입구", "03호선"))
    }

    fun lookup() = TimetableLookup(client, ApiCallLedger(UNUSED_PROPERTIES.copy(callLogPath = dir.resolve("api-calls.log").toString()), clock), TestLines.catalog, clock)

    "운행일은 04시에 바뀌고 자정을 넘긴 시각은 24시를 더해 센다" {
        // given - 시간표는 막차를 24:58:30 처럼 적는다.
        val afterMidnight = LocalDateTime.parse("2026-09-24T00:59:24")
        val morning = LocalDateTime.parse("2026-09-26T04:00:00")

        // when
        val late = ServiceTime.of(afterMidnight)
        val early = ServiceTime.of(morning)

        // then - 목요일 새벽은 수요일 운행이라 평일 시간표를 보고, 공휴일일 수 있어 휴일 시간표도 본다.
        late shouldBe ServiceTime(LocalDate.parse("2026-09-23"), 24 * 3600 + 59 * 60 + 24)
        late.weekTags shouldBe listOf("1", "3")
        early.date shouldBe LocalDate.parse("2026-09-26")
        early.weekTags shouldBe listOf("2", "3")
        ServiceTime.of(LocalDateTime.parse("2026-09-27T12:00:00")).weekTags shouldBe listOf("3")
    }

    "열차번호는 숫자만 떼어 견주고 첫 자리가 다른 노선은 끝 세 자리로 견준다" {
        // given - 1호선은 K56 이 실시간의 0056 이고, 2호선은 실시간 3180 이 시간표의 2180 이다.
        val rows = listOf(row("K56", "10:00:00", "10:00:30"), row("2180", "10:05:00", "10:05:30"))

        // when & then
        rows.findTrain("0056", seconds("10:01:00"))?.trainNo shouldBe "K56"
        rows.findTrain("3180", seconds("10:04:00"))?.trainNo shouldBe "2180"
    }

    "숫자가 통째로 같은 열차를 끝 세 자리만 같은 열차보다 먼저 고른다" {
        // given - 끝 세 자리가 같은 K602 가 시각은 더 가깝다.
        val rows = listOf(row("K602", "10:01:00", "10:01:30"), row("K1602", "10:10:00", "10:10:30"))

        // when & then
        rows.findTrain("1602", seconds("10:00:00"))?.trainNo shouldBe "K1602"
    }

    "시각이 30분 넘게 벌어진 열차는 고르지 않는다" {
        // given - 같은 번호라도 시각이 멀면 다른 운행이다.
        val rows = listOf(row("3423", "24:58:30", "24:59:00"))

        // when & then
        rows.findTrain("3423", seconds("24:20:00")).shouldBeNull()
        rows.findTrain("3423", seconds("24:30:00"))?.trainNo shouldBe "3423"
    }

    "출발역과 종착역처럼 한 시각만 있는 줄도 그 시각으로 견준다" {
        // given - 출발역은 도착 시각이, 종착역은 출발 시각이 00:00:00 으로 온다.
        val departing = listOf(row("4301", "00:00:00", "05:30:00"))
        val arriving = listOf(row("4302", "25:00:00", "00:00:00"))

        // when & then
        departing.findTrain("4301", seconds("05:31:00"))?.trainNo shouldBe "4301"
        arriving.findTrain("4302", seconds("24:58:00"))?.trainNo shouldBe "4302"
    }

    "한 번 받은 역 코드와 시간표는 다시 부르지 않는다" {
        // given
        client.rows[Triple("0322", "1", "2")] = listOf(row("3423", "24:58:30", "24:59:00", terminal = "약수"))
        val lookup = lookup()

        // when - 다음 갱신에서도 같은 열차가 어긋난 채로 온다.
        val first = lookup.recheck(TestLines.bySlug("line3"), glitch())
        val second = lookup.recheck(TestLines.bySlug("line3"), glitch())

        // then
        first shouldBe Recheck.Terminal("약수")
        second shouldBe Recheck.Terminal("약수")
        client.calls shouldBe listOf("역 동대입구", "시간표 0322/1/1", "시간표 0322/1/2")
    }

    "그 요일 시간표에 없으면 휴일 시간표에서 찾는다" {
        // given - 평일에 낀 공휴일. 공휴일 달력을 두지 않는다.
        client.rows[Triple("0322", "3", "2")] = listOf(row("3423", "24:58:30", "24:59:00", terminal = "약수"))

        // when
        val result = lookup().recheck(TestLines.bySlug("line3"), glitch())

        // then
        result shouldBe Recheck.Terminal("약수")
        client.calls shouldBe listOf("역 동대입구", "시간표 0322/1/1", "시간표 0322/1/2", "시간표 0322/3/1", "시간표 0322/3/2")
    }

    "시간표를 받지 못하면 종착역을 모른다고 두고 10분 동안 다시 부르지 않는다" {
        // given - 장애가 이어질 때 갱신마다 다시 부르면 실시간 위치를 받는 요청이 그만큼 늦어진다.
        client.failure = IllegalStateException("시간표 조회 실패")
        val lookup = lookup()
        lookup.recheck(TestLines.bySlug("line3"), glitch()) shouldBe Recheck.Unknown
        client.calls.clear()

        // when
        clock.advance(Duration.ofMinutes(9))
        val soon = lookup.recheck(TestLines.bySlug("line3"), glitch())
        clock.advance(Duration.ofMinutes(2))
        client.failure = null
        client.rows[Triple("0322", "1", "2")] = listOf(row("3423", "24:58:30", "24:59:00", terminal = "약수"))
        val later = lookup.recheck(TestLines.bySlug("line3"), glitch())

        // then - 역 코드는 받아 두었으므로 시간표만 다시 부른다.
        soon shouldBe Recheck.Unknown
        later shouldBe Recheck.Terminal("약수")
        client.calls shouldBe listOf("시간표 0322/1/1", "시간표 0322/1/2")
    }
})

/** 2026-09-24 00:59 에 받은 3호선 3423. 약수로 가는 하행 막차인데 종착이 다음 운행의 구파발로 왔다. */
private fun glitch() = TrainPositionDto(
    subwayId = "1003",
    subwayNm = "3호선",
    statnId = "1003000332",
    statnNm = "동대입구",
    trainNo = "3423",
    statnTid = "1003000320",
    statnTnm = "구파발",
    updnLine = "1",
    trainSttus = "1",
    directAt = "0",
    lstcarAt = "1",
    recptnDt = "2026-09-24 00:59:24",
)

private fun row(trainNo: String, arrive: String, leave: String, terminal: String = "오금") =
    TimetableRow(trainNo = trainNo, arriveTime = arrive, leftTime = leave, origin = "대화", terminal = terminal)

private fun seconds(time: String): Int = time.split(":").map { it.toInt() }.let { (h, m, s) -> h * 3600 + m * 60 + s }
