package kr.kennysoft.kennymetro.domain

import kr.kennysoft.kennymetro.seoul.TrainPositionDto

/**
 * 지금 탈 수 있는 열차 한 대.
 *
 * @param destination 종착역. 신분당선의 정자행처럼 노선 끝까지 가지 않는 열차가 있어
 *                    이 값이 열차를 고르는 기준이 된다.
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
)

enum class Direction {
    /** 역 순서가 작아지는 쪽. 신분당선은 신사 방면. */
    UP,

    /** 역 순서가 커지는 쪽. 신분당선은 광교 방면. */
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
    if (statnNm == statnTnm) return null

    val currentIndex = line.indexOf(statnNm) ?: return null
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
    )
}
