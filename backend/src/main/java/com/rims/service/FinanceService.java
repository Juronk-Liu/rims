package com.rims.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rims.common.BizException;
import com.rims.common.Dicts;
import com.rims.common.PageResult;
import com.rims.dto.Dtos;
import com.rims.entity.ApprovalRecord;
import com.rims.entity.BudgetItem;
import com.rims.entity.Expense;
import com.rims.entity.Project;
import com.rims.mapper.ApprovalRecordMapper;
import com.rims.mapper.BudgetItemMapper;
import com.rims.mapper.ExpenseMapper;
import com.rims.mapper.ProjectMapper;
import com.rims.security.AuthContext;
import com.rims.security.LoginUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 经费：预算科目 + 支出申请/审批。
 *
 * 额度控制采用“原子预留”方案：budget_item 上维护 reserved_amount（已占用 = 审批中 + 已通过），
 * 占用/释放都用数据库层的“条件 UPDATE + 影响行数”实现，天然带行锁，因此并发审批不会超支——
 * 这是相比“先 SELECT 已占用、再判断、再 UPDATE”的 read-modify-write 方案的关键正确性改进。
 */
@Service
public class FinanceService {

    private final BudgetItemMapper budgetMapper;
    private final ExpenseMapper expenseMapper;
    private final ProjectMapper projectMapper;
    private final ApprovalRecordMapper approvalMapper;
    private final ProjectService projectService;
    private final AuditService auditService;

    public FinanceService(BudgetItemMapper budgetMapper, ExpenseMapper expenseMapper, ProjectMapper projectMapper,
                          ApprovalRecordMapper approvalMapper, ProjectService projectService,
                          AuditService auditService) {
        this.budgetMapper = budgetMapper;
        this.expenseMapper = expenseMapper;
        this.projectMapper = projectMapper;
        this.approvalMapper = approvalMapper;
        this.projectService = projectService;
        this.auditService = auditService;
    }

    public List<BudgetItem> budgets(Long projectId) {
        projectService.requireView(projectId);
        return budgetMapper.selectList(new LambdaQueryWrapper<BudgetItem>()
                .eq(BudgetItem::getProjectId, projectId).orderByAsc(BudgetItem::getId));
    }

