package kr.kennysoft.kennymetro.domain

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kr.kennysoft.kennymetro.TestLines
import kr.kennysoft.kennymetro.seoul.TrainPositionDto

/**
 * 종착역이 그 뷰에서 어떤 운행인지. 평소 종착역, 단축, 연장, 다른 계통을 가른다.
 */
class ServiceKindTest : FreeSpec({

    "평소 종착역보다 먼저 끝나면 단축이고 더 가면 연장이다" {
        // given - 경의중앙선 동쪽은 용문에서 돌아가는 열차가 평소 운행이고, 팔당에서 돌아가는 열차는 열에 하나,
        // 지평까지 가는 열차는 그보다 드물다. 수인분당선은 왕십리에서 돌아가고 청량리까지 가는 열차는 드물다.
        // 경춘선은 상봉에서 돌아가는 열차가 열에 여덟이고 청량리까지 가는 열차가 열에 둘이다.
        val gyeongui = TestLines.bySlug("gyeongui")
        val suinbundang = TestLines.bySlug("suinbundang")
        val gyeongchun = TestLines.bySlug("gyeongchun")

        // when & then
        train(gyeongui, "청량리", "용문")?.service shouldBe ServiceKind.NORMAL
        train(gyeongui, "청량리", "팔당")?.service shouldBe ServiceKind.SHORT
        train(gyeongui, "청량리", "지평")?.service shouldBe ServiceKind.EXTENSION
        train(suinbundang, "선릉", "왕십리")?.service shouldBe ServiceKind.NORMAL
        train(suinbundang, "선릉", "청량리")?.service shouldBe ServiceKind.EXTENSION
        train(suinbundang, "선릉", "인천")?.service shouldBe ServiceKind.NORMAL
        train(gyeongchun, "망우", "상봉")?.service shouldBe ServiceKind.NORMAL
        train(gyeongchun, "망우", "청량리")?.service shouldBe ServiceKind.EXTENSION
        train(gyeongchun, "상봉", "마석")?.service shouldBe ServiceKind.SHORT
    }

    "중간에서 돌아가는 열차가 양 끝까지 가는 열차만큼 많으면 그 역도 평소 종착역이다" {
        // given - 7호선은 도봉산과 온수에서, 1호선은 청량리에서, 수인분당선은 죽전, 고색, 오이도에서, 경의중앙선은
        // 덕소에서 돌아가는 열차가 늘 다닌다. 수인분당선 오이도행은 인천과 오이도 사이만 오가는 열차다.
        // 그보다 드문 회차(7호선 수락산, 1호선 의정부와 구로)는 그 너머로 가는 사람이 타면 안 되므로 단축이다.
        val line7 = TestLines.bySlug("line7")
        val gyeongbu = TestLines.bySlug("line1-gyeongbu")
        val gyeongin = TestLines.bySlug("line1-gyeongin")
        val suinbundang = TestLines.bySlug("suinbundang")
        val gyeongui = TestLines.bySlug("gyeongui")

        // when & then
        train(line7, "고속터미널", "도봉산")?.service shouldBe ServiceKind.NORMAL
        train(line7, "고속터미널", "온수")?.service shouldBe ServiceKind.NORMAL
        train(line7, "고속터미널", "수락산")?.service shouldBe ServiceKind.SHORT
        train(gyeongbu, "금정", "청량리")?.service shouldBe ServiceKind.NORMAL
        train(gyeongin, "서울역", "청량리")?.service shouldBe ServiceKind.NORMAL
        train(gyeongbu, "금정", "구로")?.service shouldBe ServiceKind.SHORT
        train(gyeongin, "부평", "의정부")?.service shouldBe ServiceKind.SHORT
        train(suinbundang, "선릉", "죽전")?.service shouldBe ServiceKind.NORMAL
        train(suinbundang, "선릉", "고색")?.service shouldBe ServiceKind.NORMAL
        train(suinbundang, "송도", "오이도")?.service shouldBe ServiceKind.NORMAL
        train(gyeongui, "청량리", "덕소")?.service shouldBe ServiceKind.NORMAL
    }

    "형제 뷰의 종착역으로 가는 열차는 갈라지는 역을 향해 다른 계통으로 그린다" {
        // given - 신창행은 구로까지 경인 뷰와 같은 선로를 달린다. 버리면 종각에서 탈 수 있는 열차를 못 본다.
        val gyeongin = TestLines.bySlug("line1-gyeongin")

        // when
        val train = train(gyeongin, "종각", "신창")

        // then
        train?.direction shouldBe Direction.DOWN
        train?.service shouldBe ServiceKind.BRANCH
        train?.destination shouldBe "신창"
    }

    "갈라지는 역에 선 다른 계통 열차는 그리지 않는다" {
        // given - 구로에 선 신창행은 다음 역부터 경인 뷰에 없다. 어느 쪽으로 그려도 틀린다.
        val gyeongin = TestLines.bySlug("line1-gyeongin")

        // when & then
        train(gyeongin, "구로", "신창").shouldBeNull()
    }

    "어느 뷰에도 없는 종착역은 beyond 로 적은 역을 향한다" {
        // given - 서동탄은 경부 뷰의 병점에서 갈라진다. 경인 뷰에서는 그 병점을 다시 구로로 따라간다.
        val gyeongbu = TestLines.bySlug("line1-gyeongbu")
        val gyeongin = TestLines.bySlug("line1-gyeongin")

        // when
        val onGyeongbu = train(gyeongbu, "수원", "서동탄")
        val onGyeongin = train(gyeongin, "서울역", "서동탄")

        // then - 병점에서 신창 쪽이 아니라 옆으로 빠지므로 연장이 아니라 다른 계통이다.
        onGyeongbu?.direction shouldBe Direction.DOWN
        onGyeongbu?.service shouldBe ServiceKind.BRANCH
        onGyeongin?.direction shouldBe Direction.DOWN
        onGyeongin?.service shouldBe ServiceKind.BRANCH
    }

    "가장 바깥 종착역을 지나 목록 밖으로 가면 연장이다" {
        // given - 경춘선 용산행은 청량리를 지나 경의중앙선 선로로 더 간다. 광운대행은 망우에서 옆으로 빠진다.
        val line = TestLines.bySlug("gyeongchun")

        // when
        val toYongsan = train(line, "가평", "용산")
        val toGwangwoon = train(line, "가평", "광운대")

        // then
        toYongsan?.direction shouldBe Direction.UP
        toYongsan?.service shouldBe ServiceKind.EXTENSION
        toGwangwoon?.direction shouldBe Direction.UP
        toGwangwoon?.service shouldBe ServiceKind.BRANCH
    }

    "끊긴 구간의 끝에서 돌아가는 열차는 평소 운행이다" {
        // given - GTX-A 는 삼성역이 열리지 않아 서울역과 수서에서 돌아간다. 한 줄로 세워도 단축이 아니다.
        val line = TestLines.bySlug("gtx-a")

        // when
        val toSeoul = train(line, "연신내", "서울역")
        val toSuseo = train(line, "성남", "수서")

        // then
        toSeoul?.direction shouldBe Direction.DOWN
        toSeoul?.service shouldBe ServiceKind.NORMAL
        toSuseo?.direction shouldBe Direction.UP
        toSuseo?.service shouldBe ServiceKind.NORMAL
    }

    "모르는 종착역으로 가는 열차는 그리지 않는다" {
        // given - 어느 뷰에도 없고 beyond 에도 없으면 방향을 정할 근거가 없다. 서버가 로그로 남긴다.
        val line = TestLines.bySlug("line3")

        // when & then
        train(line, "교대", "없는역").shouldBeNull()
    }
})

private fun train(line: Line, current: String, terminal: String) = TrainPositionDto(
    subwayId = "1001",
    subwayNm = line.apiName,
    statnId = "1001000000",
    statnNm = current,
    trainNo = "1000",
    statnTid = "1001000001",
    statnTnm = terminal,
    updnLine = "1",
    trainSttus = "1",
    directAt = "0",
    lstcarAt = "0",
    recptnDt = "2026-09-23 10:50:00",
).toTrain(line)
