package kr.kennysoft.kennymetro.domain

import kr.kennysoft.kennymetro.TestLines
import kr.kennysoft.kennymetro.config.LineConfig
import kr.kennysoft.kennymetro.config.MetroProperties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LineCatalogTest {

    @Test
    fun `설정에 적힌 노선을 모두 읽는다`() {
        // given & when
        val slugs = TestLines.catalog.all().map { it.slug }

        // then
        assertEquals(listOf("shinbundang", "line2", "line9", "suinbundang", "everline"), slugs)
    }

    @Test
    fun `관심 구간을 역 순서와 반대로 적어도 받는다`() {
        // given - 2호선 관심 구간은 강남에서 잠실인데 역 순서로는 잠실이 앞이다.
        val line2 = TestLines.line2

        // when & then
        assertEquals(line2.indexOf("잠실"), line2.focusFrom)
        assertEquals(line2.indexOf("강남"), line2.focusTo)
    }

    @Test
    fun `편성번호를 차수로 옮긴다`() {
        // given
        val fleet = assertNotNull(TestLines.shinbundang.fleet)

        // when & then - 경계값을 각각 본다.
        assertEquals("1차분", fleet.labelFor("1"))
        assertEquals("1차분", fleet.labelFor("12"))
        assertEquals("2차분", fleet.labelFor("13"))
        assertEquals("2차분", fleet.labelFor("20"))
        assertEquals("3차분", fleet.labelFor("21"))
        assertEquals("3차분", fleet.labelFor("23"))
    }

    @Test
    fun `편성 범위를 벗어나거나 숫자가 아니면 차수가 없다`() {
        // given - 다른 노선은 4자리 운행번호라 이 자리에 들어와도 차수가 나오면 안 된다.
        val fleet = assertNotNull(TestLines.shinbundang.fleet)

        // when & then
        assertNull(fleet.labelFor("24"))
        assertNull(fleet.labelFor("0"))
        assertNull(fleet.labelFor("2495"))
        assertNull(fleet.labelFor("A1"))
    }

    @Test
    fun `편성번호로 차량을 특정할 수 없는 노선에는 차수가 없다`() {
        // given & when & then
        assertNull(TestLines.line2.fleet)
        assertNull(TestLines.everline.fleet)
    }

    @Test
    fun `노선에 없는 역을 주요 역으로 가리키면 기동이 실패한다`() {
        // given - 역명 오타가 화면에서 조용히 사라지는 대신 여기서 드러나야 한다.
        val broken = MetroProperties(
            listOf(
                LineConfig(
                    slug = "test",
                    name = "테스트선",
                    color = "#000000",
                    source = LineSource.SEOUL,
                    upLabel = "위",
                    downLabel = "아래",
                    stations = listOf("가", "나"),
                    keyStations = listOf("없는역"),
                )
            )
        )

        // when
        val error = assertFailsWith<IllegalStateException> { LineCatalog(broken) }

        // then
        assertTrue(error.message!!.contains("없는역"), "무엇이 틀렸는지 알려야 한다: ${error.message}")
    }

    @Test
    fun `역이 중복되면 기동이 실패한다`() {
        // given
        val broken = MetroProperties(
            listOf(
                LineConfig(
                    slug = "test",
                    name = "테스트선",
                    color = "#000000",
                    source = LineSource.SEOUL,
                    upLabel = "위",
                    downLabel = "아래",
                    stations = listOf("가", "나", "가"),
                )
            )
        )

        // when & then
        assertFailsWith<IllegalArgumentException> { LineCatalog(broken) }
    }
}
