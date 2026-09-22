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

/**
 * 한 노선의 환승 정보와 그 출처.
 *
 * <p>
 * 출처가 노선마다 다르다. 나무위키가 환승 정보를 노선별 하위 문서로 나눠 두어서, 어느
 * 문서의 몇 번째 판에서 가져왔는지도 노선마다 갈린다.
 */
data class LineTransfers(
    val source: TransferSource?,
    val stations: List<StationTransfer>,
)

/**
 * 데이터를 가져온 문서와 그 판. CC BY 조건이라 화면에 그대로 표시한다.
 *
 * <p>
 * 판까지 적는 것은 위키가 계속 고쳐지기 때문이다. 문서명만으로는 우리가 옮긴 시점의 내용을
 * 가리킬 수 없다.
 */
data class TransferSource(
    val document: String,
    val revision: String,
    val url: String,
    val license: String,
    val licenseUrl: String,
)
