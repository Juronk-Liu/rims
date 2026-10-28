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
import java.util.List;
import java.util.Objects;

/**
 * 经费：预算科目 + 支出申请/审批。核心规则是"分科目控额"，申请与审批两次校验。
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
        BigDecimal sum = req.items().stream()
                .map(i -> i.amount() == null ? BigDecimal.ZERO : i.amount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.subtract(project.getBudgetTotal()).abs().compareTo(new BigDecimal("0.01")) > 0) {
            throw new BizException("科目合计需等于预算总额 " + project.getBudgetTotal().stripTrailingZeros().toPlainString());
        }
        budgetMapper.delete(new LambdaQueryWrapper<BudgetItem>().eq(BudgetItem::getProjectId, projectId));
        for (Dtos.BudgetLine line : req.items()) {
            BudgetItem item = new BudgetItem();
            item.setProjectId(projectId);
            item.setCategory(line.category());
            item.setAmount(line.amount() == null ? BigDecimal.ZERO : line.amount());
            budgetMapper.insert(item);
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
        checkBudget(projectId, category, req.amount());

        Expense expense = new Expense();
        expense.setProjectId(projectId);
        expense.setCategory(category);
        expense.setAmount(req.amount());
        expense.setPurpose(req.purpose());
        expense.setExpenseDate(req.expenseDate() == null ? LocalDate.now() : req.expenseDate());
        expense.setApplicantId(user.id());
        expense.setApplicantName(user.realName());
        expense.setStatus(Dicts.ExpenseStatus.PENDING.name());
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
        if (req.approved()) {
            // 审批时重新校验额度，避免并发下单笔都"看起来"没超；排除当前单据自身占用的额度
            checkBudget(expense.getProjectId(), expense.getCategory(), expense.getAmount(), expense.getId());
            expense.setStatus(Dicts.ExpenseStatus.APPROVED.name());
        } else {
            expense.setStatus(Dicts.ExpenseStatus.REJECTED.name());
        }
        expense.setApproverId(user.id());
        expense.setApproverName(user.realName());
        expense.setOpinion(req.opinion());
        expense.setApprovedAt(LocalDateTime.now());
        expenseMapper.updateById(expense);

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

    /**
     * 分科目额度校验：已通过 + 审批中 都不能突破科目预算。
     *
     * @param excludeExpenseId 审批时传入当前单据 id，避免把自己的在途金额重复计入
     */
    private void checkBudget(Long projectId, String category, BigDecimal amount) {
        checkBudget(projectId, category, amount, null);
    }

    private void checkBudget(Long projectId, String category, BigDecimal amount, Long excludeExpenseId) {
        BigDecimal budget = budgetMapper.selectList(new LambdaQueryWrapper<BudgetItem>()
                        .eq(BudgetItem::getProjectId, projectId).eq(BudgetItem::getCategory, category))
                .stream().map(BudgetItem::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (budget.signum() == 0) {
            throw new BizException("该项目「" + label(category) + "」尚未下拨预算，无法申请");
        }
        BigDecimal committed = expenseMapper.selectList(new LambdaQueryWrapper<Expense>()
                        .eq(Expense::getProjectId, projectId).eq(Expense::getCategory, category)
                        .ne(excludeExpenseId != null, Expense::getId, excludeExpenseId)
                        .in(Expense::getStatus, List.of(Dicts.ExpenseStatus.APPROVED.name(), Dicts.ExpenseStatus.PENDING.name())))
                .stream().map(Expense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remain = budget.subtract(committed);
        if (amount.compareTo(remain) > 0) {
            throw new BizException("「" + label(category) + "」预算剩余 " + remain.stripTrailingZeros().toPlainString()
                    + " 元，本次申请 " + amount.stripTrailingZeros().toPlainString() + " 元，已超出额度");
        }
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
