package kr.kennysoft.kennymetro.seoul

import kr.kennysoft.kennymetro.TestLines
import kr.kennysoft.kennymetro.everline.EverlineClient
import kr.kennysoft.kennymetro.everline.EverlineProperties
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class TrainPositionServiceTest {

    @TempDir
    lateinit var dir: Path

    private val clock = MovableClock()
    private val seoul = FakeSeoulClient()

    @Test
    fun `같은 API 노선의 지선 뷰는 한 번 받은 것을 나눠 쓴다`() {
        // given - 1호선 경인과 경부는 화면에서 갈라 놓았을 뿐 API 로는 한 노선이다.
        seoul.next = listOf(position("0643", "회기", "천안"), position("0072", "제물포", "의정부"))
        val service = service()

        // when
        val gyeongin = service.snapshot(TestLines.bySlug("line1-gyeongin"))
        val gyeongbu = service.snapshot(TestLines.bySlug("line1-gyeongbu"))

        // then - 호출은 한 번이고, 뷰마다 자기 역 목록으로 따로 옮긴다. 제물포는 경부 뷰에 없다.
        assertEquals(1, seoul.calls)
        assertEquals(setOf("0643", "0072"), gyeongin.trains.map { it.trainNo }.toSet())
        assertEquals(setOf("0643"), gyeongbu.trains.map { it.trainNo }.toSet())
    }

    @Test
    fun `운행 중에 잠깐 빈 응답이 오면 직전 값을 받은 시각 그대로 둔다`() {
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
        assertEquals(1, blip.trains.size)
        assertEquals(first.fetchedAt, blip.fetchedAt)
    }

    @Test
    fun `빈 응답이 오래 이어지면 받아들인다`() {
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
        assertEquals(0, later.trains.size)
        assertEquals(clock.instant(), later.fetchedAt)
    }

    private fun service(): TrainPositionService {
        val properties = SeoulSubwayProperties(
            apiKey = "test-key",
            baseUrl = "http://localhost",
            cacheTtl = Duration.ofSeconds(70),
            requestTimeout = Duration.ofSeconds(5),
            dailyCallBudget = 1000,
            callLogPath = dir.resolve("api-calls.log").toString(),
        )
        return TrainPositionService(
            seoulClient = seoul,
            everlineClient = EverlineClient(EverlineProperties("http://localhost", Duration.ofSeconds(5))),
            properties = properties,
            ledger = ApiCallLedger(properties, clock),
            catalog = TestLines.catalog,
            clock = clock,
        )
    }

    private fun position(trainNo: String, current: String, terminal: String) = TrainPositionDto(
        subwayId = "1001",
        subwayNm = "1호선",
        statnId = "1001000000",
        statnNm = current,
        trainNo = trainNo,
        statnTid = "1001000001",
        statnTnm = terminal,
        updnLine = "1",
        trainSttus = "1",
        directAt = "0",
        lstcarAt = "0",
        recptnDt = "2026-09-23 10:50:00",
    )

    /** 부른 횟수를 세고 정해 둔 응답을 돌려준다. */
    private class FakeSeoulClient : SeoulSubwayClient(
        SeoulSubwayProperties("k", "http://localhost", Duration.ofSeconds(70), Duration.ofSeconds(5), 1000, "unused"),
    ) {
        var next: List<TrainPositionDto> = emptyList()
        var calls = 0

        override fun findPositions(lineName: String): List<TrainPositionDto> {
            calls++
            return next
        }
    }

    private class MovableClock : Clock() {
        private var now: Instant = Instant.parse("2026-09-23T01:50:00Z")
        fun advance(by: Duration) {
            now = now.plus(by)
        }

        override fun getZone(): ZoneId = ZoneId.of("Asia/Seoul")
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = now
    }
}
