package kr.kennysoft.kennymetro

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class KennyMetroApplication

fun main(args: Array<String>) {
    runApplication<KennyMetroApplication>(*args)
}
