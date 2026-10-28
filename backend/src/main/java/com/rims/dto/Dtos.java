package com.rims.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 按业务场景聚合的请求/响应对象，避免直接暴露实体。
 */
public final class Dtos {

    private Dtos() {
    }

    // ---------- 认证 ----------
    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record UserVO(Long id, String username, String realName, String role, String roleLabel,
                         String dept, String title, String email) {
    }

    public record LoginResponse(String token, UserVO user) {
    }

    // ---------- 项目 ----------
    public record MemberItem(Long userId, String userName, String memberRole, Integer workload) {
    }

    public record BudgetLine(String category, BigDecimal amount) {
    }

    public record ProjectSaveRequest(
            @NotBlank(message = "项目名称不能为空") String name,
            @NotBlank(message = "请选择项目类别") String category,
            @NotBlank(message = "请选择项目类型") String type,
            @NotNull(message = "请选择项目负责人") Long leaderId,
            String source,
            @NotNull(message = "预算总额不能为空") @Positive(message = "预算总额必须大于 0") BigDecimal budgetTotal,
            @NotNull(message = "请选择开始日期") LocalDate startDate,
            @NotNull(message = "请选择结束日期") LocalDate endDate,
            String summary,
            String expectedOutcome,
            List<MemberItem> members,
            List<BudgetLine> budgets
    ) {
    }

    public record ProjectListItem(
            Long id, String code, String name, String category, String categoryLabel,
            String type, String typeLabel, Long leaderId, String leaderName, String dept,
            BigDecimal budgetTotal, BigDecimal approvedExpense, Integer progressPercent,
            LocalDate startDate, LocalDate endDate, String status, String statusLabel,
            Long daysLeft, boolean overdue
    ) {
    }

    public record ProjectStats(
            BigDecimal budgetTotal, BigDecimal approvedExpense, BigDecimal pendingExpense,
            double executionRate, Integer progressPercent,
            int milestoneDone, int milestoneTotal, int achievementCount,
            long daysLeft, boolean overdue
    ) {
    }

    public record ProjectDetailVO(
            com.rims.entity.Project project,
            List<com.rims.entity.ProjectMember> members,
            List<com.rims.entity.Milestone> milestones,
            List<com.rims.entity.ProgressReport> progressReports,
            List<com.rims.entity.BudgetItem> budgets,
            List<com.rims.entity.Expense> expenses,
            List<com.rims.entity.Achievement> achievements,
            List<com.rims.entity.ApprovalRecord> approvals,
            ProjectStats stats
    ) {
    }

    public record ReviewRequest(@NotBlank String action, String opinion) {
    }

    public record AcceptanceRequest(boolean passed, String opinion) {
    }

    public record MilestoneRequest(@NotBlank(message = "节点名称不能为空") String name,
                                   LocalDate planDate, Integer sortNo) {
    }

    public record ProgressRequest(Integer progressPercent, String content, String issues,
                                  String nextPlan, LocalDate reportDate) {
    }

    public record AchievementRequest(@NotBlank(message = "成果类型不能为空") String type,
                                     @NotBlank(message = "成果名称不能为空") String title,
                                     String authors, LocalDate achievedDate, String levelName,
                                     String remark) {
    }

    // ---------- 经费 ----------
    public record ExpenseRequest(@NotBlank(message = "请选择费用科目") String category,
                                 @NotNull(message = "金额不能为空") @Positive(message = "金额必须大于 0") BigDecimal amount,
                                 String purpose, LocalDate expenseDate) {
    }

    public record ExpenseApproveRequest(boolean approved, String opinion) {
    }

    public record BudgetSaveRequest(@NotNull List<BudgetLine> items) {
    }

}
