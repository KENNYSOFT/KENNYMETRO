package kr.kennysoft.kennymetro.seoul

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * 서울시 실시간 지하철 API 접속 설정.
 * <p>
 * `apiKey` 는 기본값이 없어 주입되지 않으면 기동이 실패한다. 실시간 지하철 API 는
 * 인증키당 하루 1,000회 제한이 있어 호출을 서버에서 캐시해 나눠 쓴다.
 */
@ConfigurationProperties(prefix = "seoul-subway")
data class SeoulSubwayProperties(
    val apiKey: String,
    val baseUrl: String,
    val cacheTtl: Duration,
    val requestTimeout: Duration,
)
