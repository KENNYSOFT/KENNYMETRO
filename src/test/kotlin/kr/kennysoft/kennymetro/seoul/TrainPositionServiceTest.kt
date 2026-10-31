package kr.kennysoft.kennymetro.seoul

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.FreeSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kr.kennysoft.kennymetro.TestLines
import kr.kennysoft.kennymetro.domain.Direction
import kr.kennysoft.kennymetro.domain.ServiceKind
import kr.kennysoft.kennymetro.domain.TrainStatus
import kr.kennysoft.kennymetro.everline.EverlineClient
import kr.kennysoft.kennymetro.everline.EverlineProperties
import java.time.Duration

class TrainPositionServiceTest : FreeSpec({

    // 테스트가 시계를 움직이고 가짜 클라이언트의 응답을 바꾸므로, 테스트마다 스펙을 새로 만들어 섞이지 않게 한다.
    isolationMode = IsolationMode.InstancePerRoot

    val dir = tempdir().toPath()
    val clock = AdvancingClock()
    val seoul = FakeSeoulClient()
    val timetables = FakeTimetableClient()

    fun service(): TrainPositionService {
        val properties = SeoulSubwayProperties(
            apiKey = "test-key",
            baseUrl = "http://localhost",
            timetableBaseUrl = "http://localhost",
            cacheTtl = Duration.ofSeconds(70),
            requestTimeout = Duration.ofSeconds(5),
            dailyCallBudget = 1000,
            callLogPath = dir.resolve("api-calls.log").toString(),
        )
        val ledger = ApiCallLedger(properties, clock)
        return TrainPositionService(
            seoulClient = seoul,
            everlineClient = EverlineClient(EverlineProperties("http://localhost", Duration.ofSeconds(5))),
            timetable = TimetableLookup(timetables, ledger, TestLines.catalog, clock),
            properties = properties,
            ledger = ledger,
            catalog = TestLines.catalog,
            clock = clock,
        )
    }

    "같은 API 노선의 지선 뷰는 한 번 받은 것을 나눠 쓴다" {
        // given - 1호선 경인과 경부는 화면에서 갈라 놓았을 뿐 API 로는 한 노선이다.
        seoul.next = listOf(position("0643", "회기", "천안"), position("0072", "제물포", "의정부", updnLine = "0"))
        val service = service()

        // when
        val gyeongin = service.snapshot(TestLines.bySlug("line1-gyeongin"))
        val gyeongbu = service.snapshot(TestLines.bySlug("line1-gyeongbu"))

        // then - 호출은 한 번이고, 뷰마다 자기 역 목록으로 따로 옮긴다. 제물포는 경부 뷰에 없다.
        seoul.calls shouldBe 1
        gyeongin.trains.map { it.trainNo }.toSet() shouldBe setOf("0643", "0072")
        gyeongbu.trains.map { it.trainNo }.toSet() shouldBe setOf("0643")
    }

    "운행 중에 잠깐 빈 응답이 오면 직전 값을 받은 시각 그대로 둔다" {
        // given - 7호선이 34대에서 0대로 왔다가 5분 뒤 돌아온 적이 있다.
        val line = TestLines.bySlug("line3")
        val service = service()
        seoul.next = listOf(position("3144", "교대", "오금"))
        val first = service.snapshot(line)

        // when - 캐시 주기가 지나 다시 받았는데 비어 있다.
        clock.advance(Duration.ofSeconds(71))
        seoul.next = emptyList()
        val blip = service.snapshot(line)

        // then - 열차를 지우지 않고, 받은 시각이 옛 값이라 화면에 "N분 전 기준" 으로 드러난다.
        blip.trains shouldHaveSize 1
        blip.fetchedAt shouldBe first.fetchedAt
    }

    "빈 응답이 오래 이어지면 받아들인다" {
        // given - 막차가 끝난 뒤라면 정말로 열차가 없다.
        val line = TestLines.bySlug("line3")
        val service = service()
        seoul.next = listOf(position("3144", "교대", "오금"))
        service.snapshot(line)

        // when
        clock.advance(Duration.ofMinutes(4))
        seoul.next = emptyList()
        val later = service.snapshot(line)

        // then
        later.trains.shouldBeEmpty()
        later.fetchedAt shouldBe clock.instant()
    }

    "종착역이 다음 운행 것으로 앞서 바뀐 열차는 시간표의 종착역으로 그린다" {
        // given - 2026-09-24 00:59 에 받은 3호선 3423. 약수로 가는 하행 막차인데 종착이 구파발로 왔다.
        seoul.next = listOf(
            position("3423", "동대입구", "구파발", updnLine = "1", lastTrain = "1", receivedAt = "2026-09-24 00:59:24"),
            position("3144", "교대", "오금", updnLine = "1"),
        )
        timetables.stations["동대입구"] = listOf(StationRow("0322", "동대입구", "03호선"))
        timetables.rows[Triple("0322", "1", "2")] = listOf(TimetableRow("3423", "24:58:30", "24:59:00", "대화", "약수"))
        val service = service()

        // when
        val trains = service.snapshot(TestLines.bySlug("line3")).trains

        // then - 약수는 평소 종착역이 아니라 단축이고, 막차 표시는 실시간 값 그대로다.
        val corrected = trains.single { it.trainNo == "3423" }
        corrected.destination shouldBe "약수"
        corrected.direction shouldBe Direction.DOWN
        corrected.service shouldBe ServiceKind.SHORT
        corrected.isLastTrain shouldBe true
        // 자정을 넘긴 시각은 전날(수요일) 평일 시간표에서 찾는다. 어긋나지 않은 3144 는 시간표를 부르지 않는다.
        timetables.calls shouldBe listOf("역 동대입구", "시간표 0322/1/1", "시간표 0322/1/2")
        trains.single { it.trainNo == "3144" }.destination shouldBe "오금"
    }

    "시간표가 없는 노선은 updnLine 으로 방향만 정하고 종착역은 비운다" {
        // given - 2026-09-23 10:50 의 우이신설선 1147. 북한산우이로 가는데 종착이 신설동으로 왔다.
        seoul.next = listOf(position("1147", "가오리", "신설동", updnLine = "1", receivedAt = "2026-09-23 10:50:48"))
        val service = service()

        // when
        val train = service.snapshot(TestLines.bySlug("uisinseol")).trains.single()

        // then - 이 노선은 updnLine 1 이 북한산우이 쪽이다. 어디서 끝나는지 모르니 단축으로 칠하지 않는다.
        train.destination shouldBe null
        train.direction shouldBe Direction.UP
        train.service shouldBe ServiceKind.NORMAL
        timetables.calls.shouldBeEmpty()
    }

    "실시간 위치를 받을 곳이 없는 노선은 아무 데도 부르지 않고 빈 목록을 준다" {
        // given - 인천1호선은 서울시 API 에 없고 인천교통공사도 실시간 위치를 내지 않는다.
        val service = service()

        // when
        val snapshot = service.snapshot(TestLines.bySlug("incheon1"))

        // then - 예산을 깎지 않는다.
        snapshot.trains.shouldBeEmpty()
        seoul.calls shouldBe 0
        service.apiCallCount() shouldBe 0
    }

    "시간표 호출도 같은 인증키로 나가므로 원장에 센다" {
        // given
        seoul.next = listOf(position("3423", "동대입구", "구파발", updnLine = "1", receivedAt = "2026-09-24 00:59:24"))
        timetables.stations["동대입구"] = listOf(StationRow("0322", "동대입구", "03호선"))
        val service = service()

        // when - 시간표에서 못 찾아 휴일 시간표까지 본다.
        service.snapshot(TestLines.bySlug("line3"))

        // then - 실시간 1회, 역 코드 1회, 평일과 휴일의 상하행 4회.
        service.apiCallCount() shouldBe 6
    }

    /** 광명셔틀 뷰의 역마다 역 코드를 두고, 10:48 에 광명을 떠나 영등포로 가는 7012 를 시간표에 적는다. 시각은 예로 든 값이다. */
    fun shuttleTimetable() {
        listOf("영등포", "신도림", "구로", "가산디지털단지", "독산", "금천구청", "광명").forEachIndexed { i, name ->
            timetables.stations[name] = listOf(StationRow("S$i", name, "01호선"))
        }
        timetables.rows[Triple("S6", "1", "1")] = listOf(TimetableRow("K7012", "00:00:00", "10:48:00", "광명", "영등포"))
        timetables.rows[Triple("S5", "1", "1")] = listOf(TimetableRow("K7012", "10:52:30", "10:53:00", "광명", "영등포"))
        timetables.rows[Triple("S0", "1", "1")] = listOf(TimetableRow("K7012", "11:05:00", "00:00:00", "광명", "영등포"))
    }

    "실시간 위치에 없는 광명셔틀은 시간표로 받은 시각의 자리에 그린다" {
        // given - 수요일 10:50 에 받는다. 7012 는 광명을 2분 전에 떠나 실시간 위치가 오지 않는 금천구청 앞을 달린다.
        seoul.next = listOf(position("0643", "구로", "천안"))
        shuttleTimetable()
        val service = service()

        // when
        val trains = service.snapshot(TestLines.bySlug("line1-gwangmyeong")).trains

        // then - 화면이 역 사이에 놓도록 떠난 뒤 흐른 초와 다음 역까지 걸리는 초를 함께 준다.
        val shuttle = trains.single { it.trainNo == "7012" }
        shuttle.currentStation shouldBe "광명"
        shuttle.destination shouldBe "영등포"
        shuttle.direction shouldBe Direction.UP
        shuttle.status shouldBe TrainStatus.DEPARTED
        shuttle.elapsedSeconds shouldBe 120
        shuttle.segmentSeconds shouldBe 270
        shuttle.scheduled shouldBe true
        trains.single { it.trainNo == "0643" }.scheduled shouldBe false
    }

    "시간표로 그리던 열차가 실시간 위치에 오면 실시간 쪽을 쓴다" {
        // given - 같은 번호가 실시간 위치에 왔다.
        seoul.next = listOf(position("7012", "금천구청", "영등포", updnLine = "0"))
        shuttleTimetable()

        // when
        val trains = service().snapshot(TestLines.bySlug("line1-gwangmyeong")).trains

        // then - 한 대가 두 대로 보이지 않는다.
        trains shouldHaveSize 1
        trains.single().currentStation shouldBe "금천구청"
        trains.single().scheduled shouldBe false
    }

    "광명 바로 앞 역에 멈춘 실시간 기록은 버리고 시간표의 자리를 그린다" {
        // given - 영등포에서 광명으로 가는 7011 이 10:48 에 금천구청을 떠났다. 금천구청과 광명 사이에서는 실시간
        // 위치가 오지 않아 금천구청에서 받은 기록이 그대로 남아 있다. 시각은 예로 든 값이다.
        shuttleTimetable()
        timetables.rows[Triple("S0", "1", "2")] = listOf(TimetableRow("K7011", "00:00:00", "10:38:00", "영등포", "광명"))
        timetables.rows[Triple("S5", "1", "2")] = listOf(TimetableRow("K7011", "10:47:30", "10:48:00", "영등포", "광명"))
        timetables.rows[Triple("S6", "1", "2")] = listOf(TimetableRow("K7011", "10:53:00", "00:00:00", "영등포", "광명"))
        seoul.next = listOf(position("7011", "금천구청", "광명", receivedAt = "2026-09-23 10:47:40"))
        val service = service()
        val line = TestLines.bySlug("line1-gwangmyeong")

        // when - 10:50 에 받고, 시간표로 광명에 닿은 뒤인 10:54 에 다시 받는다. 기록은 그대로다.
        val running = service.snapshot(line).trains
        clock.advance(Duration.ofMinutes(4))
        val arrived = service.snapshot(line).trains

        // then - 달리는 동안은 시간표의 자리로 광명 쪽에 다가가고, 닿은 뒤에는 그리지 않는다.
        val shuttle = running.single { it.trainNo == "7011" }
        shuttle.scheduled shouldBe true
        shuttle.currentStation shouldBe "금천구청"
        shuttle.status shouldBe TrainStatus.DEPARTED
        shuttle.elapsedSeconds shouldBe 120
        shuttle.segmentSeconds shouldBe 300
        arrived.none { it.trainNo == "7011" } shouldBe true
    }

    "시간표를 받지 못하면 셔틀만 빠지고 실시간 열차는 그대로 그린다" {
        // given
        seoul.next = listOf(position("0643", "구로", "천안"))
        shuttleTimetable()
        timetables.failure = IllegalStateException("시간표 조회 실패")

        // when
        val trains = service().snapshot(TestLines.bySlug("line1-gwangmyeong")).trains

        // then
        trains.map { it.trainNo } shouldBe listOf("0643")
    }
})

private fun position(
    trainNo: String,
    current: String,
    terminal: String,
    updnLine: String = "1",
    lastTrain: String = "0",
    receivedAt: String = "2026-09-23 10:50:00",
) = TrainPositionDto(
    subwayId = "1001",
    subwayNm = "1호선",
    statnId = "1001000000",
    statnNm = current,
    trainNo = trainNo,
    statnTid = "1001000001",
    statnTnm = terminal,
    updnLine = updnLine,
    trainSttus = "1",
    directAt = "0",
    lstcarAt = lastTrain,
    recptnDt = receivedAt,
)

/** 부른 횟수를 세고 정해 둔 응답을 돌려준다. */
private class FakeSeoulClient : SeoulSubwayClient(UNUSED_PROPERTIES) {
    var next: List<TrainPositionDto> = emptyList()
    var calls = 0

    override fun findPositions(lineName: String): List<TrainPositionDto> {
        calls++
        return next
    }
}
