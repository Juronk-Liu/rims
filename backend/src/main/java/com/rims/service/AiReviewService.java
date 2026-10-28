package com.rims.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rims.common.Dicts;
import com.rims.dto.Dtos;
import com.rims.entity.BudgetItem;
import com.rims.entity.Project;
import com.rims.mapper.BudgetItemMapper;
import com.rims.mapper.ProjectMapper;
import com.rims.security.AuthContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AI 申报预审。
 *
 * 设计取舍：默认走确定性规则引擎（离线可跑、结果可解释、评审人不需 API Key 就能体验）；
 * 配置 rims.ai.enabled=true + api-key 后，额外调用 OpenAI 兼容接口生成"评审意见正文"，
 * 评分仍由规则给出，避免大模型评分不可复现。响应里用 engine 字段如实标注来源。
 */
@Service
public class AiReviewService {

    private static final Logger log = LoggerFactory.getLogger(AiReviewService.class);

    private final ProjectMapper projectMapper;
    private final BudgetItemMapper budgetMapper;
    private final ProjectService projectService;
    private final ObjectMapper objectMapper;
    private final boolean llmEnabled;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    public AiReviewService(ProjectMapper projectMapper, BudgetItemMapper budgetMapper, ProjectService projectService,
                           ObjectMapper objectMapper,
                           @Value("${rims.ai.enabled:false}") boolean llmEnabled,
                           @Value("${rims.ai.base-url:}") String baseUrl,
                           @Value("${rims.ai.api-key:}") String apiKey,
                           @Value("${rims.ai.model:}") String model) {
        this.projectMapper = projectMapper;
        this.budgetMapper = budgetMapper;
        this.projectService = projectService;
        this.objectMapper = objectMapper;
        this.llmEnabled = llmEnabled;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
    }

    public Dtos.AiReviewResponse review(Long projectId) {
        Project project = projectService.requireView(projectId);
        List<Dtos.AiFinding> findings = new ArrayList<>();
        int score = 100;

        String summary = project.getSummary() == null ? "" : project.getSummary().trim();
        if (summary.length() < 30) {
            score -= 25;
            findings.add(new Dtos.AiFinding("HIGH", "项目摘要过于简略（" + summary.length() + " 字）",
                    "建议补充研究背景、技术路线与关键难点，300 字左右更有说服力"));
        } else if (summary.length() < 150) {
            score -= 10;
            findings.add(new Dtos.AiFinding("MEDIUM", "摘要信息量偏少（" + summary.length() + " 字）",
                    "可补充量化指标，例如检测精度、响应时延等目标值"));
        }

        if (project.getExpectedOutcome() == null || project.getExpectedOutcome().isBlank()) {
            score -= 15;
            findings.add(new Dtos.AiFinding("HIGH", "未填写预期成果", "验收环节将按预期成果核对，建议至少列出论文/专利/样机等可核查指标"));
        }

        long months = project.getStartDate() == null || project.getEndDate() == null ? 0
                : ChronoUnit.MONTHS.between(project.getStartDate(), project.getEndDate());
        if (months > 48) {
            score -= 8;
            findings.add(new Dtos.AiFinding("MEDIUM", "项目周期 " + months + " 个月，跨度偏长",
                    "建议拆分为阶段目标，并在里程碑中体现中期检查点"));
        } else if (months > 0 && months < 6) {
            score -= 10;
            findings.add(new Dtos.AiFinding("MEDIUM", "项目周期仅 " + months + " 个月",
                    "周期过短难以支撑成果产出，请确认研究内容与周期匹配"));
        }
        if (project.getStartDate() != null && project.getStartDate().isBefore(LocalDate.now().minusYears(1))) {
            score -= 5;
            findings.add(new Dtos.AiFinding("LOW", "开始日期早于一年前", "追溯立项需说明原因，否则按当前年度重新申报"));
        }

        List<BudgetItem> budgets = budgetMapper.selectList(new LambdaQueryWrapper<BudgetItem>()
                .eq(BudgetItem::getProjectId, projectId));
        BigDecimal total = project.getBudgetTotal() == null ? BigDecimal.ZERO : project.getBudgetTotal();
        if (total.signum() <= 0) {
            score -= 30;
            findings.add(new Dtos.AiFinding("HIGH", "预算总额未填写", "请先完成预算编制，经费评审将依据科目结构"));
        } else {
            Map<String, BigDecimal> byCategory = new java.util.HashMap<>();
            budgets.forEach(b -> byCategory.merge(b.getCategory(),
                    b.getAmount() == null ? BigDecimal.ZERO : b.getAmount(), BigDecimal::add));
            BigDecimal labor = byCategory.getOrDefault(Dicts.ExpenseCategory.LABOR.name(), BigDecimal.ZERO);
            BigDecimal equipment = byCategory.getOrDefault(Dicts.ExpenseCategory.EQUIPMENT.name(), BigDecimal.ZERO);
            BigDecimal laborRate = labor.divide(total, 4, java.math.RoundingMode.HALF_UP);
            BigDecimal equipmentRate = equipment.divide(total, 4, java.math.RoundingMode.HALF_UP);
            if (laborRate.doubleValue() > 0.6) {
                score -= 8;
                findings.add(new Dtos.AiFinding("MEDIUM", "劳务费占比 " + Math.round(laborRate.doubleValue() * 100) + "%，偏高",
                        "多数科研项目劳务费上限为 30%-50%，请核对申报指南"));
            }
            if (equipmentRate.doubleValue() > 0.7) {
                score -= 5;
                findings.add(new Dtos.AiFinding("LOW", "设备费占比 " + Math.round(equipmentRate.doubleValue() * 100) + "%，偏高",
                        "建议说明设备共享情况，避免重复购置"));
            }
            if (budgets.isEmpty()) {
                score -= 10;
                findings.add(new Dtos.AiFinding("MEDIUM", "尚未编制预算科目", "预算需拆分到科目，便于后续支出管控"));
            }
        }

        int memberCount = projectService.membersOf(projectId).size();
        if (memberCount < 2) {
            score -= 5;
            findings.add(new Dtos.AiFinding("LOW", "项目组仅 " + memberCount + " 人",
                    "建议补充骨干成员，评审通常关注团队结构合理性"));
        }

        if (project.getName() != null && project.getName().length() < 8) {
            score -= 3;
            findings.add(new Dtos.AiFinding("LOW", "项目名称过短", "名称建议体现研究对象 + 方法 + 目标"));
        }

        if (findings.isEmpty()) {
            findings.add(new Dtos.AiFinding("INFO", "未发现明显的形式风险", "可进入审批流程，专家评审仍会关注创新性与可行性"));
        }

        score = Math.max(0, Math.min(100, score));
        String engine = "RULE";
        String narrative = buildSummary(score, findings);
        if (llmEnabled && apiKey != null && !apiKey.isBlank()) {
            String llmText = callLlm(project, budgets, score);
            if (llmText != null) {
                narrative = llmText;
                engine = "RULE+LLM";
            } else {
                engine = "RULE(LLM调用失败，已降级)";
            }
        }

        var user = AuthContext.get();
        boolean canSave = user.isAdmin() || java.util.Objects.equals(project.getLeaderId(), user.id())
                || java.util.Objects.equals(project.getCreatedBy(), user.id());
        if (canSave) {
            project.setAiScore(score);
            project.setAiReport(narrative);
            project.setUpdatedAt(java.time.LocalDateTime.now());
            projectMapper.updateById(project);
        }
        return new Dtos.AiReviewResponse(score, engine, findings, narrative);
    }

