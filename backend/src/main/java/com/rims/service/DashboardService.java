package com.rims.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rims.common.Dicts;
import com.rims.entity.BudgetItem;
import com.rims.entity.Achievement;
import com.rims.entity.Expense;
import com.rims.entity.Milestone;
import com.rims.entity.Project;
import com.rims.mapper.AchievementMapper;
import com.rims.mapper.BudgetItemMapper;
import com.rims.mapper.ExpenseMapper;
import com.rims.mapper.MilestoneMapper;
import com.rims.mapper.ProjectMapper;
import com.rims.security.AuthContext;
import com.rims.security.LoginUser;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 驾驶舱：按当前用户的数据权限聚合，全部为内存计算（单院所示例数据规模下足够，见 DESIGN.md）。
 */
@Service
public class DashboardService {

    private final ProjectMapper projectMapper;
    private final ExpenseMapper expenseMapper;
    private final MilestoneMapper milestoneMapper;
    private final BudgetItemMapper budgetMapper;
    private final AchievementMapper achievementMapper;
    private final ProjectService projectService;

    public DashboardService(ProjectMapper projectMapper, ExpenseMapper expenseMapper,
                            MilestoneMapper milestoneMapper, BudgetItemMapper budgetMapper,
                            AchievementMapper achievementMapper, ProjectService projectService) {
        this.projectMapper = projectMapper;
        this.expenseMapper = expenseMapper;
        this.milestoneMapper = milestoneMapper;
        this.budgetMapper = budgetMapper;
        this.achievementMapper = achievementMapper;
        this.projectService = projectService;
    }

