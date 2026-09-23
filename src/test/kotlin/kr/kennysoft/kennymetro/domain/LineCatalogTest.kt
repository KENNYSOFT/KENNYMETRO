package kr.kennysoft.kennymetro.domain

import kr.kennysoft.kennymetro.TestLines
import kr.kennysoft.kennymetro.config.LineConfig
import kr.kennysoft.kennymetro.config.MetroProperties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LineCatalogTest {

    @Test
    fun `프리셋이 가리키는 노선이 모두 있다`() {
        // given - 프리셋은 처음 방문한 브라우저가 보여줄 목록이라 하나라도 없으면 화면이 빈다.
        val catalog = TestLines.catalog
        val slugs = catalog.all().map { it.slug }

        // when & then
        assertEquals(listOf("shinbundang", "line2", "line9", "suinbundang", "everline"), catalog.preset())
        catalog.preset().forEach { assertTrue(it in slugs, "프리셋이 가리키는 노선이 없다: $it") }
    }

    @Test
    fun `지선 뷰는 같은 API 노선을 나눠 쓴다`() {
        // given - 1호선은 구로에서 갈려 뷰가 둘이지만 API 는 "1호선" 하나로 전체를 준다.
        val gyeongin = TestLines.bySlug("line1-gyeongin")
        val gyeongbu = TestLines.bySlug("line1-gyeongbu")

        // when & then
        assertEquals("1호선", gyeongin.apiName)
        assertEquals(gyeongin.cacheKey, gyeongbu.cacheKey)
        // 갈리는 지점부터는 역이 달라야 뷰를 나눈 뜻이 있다.
        assertNotNull(gyeongin.indexOf("인천"))
        assertNull(gyeongbu.indexOf("인천"))
        assertNotNull(gyeongbu.indexOf("신창"))
    }

    @Test
    fun `api-name 을 적지 않으면 노선 이름을 쓴다`() {
        // given & when & then
        assertEquals("2호선", TestLines.line2.apiName)
        assertEquals("신분당선", TestLines.shinbundang.apiName)
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
    fun `환승 노선 색의 키가 온전히 들어온다`() {
        // given - Spring 의 relaxed binding 은 맵 키에서 한글을 떼어낸다. 키를 대괄호로
        // 감싸지 않으면 1호선과 인천1호선이 둘 다 "1" 로 뭉개져 색이 뒤바뀐다.
        val colors = TestLines.catalog.transferColors()

        // when & then
        assertTrue(colors.containsKey("1호선"), "키가 뭉개졌다: ${colors.keys}")
        assertTrue(colors.containsKey("인천1호선"), "키가 뭉개졌다: ${colors.keys}")
        assertNotEquals(colors["1호선"], colors["인천1호선"])
        assertTrue(colors.size >= 20, "색이 덮여 사라졌다: ${colors.size}개")
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
    fun `대괄호를 빠뜨려 키가 지워진 역명 치환은 기동이 실패한다`() {
        // given - relaxed binding 이 한글 키를 지우면 빈 키가 된다. 조용히 넘어가면 그 역의 열차가 사라진다.
        val broken = MetroProperties(
            lines = listOf(
                LineConfig(
                    slug = "test",
                    name = "테스트선",
                    color = "#000000",
                    source = LineSource.SEOUL,
                    upLabel = "위",
                    downLabel = "아래",
                    stations = listOf("가", "나"),
                )
            ),
            stationAliases = mapOf("" to "가"),
        )

        // when
        val error = assertFailsWith<IllegalArgumentException> { LineCatalog(broken) }

        // then
        assertTrue(error.message!!.contains("대괄호"), "무엇을 고칠지 알려야 한다: ${error.message}")
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