    @Transactional
    public List<BudgetItem> saveBudgets(Long projectId, Dtos.BudgetSaveRequest req) {
        AuthContext.requireRole(Dicts.Role.ADMIN.name());
        Project project = projectService.load(projectId);
        if (Dicts.ProjectStatus.COMPLETED.name().equals(project.getStatus())
                || Dicts.ProjectStatus.TERMINATED.name().equals(project.getStatus())) {
            throw new BizException("项目已结题/终止，不能调整预算");
        }
        if (req.items() == null) {
            throw new BizException("预算科目不能为空");
        }
        Map<String, BigDecimal> reqMap = new HashMap<>();
        for (Dtos.BudgetLine line : req.items()) {
            if (line == null || !StringUtils.hasText(line.category())) {
                throw new BizException("预算科目不能为空");
            }
            String category;
            try {
                category = Dicts.ExpenseCategory.valueOf(line.category()).name();
            } catch (IllegalArgumentException e) {
                throw new BizException("非法的预算科目");
            }
            BigDecimal amount = line.amount() == null ? BigDecimal.ZERO : line.amount();
            if (reqMap.putIfAbsent(category, amount) != null) {
                throw new BizException("预算科目不能重复：" + label(category));
            }
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (Dicts.ExpenseCategory cat : Dicts.ExpenseCategory.values()) {
            BigDecimal amt = reqMap.getOrDefault(cat.name(), BigDecimal.ZERO);
            if (amt.signum() < 0) {
                throw new BizException("预算金额不能为负数");
            }
            sum = sum.add(amt);
        }
        if (sum.subtract(project.getBudgetTotal()).abs().compareTo(new BigDecimal("0.01")) > 0) {
            throw new BizException("科目合计需等于预算总额 " + project.getBudgetTotal().stripTrailingZeros().toPlainString());
        }

        // 按科目 upsert，保留 reserved_amount，并防止把科目预算下调到已占用以下
        Map<String, BudgetItem> byCat = budgetMapper.selectList(new LambdaQueryWrapper<BudgetItem>()
                        .eq(BudgetItem::getProjectId, projectId)).stream()
                .collect(Collectors.toMap(BudgetItem::getCategory, Function.identity()));
        for (Dicts.ExpenseCategory cat : Dicts.ExpenseCategory.values()) {
            BigDecimal amt = reqMap.getOrDefault(cat.name(), BigDecimal.ZERO);
            BudgetItem item = byCat.get(cat.name());
            if (item == null) {
                item = new BudgetItem();
                item.setProjectId(projectId);
                item.setCategory(cat.name());
                item.setReservedAmount(BigDecimal.ZERO);
                item.setAmount(amt);
                budgetMapper.insert(item);
            } else {
                BigDecimal reserved = item.getReservedAmount() == null ? BigDecimal.ZERO : item.getReservedAmount();
                if (reserved.compareTo(amt) > 0) {
                    throw new BizException("「" + label(cat.name()) + "」已占用 " + reserved.stripTrailingZeros().toPlainString()
                            + " 元，预算不能低于已占用金额");
                }
                int rows = budgetMapper.updateAmountIfAvailable(item.getId(), amt);
                if (rows == 0) {
                    BudgetItem latest = budgetMapper.selectById(item.getId());
                    if (latest == null || latest.getReservedAmount().compareTo(amt) > 0
                            || latest.getAmount().compareTo(amt) != 0) {
                        BigDecimal reservedNow = latest == null || latest.getReservedAmount() == null
                                ? BigDecimal.ZERO : latest.getReservedAmount();
                        throw new BizException("「" + label(cat.name()) + "」已占用 "
                                + reservedNow.stripTrailingZeros().toPlainString()
                                + " 元，预算不能低于已占用金额");
                    }
                }
            }
        }
        auditService.record("BUDGET_UPDATE", "项目 " + project.getCode(), "调整预算科目");
        return budgets(projectId);
    }

    @Transactional
    public Expense createExpense(Long projectId, Dtos.ExpenseRequest req) {
        Project project = projectService.load(projectId);
        LoginUser user = AuthContext.get();
        boolean allowed = user.isAdmin() || Objects.equals(project.getLeaderId(), user.id())
                || projectService.isMember(projectId, user.id());
        if (!allowed) {
            throw BizException.forbidden("只有项目组成员可以申请经费");
        }
        if (!Dicts.ProjectStatus.IN_PROGRESS.name().equals(project.getStatus())) {
            throw new BizException("只有执行中的项目可以申请经费");
        }
        String category;
        try {
            category = Dicts.ExpenseCategory.valueOf(req.category()).name();
        } catch (Exception e) {
            throw new BizException("非法的费用科目");
        }
        BudgetItem budget = findBudget(projectId, category);
        if (budget == null) {
            throw new BizException("该项目「" + label(category) + "」尚未下拨预算，无法申请");
        }
        // 原子预留：仅当“已占用 + 本次 <= 额度”时成功，数据库行锁保证并发安全
        int rows = budgetMapper.reserve(budget.getId(), req.amount());
        if (rows == 0) {
            BudgetItem latest = budgetMapper.selectById(budget.getId());
            BigDecimal reserved = latest.getReservedAmount() == null ? BigDecimal.ZERO : latest.getReservedAmount();
            BigDecimal remain = latest.getAmount().subtract(reserved).max(BigDecimal.ZERO);
            throw new BizException("「" + label(category) + "」预算剩余 " + remain.stripTrailingZeros().toPlainString()
                    + " 元，本次申请 " + req.amount().stripTrailingZeros().toPlainString() + " 元，已超出额度");
        }

        Expense expense = new Expense();
        expense.setProjectId(projectId);
        expense.setCategory(category);
        expense.setAmount(req.amount());
        expense.setPurpose(req.purpose());
        expense.setExpenseDate(req.expenseDate() == null ? LocalDate.now() : req.expenseDate());
        expense.setApplicantId(user.id());
        expense.setApplicantName(user.realName());
        expense.setStatus(Dicts.ExpenseStatus.PENDING.name());
        expense.setVersion(0L);
        expense.setCreatedAt(LocalDateTime.now());
        expenseMapper.insert(expense);
        auditService.record("EXPENSE_SUBMIT", "项目 " + project.getCode(),
                "提交经费申请：" + label(category) + " " + req.amount().toPlainString() + " 元");
        return expense;
    }

    public PageResult<Expense> listExpenses(int page, int size, String status, Long projectId) {
        List<Long> visibleIds = visibleProjectIds();
        if (visibleIds != null && visibleIds.isEmpty()) {
            return new PageResult<>(0, List.of());
        }
        LambdaQueryWrapper<Expense> wrapper = new LambdaQueryWrapper<>();
        if (visibleIds != null) {
            wrapper.in(Expense::getProjectId, visibleIds);
        }
        if (projectId != null) {
            wrapper.eq(Expense::getProjectId, projectId);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(Expense::getStatus, status);
        }
        wrapper.orderByAsc(Expense::getStatus).orderByDesc(Expense::getId);
        Page<Expense> result = expenseMapper.selectPage(new Page<>(page, size), wrapper);
        return new PageResult<>(result.getTotal(), result.getRecords());
    }

    @Transactional
    public Expense approveExpense(Long id, Dtos.ExpenseApproveRequest req) {
        LoginUser user = AuthContext.get();
        if (!user.isFinance() && !user.isAdmin()) {
            throw BizException.forbidden("经费审批由财务或科研处管理员处理");
        }
        Expense expense = expenseMapper.selectById(id);
        if (expense == null) {
            throw BizException.notFound("单据不存在");
        }
        if (!Dicts.ExpenseStatus.PENDING.name().equals(expense.getStatus())) {
            throw new BizException("该单据已处理，不能重复审批");
        }
        String toStatus = req.approved() ? Dicts.ExpenseStatus.APPROVED.name() : Dicts.ExpenseStatus.REJECTED.name();
        expense.setStatus(toStatus);
        expense.setApproverId(user.id());
        expense.setApproverName(user.realName());
        expense.setOpinion(req.opinion());
        expense.setApprovedAt(LocalDateTime.now());
        // 先带版本更新状态：并发审批会让后到的事务命中 0 行，抛出冲突，杜绝重复审批
        int rows = expenseMapper.updateById(expense);
        if (rows == 0) {
            throw BizException.conflict("该单据已被他人审批，请刷新后查看最新状态");
        }
        if (!req.approved()) {
            // 驳回：释放创建时占用的额度（仅在状态更新成功后进行，避免版本冲突时误释放）
            BudgetItem b = findBudget(expense.getProjectId(), expense.getCategory());
            if (b == null || budgetMapper.release(b.getId(), expense.getAmount()) != 1) {
                throw BizException.conflict("预算占用数据异常，单据未完成处理，请联系管理员");
            }
        }

        ApprovalRecord record = new ApprovalRecord();
        record.setBizType("EXPENSE");
        record.setBizId(expense.getId());
        record.setAction(req.approved() ? "APPROVE" : "REJECT");
        record.setFromStatus(Dicts.ExpenseStatus.PENDING.name());
        record.setToStatus(expense.getStatus());
        record.setOperatorId(user.id());
        record.setOperatorName(user.realName());
        record.setOpinion(req.opinion());
        record.setCreatedAt(LocalDateTime.now());
        approvalMapper.insert(record);

        Project project = projectService.load(expense.getProjectId());
        auditService.record("EXPENSE_APPROVE", "项目 " + project.getCode(),
                (req.approved() ? "通过" : "驳回") + "经费 " + expense.getAmount().toPlainString() + " 元");
        return expense;
    }

    public List<Expense> listByProject(Long projectId) {
        return expenseMapper.selectList(new LambdaQueryWrapper<Expense>()
                .eq(Expense::getProjectId, projectId).orderByDesc(Expense::getId));
    }

    private BudgetItem findBudget(Long projectId, String category) {
        return budgetMapper.selectOne(new LambdaQueryWrapper<BudgetItem>()
                .eq(BudgetItem::getProjectId, projectId).eq(BudgetItem::getCategory, category));
    }

    /**
     * 返回 null 表示可见全部项目（科研处/财务/院领导），否则返回项目 id 列表。
     */
    private List<Long> visibleProjectIds() {
        LoginUser user = AuthContext.get();
        if (user.isAdmin() || user.isFinance() || "LEADER".equals(user.role())) {
            return null;
        }
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<>();
        projectService.applyScope(wrapper, user, false);
        return projectMapper.selectList(wrapper).stream().map(Project::getId).toList();
    }

    private String label(String category) {
        return ProjectService.label(Dicts.ExpenseCategory.class, category);
    }
}
