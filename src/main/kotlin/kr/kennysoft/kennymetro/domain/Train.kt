package kr.kennysoft.kennymetro.domain

import kr.kennysoft.kennymetro.seoul.TrainPositionDto

/**
 * 지금 탈 수 있는 열차 한 대.
 *
 * @param destination 종착역. 신분당선의 정자행처럼 노선 끝까지 가지 않는 열차가 있어
 *                    이 값이 열차를 고르는 기준이 된다. 실시간 종착역이 진행 방향과 어긋나고
 *                    시간표로도 찾지 못하면 null 이다(DESIGN.md 2.2.2).
 * @param service 평소 종착역까지 가는지, 먼저 끝나는지, 더 가는지, 다른 계통으로 갈라지는지.
 * @param fleet 몇 차분 차량인지. 편성번호로 차량을 특정할 수 있는 노선에만 있다.
 */
data class Train(
    val trainNo: String,
    val currentStation: String,
    val destination: String?,
    val direction: Direction,
    val isLastTrain: Boolean,
    val isExpress: Boolean,
    val service: ServiceKind,
    val fleet: String?,
)

enum class Direction {
    /** 역 순서가 작아지는 쪽. 신분당선은 신사 방면, 2호선은 외선(`updnLine` 1). */
    UP,

    /** 역 순서가 커지는 쪽. 신분당선은 광교 방면, 2호선은 내선(`updnLine` 0). */
    DOWN,
}

/**
 * 실시간 위치 응답을 열차 한 대에 한 줄로 정리한다.
 *
 * <p>
 * <b>같은 열차가 두 번 온다.</b> 한 열차가 옛 기록과 새 기록으로 함께 오는데, 수신 시각이
 * 몇 초에서 4분까지 벌어져 있고 역이 다르기도 하다(1, 3, 4, 5호선에서 확인). 둘 다 그리면
 * 한 대가 두 대로 보이므로 수신 시각이 늦은 것만 남긴다. 수신 시각은 `yyyy-MM-dd HH:mm:ss`
 * 라 문자열 순서가 곧 시간 순서다.
 *
 * <p>
 * 역명은 여기서 우리 목록의 표기로 옮긴다(`StationNames`). 뷰마다 다시 옮기지 않으려는 것이다.
 */
fun List<TrainPositionDto>.tidy(names: StationNames): List<TrainPositionDto> =
    groupBy { it.trainNo }.values
        .map { records -> records.maxBy { it.recptnDt } }
        .map { it.copy(statnNm = names.normalize(it.statnNm), statnTnm = names.normalize(it.statnTnm)) }

/**
 * 종착역을 믿을 수 없는 열차를 시간표로 다시 본 결과.
 *
 * <p>
 * 운행을 거의 마친 열차는 종착역이 다음 운행 것으로 먼저 바뀌어 온다(DESIGN.md 2.2.2).
 */
sealed interface Recheck {
    /** 시간표에 적힌 이 운행의 종착역. */
    data class Terminal(val station: String) : Recheck

    /** 시간표로도 알 수 없다. 방향은 `updnLine` 으로 정하고 종착역은 비운다. */
    data object Unknown : Recheck
}

/**
 * 종착역이 가리키는 쪽과 `updnLine` 이 가리키는 쪽이 어긋나 시간표로 다시 봐야 하는지.
 *
 * <p>
 * 열차가 돌아가는 역(`Line.turnsBackAt`)에서는 `updnLine` 이 직전 운행의 값이라 원래 어긋나므로
 * 보지 않는다. 한 방향으로만 다니는 역은 방향이 정해져 있고, 순환선은 종착역으로 방향을 정하지
 * 않으므로 대상이 아니다.
 */
fun TrainPositionDto.needsRecheck(line: Line): Boolean {
    if (line.circular || statnNm == statnTnm || statnNm in line.downOnly) return false
    val current = line.indexOf(statnNm) ?: return false
    if (line.turnsBackAt(current)) return false
    val destination = line.destinationOf(statnTnm) ?: return false
    if (destination.index == current) return false
    val towardDestination = if (destination.index > current) Direction.DOWN else Direction.UP
    return towardDestination != line.directionOf(updnLine)
}

