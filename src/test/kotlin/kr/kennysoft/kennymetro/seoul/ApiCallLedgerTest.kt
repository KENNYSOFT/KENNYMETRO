package kr.kennysoft.kennymetro.seoul

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApiCallLedgerTest {

    @TempDir
    lateinit var dir: Path

    @Test
    fun `날짜가 바뀌면 오늘 수가 0 으로 돌아간다`() {
        // given - 일일 한도는 자정에 돌아온다. 누적으로 세면 남은 예산을 가리키지 못한다.
        val clock = MovableClock("2026-09-23T01:00:00Z")
        val ledger = ledger(clock)
        repeat(3) { ledger.record() }

        // when
        // 서울 기준 다음 날 00:00:01 로 넘긴다.
        clock.now = Instant.parse("2026-09-23T15:00:01Z")

        // then
        assertEquals(0L, ledger.today())
        assertEquals(1L, ledger.record())
    }

    @Test
    fun `다시 띄워도 그 날 쓴 수를 이어받는다`() {
        // given - 재배포마다 0 부터 세면 화면의 남은 예산이 실제 한도와 어긋난다.
        val clock = MovableClock("2026-09-23T01:00:00Z")
        repeat(7) { ledger(clock).record() }

        // when
        val restarted = ledger(clock)

        // then
        assertEquals(7L, restarted.today())
        assertEquals(8L, restarted.record())
        assertTrue(
            Files.readString(path()).contains("2026-09-23 8"),
            "원장에 오늘 줄이 없다: ${Files.readString(path())}",
        )
    }

    @Test
    fun `쓸 수 없는 경로여도 호출은 센다`() {
        // given - 볼륨을 빠뜨렸다고 화면이 안 뜨면 안 된다. 디렉터리 자리에 파일을 둬 막는다.
        val blocked = dir.resolve("blocked")
        Files.writeString(blocked, "디렉터리가 아니다")
        val ledger = ApiCallLedger(properties(blocked.resolve("api-calls.log")), MovableClock("2026-09-23T01:00:00Z"))

        // when & then
        assertEquals(1L, ledger.record())
        assertEquals(1L, ledger.today())
    }

    @Test
    fun `깨진 줄이 있어도 나머지를 읽어낸다`() {
        // given - 사람이 열어 보는 텍스트 파일이라 한 줄이 상해도 기동이 걸리면 안 된다.
        Files.writeString(
            path(),
            """
            # kennymetro 서울시 API 호출 수 (날짜 횟수)
            2026-09-22 412
            어제 많이
            2026-09-23 137
            """.trimIndent(),
        )

        // when
        val ledger = ledger(MovableClock("2026-09-23T01:00:00Z"))

        // then
        assertEquals(137L, ledger.today())
    }

    private fun path(): Path = dir.resolve("api-calls.log")

    private fun ledger(clock: Clock) = ApiCallLedger(properties(path()), clock)

    private fun properties(logPath: Path) = SeoulSubwayProperties(
        apiKey = "test-key",
        baseUrl = "http://localhost",
        cacheTtl = Duration.ofSeconds(70),
        requestTimeout = Duration.ofSeconds(5),
        dailyCallBudget = 1000,
        callLogPath = logPath.toString(),
    )

    /** 날짜 경계를 넘겨 보려면 시계를 움직일 수 있어야 한다. */
    private class MovableClock(start: String) : Clock() {
        var now: Instant = Instant.parse(start)
        override fun getZone(): ZoneId = ZoneId.of("Asia/Seoul")
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = now
    }
}
