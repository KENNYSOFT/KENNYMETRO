package kr.kennysoft.kennymetro.domain

import kr.kennysoft.kennymetro.seoul.TrainPositionDto

/**
 * 지금 탈 수 있는 열차 한 대.
 *
 * @param destination 종착역. 신분당선의 정자행처럼 노선 끝까지 가지 않는 열차가 있어
 *                    이 값이 열차를 고르는 기준이 된다.
 * @param fleet 몇 차분 차량인지. 편성번호로 차량을 특정할 수 있는 노선에만 있다.
 */
data class Train(
    val trainNo: String,
    val currentStation: String,
    val destination: String,
    val direction: Direction,
    val isLastTrain: Boolean,
    val isExpress: Boolean,
    /** 종착역이 노선 끝이 아닌 열차. 타기 전에 알아야 한다. */
    val isShortTurn: Boolean,
    val fleet: String?,
)

enum class Direction {
    /** 역 순서가 작아지는 쪽. 신분당선은 신사 방면, 순환선은 `updnLine` 0. */
    UP,

    /** 역 순서가 커지는 쪽. 신분당선은 광교 방면, 순환선은 `updnLine` 1. */
    DOWN,
}

/**
 * 실시간 위치 응답을 도메인 열차로 옮긴다.
 * <p>
 * 현재역과 종착역이 같은 열차는 운행을 마치는 중이므로 제외한다. 그런 열차는 다음
 * 조회에서 목록에서 사라지며, 세지 않으면 이미 내린 열차가 "탈 수 있는 열차" 로 잡힌다.
 * 방향은 `updnLine` 이 아니라 현재역과 종착역의 자리로 정한다 - 종착역에서 회차를
 * 기다리는 열차는 `updnLine` 이 직전 운행의 값이라 반대로 나온다.
 */
fun TrainPositionDto.toTrain(line: Line): Train? {
    val currentIndex = line.indexOf(statnNm) ?: return null
    return if (line.circular) toCircularTrain(line) else toLinearTrain(line, currentIndex)
}

private fun TrainPositionDto.toLinearTrain(line: Line, currentIndex: Int): Train? {
    if (statnNm == statnTnm) return null

    val destinationIndex = line.indexOf(statnTnm) ?: return null
    if (currentIndex == destinationIndex) return null

    return Train(
        trainNo = trainNo,
        currentStation = statnNm,
        destination = statnTnm,
        direction = if (destinationIndex > currentIndex) Direction.DOWN else Direction.UP,
        isLastTrain = lstcarAt == "1",
        isExpress = directAt == "1",
        isShortTurn = destinationIndex != 0 && destinationIndex != line.stations.lastIndex,
        fleet = line.fleet?.labelFor(trainNo),
    )
}

/**
 * 순환선의 열차.
 *
 * <p>
 * 2호선은 종착 표기가 역명이 아니라 "성수종착", "신도림지선" 처럼 온다. 자리를 견줄 수
 * 없으므로 방향은 `updnLine` 을 그대로 쓴다. 회차 대기 열차가 반대로 잡히는 문제가 남지만,
 * 순환선에는 그 대신 쓸 것이 없다.
 *
 * <p>
 * 노선 끝이 없으므로 `isShortTurn` 도 없다. 지선으로 들어가는 열차는 종착이 본선 역과
 * 맞지 않아 여기까지 오더라도 화면에서는 본선 구간에만 그려진다.
 */
private fun TrainPositionDto.toCircularTrain(line: Line): Train? {
    // "성수종착" 처럼 뒤에 붙는 말을 떼면 역명이 된다. 그 역에 서 있으면 운행을 마치는 중이다.
    val destination = statnTnm.removeSuffix("종착")
    if (statnNm == destination) return null

    return Train(
        trainNo = trainNo,
        currentStation = statnNm,
        destination = destination,
        direction = if (updnLine == "0") Direction.UP else Direction.DOWN,
        isLastTrain = lstcarAt == "1",
        isExpress = directAt == "1",
        isShortTurn = false,
        fleet = line.fleet?.labelFor(trainNo),
    )
}
