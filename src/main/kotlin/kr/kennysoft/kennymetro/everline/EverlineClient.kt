package kr.kennysoft.kennymetro.everline

import com.fasterxml.jackson.annotation.JsonProperty
import kr.kennysoft.kennymetro.domain.Direction
import kr.kennysoft.kennymetro.domain.Line
import kr.kennysoft.kennymetro.domain.Train
import kr.kennysoft.kennymetro.domain.TrainStatus
import org.slf4j.LoggerFactory
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.time.Duration

/**
 * 용인경전철 접속 설정. 공식 실시간 페이지가 쓰는 엔드포인트라 인증이 없고 호출 제한도
 * 공지되어 있지 않다.
 */
@ConfigurationProperties(prefix = "everline")
data class EverlineProperties(
    val baseUrl: String,
    val requestTimeout: Duration,
)

/**
 * 용인경전철 실시간 위치 호출자.
 *
 * <p>
 * 서울시 API 와 달리 노선을 지정하지 않는다. 노선이 하나뿐이라 엔드포인트가 그 노선의
 * 전체 열차를 돌려준다.
 */
@Component
@RegisterReflectionForBinding(EverlineResponse::class, EverlineTrainDto::class)
class EverlineClient(private val properties: EverlineProperties) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val restClient: RestClient = RestClient.builder()
        .requestFactory(
            JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(properties.requestTimeout).build()
            ).apply { setReadTimeout(properties.requestTimeout) }
        )
        .build()

    fun findPositions(): List<EverlineTrainDto> {
        val response = restClient.get()
            .uri("${properties.baseUrl}/api/api009.json")
            .retrieve()
            .body(EverlineResponse::class.java)

        if (response?.data == null) {
            log.warn("용인경전철 실시간 위치 응답이 비었다")
            return emptyList()
        }
        return response.data
    }
}

data class EverlineResponse(
    val data: List<EverlineTrainDto>? = null,
)

/**
 * 열차 한 대의 위치. 역은 이름이 아니라 `Y110` 같은 코드로 온다.
 */
data class EverlineTrainDto(
    @param:JsonProperty("TrainNo") val trainNo: String,
    @param:JsonProperty("StCode") val stationCode: String,
    @param:JsonProperty("DestCode") val destinationCode: String,
    @param:JsonProperty("StatusCode") val statusCode: String,
    @param:JsonProperty("updownCode") val updownCode: String,
    @param:JsonProperty("time") val time: String,
)

/**
 * 역 코드를 노선의 몇 번째 역인지로 옮긴다.
 *
 * <p>
 * `Y110`(기흥)부터 `Y124`(전대 에버랜드)까지 순서대로 붙는다. **`Y109` 는 차량기지이고
 * 실제 역이 아니다.** 공식 페이지 스크립트도 이 값을 걸러내므로 여기서도 범위 밖으로 떨어진다.
 */
private fun stationIndex(code: String, line: Line): Int? {
    val no = code.removePrefix("Y").toIntOrNull() ?: return null
    return (no - 110).takeIf { it in line.stations.indices }
}

/**
 * 실시간 위치 응답을 도메인 열차로 옮긴다. 판정 규칙은 서울시 쪽과 같다 - 현재역과
 * 종착역이 같으면 운행을 마치는 중이므로 제외하고, 방향은 두 역의 자리로 정한다.
 *
 * <p>
 * 달리는 중인 열차의 역 코드는 떠난 역이고 `time` 은 떠난 뒤 흐른 초다. 서 있는 동안은 같은 역 코드로
 * 선 뒤 흐른 초가 온다(DESIGN.md 2.4).
 */
fun EverlineTrainDto.toTrain(line: Line): Train? {
    val currentIndex = stationIndex(stationCode, line) ?: return null
    val destinationIndex = stationIndex(destinationCode, line) ?: return null
    if (currentIndex == destinationIndex) return null

    val direction = if (destinationIndex > currentIndex) Direction.DOWN else Direction.UP
    val status = when (statusCode) {
        STATUS_STOPPED -> TrainStatus.STOPPED
        STATUS_RUNNING -> TrainStatus.DEPARTED
        else -> null
    }
    return Train(
        trainNo = trainNo,
        currentStation = line.stations[currentIndex],
        destination = line.stations[destinationIndex],
        direction = direction,
        // 이 엔드포인트에는 막차와 급행 표시가 없다. 용인경전철은 급행 운행도 없다.
        isLastTrain = false,
        isExpress = false,
        service = line.serviceAt(destinationIndex),
        fleet = line.fleet?.labelFor(trainNo),
        status = status,
        elapsedSeconds = time.toIntOrNull(),
        segmentSeconds = if (status == TrainStatus.DEPARTED) segmentSeconds(currentIndex, direction) else null,
    )
}

/** 떠난 역에서 다음 역까지 걸리는 초. 표에 없는 자리면 null 이다. */
private fun segmentSeconds(departedIndex: Int, direction: Direction): Int? = when (direction) {
    Direction.DOWN -> DOWN_SEGMENT_SECONDS.getOrNull(departedIndex)
    Direction.UP -> UP_SEGMENT_SECONDS.getOrNull(departedIndex - 1)
}

/** 공식 스크립트의 주행 상태 값. */
private const val STATUS_RUNNING = "3"

/** 공식 스크립트의 정차 상태 값. */
private const val STATUS_STOPPED = "2"

/**
 * 하행(전대 에버랜드 쪽)으로 역과 역 사이를 달리는 데 걸리는 초. 공식 실시간 페이지 스크립트의 배열이다.
 * k 번째 값이 k 번째 역에서 k+1 번째 역까지다(DESIGN.md 2.4).
 */
private val DOWN_SEGMENT_SECONDS = listOf(89, 74, 78, 83, 121, 147, 79, 77, 64, 71, 102, 110, 77, 179)

/**
 * 상행(기흥 쪽)으로 걸리는 초. 하행 배열과 같은 구간 순서로 적혀 있어 k 번째 값이 k+1 번째 역에서
 * k 번째 역까지다(DESIGN.md 2.4).
 */
private val UP_SEGMENT_SECONDS = listOf(96, 82, 77, 86, 122, 172, 79, 75, 62, 70, 76, 124, 85, 184)
