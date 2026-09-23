package kr.kennysoft.kennymetro.web

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

/**
 * 첫 화면 요청을 index.html 로 넘긴다.
 *
 * <p>
 * 화면은 이미지 밖 볼륨에서 먼저 찾는다(application.yml 의 `spring.web.resources.static-locations`).
 * 첫 화면을 welcome page 매핑에 맡기지 않고 forward 로 넘기면, 리소스 핸들러가 요청 시점에 그 순서대로
 * 찾으므로 화면만 새로 올린 index.html 이 `/` 에도 바로 나온다. `scripts/smoke-test.sh` 가 native
 * 바이너리에서 확인한다.
 */
@Controller
class RootPageController {

    @GetMapping("/")
    fun index(): String = "forward:/index.html"
}
