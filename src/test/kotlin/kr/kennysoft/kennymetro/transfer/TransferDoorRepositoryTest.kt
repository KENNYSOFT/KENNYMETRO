package kr.kennysoft.kennymetro.transfer

import kr.kennysoft.kennymetro.TestLines
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TransferDoorRepositoryTest {

    private val repository = TransferDoorRepository(TestLines.catalog)

    @Test
    fun `환승역을 노선 순서대로 돌려준다`() {
        // given & when
        val stations = repository.findByLine(TestLines.shinbundang).stations.map { it.station }

        // then - 신사가 먼저고 미금이 나중이다. 파일에 적힌 줄 순서가 아니라 노선 순서를 따른다.
        assertEquals(
            listOf("신사", "논현", "신논현", "강남", "양재", "판교", "정자", "미금"),
            stations,
        )
    }

    @Test
    fun `열차 방면에 따라 다른 문을 돌려준다`() {
        // given
        val yangjae = repository.findByLine(TestLines.shinbundang).stations.single { it.station == "양재" }

        // when
        val toSinsa = yangjae.doors.single { it.trainDirection == "신사" }
        val toGwanggyo = yangjae.doors.single { it.trainDirection == "광교" }

        // then - 이 구분이 이 데이터를 쓰는 이유다. 공식 데이터에는 이 축이 없다.
        assertEquals(1 to 1, toSinsa.car to toSinsa.door)
        assertEquals(6 to 4, toGwanggyo.car to toGwanggyo.door)
    }

    @Test
    fun `시종착역은 열차 방면 대신 갈아탈 노선의 방면으로 나뉜다`() {
        // given - 신사는 신분당선의 끝이라 열차 방면을 가릴 것이 없다.
        val sinsa = repository.findByLine(TestLines.shinbundang).stations.single { it.station == "신사" }

        // when & then
        assertTrue(sinsa.doors.all { it.trainDirection == null })
        assertEquals(setOf("대화", "오금"), sinsa.doors.map { it.targetDirection }.toSet())
    }

    @Test
    fun `한 방면에 환승 통로가 여럿이면 모두 돌려준다`() {
        // given & when
        val gangnam = repository.findByLine(TestLines.shinbundang).stations.single { it.station == "강남" }

        // then
        assertEquals(
            setOf(1 to 1, 3 to 2),
            gangnam.doors.filter { it.trainDirection == "신사" }.map { it.car to it.door }.toSet(),
        )
    }

    @Test
    fun `노선마다 가져온 문서와 판을 함께 돌려준다`() {
        // given - 나무위키는 환승 정보를 노선별 하위 문서로 나눠 두어 출처가 노선마다 다르다.
        val shinbundang = repository.findByLine(TestLines.shinbundang).source
        val line2 = repository.findByLine(TestLines.line2).source

        // then
        assertNotNull(shinbundang)
        assertNotNull(line2)
        assertNotEquals(shinbundang.document, line2.document)
        assertTrue(line2.revision.startsWith("r"), "판은 r 로 시작한다: ${line2.revision}")
        assertEquals("CC BY-NC-SA 2.0 KR", line2.license)
    }

    @Test
    fun `모든 노선의 환승 데이터에 출처가 있다`() {
        // given - 표기 없이 데이터만 나가는 것이 라이선스 위반이라 빠뜨리면 기동이 실패해야 한다.
        val withData = TestLines.catalog.all()
            .map { it to repository.findByLine(it) }
            .filter { (_, transfers) -> transfers.stations.isNotEmpty() }

        // then
        assertTrue(withData.size >= 5, "다섯 노선 모두 환승 데이터가 있어야 한다: ${withData.size}")
        withData.forEach { (line, transfers) ->
            assertNotNull(transfers.source, "${line.slug} 에 출처가 없다")
        }
    }

    @Test
    fun `비고가 있는 역과 없는 역을 가른다`() {
        // given & when
        val stations = repository.findByLine(TestLines.shinbundang).stations

        // then
        assertNotNull(stations.single { it.station == "미금" }.note)
        assertNull(stations.single { it.station == "강남" }.note)
    }
}
