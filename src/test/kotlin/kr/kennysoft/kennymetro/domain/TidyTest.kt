package kr.kennysoft.kennymetro.domain

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import kr.kennysoft.kennymetro.TestLines
import kr.kennysoft.kennymetro.seoul.TrainPositionDto

/**
 * 실시간 API 응답은 그대로 쓸 수 없다. 같은 열차가 두 번 오고, 역명을 우리와 다르게 적는다.
 */
class TidyTest : FreeSpec({

    val names = TestLines.catalog.stationNames

    "같은 열차가 두 번 오면 수신 시각이 늦은 기록만 남긴다" {
        // given - 1호선에서 실제로 온 모양이다. 한 열차가 청량리에서 20초 차로 종착만 다르게 두 번 왔다.
        val old = position(trainNo = "0901", current = "청량리", terminal = "서울", received = "2026-09-23 10:50:30")
        val new = position(trainNo = "0901", current = "청량리", terminal = "용산", received = "2026-09-23 10:50:50")
        val other = position(trainNo = "0626", current = "동대문", terminal = "광운대", received = "2026-09-23 10:50:59")

        // when
        val tidied = listOf(old, other, new).tidy(names)

        // then
        tidied.map { it.trainNo } shouldBe listOf("0901", "0626")
        tidied.first().statnTnm shouldBe "용산"
    }

    "괄호 부기와 2호선 꼬리말을 떼고 이름이 다른 역은 우리 표기로 옮긴다" {
        // given & when & then - 하나라도 못 옮기면 그 역의 열차가 화면에서 빠진다.
        names.normalize("군자(능동)") shouldBe "군자"
        names.normalize("총신대입구(이수)") shouldBe "총신대입구"
        names.normalize("남한산성입구(성남법원,검찰청)") shouldBe "남한산성입구"
        names.normalize("성수종착") shouldBe "성수"
        names.normalize("신도림지선") shouldBe "신도림"
        names.normalize("서울") shouldBe "서울역"
        names.normalize("응암순환(상선)") shouldBe "응암"
        names.normalize("지제") shouldBe "평택지제"
        // 이미 우리 표기인 이름은 그대로 둔다. GTX-A 는 서울역을 서울역으로 준다.
        names.normalize("서울역") shouldBe "서울역"
        names.normalize("강남") shouldBe "강남"
    }
})

private fun position(trainNo: String, current: String, terminal: String, received: String) = TrainPositionDto(
    subwayId = "1001",
    subwayNm = "1호선",
    statnId = "1001000124",
    statnNm = current,
    trainNo = trainNo,
    statnTid = "1001000000",
    statnTnm = terminal,
    updnLine = "1",
    trainSttus = "1",
    directAt = "0",
    lstcarAt = "0",
    recptnDt = received,
)
