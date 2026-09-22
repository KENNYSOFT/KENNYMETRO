package kr.kennysoft.kennymetro.domain

/**
 * 환승 문 한 자리.
 *
 * <p>
 * 두 방면이 각각 null 일 수 있고 그 의미가 다르다. `trainDirection` 이 null 이면 지금 탄
 * 열차가 어느 쪽으로 가든 같은 문이라는 뜻이고(신분당선 시종착역인 신사가 그렇다),
 * `targetDirection` 이 null 이면 갈아타서 어느 쪽으로 가든 같은 문이라는 뜻이다.
 */
data class TransferDoor(
    val trainDirection: String?,
    val targetLine: String,
    val targetDirection: String?,
    val car: Int,
    val door: Int,
)

/**
 * 한 환승역의 정보. `doors` 는 그 역에서 갈아탈 수 있는 모든 경우다.
 */
data class StationTransfer(
    val station: String,
    val note: String?,
    val doors: List<TransferDoor>,
)
