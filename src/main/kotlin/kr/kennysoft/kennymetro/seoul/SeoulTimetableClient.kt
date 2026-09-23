package kr.kennysoft.kennymetro.seoul

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.net.http.HttpClient

/**
 * 서울시 역별 열차 시간표 호출자.
 *
 * <p>
 * 실시간 위치의 종착역을 믿을 수 없는 열차를 다시 볼 때만 부른다. 이 클래스를 직접 부르지 말고
 * {@link TimetableLookup} 을 통해 캐시된 값을 쓴다.
 *
 * <p>
 * 데이터가 없으면 서비스 이름 아래가 아니라 최상위에 `RESULT` 만 담아 온다. 그것은 오류가 아니라
 * 빈 목록이다. 1~9호선 밖의 노선은 역 코드가 있어도 시간표가 늘 그렇게 온다.
 *
 * <p>
 * 응답 DTO 를 직접 등록하는 이유는 {@link SeoulSubwayClient} 와 같다. 빠뜨리면 native image 에서만
 * 역직렬화가 실패한다.
 */
@Component
@RegisterReflectionForBinding(
    StationSearchResponse::class,
    StationSearchBody::class,
    StationRow::class,
    TimetableResponse::class,
    TimetableBody::class,
    TimetableRow::class,
    OpenApiResult::class,
)
class SeoulTimetableClient(private val properties: SeoulSubwayProperties) {

    private val restClient: RestClient = RestClient.builder()
        .requestFactory(
            JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(properties.requestTimeout).build()
            ).apply { setReadTimeout(properties.requestTimeout) }
        )
        .build()

    /** 그 이름의 역. 역마다가 아니라 노선마다 한 줄씩 온다(종로3가는 1, 3, 5호선). */
    fun findStations(name: String): List<StationRow> {
        // 역명은 실시간 API 와 같은 이유로 템플릿 변수로 넘긴다(SeoulSubwayClient).
        val response = restClient.get()
            .uri("${properties.timetableBaseUrl}/{key}/json/SearchInfoBySubwayNameService/1/20/{name}/", properties.apiKey, name)
            .retrieve()
            .body(StationSearchResponse::class.java)
        return response?.body?.row ?: noRows(response?.result, "역 코드")
    }

    /**
     * 그 역의 하루치 시간표.
     *
     * @param weekTag 1 평일, 2 토요일, 3 휴일
     * @param inoutTag 1 상행과 내선, 2 하행과 외선
     */
    fun findTimetable(stationCode: String, weekTag: String, inoutTag: String): List<TimetableRow> {
        val response = restClient.get()
            .uri(
                "${properties.timetableBaseUrl}/{key}/json/SearchSTNTimeTableByIDService/1/999/{code}/{week}/{inout}/",
                properties.apiKey, stationCode, weekTag, inoutTag,
            )
            .retrieve()
            .body(TimetableResponse::class.java)
        return response?.body?.row ?: noRows(response?.result, "시간표")
    }

    /** 데이터가 없다는 응답은 빈 목록이고, 그 밖의 응답은 실패로 던진다. 부르는 쪽이 잠시 다시 부르지 않는다. */
    private fun <T> noRows(result: OpenApiResult?, what: String): List<T> {
        if (result?.code == NO_DATA_CODE) return emptyList()
        error("$what 조회 실패. code=${result?.code} message=${result?.message}")
    }

    companion object {
        private const val NO_DATA_CODE = "INFO-200"
    }
}

/** 역명 검색 응답. 데이터가 없으면 서비스 이름 없이 최상위 `RESULT` 만 온다. */
data class StationSearchResponse(
    @param:JsonProperty("SearchInfoBySubwayNameService") val body: StationSearchBody?,
    @param:JsonProperty("RESULT") val result: OpenApiResult?,
)

data class StationSearchBody(
    val row: List<StationRow>?,
)

/** 역 하나. `LINE_NUM` 은 `03호선`, `우이신설경전철` 처럼 온다. */
data class StationRow(
    @param:JsonProperty("STATION_CD") val stationCode: String,
    @param:JsonProperty("STATION_NM") val stationName: String,
    @param:JsonProperty("LINE_NUM") val lineNum: String,
)

/** 역별 시간표 응답. 데이터가 없으면 역명 검색과 같은 모양으로 온다. */
data class TimetableResponse(
    @param:JsonProperty("SearchSTNTimeTableByIDService") val body: TimetableBody?,
    @param:JsonProperty("RESULT") val result: OpenApiResult?,
)

data class TimetableBody(
    val row: List<TimetableRow>?,
)

/**
 * 시간표 한 줄. 필요한 필드만 받는다.
 *
 * <p>
 * 시각은 `24:58:30` 처럼 24시를 넘겨 적힌다. 출발역은 도착 시각이, 종착역은 출발 시각이
 * `00:00:00` 이다. 열차번호는 노선마다 표기가 달라 `K56`, `3426K`, `C9008` 처럼 글자가 붙는다.
 */
data class TimetableRow(
    @param:JsonProperty("TRAIN_NO") val trainNo: String,
    @param:JsonProperty("ARRIVETIME") val arriveTime: String,
    @param:JsonProperty("LEFTTIME") val leftTime: String,
    @param:JsonProperty("SUBWAYSNAME") val origin: String,
    @param:JsonProperty("SUBWAYENAME") val terminal: String,
)

data class OpenApiResult(
    @param:JsonProperty("CODE") val code: String?,
    @param:JsonProperty("MESSAGE") val message: String?,
)
