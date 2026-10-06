package team.startup.expo

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class ExpoUserServerApplication

fun main(args: Array<String>) {
    runApplication<ExpoUserServerApplication>(*args)
}
