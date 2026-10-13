package kr.kennysoft.kennymetro.everline

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kr.kennysoft.kennymetro.TestLines
import kr.kennysoft.kennymetro.domain.Direction
import kr.kennysoft.kennymetro.domain.TrainStatus

class EverlineTrainTest : FreeSpec({

    "역 코드를 역명으로 옮긴다" {
        // given - Y110 이 기흥이고 거기서부터 순서대로 붙는다.
        val dto = position(station = "Y121", destination = "Y110")

        // when
        val train = dto.toTrain(TestLines.everline)!!

        // then
        train.currentStation shouldBe "고진"
        train.destination shouldBe "기흥"
    }

    "차량기지 코드는 역이 아니라 제외한다" {
        // given - Y109 는 차량기지다. 공식 페이지 스크립트도 이 값을 걸러낸다.
        val dto = position(station = "Y109", destination = "Y124")

        // when & then
        dto.toTrain(TestLines.everline).shouldBeNull()
    }

    "역 범위를 벗어난 코드는 제외한다" {
        // given
        val tooHigh = position(station = "Y125", destination = "Y110")
        val notNumeric = position(station = "YXXX", destination = "Y110")

        // when & then
        tooHigh.toTrain(TestLines.everline).shouldBeNull()
        notNumeric.toTrain(TestLines.everline).shouldBeNull()
    }

    "종착역 자리로 방향을 정한다" {
        // given
        val toGiheung = position(station = "Y121", destination = "Y110")
        val toJeondae = position(station = "Y112", destination = "Y124")

        // when & then
        toGiheung.toTrain(TestLines.everline)?.direction shouldBe Direction.UP
        toJeondae.toTrain(TestLines.everline)?.direction shouldBe Direction.DOWN
    }

    "종착역에 도착한 열차는 제외한다" {
        // given
        val dto = position(station = "Y110", destination = "Y110")

        // when & then
        dto.toTrain(TestLines.everline).shouldBeNull()
    }

    "달리는 열차는 떠난 역에서 다음 역까지 걸리는 초를 함께 싣는다" {
        // given - 2026-10-13 밤에 받은 두 대다. 29번은 삼가를 떠나 초당으로, 19번은 명지대를 떠나 김량장으로 갔다.
        val up = position(station = "Y116", destination = "Y110", statusCode = "3", time = "39")
        val down = position(station = "Y118", destination = "Y124", statusCode = "3", time = "22")

        // when
        val toChodang = up.toTrain(TestLines.everline)!!
        val toGimnyangjang = down.toTrain(TestLines.everline)!!

        // then - 상행 배열도 기흥 쪽부터 센 구간 순서다. 초당과 삼가 사이는 여섯 번째 구간이다.
        toChodang.status shouldBe TrainStatus.DEPARTED
        toChodang.elapsedSeconds shouldBe 39
        toChodang.segmentSeconds shouldBe 172
        toGimnyangjang.segmentSeconds shouldBe 64
    }

    "서 있는 열차는 선 뒤 흐른 초만 싣는다" {
        // given
        val dto = position(station = "Y118", destination = "Y124", statusCode = "2", time = "16")

        // when
        val train = dto.toTrain(TestLines.everline)!!

        // then
        train.status shouldBe TrainStatus.STOPPED
        train.elapsedSeconds shouldBe 16
        train.segmentSeconds.shouldBeNull()
    }

    "모르는 상태 값이면 비워 역에 선 것으로 그리게 한다" {
        // given
        val dto = position(station = "Y118", destination = "Y124", statusCode = "9", time = "16")

        // when
        val train = dto.toTrain(TestLines.everline)!!

        // then
        train.status.shouldBeNull()
        train.segmentSeconds.shouldBeNull()
    }
})

private fun position(
    station: String,
    destination: String,
    statusCode: String = "3",
    time: String = "40",
) = EverlineTrainDto(
    trainNo = "1",
    stationCode = station,
    destinationCode = destination,
    statusCode = statusCode,
    updownCode = "1",
    time = time,
)
