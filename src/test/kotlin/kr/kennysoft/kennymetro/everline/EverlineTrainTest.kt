package kr.kennysoft.kennymetro.everline

import kr.kennysoft.kennymetro.TestLines
import kr.kennysoft.kennymetro.domain.Direction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EverlineTrainTest {

    @Test
    fun `역 코드를 역명으로 옮긴다`() {
        // given - Y110 이 기흥이고 거기서부터 순서대로 붙는다.
        val dto = position(station = "Y121", destination = "Y110")

        // when
        val train = dto.toTrain(TestLines.everline)!!

        // then
        assertEquals("고진", train.currentStation)
        assertEquals("기흥", train.destination)
    }

    @Test
    fun `차량기지 코드는 역이 아니라 제외한다`() {
        // given - Y109 는 차량기지다. 공식 페이지 스크립트도 이 값을 걸러낸다.
        val dto = position(station = "Y109", destination = "Y124")

        // when & then
        assertNull(dto.toTrain(TestLines.everline))
    }

    @Test
    fun `역 범위를 벗어난 코드는 제외한다`() {
        // given
        val tooHigh = position(station = "Y125", destination = "Y110")
        val notNumeric = position(station = "YXXX", destination = "Y110")

        // when & then
        assertNull(tooHigh.toTrain(TestLines.everline))
        assertNull(notNumeric.toTrain(TestLines.everline))
    }

    @Test
    fun `종착역 자리로 방향을 정한다`() {
        // given
        val toGiheung = position(station = "Y121", destination = "Y110")
        val toJeondae = position(station = "Y112", destination = "Y124")

        // when & then
        assertEquals(Direction.UP, toGiheung.toTrain(TestLines.everline)?.direction)
        assertEquals(Direction.DOWN, toJeondae.toTrain(TestLines.everline)?.direction)
    }

    @Test
    fun `종착역에 도착한 열차는 제외한다`() {
        // given
        val dto = position(station = "Y110", destination = "Y110")

        // when & then
        assertNull(dto.toTrain(TestLines.everline))
    }

    private fun position(station: String, destination: String) = EverlineTrainDto(
        trainNo = "1",
        stationCode = station,
        destinationCode = destination,
        statusCode = "3",
        updownCode = "1",
        time = "40",
    )
}
