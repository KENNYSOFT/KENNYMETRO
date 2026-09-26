package kr.kennysoft.kennymetro.transfer

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import kr.kennysoft.kennymetro.TestLines
import kr.kennysoft.kennymetro.config.LineConfig
import kr.kennysoft.kennymetro.config.MetroProperties
import kr.kennysoft.kennymetro.domain.Direction
import kr.kennysoft.kennymetro.domain.LineCatalog
import kr.kennysoft.kennymetro.domain.LineSource

class TransferDoorRepositoryTest : FreeSpec({

    val repository = TransferDoorRepository(TestLines.catalog)

    "환승역을 노선 순서대로 돌려준다" {
        // given & when
        val stations = repository.findByLine(TestLines.shinbundang).stations.map { it.station }

        // then - 신사가 먼저고 미금이 나중이다. 파일에 적힌 줄 순서가 아니라 노선 순서를 따른다.
        stations shouldBe listOf("신사", "논현", "신논현", "강남", "양재", "판교", "정자", "미금")
    }

    "열차 방면에 따라 다른 문을 돌려준다" {
        // given
        val yangjae = repository.findByLine(TestLines.shinbundang).stations.single { it.station == "양재" }

        // when
        val toSinsa = yangjae.doors.single { it.trainDirection == "신사" }
        val toGwanggyo = yangjae.doors.single { it.trainDirection == "광교" }

        // then - 이 구분이 이 데이터를 쓰는 이유다. 공식 데이터에는 이 축이 없다.
        (toSinsa.car to toSinsa.door) shouldBe (1 to 1)
        (toGwanggyo.car to toGwanggyo.door) shouldBe (6 to 4)
    }

    "시종착역은 열차 방면 대신 갈아탈 노선의 방면으로 나뉜다" {
        // given - 신사는 신분당선의 끝이라 열차 방면을 가릴 것이 없다.
        val sinsa = repository.findByLine(TestLines.shinbundang).stations.single { it.station == "신사" }

        // when & then
        sinsa.doors.forAll { it.trainDirection.shouldBeNull() }
        sinsa.doors.map { it.targetDirection }.toSet() shouldBe setOf("대화", "오금")
    }

    "한 방면에 환승 통로가 여럿이면 모두 돌려준다" {
        // given & when
        val gangnam = repository.findByLine(TestLines.shinbundang).stations.single { it.station == "강남" }

        // then
        gangnam.doors.filter { it.trainDirection == "신사" }.map { it.car to it.door }.toSet() shouldBe setOf(1 to 1, 3 to 2)
    }

    "노선마다 가져온 문서와 판을 함께 돌려준다" {
        // given - 나무위키는 환승 정보를 노선별 하위 문서로 나눠 두어 출처가 노선마다 다르다.
        val shinbundang = repository.findByLine(TestLines.shinbundang).source
        val line2 = repository.findByLine(TestLines.line2).source

        // then
        shinbundang.shouldNotBeNull()
        line2.shouldNotBeNull()
        shinbundang.document shouldNotBe line2.document
        line2.revision shouldStartWith "r"
        line2.license shouldBe "CC BY-NC-SA 2.0 KR"
    }

    "모든 노선의 환승 데이터에 출처가 있다" {
        // given - 표기 없이 데이터만 나가는 것이 라이선스 위반이라 빠뜨리면 기동이 실패해야 한다.
        val withData = TestLines.catalog.all()
            .map { it to repository.findByLine(it) }
            .filter { (_, transfers) -> transfers.stations.isNotEmpty() }

        // then - 지금은 모든 뷰에 환승역이 있다.
        withData.size shouldBe TestLines.catalog.all().size
        withData.forAll { (line, transfers) ->
            withClue("${line.slug} 에 출처가 없다") { transfers.source.shouldNotBeNull() }
        }
    }

    "지선 뷰는 환승 파일 하나를 나눠 쓰되 자기 목록에 있는 역만 가져간다" {
        // given - 1호선은 구로에서 갈려 뷰가 둘이지만 옮겨 온 문서는 하나다.
        val gyeongin = repository.findByLine(TestLines.bySlug("line1-gyeongin"))
        val gyeongbu = repository.findByLine(TestLines.bySlug("line1-gyeongbu"))

        // when
        val onGyeongin = gyeongin.stations.map { it.station }
        val onGyeongbu = gyeongbu.stations.map { it.station }

        // then - 구로까지는 같고 그 뒤로 갈린다. 출처는 한 문서다.
        onGyeongin.shouldContainAll("신도림", "부평")
        onGyeongin shouldNotContain "수원"
        onGyeongbu.shouldContainAll("신도림", "수원")
        onGyeongbu shouldNotContain "부평"
        gyeongin.source shouldBe gyeongbu.source
    }

    "모든 문과 이어진 문 범위를 가린다" {
        // given - 4호선 금정은 1호선과 승강장을 나눠 쓰고, 한대앞은 수인분당선과 승강장 일부를 나눠 쓴다.
        val line4 = repository.findByLine(TestLines.bySlug("line4")).stations
        val geumjeong = line4.single { it.station == "금정" }.doors
            .single { it.trainDirection == "불암산" && it.targetDirection == "청량리/광운대" }
        val handaeap = line4.single { it.station == "한대앞" }.doors.single { it.trainDirection == "불암산" }

        // when & then
        listOf(geumjeong.car, geumjeong.door, geumjeong.toCar, geumjeong.toDoor).forAll { it.shouldBeNull() }
        listOf(handaeap.car, handaeap.door, handaeap.toCar, handaeap.toDoor) shouldBe listOf(1, 1, 6, 4)
    }

    "비고가 있는 역과 없는 역을 가른다" {
        // given & when
        val stations = repository.findByLine(TestLines.shinbundang).stations

        // then
        stations.single { it.station == "미금" }.note.shouldNotBeNull()
        stations.single { it.station == "강남" }.note.shouldBeNull()
    }

    "환승 줄을 지금 탄 열차가 가는 쪽의 칸에 둔다" {
        // given - 1호선 종로3가. 열차 방면이 원문의 열 제목이라 화면의 좌우 이름(연천, 인천)과 글자가 다르다.
        val jongno = repository.findByLine(TestLines.bySlug("line1-gyeongin")).stations.single { it.station == "종로3가" }

        // when
        val sides = jongno.doors.map { it.trainDirection to it.side }.toSet()

        // then
        sides shouldBe setOf("연천/광운대" to Direction.UP, "인천/신창" to Direction.DOWN)
    }

    "급행이나 셔틀이 붙은 방면도 거기 적힌 역으로 칸을 가린다" {
        // given - 1호선 신도림. 광명은 경부 뷰에 없고 광명셔틀 뷰에 신도림과 함께 있어 그 뷰에서 자리를 안다.
        val sindorim = repository.findByLine(TestLines.bySlug("line1-gyeongbu")).stations.single { it.station == "신도림" }

        // when
        val sides = sindorim.doors.map { it.trainDirection to it.side }.toSet()

        // then
        sides shouldBe setOf(
            "연천/광운대" to Direction.UP,
            "인천/신창" to Direction.DOWN,
            "용산 특급과 급행" to Direction.UP,
            "동인천 특급과 급행" to Direction.DOWN,
            "광명셔틀 영등포" to Direction.UP,
            "광명셔틀 광명" to Direction.DOWN,
        )
    }

    "지금 역에서 갈라져 나가는 종착역은 그 역을 담은 뷰에서 칸을 가린다" {
        // given - 5호선 마천 뷰의 강동. 하남검단산행은 바로 이 역에서 갈라져 이 뷰만으로는 자리가 같다.
        val gangdong = repository.findByLine(TestLines.bySlug("line5-macheon")).stations.single { it.station == "강동" }

        // when
        val sides = gangdong.doors.map { it.trainDirection to it.side }.toSet()

        // then
        sides shouldBe setOf("방화" to Direction.UP, "하남검단산" to Direction.DOWN)
    }

    "순환선은 외선과 내선으로 칸을 가린다" {
        // given - 2호선은 끝이 없어 자리를 견줄 수 없다. 원문도 방면을 외선과 내선으로 적는다.
        val doors = repository.findByLine(TestLines.line2).stations.flatMap { it.doors }

        // when & then
        doors.map { it.trainDirection to it.side }.toSet() shouldBe setOf("외선" to Direction.UP, "내선" to Direction.DOWN)
    }

    "방면 없는 줄은 열차가 들어오는 쪽에만 둔다" {
        // given - 수인분당선 청량리와 인천은 노선 끝이라 열차가 한쪽으로만 들어온다.
        val stations = repository.findByLine(TestLines.bySlug("suinbundang")).stations

        // when
        val cheongnyangni = stations.single { it.station == "청량리" }.doors.map { it.side }
        val incheon = stations.single { it.station == "인천" }.doors.map { it.side }

        // then
        cheongnyangni shouldBe listOf(Direction.UP)
        incheon shouldBe listOf(Direction.DOWN)
    }

    "한 방향으로만 도는 구간의 방면 없는 줄은 그쪽에만 둔다" {
        // given - 6호선 응암 순환 구간은 한 방향으로만 돌아 불광에는 신내 방면 열차만 들어온다.
        val bulgwang = repository.findByLine(TestLines.bySlug("line6")).stations.single { it.station == "불광" }

        // when & then
        bulgwang.doors.map { it.side } shouldBe listOf(Direction.DOWN, Direction.DOWN)
    }

    "열차가 다니지 않는 역 바로 옆의 방면 없는 줄은 열차가 들어오는 쪽에만 둔다" {
        // given - GTX-A 는 삼성역이 열리지 않아 수서가 남쪽 구간의 끝이다. 열차는 성남 쪽에서만 들어온다.
        val suseo = repository.findByLine(TestLines.bySlug("gtx-a")).stations.single { it.station == "수서" }

        // when & then
        suseo.doors.map { it.side } shouldBe listOf(Direction.UP)
    }

    "방면 없는 줄이 중간역에 있으면 양쪽에 둔다" {
        // given - 8호선 구리는 어느 쪽으로 가든 같은 문이다.
        val guri = repository.findByLine(TestLines.bySlug("line8")).stations.single { it.station == "구리" }

        // when & then
        guri.doors.map { it.side } shouldBe listOf(null, null)
    }

    "갈아탈 노선의 방면으로 가면 바로 다음에 서는 역을 함께 돌려준다" {
        // given - 종착역으로 적은 방면은 그 노선을 자주 타지 않는 사람에게 어느 쪽인지 잘 떠오르지 않는다.
        fun nextOf(slug: String, station: String) = repository.findByLine(TestLines.bySlug(slug)).stations
            .single { it.station == station }.doors.map { it.targetLine to it.targetDirection to it.targetNext }.toSet()

        // when & then - 신분당선 신사에서 3호선으로 갈아탄다.
        nextOf("shinbundang", "신사") shouldBe setOf("3호선" to "대화" to "압구정", "3호선" to "오금" to "잠원")
        // 순환선은 외선과 내선으로 가린다. 3호선 교대에서 2호선 내선은 서초, 외선은 강남으로 간다.
        nextOf("line3", "교대") shouldContainAll setOf("2호선" to "내선" to "서초", "2호선" to "외선" to "강남")
        // 지선은 그 지선의 뷰에서 가린다. 5호선 까치산에서 신정지선 신도림 방면은 신정네거리로 간다.
        nextOf("line5-hanam", "까치산") shouldBe setOf("2호선 신정지선" to "신도림" to "신정네거리")
        // 1호선은 계통마다 뷰가 있어도 종로3가에서는 두 뷰 모두 종각으로 간다.
        nextOf("line3", "종로3가") shouldContainAll setOf("1호선" to "인천/신창/서동탄" to "종각")
        // 실시간 위치가 없는 노선도 역 목록이 있으면 풀린다. 7호선 부평구청에서 인천1호선으로 갈아탄다.
        nextOf("line7", "부평구청") shouldContainAll setOf("인천1호선" to "계양" to "갈산", "인천1호선" to "송도달빛축제공원" to "부평시장")
        // 광명셔틀은 그 뷰의 이름으로 찾는다. 금천구청에서 광명 방면은 바로 광명이다.
        nextOf("line1-gyeongbu", "금천구청") shouldContainAll setOf("1호선 광명셔틀" to "광명" to "광명")
    }

    "갈라지는 역에서는 방면이 가리키는 지선의 다음 역을 쓰고 두 지선이면 비운다" {
        // given - 5호선 강동에서 하남과 마천으로 갈린다. 마천 방면은 둔촌동이지만, 두 지선을 함께 적은
        // 방면은 타는 열차에 따라 길동이기도 둔촌동이기도 하다.
        val gangdong = repository.findByLine(TestLines.bySlug("line5-hanam")).stations.single { it.station == "강동" }

        // when
        val next = gangdong.doors.map { it.targetDirection to it.targetNext }.toSet()

        // then
        next shouldContainAll setOf("마천" to "둔촌동", "하남검단산/마천" to null)
    }

    "여러 노선을 한 줄에 적었거나 방면이 없는 줄에는 다음 역이 없다" {
        // given - 9호선 김포공항의 "5호선/공항철도 서울역 방면" 은 서울역이 공항철도에만 있다. 방면이 없는
        // 줄은 어느 쪽으로 가든 같은 문이다.
        val gimpo = repository.findByLine(TestLines.bySlug("line9")).stations.single { it.station == "김포공항" }
        val guri = repository.findByLine(TestLines.bySlug("line8")).stations.single { it.station == "구리" }

        // when & then
        gimpo.doors.filter { it.targetLine == "5호선/공항철도" }.forAll { it.targetNext.shouldBeNull() }
        guri.doors.forAll { it.targetNext.shouldBeNull() }
    }

    "열차 방면으로 칸을 가릴 수 없으면 기동이 실패한다" {
        // given - 방면에 노선의 역이 하나도 없다. 조용히 양쪽에 두면 틀린 칸이 화면에 남는다.
        val catalog = LineCatalog(
            MetroProperties(
                listOf(
                    LineConfig(
                        slug = "test",
                        name = "테스트선",
                        color = "#000000",
                        source = LineSource.SEOUL,
                        upLabel = "가",
                        downLabel = "다",
                        stations = listOf("가", "나", "다"),
                        transferFile = "broken-side",
                    )
                )
            )
        )

        // when
        val error = shouldThrow<IllegalArgumentException> { TransferDoorRepository(catalog) }

        // then - 어느 파일의 어느 방면인지 알려야 한다.
        error.message shouldContain "broken-side-doors.csv"
        error.message shouldContain "없는역"
    }
})
