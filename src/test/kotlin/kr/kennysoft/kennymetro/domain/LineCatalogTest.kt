package kr.kennysoft.kennymetro.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import kr.kennysoft.kennymetro.TestLines
import kr.kennysoft.kennymetro.config.LineConfig
import kr.kennysoft.kennymetro.config.MetroProperties

class LineCatalogTest : FreeSpec({

    "프리셋이 가리키는 노선이 모두 있다" {
        // given - 프리셋은 처음 방문한 브라우저가 보여줄 목록이라 하나라도 없으면 화면이 빈다.
        val catalog = TestLines.catalog
        val slugs = catalog.all().map { it.slug }

        // when & then
        catalog.preset() shouldBe listOf("shinbundang", "line2", "line9", "suinbundang", "everline")
        slugs shouldContainAll catalog.preset()
    }

    "지선 뷰는 같은 API 노선을 나눠 쓴다" {
        // given - 1호선은 구로에서 갈려 뷰가 둘이지만 API 는 "1호선" 하나로 전체를 준다.
        val gyeongin = TestLines.bySlug("line1-gyeongin")
        val gyeongbu = TestLines.bySlug("line1-gyeongbu")

        // when & then
        gyeongin.apiName shouldBe "1호선"
        gyeongbu.cacheKey shouldBe gyeongin.cacheKey
        // 갈리는 지점부터는 역이 달라야 뷰를 나눈 뜻이 있다.
        gyeongin.indexOf("인천").shouldNotBeNull()
        gyeongbu.indexOf("인천").shouldBeNull()
        gyeongbu.indexOf("신창").shouldNotBeNull()
    }

    "api-name 을 적지 않으면 노선 이름을 쓴다" {
        // given & when & then
        TestLines.line2.apiName shouldBe "2호선"
        TestLines.shinbundang.apiName shouldBe "신분당선"
    }

    "관심 구간을 역 순서와 반대로 적어도 받는다" {
        // given - 2호선 관심 구간은 강남에서 잠실인데 역 순서로는 잠실이 앞이다.
        val line2 = TestLines.line2

        // when & then
        line2.focusFrom shouldBe line2.indexOf("잠실")
        line2.focusTo shouldBe line2.indexOf("강남")
    }

    "편성번호를 차수로 옮긴다" {
        // given
        val fleet = TestLines.shinbundang.fleet.shouldNotBeNull()

        // when & then - 경계값을 각각 본다.
        fleet.labelFor("1") shouldBe "1차분"
        fleet.labelFor("12") shouldBe "1차분"
        fleet.labelFor("13") shouldBe "2차분"
        fleet.labelFor("20") shouldBe "2차분"
        fleet.labelFor("21") shouldBe "3차분"
        fleet.labelFor("23") shouldBe "3차분"
    }

    "편성 범위를 벗어나거나 숫자가 아니면 차수가 없다" {
        // given - 다른 노선은 4자리 운행번호라 이 자리에 들어와도 차수가 나오면 안 된다.
        val fleet = TestLines.shinbundang.fleet.shouldNotBeNull()

        // when & then
        fleet.labelFor("24").shouldBeNull()
        fleet.labelFor("0").shouldBeNull()
        fleet.labelFor("2495").shouldBeNull()
        fleet.labelFor("A1").shouldBeNull()
    }

    "편성번호로 차량을 특정할 수 없는 노선에는 차수가 없다" {
        // given & when & then
        TestLines.line2.fleet.shouldBeNull()
        TestLines.everline.fleet.shouldBeNull()
    }

    "환승 노선 색의 키가 온전히 들어온다" {
        // given - Spring 의 relaxed binding 은 맵 키에서 한글을 떼어낸다. 키를 대괄호로
        // 감싸지 않으면 1호선과 인천1호선이 둘 다 "1" 로 뭉개져 색이 뒤바뀐다.
        val colors = TestLines.catalog.transferColors()

        // when & then
        colors.keys.shouldContainAll("1호선", "인천1호선")
        colors["1호선"] shouldNotBe colors["인천1호선"]
        // 뭉개진 키끼리는 나중에 적은 색이 앞의 것을 덮어 수가 준다.
        colors.size shouldBeGreaterThanOrEqual 20
    }

    "환승 대상 이름으로 우리 뷰를 찾는다" {
        // given - 환승 표는 지선 뷰를 모르고 노선 이름으로 적는다. 지선은 괄호 없이 적고, 방면을 괄호로 덧붙이기도 한다.
        val catalog = TestLines.catalog

        // when & then
        catalog.viewsNamed("1호선").map { it.slug } shouldBe listOf("line1-gyeongin", "line1-gyeongbu")
        catalog.viewsNamed("2호선 신정지선").map { it.slug } shouldBe listOf("line2-sinjeong")
        catalog.viewsNamed("5호선 (방화 방면)").map { it.slug } shouldBe listOf("line5-hanam", "line5-macheon")
        catalog.viewsNamed("인천1호선").shouldBeEmpty()
    }

    "노선마다 이름이 다른 환승역을 같은 역으로 본다" {
        // given - 4호선의 총신대입구와 7호선의 이수는 한 역이다. 모르면 두 노선 사이의 환승 문이 대조에 걸린다.
        val catalog = TestLines.catalog

        // when & then
        catalog.sameStations("이수") shouldBe setOf("이수", "총신대입구")
        catalog.sameStations("총신대입구") shouldBe setOf("총신대입구", "이수")
        catalog.sameStations("강남") shouldBe setOf("강남")
    }

    "환승 파일 이름을 적지 않으면 slug 를 쓴다" {
        // given & when & then - 지선 뷰만 원본 문서를 따라 파일을 나눠 쓴다.
        TestLines.bySlug("line1-gyeongbu").transferFile shouldBe "line1"
        TestLines.line2.transferFile shouldBe "line2"
    }

    "노선에 없는 역을 주요 역으로 가리키면 기동이 실패한다" {
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
        val error = shouldThrow<IllegalStateException> { LineCatalog(broken) }

        // then - 무엇이 틀렸는지 알려야 한다.
        error.message shouldContain "없는역"
    }

    "대괄호를 빠뜨려 키가 지워진 역명 치환은 기동이 실패한다" {
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
        val error = shouldThrow<IllegalArgumentException> { LineCatalog(broken) }

        // then - 무엇을 고칠지 알려야 한다.
        error.message shouldContain "대괄호"
    }

    "역이 중복되면 기동이 실패한다" {
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
        shouldThrow<IllegalArgumentException> { LineCatalog(broken) }
    }
})
