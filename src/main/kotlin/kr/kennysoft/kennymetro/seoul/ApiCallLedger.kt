package kr.kennysoft.kennymetro.seoul

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Clock
import java.time.LocalDate

/**
 * 서울시 API 로 나간 호출 수를 날짜별로 세어 파일에 남긴다.
 *
 * <p>
 * <b>날짜별인 이유.</b> 일일 한도는 자정에 돌아온다. 누적으로 세면 며칠만 지나도 1,000 을
 * 넘어 남은 예산을 가리키지 못한다.
 *
 * <p>
 * <b>파일인 이유.</b> 메모리에만 두면 재배포할 때마다 0 부터 다시 센다. 그 날 이미 쓴 호출이
 * 보이지 않으니 화면의 숫자가 실제 한도와 어긋난다. 이 앱에 저장소를 두지 않는다는 원칙
 * (DESIGN.md 1.3)의 예외이고, 그래서 DB 가 아니라 컨테이너 밖 볼륨의 텍스트 파일 하나다.
 *
 * <p>
 * <b>기록에 실패해도 호출은 계속한다.</b> 예산 표시가 못 미더운 것과 화면이 안 뜨는 것
 * 중에는 앞이 낫다. 다만 기동 때 한 번 써 보고 안 되면 경고를 남겨, 볼륨을 빠뜨린 것이
 * 배포 직후 드러나게 한다.
 */
@Component
class ApiCallLedger(
    properties: SeoulSubwayProperties,
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val path: Path = Path.of(properties.callLogPath).toAbsolutePath()
    private val counts = HashMap<LocalDate, Long>()
    private val lock = Any()

    /** 같은 경고를 호출마다 쌓지 않으려는 것. 한 번 성공하면 다시 열린다. */
    private var warned = false

    init {
        synchronized(lock) {
            counts.putAll(read())
            write()
        }
        log.info("호출 원장: {} (오늘 {}회)", path, today())
    }

    /** 호출 한 번을 세고 오늘 지금까지 나간 수를 돌려준다. */
    fun record(): Long = synchronized(lock) {
        val today = LocalDate.now(clock)
        val next = (counts[today] ?: 0L) + 1
        counts[today] = next
        write()
        next
    }

    /** 오늘 나간 호출 수. */
    fun today(): Long = synchronized(lock) { counts[LocalDate.now(clock)] ?: 0L }

    private fun read(): Map<LocalDate, Long> {
        if (!Files.exists(path)) return emptyMap()
        return try {
            Files.readAllLines(path)
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .mapNotNull { parse(it) }
                .toMap()
        } catch (e: IOException) {
            log.warn("호출 원장을 읽지 못했다. 오늘 수를 0 부터 센다. path={} error={}", path, e.message, e)
            emptyMap()
        }
    }

    /** `2026-09-23 137` 한 줄. 깨진 줄에 기동이 걸리면 안 되므로 건너뛰고 알리기만 한다. */
    private fun parse(line: String): Pair<LocalDate, Long>? {
        val parts = line.split(" ", limit = 2)
        val date = parts.getOrNull(0)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val count = parts.getOrNull(1)?.trim()?.toLongOrNull()
        if (date == null || count == null) {
            log.warn("호출 원장에 알아볼 수 없는 줄이 있다. 건너뛴다: {}", line)
            return null
        }
        return date to count
    }

    /**
     * 통째로 다시 쓴다. 줄이 며칠치뿐이라 덧붙이기로 얻을 것이 없고, 임시 파일에 쓰고
     * 옮기면 쓰는 도중에 죽어도 반쪽짜리 파일이 남지 않는다.
     */
    private fun write() {
        val body = buildString {
            append(HEADER)
            counts.entries.sortedBy { it.key }.takeLast(KEEP_DAYS).forEach { (date, count) ->
                append(date).append(' ').append(count).append('\n')
            }
        }
        try {
            path.parent?.let { Files.createDirectories(it) }
            val temp = path.resolveSibling("${path.fileName}.tmp")
            Files.writeString(temp, body)
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING)
            warned = false
        } catch (e: IOException) {
            if (!warned) {
                log.warn(
                    "호출 수를 남기지 못했다. 재배포하면 예산 표시가 0 부터 시작한다. path={} error={}",
                    path, e.message, e,
                )
                warned = true
            }
        }
    }

    private companion object {
        private const val HEADER = "# kennymetro 서울시 API 호출 수 (날짜 횟수)\n"

        /** 지난 값은 예산 판단에 쓰지 않지만 추세를 보려고 남긴다. */
        private const val KEEP_DAYS = 60
    }
}
