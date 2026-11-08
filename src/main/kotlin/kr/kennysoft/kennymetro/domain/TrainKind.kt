package kr.kennysoft.kennymetro.domain

/**
 * 열차에 붙이는 종류(급행, ITX-청춘, 직통). 이름은 `lines.yml` 의 `train-kinds` 키다.
 *
 * @param color 화면이 그 종류의 표시와 칩 테두리에 쓰는 색. `#rrggbb` 다.
 * @param separateTicket 승차권을 따로 끊는 열차인지. 화면은 이 열차에 단축이나 연장 색을 칠하지 않는다
 *                       (DESIGN.md 1.6).
 * @param note 범례에 적는 설명. 승차권을 따로 끊는 열차에만 있다.
 */
data class TrainKind(val color: String, val separateTicket: Boolean, val note: String?)
