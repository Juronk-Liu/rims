package com.rims.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rims.common.BizException;
import com.rims.common.Dicts;
import com.rims.common.PageResult;
import com.rims.dto.Dtos;
import com.rims.entity.*;
import com.rims.mapper.*;
import com.rims.security.AuthContext;
import com.rims.security.LoginUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 项目主档：查询（含数据权限）、申报草稿维护、详情聚合、进度与经费汇总。
 * 状态流转不在这里，放在 WorkflowService，保证"改数据"和"改状态"边界清晰。
 */
@Service
public class ProjectService {

    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper memberMapper;
    private final MilestoneMapper milestoneMapper;
    private final ProgressReportMapper progressMapper;
    private final BudgetItemMapper budgetMapper;
    private final ExpenseMapper expenseMapper;
    private final AchievementMapper achievementMapper;
    private final ApprovalRecordMapper approvalMapper;
    private final SysUserMapper userMapper;
    private final AuditService auditService;

    public ProjectService(ProjectMapper projectMapper, ProjectMemberMapper memberMapper,
                          MilestoneMapper milestoneMapper, ProgressReportMapper progressMapper,
                          BudgetItemMapper budgetMapper, ExpenseMapper expenseMapper,
                          AchievementMapper achievementMapper, ApprovalRecordMapper approvalMapper,
                          SysUserMapper userMapper, AuditService auditService) {
        this.projectMapper = projectMapper;
        this.memberMapper = memberMapper;
        this.milestoneMapper = milestoneMapper;
        this.progressMapper = progressMapper;
        this.budgetMapper = budgetMapper;
        this.expenseMapper = expenseMapper;
        this.achievementMapper = achievementMapper;
        this.approvalMapper = approvalMapper;
        this.userMapper = userMapper;
        this.auditService = auditService;
    }

    // ================= 查询 =================

