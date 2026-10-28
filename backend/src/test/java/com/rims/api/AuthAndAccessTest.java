package com.rims.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 认证与访问控制：未登录拦截、错误密码拒绝、正确登录拿 token。 */
class AuthAndAccessTest extends AbstractRimsApiTest {

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        String resp = get(null, "/api/projects");
        assertEquals(401, codeOf(resp));
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        Map<String, String> body = Map.of("username", "admin", "password", "wrong-password");
        String resp = mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andReturn().getResponse().getContentAsString();
        assertEquals(401, codeOf(resp));
    }

    @Test
    void correctLoginReturnsToken() throws Exception {
        String token = login("admin", "123456");
        assertNotNull(token);
        assertTrue(token.length() > 10, "token 长度异常");
    }
}
