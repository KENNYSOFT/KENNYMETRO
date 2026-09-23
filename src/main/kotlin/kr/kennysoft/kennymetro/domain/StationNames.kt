package kr.kennysoft.kennymetro.domain

/**
 * 실시간 API 의 역명을 우리 역 목록의 표기로 옮긴다.
 *
 * <p>
 * API 는 같은 역을 우리와 다르게 적는다. 세 가지가 있다.
 * <ol>
 *   <li>부역명을 괄호로 붙인다 - `군자(능동)`, `총신대입구(이수)`, `응암(하선-종착)`. 떼어 낸다.</li>
 *   <li>2호선은 종착에 꼬리말을 붙인다 - `성수종착`, `성수지선`, `신도림지선`. 떼어 낸다.</li>
 *   <li>이름이 아예 다르다 - `서울`(서울역), `지제`(평택지제), `응암순환`(응암). lines.yml 의
 *       station-aliases 로 옮긴다.</li>
 * </ol>
 *
 * <p>
 * 옮기지 못하면 그 역에 선 열차와 그 역으로 가는 열차가 화면에서 조용히 사라진다. 공항철도
 * 서울역행이 통째로 안 보였던 것이 이것이다. 그래서 옮긴 뒤에도 모르는 이름이 남으면
 * `TrainPositionService` 가 로그로 남긴다.
 */
class StationNames(private val aliases: Map<String, String>) {

    fun normalize(raw: String): String {
        val base = raw.substringBefore('(').trim()
        val stem = SUFFIXES.firstOrNull { base.length > it.length && base.endsWith(it) }
            ?.let { base.removeSuffix(it) }
            ?: base
        return aliases[stem] ?: stem
    }

    private companion object {
        private val SUFFIXES = listOf("종착", "지선")
    }
}