    public PageResult<Dtos.ProjectListItem> list(int page, int size, String keyword, String status,
                                                 String category, boolean onlyMine) {
        LoginUser user = AuthContext.get();
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<>();
        applyScope(wrapper, user, onlyMine);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Project::getName, keyword).or().like(Project::getCode, keyword)
                    .or().like(Project::getLeaderName, keyword));
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(Project::getStatus, status);
        }
        if (StringUtils.hasText(category)) {
            wrapper.eq(Project::getCategory, category);
        }
        wrapper.orderByDesc(Project::getUpdatedAt).orderByDesc(Project::getId);

        Page<Project> result = projectMapper.selectPage(new Page<>(page, size), wrapper);
        List<Long> ids = result.getRecords().stream().map(Project::getId).toList();

        Map<Long, BigDecimal> approvedExpense = ids.isEmpty() ? Map.of() : sumExpenseByProject(ids, Dicts.ExpenseStatus.APPROVED.name());
        Map<Long, Integer> progressMap = ids.isEmpty() ? Map.of() : latestProgress(ids);

        List<Dtos.ProjectListItem> items = result.getRecords().stream()
                .map(p -> toListItem(p, approvedExpense.getOrDefault(p.getId(), BigDecimal.ZERO),
                        progressMap.getOrDefault(p.getId(), defaultProgress(p))))
                .toList();
        return new PageResult<>(result.getTotal(), items);
    }

    public Dtos.ProjectDetailVO detail(Long id) {
        Project project = requireView(id);
        List<ProjectMember> members = memberMapper.selectList(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, id).orderByAsc(ProjectMember::getId));
        List<Milestone> milestones = milestoneMapper.selectList(new LambdaQueryWrapper<Milestone>()
                .eq(Milestone::getProjectId, id).orderByAsc(Milestone::getSortNo).orderByAsc(Milestone::getId));
        List<ProgressReport> progress = progressMapper.selectList(new LambdaQueryWrapper<ProgressReport>()
                .eq(ProgressReport::getProjectId, id).orderByDesc(ProgressReport::getReportDate)
                .orderByDesc(ProgressReport::getId));
        List<BudgetItem> budgets = budgetMapper.selectList(new LambdaQueryWrapper<BudgetItem>()
                .eq(BudgetItem::getProjectId, id).orderByAsc(BudgetItem::getId));
        List<Expense> expenses = expenseMapper.selectList(new LambdaQueryWrapper<Expense>()
                .eq(Expense::getProjectId, id).orderByDesc(Expense::getId));
        List<Achievement> achievements = achievementMapper.selectList(new LambdaQueryWrapper<Achievement>()
                .eq(Achievement::getProjectId, id).orderByDesc(Achievement::getAchievedDate)
                .orderByDesc(Achievement::getId));
        List<ApprovalRecord> approvals = approvalMapper.selectList(new LambdaQueryWrapper<ApprovalRecord>()
                .eq(ApprovalRecord::getBizType, "PROJECT").eq(ApprovalRecord::getBizId, id)
                .orderByDesc(ApprovalRecord::getId));

        return new Dtos.ProjectDetailVO(project, members, milestones, progress, budgets, expenses,
                achievements, approvals, buildStats(project, milestones, progress, expenses, achievements));
    }

    public List<Milestone> milestones(Long projectId) {
        requireView(projectId);
        return milestoneMapper.selectList(new LambdaQueryWrapper<Milestone>()
                .eq(Milestone::getProjectId, projectId).orderByAsc(Milestone::getSortNo).orderByAsc(Milestone::getId));
    }

    // ================= 申报草稿 =================

    @Transactional
    public Project create(Dtos.ProjectSaveRequest req) {
        LoginUser user = AuthContext.get();
        if ("LEADER".equals(user.role()) || "FINANCE".equals(user.role())) {
            throw BizException.forbidden("当前角色不能申报项目");
        }
        validateRequest(req);

        SysUser leader = userMapper.selectById(req.leaderId());
        if (leader == null) {
            throw new BizException("项目负责人不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        Project project = new Project();
        project.setCode(nextCode());
        project.setName(req.name().trim());
        project.setCategory(validEnum(Dicts.ProjectCategory.class, req.category(), "项目类别"));
        project.setType(validEnum(Dicts.ProjectType.class, req.type(), "项目类型"));
        project.setLeaderId(leader.getId());
        project.setLeaderName(leader.getRealName());
        project.setDept(leader.getDept());
        project.setSource(req.source());
        project.setBudgetTotal(req.budgetTotal());
        project.setStartDate(req.startDate());
        project.setEndDate(req.endDate());
        project.setSummary(req.summary());
        project.setExpectedOutcome(req.expectedOutcome());
        project.setStatus(Dicts.ProjectStatus.DRAFT.name());
        project.setCreatedBy(user.id());
        project.setCreatedAt(now);
        project.setUpdatedAt(now);
        project.setDeleted(0);
        project.setVersion(0L);
        projectMapper.insert(project);

        replaceMembers(project, req.members());
        replaceBudgets(project, req.budgets());
        auditService.record("PROJECT_CREATE", "项目 " + project.getCode(), "创建申报草稿：" + project.getName());
        return project;
    }

    @Transactional
    public Project update(Long id, Dtos.ProjectSaveRequest req) {
        Project project = requireEdit(id);
        validateRequest(req);
        SysUser leader = userMapper.selectById(req.leaderId());
        if (leader == null) {
            throw new BizException("项目负责人不存在");
        }
        project.setName(req.name().trim());
        project.setCategory(validEnum(Dicts.ProjectCategory.class, req.category(), "项目类别"));
        project.setType(validEnum(Dicts.ProjectType.class, req.type(), "项目类型"));
        project.setLeaderId(leader.getId());
        project.setLeaderName(leader.getRealName());
        project.setDept(leader.getDept());
        project.setSource(req.source());
        project.setBudgetTotal(req.budgetTotal());
        project.setStartDate(req.startDate());
        project.setEndDate(req.endDate());
        project.setSummary(req.summary());
        project.setExpectedOutcome(req.expectedOutcome());
        project.setUpdatedAt(LocalDateTime.now());
        projectMapper.updateById(project);

        replaceMembers(project, req.members());
        replaceBudgets(project, req.budgets());
        auditService.record("PROJECT_UPDATE", "项目 " + project.getCode(), "修改申报内容：" + project.getName());
        return project;
    }

    @Transactional
    public void delete(Long id) {
        Project project = requireEdit(id);
        projectMapper.deleteById(project.getId());
        memberMapper.delete(new LambdaQueryWrapper<ProjectMember>().eq(ProjectMember::getProjectId, id));
        budgetMapper.delete(new LambdaQueryWrapper<BudgetItem>().eq(BudgetItem::getProjectId, id));
        auditService.record("PROJECT_DELETE", "项目 " + project.getCode(), "删除草稿：" + project.getName());
    }

    // ================= 权限 =================

    public Project requireView(Long id) {
        Project project = load(id);
        LoginUser user = AuthContext.get();
        if (user.isAdmin() || user.isFinance() || "LEADER".equals(user.role())) {
            return project;
        }
        boolean isMember = isMember(id, user.id());
        boolean isLeader = Objects.equals(project.getLeaderId(), user.id());
        if (user.isDeptAdmin() && Objects.equals(project.getDept(), user.dept())) {
            return project;
        }
        if (!isLeader && !isMember) {
            throw BizException.forbidden("无权查看该项目");
        }
        return project;
    }

    /** 可编辑/可填报：负责人、创建人、科研处管理员，且项目不在终态。 */
    public Project requireOperate(Long id) {
        Project project = load(id);
        LoginUser user = AuthContext.get();
        boolean allowed = user.isAdmin()
                || Objects.equals(project.getLeaderId(), user.id())
                || Objects.equals(project.getCreatedBy(), user.id());
        if (!allowed) {
            throw BizException.forbidden("只有项目负责人或科研处管理员可以执行该操作");
        }
        if (Dicts.ProjectStatus.COMPLETED.name().equals(project.getStatus())
                || Dicts.ProjectStatus.TERMINATED.name().equals(project.getStatus())) {
            throw new BizException("项目已结题/终止，不能再修改");
        }
        return project;
    }

    private Project requireEdit(Long id) {
        Project project = load(id);
        LoginUser user = AuthContext.get();
        boolean editableStatus = Dicts.ProjectStatus.DRAFT.name().equals(project.getStatus())
                || Dicts.ProjectStatus.REJECTED.name().equals(project.getStatus());
        if (!editableStatus) {
            throw new BizException("只有草稿或已驳回的项目可以编辑");
        }
        boolean allowed = user.isAdmin() || Objects.equals(project.getLeaderId(), user.id())
                || Objects.equals(project.getCreatedBy(), user.id());
        if (!allowed) {
            throw BizException.forbidden("无权修改该项目");
        }
        return project;
    }

    public Project load(Long id) {
        Project project = projectMapper.selectById(id);
        if (project == null) {
            throw BizException.notFound("项目不存在或已删除");
        }
        return project;
    }

    public boolean isMember(Long projectId, Long userId) {
        return memberMapper.selectCount(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, projectId)
                .eq(ProjectMember::getUserId, userId)) > 0;
    }

    // ================= 统计 =================

    public Dtos.ProjectStats buildStats(Project project, List<Milestone> milestones,
                                        List<ProgressReport> progress, List<Expense> expenses,
                                        List<Achievement> achievements) {
        BigDecimal approved = expenses.stream().filter(e -> Dicts.ExpenseStatus.APPROVED.name().equals(e.getStatus()))
                .map(Expense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pending = expenses.stream().filter(e -> Dicts.ExpenseStatus.PENDING.name().equals(e.getStatus()))
                .map(Expense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total = project.getBudgetTotal() == null ? BigDecimal.ZERO : project.getBudgetTotal();
        double rate = total.compareTo(BigDecimal.ZERO) == 0 ? 0
                : approved.divide(total, 4, RoundingMode.HALF_UP).doubleValue() * 100;
        int percent = progress.isEmpty() ? defaultProgress(project) : progress.get(0).getProgressPercent();
        int done = (int) milestones.stream().filter(m -> Dicts.MilestoneStatus.DONE.name().equals(m.getStatus())).count();
        long daysLeft = project.getEndDate() == null ? 0 : ChronoUnit.DAYS.between(LocalDate.now(), project.getEndDate());
        boolean overdue = daysLeft < 0 && isActive(project.getStatus());
        return new Dtos.ProjectStats(total, approved, pending, Math.round(rate * 10) / 10.0, percent,
                done, milestones.size(), achievements.size(), daysLeft, overdue);
    }

    public boolean isActive(String status) {
        return Dicts.ProjectStatus.IN_PROGRESS.name().equals(status)
                || Dicts.ProjectStatus.PENDING_ACCEPTANCE.name().equals(status)
                || Dicts.ProjectStatus.SUBMITTED.name().equals(status)
                || Dicts.ProjectStatus.DEPT_APPROVED.name().equals(status);
    }

    private int defaultProgress(Project project) {
        return Dicts.ProjectStatus.COMPLETED.name().equals(project.getStatus()) ? 100 : 0;
    }

    public void applyScope(LambdaQueryWrapper<Project> wrapper, LoginUser user, boolean onlyMine) {
        if (onlyMine || user.isResearcher()) {
            List<Long> ids = memberMapper.selectList(new LambdaQueryWrapper<ProjectMember>()
                            .eq(ProjectMember::getUserId, user.id()))
                    .stream().map(ProjectMember::getProjectId).toList();
            wrapper.and(w -> {
                w.eq(Project::getLeaderId, user.id());
                if (!ids.isEmpty()) {
                    w.or().in(Project::getId, ids);
                }
            });
        } else if (user.isDeptAdmin()) {
            List<Long> ids = memberMapper.selectList(new LambdaQueryWrapper<ProjectMember>()
                            .eq(ProjectMember::getUserId, user.id()))
                    .stream().map(ProjectMember::getProjectId).toList();
            wrapper.and(w -> {
                w.eq(Project::getDept, user.dept()).or().eq(Project::getLeaderId, user.id());
                if (!ids.isEmpty()) {
                    w.or().in(Project::getId, ids);
                }
            });
        }
    }

    private Map<Long, BigDecimal> sumExpenseByProject(List<Long> projectIds, String status) {
        return expenseMapper.selectList(new LambdaQueryWrapper<Expense>()
                        .in(Expense::getProjectId, projectIds).eq(Expense::getStatus, status))
                .stream().collect(Collectors.groupingBy(Expense::getProjectId,
                        Collectors.reducing(BigDecimal.ZERO, Expense::getAmount, BigDecimal::add)));
    }

    private Map<Long, Integer> latestProgress(List<Long> projectIds) {
        Map<Long, Integer> map = new HashMap<>();
        progressMapper.selectList(new LambdaQueryWrapper<ProgressReport>()
                        .in(ProgressReport::getProjectId, projectIds)
                        .orderByDesc(ProgressReport::getReportDate).orderByDesc(ProgressReport::getId))
                .forEach(r -> map.putIfAbsent(r.getProjectId(), r.getProgressPercent()));
        return map;
    }

    public List<ProjectMember> membersOf(Long projectId) {
        return memberMapper.selectList(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, projectId));
    }

    private Dtos.ProjectListItem toListItem(Project p, BigDecimal approvedExpense, int progress) {
        long daysLeft = p.getEndDate() == null ? 0 : ChronoUnit.DAYS.between(LocalDate.now(), p.getEndDate());
        return new Dtos.ProjectListItem(p.getId(), p.getCode(), p.getName(),
                p.getCategory(), label(Dicts.ProjectCategory.class, p.getCategory()),
                p.getType(), label(Dicts.ProjectType.class, p.getType()),
                p.getLeaderId(), p.getLeaderName(), p.getDept(), p.getBudgetTotal(), approvedExpense, progress,
                p.getStartDate(), p.getEndDate(), p.getStatus(), label(Dicts.ProjectStatus.class, p.getStatus()),
                daysLeft, daysLeft < 0 && isActive(p.getStatus()));
    }

    private void replaceMembers(Project project, List<Dtos.MemberItem> members) {
        memberMapper.delete(new LambdaQueryWrapper<ProjectMember>().eq(ProjectMember::getProjectId, project.getId()));
        Set<Long> seen = new HashSet<>();
        insertMember(project.getId(), project.getLeaderId(), project.getLeaderName(), "负责人", 60);
        seen.add(project.getLeaderId());
        if (members != null) {
            for (Dtos.MemberItem item : members) {
                Long userId = item.userId();
                if (userId == null || !seen.add(userId)) {
                    continue;
                }
                SysUser user = userMapper.selectById(userId);
                String name = user != null ? user.getRealName() : item.userName();
                insertMember(project.getId(), userId, name,
                        StringUtils.hasText(item.memberRole()) ? item.memberRole() : "参与人",
                        item.workload() == null ? 0 : item.workload());
            }
        }
    }

    private void insertMember(Long projectId, Long userId, String userName, String role, int workload) {
        ProjectMember member = new ProjectMember();
        member.setProjectId(projectId);
        member.setUserId(userId);
        member.setUserName(userName);
        member.setMemberRole(role);
        member.setWorkload(workload);
        memberMapper.insert(member);
    }

    private void replaceBudgets(Project project, List<Dtos.BudgetLine> budgets) {
        budgetMapper.delete(new LambdaQueryWrapper<BudgetItem>().eq(BudgetItem::getProjectId, project.getId()));
        if (budgets == null || budgets.isEmpty()) {
            for (Dicts.ExpenseCategory category : Dicts.ExpenseCategory.values()) {
                insertBudget(project.getId(), category.name(), BigDecimal.ZERO);
            }
            return;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (Dtos.BudgetLine line : budgets) {
            String category = validEnum(Dicts.ExpenseCategory.class, line.category(), "费用科目");
            BigDecimal amount = line.amount() == null ? BigDecimal.ZERO : line.amount();
            if (amount.compareTo(BigDecimal.ZERO) < 0) {
                throw new BizException("预算金额不能为负数");
            }
            sum = sum.add(amount);
            insertBudget(project.getId(), category, amount);
        }
        if (sum.subtract(project.getBudgetTotal()).abs().compareTo(new BigDecimal("0.01")) > 0) {
            throw new BizException("预算科目合计 " + sum.stripTrailingZeros().toPlainString()
                    + " 与预算总额不一致，请调整后提交");
        }
    }

    private void insertBudget(Long projectId, String category, BigDecimal amount) {
        BudgetItem item = new BudgetItem();
        item.setProjectId(projectId);
        item.setCategory(category);
        item.setAmount(amount);
        item.setReservedAmount(BigDecimal.ZERO);
        budgetMapper.insert(item);
    }

    private void validateRequest(Dtos.ProjectSaveRequest req) {
        if (req.endDate().isBefore(req.startDate())) {
            throw new BizException("结束日期不能早于开始日期");
        }
        if (req.startDate().plusYears(10).isBefore(req.endDate())) {
            throw new BizException("项目周期异常，请检查起止日期");
        }
    }

    private String nextCode() {
        String prefix = "PRJ-" + LocalDate.now().getYear() + "-";
        long count = projectMapper.selectCount(new LambdaQueryWrapper<Project>().likeRight(Project::getCode, prefix));
        String code;
        do {
            count++;
            code = prefix + String.format("%03d", count);
        } while (projectMapper.selectCount(new LambdaQueryWrapper<Project>().eq(Project::getCode, code)) > 0);
        return code;
    }

    private <E extends Enum<E>> String validEnum(Class<E> type, String value, String field) {
        try {
            return Enum.valueOf(type, value).name();
        } catch (Exception e) {
            throw new BizException("非法的" + field + "：" + value);
        }
    }

    public static <E extends Enum<E>> String label(Class<E> type, String value) {
        if (value == null) {
            return "";
        }
        try {
            Object enumValue = Enum.valueOf(type, value);
            return (String) enumValue.getClass().getMethod("getLabel").invoke(enumValue);
        } catch (Exception e) {
            return value;
        }
    }

    public Map<Long, Project> mapByIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return projectMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Project::getId, Function.identity()));
    }
}
