package kr.kennysoft.kennymetro.domain

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kr.kennysoft.kennymetro.TestLines
import kr.kennysoft.kennymetro.seoul.TrainPositionDto

/**
 * 2호선은 순환선이라 선형 노선과 판정이 다르다.
 */
class CircularTrainTest : FreeSpec({

    "순환선은 종착역 자리가 아니라 updnLine 으로 방향을 정한다" {
        // given - 같은 역 같은 종착인데 updnLine 만 다르다. 선형 노선의 자리 비교로는 갈리지 않는다.
        // 같은 열차를 두 번 받아 보니 0 인 열차는 역 순서가 커지는 쪽(시청에서 을지로입구로)으로 갔다.
        val inner = train(current = "강남", terminal = "성수종착", updnLine = "0")
        val outer = train(current = "강남", terminal = "성수종착", updnLine = "1")

        // when & then
        inner?.direction shouldBe Direction.DOWN
        outer?.direction shouldBe Direction.UP
    }

    "종착 표기의 꼬리말을 떼어 역명으로 쓴다" {
        // given - 2호선 종착은 "성수종착" 처럼 역명이 아닌 값으로 온다.
        val train = train(current = "강남", terminal = "성수종착", updnLine = "0")

        // when & then
        train?.destination shouldBe "성수"
    }

    "종착역에 서서 운행을 마치는 열차는 제외한다" {
        // given - 꼬리말을 뗀 뒤 현재역과 같아지는 경우다.
        val train = train(current = "성수", terminal = "성수종착", updnLine = "0")

        // when & then
        train.shouldBeNull()
    }

    "순환선에는 노선 끝이 없어 단축 운행 표시를 하지 않는다" {
        // given
        val train = train(current = "강남", terminal = "성수종착", updnLine = "0")

        // when & then
        train?.service shouldBe ServiceKind.NORMAL
    }

    "지선으로 들어가는 열차는 본선 뷰에 그리지 않고 지선 뷰가 보여준다" {
        // given - 성수에 선 신설동행은 다음 역부터 성수지선이다.
        val onLoop = train(current = "성수", terminal = "신설동", updnLine = "1")
        val onBranch = train(current = "용두", terminal = "성수지선", updnLine = "0", line = TestLines.bySlug("line2-seongsu"))

        // when & then
        onLoop.shouldBeNull()
        onBranch?.destination shouldBe "성수"
        onBranch?.direction shouldBe Direction.UP
    }
})

private fun train(
    current: String,
    terminal: String,
    updnLine: String,
    line: Line = TestLines.line2,
): Train? = listOf(
    TrainPositionDto(
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
).tidy(TestLines.catalog.stationNames).single().toTrain(line)