/**
 * 실시간 위치 응답을 도메인 열차로 옮긴다. 역명은 `tidy` 로 이미 옮겨 둔 것이어야 한다.
 * <p>
 * 현재역과 종착역이 같은 열차는 운행을 마치는 중이므로 제외한다. 그런 열차는 다음
 * 조회에서 목록에서 사라지며, 세지 않으면 이미 내린 열차가 "탈 수 있는 열차" 로 잡힌다.
 * 방향은 `updnLine` 이 아니라 현재역과 종착역의 자리로 정한다 - 종착역에서 회차를
 * 기다리는 열차는 `updnLine` 이 직전 운행의 값이라 반대로 나온다.
 * <p>
 * 다만 노선 중간에서 두 값이 어긋나면 틀린 쪽은 종착역이다. `recheck` 는 그런 열차를 시간표로
 * 다시 본 결과이고, 이 뷰에서도 어긋날 때만 쓴다(`needsRecheck`).
 */
fun TrainPositionDto.toTrain(line: Line, recheck: Recheck? = null): Train? {
    val currentIndex = line.indexOf(statnNm) ?: return null
    if (line.circular) return toCircularTrain(line)
    if (recheck == null || !needsRecheck(line)) return toLinearTrain(line, currentIndex, statnTnm)
    return when (recheck) {
        // 시간표의 종착역이 이 역이면 운행을 마치는 중이라 toLinearTrain 이 뺀다. 이 뷰가 모르는
        // 종착역이면 방향을 정할 수 없어 종착역 없이 그린다.
        is Recheck.Terminal ->
            if (line.destinationOf(recheck.station) == null) toTrainWithoutDestination(line)
            else toLinearTrain(line, currentIndex, recheck.station)

        Recheck.Unknown -> toTrainWithoutDestination(line)
    }
}

private fun TrainPositionDto.toLinearTrain(line: Line, currentIndex: Int, terminal: String): Train? {
    if (statnNm == terminal) return null

    val destination = line.destinationOf(terminal) ?: return null
    // 목록 밖 종착역이 바로 이 역에서 갈라진다. 다음 역부터는 이 뷰에 없으니 그리지 않는다.
    if (destination.index == currentIndex) return null

    return Train(
        trainNo = trainNo,
        currentStation = statnNm,
        destination = terminal,
        direction = if (destination.index > currentIndex) Direction.DOWN else Direction.UP,
        isLastTrain = lstcarAt == "1",
        isExpress = directAt == "1",
        service = destination.service,
        fleet = line.fleet?.labelFor(trainNo),
    )
}

/**
 * 종착역을 모르는 열차. 방향은 `updnLine` 으로 정하고, 어디서 끝나는지 모르므로 단축이나 연장도
 * 가리지 않는다. 막차와 급행 표시는 실시간 값 그대로 둔다.
 */
private fun TrainPositionDto.toTrainWithoutDestination(line: Line) = Train(
    trainNo = trainNo,
    currentStation = statnNm,
    destination = null,
    direction = line.directionOf(updnLine),
    isLastTrain = lstcarAt == "1",
    isExpress = directAt == "1",
    service = ServiceKind.NORMAL,
    fleet = line.fleet?.labelFor(trainNo),
)

/**
 * 순환선의 열차.
 *
 * <p>
 * 종착이 `성수종착` 처럼 와서 자리를 견줄 수 없으므로 방향은 `updnLine` 을 쓴다. 두 번
 * 받아 같은 열차가 움직인 쪽을 보니 0 인 열차는 모두 역 순서가 커지는 쪽(시청에서
 * 을지로입구로), 1 인 열차는 모두 작아지는 쪽으로 갔다. API 문서대로 0 이 내선이고, 목록의
 * 뒤쪽이라 `lines.yml` 에 `updn-line-reversed` 로 적는다.
 *
 * <p>
 * 지선 끝(신설동, 까치산)으로 가는 열차는 본선 뷰에 그리지 않는다. 그 열차는 지선 뷰가
 * 보여준다.
 *
 * <p>
 * 종착은 대부분 `성수종착` 으로 온다. 성수에서 운행이 바뀔 뿐 열차는 계속 돌므로 성수를 평소
 * 종착역으로 두고, 그 밖의 역에서 끝나는 열차(신도림, 밤늦게 을지로입구와 홍대입구 등)는
 * 단축으로 가린다(DESIGN.md 1.6).
 */
private fun TrainPositionDto.toCircularTrain(line: Line): Train? {
    if (statnNm == statnTnm) return null
    val destinationIndex = line.indexOf(statnTnm) ?: return null

    return Train(
        trainNo = trainNo,
        currentStation = statnNm,
        destination = statnTnm,
        direction = line.directionOf(updnLine),
        isLastTrain = lstcarAt == "1",
        isExpress = directAt == "1",
        service = line.serviceAt(destinationIndex),
        fleet = line.fleet?.labelFor(trainNo),
    )
}
