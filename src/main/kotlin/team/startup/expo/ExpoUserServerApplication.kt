package team.startup.expo

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ExpoUserServerApplication

fun main(args: Array<String>) {
    runApplication<ExpoUserServerApplication>(*args)
}
