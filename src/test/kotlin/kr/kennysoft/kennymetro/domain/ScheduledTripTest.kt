package kr.kennysoft.kennymetro.domain

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kr.kennysoft.kennymetro.TestLines

/**
 * 실시간 위치가 빠지는 구간을 달리는 광명셔틀을 시간표로 그 시각의 자리에 놓는다(DESIGN.md 2.2.3).
 */
class ScheduledTripTest : FreeSpec({

    "시발역에서는 떠나기 10분 전부터 서 있고 떠나면 다음 역까지 얼마나 갔는지 안다" {
        // given - 광명을 20:09 에 떠나 영등포로 가는 7032. 금천구청과 영등포의 시각은 예로 든 값이다.
        val trip = toYeongdeungpo()

        // when & then - 화면은 떠난 뒤 흐른 초를 다음 역까지 걸리는 초로 나눠 역 사이에 놓는다.
        trip.positionAt(seconds("19:58:59")).shouldBeNull()
        trip.positionAt(seconds("19:59:00")) shouldBe ScheduledPosition(trip, "광명", TrainStatus.STOPPED, null, null)
        trip.positionAt(seconds("20:10:00")) shouldBe ScheduledPosition(trip, "광명", TrainStatus.DEPARTED, 60, 270)
        trip.positionAt(seconds("20:13:45")) shouldBe ScheduledPosition(trip, "금천구청", TrainStatus.STOPPED, null, null)
        trip.positionAt(seconds("20:20:00")) shouldBe ScheduledPosition(trip, "금천구청", TrainStatus.DEPARTED, 360, 720)
    }

    "종착역에 닿은 운행은 그리지 않는다" {
        // given - 실시간 위치도 현재역과 종착역이 같은 열차는 운행을 마치는 중이라 뺀다.
        val trip = toYeongdeungpo()

        // when & then
        trip.positionAt(seconds("20:26:00")).shouldBeNull()
    }

    "뷰 밖에서 들어오는 운행은 첫 역에 닿을 때부터 그리고 뷰 밖으로 나가면 그리지 않는다" {
        // given - 첫 역에 도착 시각이 있고 마지막 역에 출발 시각이 있으면 뷰의 앞뒤로 더 달리는 운행이다.
        val trip = ScheduledTrip(
            trainNo = "7099",
            terminal = "서울역",
            isLast = false,
            stops = listOf(
                ScheduledStop("구로", seconds("10:00:00"), seconds("10:00:30")),
                ScheduledStop("신도림", seconds("10:02:30"), seconds("10:03:00")),
            ),
        )

        // when & then - 시발역이 아니라 떠나기 전에 미리 세우지 않는다.
        trip.positionAt(seconds("09:59:59")).shouldBeNull()
        trip.positionAt(seconds("10:00:00"))?.station shouldBe "구로"
        trip.positionAt(seconds("10:03:00")).shouldBeNull()
    }

    "광명으로 가는 셔틀이 시간표로 금천구청을 떠난 뒤 금천구청에 남은 실시간 기록은 멈춘 것이다" {
        // given - 2026-11-01 06:36:26 에 금천구청에서 받은 7003 의 기록이 광명에 닿을 때까지 그대로 남았다.
        // 금천구청과 광명 사이에서는 실시간 위치가 오지 않는다. 시각은 예로 든 값이다.
        val toGwangmyeong = ScheduledTrip(
            trainNo = "7003",
            terminal = "광명",
            isLast = false,
            stops = listOf(
                ScheduledStop("독산", seconds("06:34:00"), seconds("06:34:30")),
                ScheduledStop("금천구청", seconds("06:36:00"), seconds("06:36:30")),
                ScheduledStop("광명", seconds("06:41:00"), null),
            ),
        )
        val terminals = setOf("광명")

        // when & then - 아직 금천구청에 서 있거나, 늦게 다녀 독산에 있는 기록은 믿는다.
        toGwangmyeong.isStaleAt("금천구청", seconds("06:36:10"), terminals) shouldBe false
        toGwangmyeong.isStaleAt("금천구청", seconds("06:37:00"), terminals) shouldBe true
        toGwangmyeong.isStaleAt("독산", seconds("06:37:00"), terminals) shouldBe false
        // 광명을 떠나 영등포로 가는 셔틀은 금천구청부터 실시간 위치에 다시 온다.
        toYeongdeungpo().isStaleAt("금천구청", seconds("20:20:00"), terminals) shouldBe false
    }

    "광명셔틀 뷰에는 평소 운행으로, 같은 1호선의 다른 뷰에는 빠져나가는 역을 향하는 다른 계통으로 그린다" {
        // given - 영등포에서 광명으로 가는 7031 이 독산을 떠났고, 같은 운행이 신도림에 서 있다고 해 본다.
        val shuttle = TestLines.bySlug("line1-gwangmyeong")
        val gyeongbu = TestLines.bySlug("line1-gyeongbu")
        val gyeongin = TestLines.bySlug("line1-gyeongin")
        val trip = ScheduledTrip("7031", "광명", true, emptyList())
        val atDoksan = ScheduledPosition(trip, "독산", TrainStatus.DEPARTED, 30, 120)
        val atSindorim = ScheduledPosition(trip, "신도림", TrainStatus.STOPPED, null, null)

        // when
        val onShuttle = atDoksan.toTrain(shuttle)!!
        val onGyeongbu = atDoksan.toTrain(gyeongbu)!!

        // then - 시간표로 그린 열차라는 표시와 막차, 역 사이 자리를 그대로 옮긴다.
        onShuttle.direction shouldBe Direction.DOWN
        onShuttle.service shouldBe ServiceKind.NORMAL
        onShuttle.scheduled shouldBe true
        onShuttle.isLastTrain shouldBe true
        onShuttle.elapsedSeconds shouldBe 30
        onShuttle.segmentSeconds shouldBe 120
        // 경부 뷰에서는 금천구청에서, 경인 뷰에서는 구로에서 빠져나간다. 경인 뷰에는 독산이 없다.
        onGyeongbu.direction shouldBe Direction.DOWN
        onGyeongbu.service shouldBe ServiceKind.BRANCH
        atDoksan.toTrain(gyeongin).shouldBeNull()
        atSindorim.toTrain(gyeongin)?.service shouldBe ServiceKind.BRANCH
    }
})

/** 광명을 20:09 에 떠나 금천구청을 거쳐 영등포로 가는 셔틀. 다른 역은 줄였다. */
private fun toYeongdeungpo() = ScheduledTrip(
    trainNo = "7032",
    terminal = "영등포",
    isLast = false,
    stops = listOf(
        ScheduledStop("광명", null, seconds("20:09:00")),
        ScheduledStop("금천구청", seconds("20:13:30"), seconds("20:14:00")),
        ScheduledStop("영등포", seconds("20:26:00"), null),
    ),
)

private fun seconds(time: String): Int = time.split(":").map { it.toInt() }.let { (h, m, s) -> h * 3600 + m * 60 + s }
