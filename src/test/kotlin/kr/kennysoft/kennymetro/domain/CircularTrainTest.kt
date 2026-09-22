package kr.kennysoft.kennymetro.domain

import kr.kennysoft.kennymetro.TestLines
import kr.kennysoft.kennymetro.seoul.TrainPositionDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * 2호선은 순환선이라 선형 노선과 판정이 다르다.
 */
class CircularTrainTest {

    @Test
    fun `순환선은 종착역 자리가 아니라 updnLine 으로 방향을 정한다`() {
        // given - 같은 역 같은 종착인데 updnLine 만 다르다. 선형 노선의 자리 비교로는 갈리지 않는다.
        val inner = position(current = "강남", terminal = "성수종착", updnLine = "0")
        val outer = position(current = "강남", terminal = "성수종착", updnLine = "1")

        // when & then
        assertEquals(Direction.UP, inner.toTrain(TestLines.line2)?.direction)
        assertEquals(Direction.DOWN, outer.toTrain(TestLines.line2)?.direction)
    }

    @Test
    fun `종착 표기의 꼬리말을 떼어 역명으로 쓴다`() {
        // given - 2호선 종착은 "성수종착" 처럼 역명이 아닌 값으로 온다.
        val dto = position(current = "강남", terminal = "성수종착", updnLine = "0")

        // when
        val train = dto.toTrain(TestLines.line2)

        // then
        assertEquals("성수", train?.destination)
    }

    @Test
    fun `종착역에 서서 운행을 마치는 열차는 제외한다`() {
        // given - 꼬리말을 뗀 뒤 현재역과 같아지는 경우다.
        val dto = position(current = "성수", terminal = "성수종착", updnLine = "0")

        // when & then
        assertNull(dto.toTrain(TestLines.line2))
    }

    @Test
    fun `순환선에는 노선 끝이 없어 단축 운행 표시를 하지 않는다`() {
        // given
        val dto = position(current = "강남", terminal = "성수종착", updnLine = "0")

        // when
        val train = dto.toTrain(TestLines.line2)!!

        // then
        assertFalse(train.isShortTurn)
    }

    @Test
    fun `지선으로 가는 열차는 본선 역 목록에 걸리지 않아 빠진다`() {
        // given - 신설동은 성수지선 역이라 본선 목록에 없다.
        val dto = position(current = "신설동", terminal = "신설동", updnLine = "1")

        // when & then
        assertNull(dto.toTrain(TestLines.line2))
    }

    private fun position(
        current: String,
        terminal: String,
        updnLine: String,
    ) = TrainPositionDto(
        subwayId = "1002",
        subwayNm = "2호선",
        statnId = "1002000222",
        statnNm = current,
        trainNo = "2495",
        statnTid = "1002000211",
        statnTnm = terminal,
        updnLine = updnLine,
        trainSttus = "1",
        directAt = "0",
        lstcarAt = "0",
        recptnDt = "2026-09-22 23:15:13",
    )
}
