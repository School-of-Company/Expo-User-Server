package team.startup.expo.docs

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.startup.expo.support.IntegrationTestSupport

/** `SWAGGER_ENABLED`를 켜지 않은 기본 상태에서는 문서 경로가 열리지 않는다. */
class SwaggerDisabledTests : IntegrationTestSupport() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `기본 설정에서는 API 문서와 문서 화면이 열리지 않고 인증이 필요하다`() {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/v3/api-docs/public")).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/swagger-ui.html")).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isUnauthorized)
    }
}
