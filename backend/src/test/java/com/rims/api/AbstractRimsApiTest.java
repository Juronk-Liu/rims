package com.rims.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 后端集成测试基类：启动完整 Spring 上下文（含拦截器、Service、Mapper、H2），
 * 用 MockMvc 打真实接口，验证业务规则而非仅仅 happy path。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractRimsApiTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    protected String login(String username, String password) throws Exception {
        Map<String, String> body = Map.of("username", username, "password", password);
        String resp = mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(resp);
        if (node.get("code").asInt() != 0) {
            throw new IllegalStateException("登录失败: " + node.get("message").asText());
        }
        return node.get("data").get("token").asText();
    }

    /** 携带 token 发 POST，返回完整响应体字符串。body 为 null 时只发空体。 */
    protected String post(String token, String url, Object body) throws Exception {
        var builder = MockMvcRequestBuilders.post(url).contentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            builder.content(objectMapper.writeValueAsString(body));
        }
        return mockMvc.perform(builder).andReturn().getResponse().getContentAsString();
    }

    protected String get(String token, String url) throws Exception {
        var builder = MockMvcRequestBuilders.get(url);
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(builder).andReturn().getResponse().getContentAsString();
    }

    protected int codeOf(String resp) throws Exception {
        return objectMapper.readTree(resp).get("code").asInt();
    }

    protected JsonNode dataOf(String resp) throws Exception {
        return objectMapper.readTree(resp).get("data");
    }

    /** 构造一个标准项目创建请求（leaderId 默认 1=张岩，EQUIPMENT 占满预算）。 */
    protected Map<String, Object> buildProjectRequest(long leaderId, BigDecimal budgetTotal) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("name", "测试项目-" + System.nanoTime());
        req.put("category", "PROVINCIAL");
        req.put("type", "APPLIED");
        req.put("leaderId", leaderId);
        req.put("source", "测试来源");
        req.put("budgetTotal", budgetTotal);
        req.put("startDate", LocalDate.now().toString());
        req.put("endDate", LocalDate.now().plusYears(1).toString());
        req.put("summary", "用于集成测试的科研项目申报摘要，内容充分描述研究背景、目标与关键技术路线，满足评审要求且字数充足。");
        req.put("expectedOutcome", "测试预期成果。");
        List<Map<String, Object>> budgets = new ArrayList<>();
        for (String cat : List.of("EQUIPMENT", "MATERIAL", "TRAVEL", "LABOR", "OTHER")) {
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("category", cat);
            line.put("amount", cat.equals("EQUIPMENT") ? budgetTotal : BigDecimal.ZERO);
            budgets.add(line);
        }
        req.put("budgets", budgets);
        return req;
    }

    /** 仅创建项目（DRAFT），返回 id。 */
    protected Long createProject() throws Exception {
        String token = login("admin", "123456");
        String resp = post(token, "/api/projects", buildProjectRequest(1L, new BigDecimal("1000")));
        if (codeOf(resp) != 0) {
            throw new IllegalStateException("创建项目失败: " + objectMapper.readTree(resp).get("message").asText());
        }
        return dataOf(resp).get("id").asLong();
    }

    /** 创建并提交（SUBMITTED），返回 id。 */
    protected Long createSubmittedProject() throws Exception {
        String token = login("admin", "123456");
        long id = createProject();
        ensureOk(token, "/api/projects/" + id + "/submit", null);
        return id;
    }

    /** 用 admin 账号造一个已立项（IN_PROGRESS）的项目，返回项目 id。 */
    protected Long createActivatedProject() throws Exception {
        String token = login("admin", "123456");
        long id = createProject();
        ensureOk(token, "/api/projects/" + id + "/submit", null);
        ensureOk(token, "/api/projects/" + id + "/review", Map.of("action", "APPROVE"));
        ensureOk(token, "/api/projects/" + id + "/review", Map.of("action", "APPROVE"));
        return id;
    }

    protected void ensureOk(String token, String url, Object body) throws Exception {
        String resp = post(token, url, body);
        if (codeOf(resp) != 0) {
            throw new IllegalStateException("期望成功却失败 [" + url + "]: "
                    + objectMapper.readTree(resp).get("message").asText());
        }
    }
}
