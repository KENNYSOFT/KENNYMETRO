package kr.kennysoft.kennymetro.domain

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kr.kennysoft.kennymetro.TestLines
import kr.kennysoft.kennymetro.config.LineConfig
import kr.kennysoft.kennymetro.config.MetroProperties
import kr.kennysoft.kennymetro.seoul.TrainPositionDto

/**
 * 종착역이 다음 운행 것으로 앞서 바뀐 열차를 가려내고, 시간표로 다시 본 결과로 그린다(DESIGN.md 2.2.2).
 */
class RecheckTest : FreeSpec({

    val line3 = TestLines.bySlug("line3")
    val uisinseol = TestLines.bySlug("uisinseol")

    "노선 중간에서 종착역과 updnLine 이 어긋나면 시간표로 다시 본다" {
        // given - 3호선 3423. 동대입구에서 종착이 구파발(목록 앞쪽)로 왔는데 updnLine 은 하행이다.
        val glitch = position(current = "동대입구", terminal = "구파발", updnLine = "1")
        val normal = position(current = "동대입구", terminal = "오금", updnLine = "1")

        // when & then
        glitch.needsRecheck(line3).shouldBeTrue()
        normal.needsRecheck(line3).shouldBeFalse()
    }

    "열차가 돌아가는 역에서는 어긋나도 다시 보지 않는다" {
        // given - 회차를 기다리는 열차는 updnLine 이 직전 운행의 값이다. 목록의 끝과 평소 종착역이 그렇다.
        val atEnd = position(current = "광교", terminal = "신사", updnLine = "1")
        val atTerminus = position(current = "김포공항", terminal = "중앙보훈병원", updnLine = "0")

        // when & then
        atEnd.needsRecheck(TestLines.shinbundang).shouldBeFalse()
        atTerminus.needsRecheck(TestLines.bySlug("line9")).shouldBeFalse()
    }

    "열차가 다니지 않는 역의 바로 옆도 구간의 끝이라 다시 보지 않는다" {
        // given - 다 가 열리지 않아 나 에서 돌아간다. 평소 종착역으로 적지 않아도 끝으로 본다.
        val line = LineCatalog(
            MetroProperties(
                listOf(
                    LineConfig(
                        slug = "test",
                        name = "테스트선",
                        color = "#000000",
                        source = LineSource.SEOUL,
                        upLabel = "가",
                        downLabel = "마",
                        stations = listOf("가", "나", "다", "라", "마"),
                        noService = listOf("다"),
                    )
                )
            )
        ).bySlug("test")!!
        val turning = position(current = "나", terminal = "가", updnLine = "1")

        // when & then
        turning.needsRecheck(line).shouldBeFalse()
    }

    "순환선과 한 방향으로만 다니는 역은 방향이 정해져 있어 다시 보지 않는다" {
        // given - 2호선은 updnLine 으로 방향을 정하고, 6호선 응암 순환 구간은 늘 목록 뒤쪽으로 돈다.
        val loop = position(current = "강남", terminal = "성수", updnLine = "1")
        val inLoop = position(current = "불광", terminal = "응암", updnLine = "0")

        // when & then
        loop.needsRecheck(TestLines.line2).shouldBeFalse()
        inLoop.needsRecheck(TestLines.bySlug("line6")).shouldBeFalse()
    }

    "updnLine 이 가리키는 쪽은 노선마다 확인한 대로 견준다" {
        // given - 우이신설선은 0 이 신설동(목록 뒤쪽)으로 온다. 2026-09-23 10:50 의 1147 은 북한산우이로
        // 가면서 종착이 신설동으로 왔고, 1154 는 신설동으로 가는 평범한 열차다.
        val glitch = position(current = "가오리", terminal = "신설동", updnLine = "1")
        val normal = position(current = "성신여대입구", terminal = "신설동", updnLine = "0")

        // when & then
        glitch.needsRecheck(uisinseol).shouldBeTrue()
        normal.needsRecheck(uisinseol).shouldBeFalse()
        line3.directionOf("0") shouldBe Direction.UP
        uisinseol.directionOf("0") shouldBe Direction.DOWN
        TestLines.bySlug("line2-sinjeong").directionOf("0") shouldBe Direction.DOWN
        TestLines.bySlug("line2-seongsu").directionOf("0") shouldBe Direction.UP
        TestLines.line2.directionOf("0") shouldBe Direction.DOWN
    }

    "시간표의 종착역으로 방향과 운행 종류를 다시 정한다" {
        // given
        val glitch = position(current = "동대입구", terminal = "구파발", updnLine = "1", lastTrain = "1")

        // when
        val train = glitch.toTrain(line3, Recheck.Terminal("약수"))

        // then - 약수는 평소 종착역이 아니라 단축이다. 막차 표시는 실시간 값 그대로다.
        train?.destination shouldBe "약수"
        train?.direction shouldBe Direction.DOWN
        train?.service shouldBe ServiceKind.SHORT
        train?.isLastTrain shouldBe true
    }

    "시간표로는 지금 역에서 끝나는 열차는 종착 처리 중인 열차처럼 뺀다" {
        // given
        val glitch = position(current = "동대입구", terminal = "구파발", updnLine = "1")

        // when & then
        glitch.toTrain(line3, Recheck.Terminal("동대입구")).shouldBeNull()
    }

    "시간표로도 모르면 updnLine 으로 방향만 정하고 종착역을 비운다" {
        // given
        val glitch = position(current = "가오리", terminal = "신설동", updnLine = "1", lastTrain = "1", express = "1")

        // when
        val train = glitch.toTrain(uisinseol, Recheck.Unknown)

        // then - 어디서 끝나는지 모르니 단축이나 연장으로 칠하지 않는다. 막차와 급행 표시는 그대로다.
        train?.destination.shouldBeNull()
        train?.direction shouldBe Direction.UP
        train?.service shouldBe ServiceKind.NORMAL
        train?.isLastTrain shouldBe true
        train?.isExpress shouldBe true
    }

    "이 뷰에서 어긋나지 않는 열차에는 다시 본 결과를 쓰지 않는다" {
        // given - 같은 API 노선의 다른 뷰에서 어긋나 다시 본 열차다.
        val normal = position(current = "동대입구", terminal = "오금", updnLine = "1")

        // when & then
        normal.toTrain(line3, Recheck.Unknown)?.destination shouldBe "오금"
    }
})

private fun position(
    current: String,
    terminal: String,
    updnLine: String,
    lastTrain: String = "0",
    express: String = "0",
) = TrainPositionDto(
    subwayId = "1003",
    subwayNm = "3호선",
    statnId = "1003000332",
    statnNm = current,
    trainNo = "3423",
    statnTid = "1003000320",
    statnTnm = terminal,
    updnLine = updnLine,
    trainSttus = "1",
    directAt = express,
    lstcarAt = lastTrain,
    recptnDt = "2026-09-24 00:59:24",
)