    public Map<String, Object> overview() {
        LoginUser user = AuthContext.get();
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<>();
        projectService.applyScope(wrapper, user, false);
        List<Project> projects = projectMapper.selectList(wrapper);
        List<Long> ids = projects.stream().map(Project::getId).toList();

        Map<String, Long> byStatus = projects.stream()
                .collect(Collectors.groupingBy(Project::getStatus, LinkedHashMap::new, Collectors.counting()));
        long total = projects.size();
        long inProgress = byStatus.getOrDefault(Dicts.ProjectStatus.IN_PROGRESS.name(), 0L);
        long pendingApproval = byStatus.getOrDefault(Dicts.ProjectStatus.SUBMITTED.name(), 0L)
                + byStatus.getOrDefault(Dicts.ProjectStatus.DEPT_APPROVED.name(), 0L);
        long pendingAcceptance = byStatus.getOrDefault(Dicts.ProjectStatus.PENDING_ACCEPTANCE.name(), 0L);
        long completed = byStatus.getOrDefault(Dicts.ProjectStatus.COMPLETED.name(), 0L);

        List<Expense> expenses = ids.isEmpty() ? List.of()
                : expenseMapper.selectList(new LambdaQueryWrapper<Expense>().in(Expense::getProjectId, ids));
        BigDecimal approvedExpense = expenses.stream()
                .filter(e -> Dicts.ExpenseStatus.APPROVED.name().equals(e.getStatus()))
                .map(Expense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pendingExpense = expenses.stream()
                .filter(e -> Dicts.ExpenseStatus.PENDING.name().equals(e.getStatus()))
                .map(Expense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal budgetTotal = projects.stream()
                .filter(p -> !Dicts.ProjectStatus.DRAFT.name().equals(p.getStatus())
                        && !Dicts.ProjectStatus.REJECTED.name().equals(p.getStatus())
                        && !Dicts.ProjectStatus.TERMINATED.name().equals(p.getStatus()))
                .map(p -> p.getBudgetTotal() == null ? BigDecimal.ZERO : p.getBudgetTotal())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        double execRate = budgetTotal.signum() == 0 ? 0
                : approvedExpense.divide(budgetTotal, 4, RoundingMode.HALF_UP).doubleValue() * 100;

        List<Milestone> milestones = ids.isEmpty() ? List.of()
                : milestoneMapper.selectList(new LambdaQueryWrapper<Milestone>().in(Milestone::getProjectId, ids));
        List<BudgetItem> budgets = ids.isEmpty() ? List.of()
                : budgetMapper.selectList(new LambdaQueryWrapper<BudgetItem>().in(BudgetItem::getProjectId, ids));

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("projectTotal", total);
        stats.put("draftCount", byStatus.getOrDefault(Dicts.ProjectStatus.DRAFT.name(), 0L));
        stats.put("inProgress", inProgress);
        stats.put("pendingApproval", pendingApproval);
        stats.put("pendingAcceptance", pendingAcceptance);
        stats.put("completed", completed);
        stats.put("budgetTotal", budgetTotal);
        stats.put("expenseApproved", approvedExpense);
        stats.put("expensePending", pendingExpense);
        stats.put("execRate", Math.round(execRate * 10) / 10.0);
        stats.put("achievementCount", countAchievements(ids));
        stats.put("todoCount", todoCount(user, projects, expenses));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stats", stats);
        result.put("statusDist", projects.stream()
                .collect(Collectors.groupingBy(Project::getStatus, Collectors.counting()))
                .entrySet().stream()
                .map(e -> Map.of("name", ProjectService.label(Dicts.ProjectStatus.class, e.getKey()),
                        "status", e.getKey(), "value", e.getValue()))
                .toList());
        result.put("categoryStats", categoryStats(projects, budgets, expenses));
        result.put("monthlyExpense", monthlyExpense(expenses));
        result.put("alerts", alerts(projects, milestones, budgets, expenses));
        result.put("myRole", user.role());
        return result;
    }

    private long countAchievements(List<Long> ids) {
        if (ids.isEmpty()) {
            return 0;
        }
        return achievementMapper.selectCount(new LambdaQueryWrapper<Achievement>()
                .in(Achievement::getProjectId, ids));
    }

    private long todoCount(LoginUser user, List<Project> projects, List<Expense> expenses) {
        long pendingExpenses = expenses.stream()
                .filter(e -> Dicts.ExpenseStatus.PENDING.name().equals(e.getStatus())).count();
        if (user.isFinance()) {
            return pendingExpenses;
        }
        long submitted = projects.stream()
                .filter(p -> Dicts.ProjectStatus.SUBMITTED.name().equals(p.getStatus())).count();
        long toApprove = projects.stream()
                .filter(p -> Dicts.ProjectStatus.DEPT_APPROVED.name().equals(p.getStatus())).count();
        long toAccept = projects.stream()
                .filter(p -> Dicts.ProjectStatus.PENDING_ACCEPTANCE.name().equals(p.getStatus())).count();
        if (user.isAdmin()) {
            return submitted + toApprove + toAccept + pendingExpenses;
        }
        if (user.isDeptAdmin()) {
            return submitted;
        }
        if (user.isResearcher()) {
            return projects.stream().filter(p -> Dicts.ProjectStatus.DRAFT.name().equals(p.getStatus())
                    || Dicts.ProjectStatus.REJECTED.name().equals(p.getStatus())).count();
        }
        return toApprove + toAccept;
    }

    private List<Map<String, Object>> categoryStats(List<Project> projects, List<BudgetItem> budgets,
                                                    List<Expense> expenses) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Dicts.ProjectCategory category : Dicts.ProjectCategory.values()) {
            Set<Long> projectIds = projects.stream()
                    .filter(p -> category.name().equals(p.getCategory()))
                    .map(Project::getId).collect(Collectors.toSet());
            BigDecimal budget = budgets.stream().filter(b -> projectIds.contains(b.getProjectId()))
                    .map(BudgetItem::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal expense = expenses.stream()
                    .filter(e -> projectIds.contains(e.getProjectId())
                            && Dicts.ExpenseStatus.APPROVED.name().equals(e.getStatus()))
                    .map(Expense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (budget.signum() == 0 && expense.signum() == 0) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("category", category.name());
            item.put("name", category.getLabel());
            item.put("budget", budget);
            item.put("expense", expense);
            list.add(item);
        }
        return list;
    }

    /**
     * 近 6 个月已审批通过的支出，按审批月份聚合，用于观察经费消耗节奏。
     */
    private List<Map<String, Object>> monthlyExpense(List<Expense> expenses) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            YearMonth month = YearMonth.now().minusMonths(i);
            BigDecimal amount = expenses.stream()
                    .filter(e -> Dicts.ExpenseStatus.APPROVED.name().equals(e.getStatus())
                            && e.getApprovedAt() != null
                            && YearMonth.from(e.getApprovedAt()).equals(month))
                    .map(Expense::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            list.add(Map.of("month", month.getMonthValue() + "月", "amount", amount));
        }
        return list;
    }

    private List<Map<String, Object>> alerts(List<Project> projects, List<Milestone> milestones,
                                             List<BudgetItem> budgets, List<Expense> expenses) {
        List<Map<String, Object>> alerts = new ArrayList<>();
        LocalDate today = LocalDate.now();
        Map<Long, Project> projectMap = projects.stream()
                .collect(Collectors.toMap(Project::getId, p -> p, (a, b) -> a));

        for (Project project : projects) {
            if (project.getEndDate() == null || !projectService.isActive(project.getStatus())) {
                continue;
            }
            long daysLeft = ChronoUnit.DAYS.between(today, project.getEndDate());
            if (daysLeft < 0) {
                alerts.add(alert("HIGH", "项目逾期", "已逾期 " + (-daysLeft) + " 天未结题",
                        project.getId(), project.getName()));
            } else if (daysLeft <= 30) {
                alerts.add(alert("MEDIUM", "临近结题", "距结束仅 " + daysLeft + " 天",
                        project.getId(), project.getName()));
            }
        }

        for (Milestone milestone : milestones) {
            Project project = projectMap.get(milestone.getProjectId());
            if (project == null || !Dicts.MilestoneStatus.PENDING.name().equals(milestone.getStatus())
                    || milestone.getPlanDate() == null || !milestone.getPlanDate().isBefore(today)) {
                continue;
            }
            long days = ChronoUnit.DAYS.between(milestone.getPlanDate(), today);
            if (days <= 0 || !projectService.isActive(project.getStatus())) {
                continue;
            }
            alerts.add(alert("MEDIUM", "里程碑逾期", "「" + milestone.getName() + "」计划于 "
                    + milestone.getPlanDate() + " 完成，已逾期 " + days + " 天", project.getId(), project.getName()));
        }

        Map<Long, BigDecimal> budgetByProject = budgets.stream()
                .collect(Collectors.groupingBy(BudgetItem::getProjectId,
                        Collectors.reducing(BigDecimal.ZERO, BudgetItem::getAmount, BigDecimal::add)));
        Map<Long, BigDecimal> expenseByProject = expenses.stream()
                .filter(e -> Dicts.ExpenseStatus.APPROVED.name().equals(e.getStatus()))
                .collect(Collectors.groupingBy(Expense::getProjectId,
                        Collectors.reducing(BigDecimal.ZERO, Expense::getAmount, BigDecimal::add)));
        for (Project project : projects) {
            if (!projectService.isActive(project.getStatus())) {
                continue;
            }
            BigDecimal budget = budgetByProject.getOrDefault(project.getId(), BigDecimal.ZERO);
            BigDecimal used = expenseByProject.getOrDefault(project.getId(), BigDecimal.ZERO);
            if (budget.signum() <= 0) {
                continue;
            }
            double rate = used.divide(budget, 4, RoundingMode.HALF_UP).doubleValue() * 100;
            if (rate >= 90) {
                alerts.add(alert("HIGH", "经费接近上限", "预算执行率 " + Math.round(rate) + "%，请注意后续支出",
                        project.getId(), project.getName()));
            } else if (rate >= 75) {
                alerts.add(alert("MEDIUM", "经费消耗较快", "预算执行率 " + Math.round(rate) + "%",
                        project.getId(), project.getName()));
            }
        }

        Map<String, Integer> weight = Map.of("HIGH", 0, "MEDIUM", 1, "LOW", 2);
        alerts.sort(Comparator.comparingInt(a -> weight.getOrDefault((String) a.get("level"), 3)));
        return alerts.stream().limit(12).toList();
    }

    private Map<String, Object> alert(String level, String type, String desc, Long projectId, String projectName) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("level", level);
        map.put("type", type);
        map.put("desc", desc);
        map.put("projectId", projectId);
        map.put("projectName", projectName);
        return map;
    }
}
