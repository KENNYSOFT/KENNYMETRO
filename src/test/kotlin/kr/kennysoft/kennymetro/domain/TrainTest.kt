package kr.kennysoft.kennymetro.domain

import kr.kennysoft.kennymetro.TestLines
import kr.kennysoft.kennymetro.seoul.TrainPositionDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrainTest {

    @Test
    fun `종착역에 도착해 운행을 마치는 열차는 제외한다`() {
        // given - 현재역과 종착역이 모두 정자인 열차. 다음 조회에서 목록에서 사라진다.
        val dto = position(current = "정자", terminal = "정자", updnLine = "1")

        // when
        val train = dto.toTrain(TestLines.shinbundang)

        // then
        assertNull(train)
    }

    @Test
    fun `종착역으로 방향을 정한다`() {
        // given
        val toSinsa = position(current = "청계산입구", terminal = "신사", updnLine = "0")
        val toGwanggyo = position(current = "청계산입구", terminal = "광교", updnLine = "1")

        // when & then
        assertEquals(Direction.UP, toSinsa.toTrain(TestLines.shinbundang)?.direction)
        assertEquals(Direction.DOWN, toGwanggyo.toTrain(TestLines.shinbundang)?.direction)
    }

    @Test
    fun `회차를 기다리는 열차도 종착역 쪽으로 판정한다`() {
        // given - 광교에 있는데 종착이 신사다. updnLine 은 직전 운행의 값이라 하행으로 남아 있다.
        val dto = position(current = "광교", terminal = "신사", updnLine = "1")

        // when
        val train = dto.toTrain(TestLines.shinbundang)

        // then - updnLine 을 믿었다면 DOWN 이 되어 반대로 그려진다.
        assertEquals(Direction.UP, train?.direction)
    }

    @Test
    fun `노선 끝까지 가지 않는 열차를 가려낸다`() {
        // given
        val shortTurn = position(current = "강남", terminal = "정자", updnLine = "1")
        val full = position(current = "강남", terminal = "광교", updnLine = "1")

        // when & then
        assertTrue(shortTurn.toTrain(TestLines.shinbundang)!!.isShortTurn)
        assertTrue(!full.toTrain(TestLines.shinbundang)!!.isShortTurn)
    }

    @Test
    fun `노선에 없는 역이 오면 제외한다`() {
        // given - 차량기지나 노선 정보가 낡았을 때.
        val dto = position(current = "없는역", terminal = "광교", updnLine = "1")

        // when & then
        assertNull(dto.toTrain(TestLines.shinbundang))
    }

    @Test
    fun `막차와 급행 표시를 옮긴다`() {
        // given
        val dto = position(current = "판교", terminal = "광교", updnLine = "1", lstcarAt = "1", directAt = "1")

        // when
        val train = dto.toTrain(TestLines.shinbundang)!!

        // then
        assertTrue(train.isLastTrain)
        assertTrue(train.isExpress)
    }

    private fun position(
        current: String,
        terminal: String,
        updnLine: String,
        lstcarAt: String = "0",
        directAt: String = "0",
    ) = TrainPositionDto(
        subwayId = "1077",
        subwayNm = "신분당선",
        statnId = "1077000000",
        statnNm = current,
        trainNo = "1",
        statnTid = "1077000001",
        statnTnm = terminal,
        updnLine = updnLine,
        trainSttus = "1",
        directAt = directAt,
        lstcarAt = lstcarAt,
        recptnDt = "2026-09-21 10:22:23",
    )
}
