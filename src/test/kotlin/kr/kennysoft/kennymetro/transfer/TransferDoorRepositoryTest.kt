package kr.kennysoft.kennymetro.transfer

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldStartWith
import kr.kennysoft.kennymetro.TestLines

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

        // then - 다섯 노선 모두 환승 데이터가 있어야 한다.
        withData.size shouldBeGreaterThanOrEqual 5
        withData.forAll { (line, transfers) ->
            withClue("${line.slug} 에 출처가 없다") { transfers.source.shouldNotBeNull() }
        }
    }

    "비고가 있는 역과 없는 역을 가른다" {
        // given & when
        val stations = repository.findByLine(TestLines.shinbundang).stations

        // then
        stations.single { it.station == "미금" }.note.shouldNotBeNull()
        stations.single { it.station == "강남" }.note.shouldBeNull()
    }
})
