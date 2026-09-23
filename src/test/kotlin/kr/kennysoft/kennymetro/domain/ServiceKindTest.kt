package kr.kennysoft.kennymetro.domain

import kr.kennysoft.kennymetro.TestLines
import kr.kennysoft.kennymetro.seoul.TrainPositionDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 종착역이 그 뷰에서 어떤 운행인지. 평소 종착역, 단축, 연장, 다른 계통을 가른다.
 */
class ServiceKindTest {

    @Test
    fun `평소 종착역보다 먼저 끝나면 단축이고 더 가면 연장이다`() {
        // given - 수인분당선은 왕십리에서 돌아가는 열차가 평소 운행이고 청량리까지 가는 열차는 드물다.
        val line = TestLines.bySlug("suinbundang")

        // when & then
        assertEquals(ServiceKind.NORMAL, train(line, "선릉", "왕십리")?.service)
        assertEquals(ServiceKind.EXTENSION, train(line, "선릉", "청량리")?.service)
        assertEquals(ServiceKind.SHORT, train(line, "선릉", "죽전")?.service)
        assertEquals(ServiceKind.NORMAL, train(line, "선릉", "인천")?.service)
    }

    @Test
    fun `형제 뷰의 종착역으로 가는 열차는 갈라지는 역을 향해 다른 계통으로 그린다`() {
        // given - 신창행은 구로까지 경인 뷰와 같은 선로를 달린다. 버리면 종각에서 탈 수 있는 열차를 못 본다.
        val gyeongin = TestLines.bySlug("line1-gyeongin")

        // when
        val train = train(gyeongin, "종각", "신창")

        // then
        assertEquals(Direction.DOWN, train?.direction)
        assertEquals(ServiceKind.BRANCH, train?.service)
        assertEquals("신창", train?.destination)
    }

    @Test
    fun `갈라지는 역에 선 다른 계통 열차는 그리지 않는다`() {
        // given - 구로에 선 신창행은 다음 역부터 경인 뷰에 없다. 어느 쪽으로 그려도 틀린다.
        val gyeongin = TestLines.bySlug("line1-gyeongin")

        // when & then
        assertNull(train(gyeongin, "구로", "신창"))
    }

    @Test
    fun `어느 뷰에도 없는 종착역은 beyond 로 적은 역을 향한다`() {
        // given - 서동탄은 경부 뷰의 병점에서 갈라진다. 경인 뷰에서는 그 병점을 다시 구로로 따라간다.
        val gyeongbu = TestLines.bySlug("line1-gyeongbu")
        val gyeongin = TestLines.bySlug("line1-gyeongin")

        // when
        val onGyeongbu = train(gyeongbu, "수원", "서동탄")
        val onGyeongin = train(gyeongin, "서울역", "서동탄")

        // then - 병점에서 신창 쪽이 아니라 옆으로 빠지므로 연장이 아니라 다른 계통이다.
        assertEquals(Direction.DOWN, onGyeongbu?.direction)
        assertEquals(ServiceKind.BRANCH, onGyeongbu?.service)
        assertEquals(Direction.DOWN, onGyeongin?.direction)
        assertEquals(ServiceKind.BRANCH, onGyeongin?.service)
    }

    @Test
    fun `가장 바깥 종착역을 지나 목록 밖으로 가면 연장이다`() {
        // given - 경춘선 용산행은 청량리를 지나 경의중앙선 선로로 더 간다. 광운대행은 망우에서 옆으로 빠진다.
        val line = TestLines.bySlug("gyeongchun")

        // when
        val toYongsan = train(line, "가평", "용산")
        val toGwangwoon = train(line, "가평", "광운대")

        // then
        assertEquals(Direction.UP, toYongsan?.direction)
        assertEquals(ServiceKind.EXTENSION, toYongsan?.service)
        assertEquals(Direction.UP, toGwangwoon?.direction)
        assertEquals(ServiceKind.BRANCH, toGwangwoon?.service)
    }

    @Test
    fun `모르는 종착역으로 가는 열차는 그리지 않는다`() {
        // given - 어느 뷰에도 없고 beyond 에도 없으면 방향을 정할 근거가 없다. 서버가 로그로 남긴다.
        val line = TestLines.bySlug("line3")

        // when & then
        assertNull(train(line, "교대", "없는역"))
    }

    private fun train(line: Line, current: String, terminal: String) = TrainPositionDto(
        subwayId = "1001",
        subwayNm = line.apiName,
        statnId = "1001000000",
        statnNm = current,
        trainNo = "1000",
        statnTid = "1001000001",
        statnTnm = terminal,
        updnLine = "1",
        trainSttus = "1",
        directAt = "0",
        lstcarAt = "0",
        recptnDt = "2026-09-23 10:50:00",
    ).toTrain(line)
}
