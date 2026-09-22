package kr.kennysoft.kennymetro.transfer

import kr.kennysoft.kennymetro.domain.Line
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TransferDoorRepositoryTest {

    private val repository = TransferDoorRepository()

    @Test
    fun `환승역을 노선 순서대로 돌려준다`() {
        // given & when
        val stations = repository.findByLine(Line.SHINBUNDANG).map { it.station }

        // then - 신사가 먼저고 미금이 나중이다. 파일에 적힌 줄 순서가 아니라 노선 순서를 따른다.
        assertEquals(
            listOf("신사", "논현", "신논현", "강남", "양재", "판교", "정자", "미금"),
            stations,
        )
    }

    @Test
    fun `열차 방면에 따라 다른 문을 돌려준다`() {
        // given
        val yangjae = repository.findByLine(Line.SHINBUNDANG).single { it.station == "양재" }

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
        val sinsa = repository.findByLine(Line.SHINBUNDANG).single { it.station == "신사" }

        // when & then
        assertTrue(sinsa.doors.all { it.trainDirection == null })
        assertEquals(setOf("대화", "오금"), sinsa.doors.map { it.targetDirection }.toSet())
    }

    @Test
    fun `한 방면에 환승 통로가 여럿이면 모두 돌려준다`() {
        // given & when
        val gangnam = repository.findByLine(Line.SHINBUNDANG).single { it.station == "강남" }

        // then
        assertEquals(
            setOf(1 to 1, 3 to 2),
            gangnam.doors.filter { it.trainDirection == "신사" }.map { it.car to it.door }.toSet(),
        )
    }

    @Test
    fun `비고가 있는 역과 없는 역을 가른다`() {
        // given & when
        val stations = repository.findByLine(Line.SHINBUNDANG)

        // then
        assertNotNull(stations.single { it.station == "미금" }.note)
        assertNull(stations.single { it.station == "강남" }.note)
    }
}