    private String buildSummary(int score, List<Dtos.AiFinding> findings) {
        long high = findings.stream().filter(f -> "HIGH".equals(f.level())).count();
        long medium = findings.stream().filter(f -> "MEDIUM".equals(f.level())).count();
        if (high == 0 && medium == 0) {
            return "形式审查得分 " + score + " 分，未发现阻塞性问题，建议按流程提交审批。";
        }
        if (high == 0) {
            return "形式审查得分 " + score + " 分，无高风险项，有 " + medium + " 项中风险。建议补充完善后再提交，可减少审批环节的来回沟通。";
        }
        return "形式审查得分 " + score + " 分：高风险 " + high + " 项、中风险 " + medium + " 项。建议先补齐高风险项再提交，避免在部门/科研处环节被退回。";
    }

    private String callLlm(Project project, List<BudgetItem> budgets, int score) {
        try {
            String prompt = """
                    你是科研院所的评审专家。请基于以下项目申报信息，给出 200 字以内的评审意见，
                    指出最关键的 1-2 个问题并给出可执行建议，语气客观、不要客套。
                    形式审查得分：%d
                    项目名称：%s
                    类别/类型：%s / %s
                    起止：%s ~ %s
                    预算：%s 元，科目：%s
                    摘要：%s
                    预期成果：%s
                    """.formatted(score, project.getName(),
                    ProjectService.label(Dicts.ProjectCategory.class, project.getCategory()),
                    ProjectService.label(Dicts.ProjectType.class, project.getType()),
                    project.getStartDate(), project.getEndDate(), project.getBudgetTotal(),
                    budgets.stream().map(b -> ProjectService.label(Dicts.ExpenseCategory.class, b.getCategory())
                            + ":" + b.getAmount()).toList(),
                    project.getSummary(), project.getExpectedOutcome());

            String body = objectMapper.writeValueAsString(Map.of(
                    "model", model,
                    "temperature", 0.3,
                    "messages", List.of(Map.of("role", "user", "content", prompt))
            ));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl.replaceAll("/$", "") + "/chat/completions"))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("LLM 预审返回 {}: {}", response.statusCode(), response.body());
                return null;
            }
            JsonNode node = objectMapper.readTree(response.body());
            return node.path("choices").path(0).path("message").path("content").asText(null);
        } catch (Exception e) {
            log.warn("LLM 预审调用失败，降级为规则引擎: {}", e.getMessage());
            return null;
        }
    }
}
