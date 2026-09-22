package kr.kennysoft.kennymetro.seoul

import org.slf4j.LoggerFactory
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.net.http.HttpClient

/**
 * 서울시 실시간 지하철 API 호출자.
 * <p>
 * 호출 한 번이 일일 예산 1,000회를 깎으므로 이 클래스를 직접 부르지 말고
 * {@link TrainPositionService} 를 통해 캐시된 값을 쓴다.
 * <p>
 * 응답 DTO 는 `body(X::class.java)` 로 런타임에 지정하므로 Spring AOT 의 정적 분석에
 * 잡히지 않는다. 컨트롤러 반환 타입과 달리 직접 등록하지 않으면 native image 에서
 * `KotlinReflectionInternalError: Unresolved class` 로 역직렬화가 실패한다.
 */
@Component
@RegisterReflectionForBinding(RealtimePositionResponse::class, TrainPositionDto::class)
class SeoulSubwayClient(private val properties: SeoulSubwayProperties) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val restClient: RestClient = RestClient.builder()
        .requestFactory(
            JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(properties.requestTimeout).build()
            ).apply { setReadTimeout(properties.requestTimeout) }
        )
        .build()

    /**
     * 노선 전체 열차의 현재 위치를 조회한다.
     * <p>
     * 운행 중인 열차가 없을 때와 존재하지 않는 노선명을 넘겼을 때의 응답이
     * `INFO-200` 으로 같으므로, 이 메서드는 둘을 구분하지 않고 빈 목록을 돌려준다.
     * 노선이 실제로 수록돼 있는지는 운행 시간대에 확인해야 한다.
     */
    fun findPositions(lineName: String): List<TrainPositionDto> {
        // 노선명을 손수 인코딩해 문자열로 붙이지 말 것. uri(String) 은 URI 템플릿으로
        // 해석해 이미 인코딩된 `%` 를 `%25` 로 한 번 더 인코딩하고, 서울시 API 는 그것을
        // 오류가 아니라 INFO-200(데이터 없음)으로 돌려주므로 조용히 빈 화면이 된다.
        // 템플릿 변수로 넘겨 인코딩을 한 번만 거치게 한다.
        val response = restClient.get()
            .uri("${properties.baseUrl}/{key}/json/realtimePosition/0/999/{line}", properties.apiKey, lineName)
            .retrieve()
            .body(RealtimePositionResponse::class.java)

        if (response == null) {
            log.warn("실시간 위치 응답이 비었다. line={}", lineName)
            return emptyList()
        }
        if (response.realtimePositionList == null) {
            // INFO-200 은 "열차 없음" 이자 "노선 없음" 이다. 오류로 취급하지 않는다.
            if (response.code != null && response.code != NO_DATA_CODE) {
                log.warn("실시간 위치 조회 실패. line={} code={} message={}", lineName, response.code, response.message)
            }
            return emptyList()
        }
        return response.realtimePositionList
    }

    companion object {
        private const val NO_DATA_CODE = "INFO-200"
    }
}

/**
 * 실시간 위치 응답. 정상이면 `realtimePositionList` 가 오고, 데이터가 없으면
 * 최상위에 `code` 와 `message` 만 담긴 다른 모양으로 온다.
 */
data class RealtimePositionResponse(
    val realtimePositionList: List<TrainPositionDto>? = null,
    val code: String? = null,
    val message: String? = null,
)

/**
 * 열차 한 대의 위치. 필드명은 API 응답 그대로다.
 */
data class TrainPositionDto(
    val subwayId: String,
    val subwayNm: String?,
    val statnId: String,
    val statnNm: String,
    val trainNo: String,
    val statnTid: String,
    val statnTnm: String,
    val updnLine: String,
    val trainSttus: String,
    val directAt: String,
    val lstcarAt: String,
    val recptnDt: String,
)
