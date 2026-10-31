package kr.kennysoft.kennymetro.domain

/**
 * 시간표의 한 운행이 서는 역과 그 역의 도착, 출발 시각. 시각은 운행일의 초다(`24:58:30` 은 89910).
 * 운행이 이 역에서 시작하면 도착이, 이 역에서 끝나면 출발이 없다.
 */
data class ScheduledStop(val station: String, val arrive: Int?, val depart: Int?)

/**
 * 시간표로 아는 한 운행. 실시간 위치가 빠지는 구간에서 그 열차를 그리는 데 쓴다(DESIGN.md 2.2.3).
 *
 * @param trainNo 실시간 표기에 맞춘 열차번호. 시간표의 `K7032` 를 `7032` 로 적는다.
 * @param terminal 이 운행의 종착역.
 * @param isLast 같은 시발역과 종착역으로 다니는 운행 가운데 그 날 마지막인지.
 * @param stops 뷰의 역 가운데 이 운행이 서는 역. 시각 순서다.
 */
data class ScheduledTrip(
    val trainNo: String,
    val terminal: String,
    val isLast: Boolean,
    val stops: List<ScheduledStop>,
) {

    /**
     * 그 시각에 어디에 있는지. 아직 나타나지 않았거나 이미 끝났으면 null 이다.
     *
     * <p>
     * 시발역에서는 떠나기 10분 전부터 서 있는 것으로 그린다. 실시간 위치도 시발역에서 떠나기를 기다리는
     * 열차를 준다. 뷰 밖에서 들어오는 운행(첫 역에 도착 시각이 있다)은 그 역에 닿을 때부터 그리고, 뷰 밖으로
     * 나가는 운행은 마지막 역을 떠나면 그리지 않는다. 종착역에 닿은 운행도 실시간 위치처럼 그리지 않는다.
     */
    fun positionAt(seconds: Int): ScheduledPosition? {
        val first = stops.firstOrNull() ?: return null
        val shownFrom = first.arrive ?: first.depart?.minus(WAIT_AT_ORIGIN_SECONDS) ?: return null
        if (seconds < shownFrom) return null
        stops.forEachIndexed { i, stop ->
            val depart = stop.depart ?: return null
            if (seconds < depart) return ScheduledPosition(this, stop.station, TrainStatus.STOPPED, null, null)
            val next = stops.getOrNull(i + 1) ?: return null
            val nextArrive = next.arrive ?: next.depart ?: return null
            if (seconds < nextArrive) {
                return ScheduledPosition(this, stop.station, TrainStatus.DEPARTED, seconds - depart, nextArrive - depart)
            }
        }
        return null
    }

    /**
     * 그 역의 실시간 기록이 멈춘 것인지.
     *
     * <p>
     * 실시간 위치는 `terminals` 와 그 바로 앞 역 사이에서 열차를 주지 않는다. 그래서 그 앞 역을 떠나
     * 종착역으로 가는 열차의 기록은 그 앞 역에 머물다 종착역에 닿은 뒤에 사라진다(DESIGN.md 2.2.3). 시간표로
     * 그 앞 역을 떠난 뒤라면 그 역에 남은 기록은 멈춘 것이다. 늦게 다녀 그보다 뒤에 있는 기록은 믿는다.
     */
    fun isStaleAt(station: String, seconds: Int, terminals: Set<String>): Boolean {
        if (terminal !in terminals || stops.size < 2) return false
        val beforeTerminal = stops[stops.size - 2]
        val depart = beforeTerminal.depart ?: return false
        return station == beforeTerminal.station && seconds >= depart
    }
}

/** 시발역에서 떠나기 몇 초 전부터 그 역에 서 있는 것으로 그리는지. */
private const val WAIT_AT_ORIGIN_SECONDS = 10 * 60

/**
 * 시간표로 아는 운행이 그 시각에 있는 자리. 역을 떠난 뒤라면 그 역을 떠난 뒤 흐른 초와 다음 역까지 걸리는
 * 초를 함께 든다. 화면은 용인경전철처럼 이 둘로 역 사이 어디쯤인지 그린다.
 */
data class ScheduledPosition(
    val trip: ScheduledTrip,
    val station: String,
    val status: TrainStatus,
    val elapsedSeconds: Int?,
    val segmentSeconds: Int?,
) {

    /**
     * 뷰에 그릴 열차로 옮긴다. 실시간 열차와 같은 규칙이다(`toTrain`) - 이 뷰에 없는 역에 있거나 모르는
     * 종착역으로 가면 그리지 않고, 목록 밖 종착역은 그 열차가 뷰를 떠나는 역을 향해 다른 계통으로 그린다.
     */
    fun toTrain(line: Line): Train? {
        val currentIndex = line.indexOf(station) ?: return null
        val destination = line.destinationOf(trip.terminal) ?: return null
        // 목록 밖 종착역으로 빠지는 자리가 바로 이 역이다. 다음 역부터는 이 뷰에 없으니 그리지 않는다.
        if (destination.index == currentIndex) return null
        return Train(
            trainNo = trip.trainNo,
            currentStation = station,
            destination = trip.terminal,
            direction = if (destination.index > currentIndex) Direction.DOWN else Direction.UP,
            isLastTrain = trip.isLast,
            isExpress = false,
            service = destination.service,
            fleet = null,
            status = status,
            elapsedSeconds = elapsedSeconds,
            segmentSeconds = segmentSeconds,
            scheduled = true,
        )
    }
}
