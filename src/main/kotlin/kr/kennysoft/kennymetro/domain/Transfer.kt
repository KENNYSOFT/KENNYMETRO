package kr.kennysoft.kennymetro.domain

/**
 * 환승 문 한 자리.
 *
 * <p>
 * 두 방면이 각각 null 일 수 있고 그 의미가 다르다. `trainDirection` 이 null 이면 지금 탄
 * 열차가 어느 쪽으로 가든 같은 문이라는 뜻이고(신분당선 시종착역인 신사가 그렇다),
 * `targetDirection` 이 null 이면 갈아타서 어느 쪽으로 가든 같은 문이라는 뜻이다.
 *
 * <p>
 * `side` 는 화면의 어느 칸에 둘지다. 지금 탄 열차가 역 목록 앞쪽으로 가면 UP(왼쪽 칸), 뒤쪽으로
 * 가면 DOWN(오른쪽 칸)이고, null 이면 양쪽이다. `trainDirection` 은 원문의 열 제목이라 화면의
 * 좌우 이름과 글자가 달라("연천/광운대", "용산 급행") 서버가 역의 자리로 가려 둔다
 * (`TransferDoorRepository`). `trainDirection` 이 null 이어도 열차가 한쪽으로만 들어오는 역
 * (시종착역, 6호선 순환 구간)이면 그쪽이다.
 *
 * <p>
 * `targetNext` 는 갈아탄 뒤 `targetDirection` 으로 가면 바로 다음에 서는 역이다. 방면을 종착역으로
 * 적어 두면 그 노선을 자주 타지 않는 사람은 어느 쪽인지 떠올리기 어려워 함께 보인다. 방면이
 * 없거나, 우리가 담지 않은 노선이거나, 가릴 수 없으면 null 이다(`TransferDoorRepository`).
 *
 * <p>
 * 자리는 세 가지다. `car` 와 `door` 만 있으면 그 문 하나이고, `toCar` 와 `toDoor` 까지 있으면
 * 앞 자리부터 그 자리까지 이어진 모든 문이다. 넷 다 null 이면 어느 문에서 내려도 된다. 뒤의
 * 둘은 같은 승강장 건너편에서 갈아타는 역에서 나온다(4호선 한대앞에서 수인분당선이 그렇다).
 */
data class TransferDoor(
    val trainDirection: String?,
    val side: Direction?,
    val targetLine: String,
    val targetDirection: String?,
    val targetNext: String?,
    val car: Int?,
    val door: Int?,
    val toCar: Int?,
    val toDoor: Int?,
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
