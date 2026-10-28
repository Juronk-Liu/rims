package com.rims.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rims.common.BizException;
import com.rims.common.Dicts;
import com.rims.dto.Dtos;
import com.rims.entity.ApprovalRecord;
import com.rims.entity.Achievement;
import com.rims.entity.Expense;
import com.rims.entity.Project;
import com.rims.mapper.AchievementMapper;
import com.rims.mapper.ApprovalRecordMapper;
import com.rims.mapper.ExpenseMapper;
import com.rims.mapper.ProjectMapper;
import com.rims.security.AuthContext;
import com.rims.security.LoginUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 项目状态机。所有状态迁移集中在这里，任何一条流转都必须留审批记录。
 *
 * DRAFT -> SUBMITTED -> DEPT_APPROVED -> IN_PROGRESS -> PENDING_ACCEPTANCE -> COMPLETED
 *              |               |                              |
 *              +--> REJECTED <-+              验收不通过 <----+
 */
@Service
public class WorkflowService {

    private final ProjectMapper projectMapper;
    private final ApprovalRecordMapper approvalMapper;
    private final AchievementMapper achievementMapper;
    private final ExpenseMapper expenseMapper;
    private final ProjectService projectService;
    private final AuditService auditService;

    public WorkflowService(ProjectMapper projectMapper, ApprovalRecordMapper approvalMapper,
                           AchievementMapper achievementMapper, ExpenseMapper expenseMapper,
                           ProjectService projectService, AuditService auditService) {
        this.projectMapper = projectMapper;
        this.approvalMapper = approvalMapper;
        this.achievementMapper = achievementMapper;
        this.expenseMapper = expenseMapper;
        this.projectService = projectService;
        this.auditService = auditService;
    }

    @Transactional
    public Project submit(Long id) {
        Project project = projectService.load(id);
        LoginUser user = AuthContext.get();
        boolean allowed = user.isAdmin() || Objects.equals(project.getLeaderId(), user.id())
                || Objects.equals(project.getCreatedBy(), user.id());
        if (!allowed) {
            throw BizException.forbidden("只有项目负责人可以提交申报");
        }
        String status = project.getStatus();
        if (!Dicts.ProjectStatus.DRAFT.name().equals(status) && !Dicts.ProjectStatus.REJECTED.name().equals(status)) {
            throw new BizException("当前状态（" + label(status) + "）不能提交申报");
        }
        if (!StringUtils.hasText(project.getSummary()) || project.getSummary().trim().length() < 30) {
            throw new BizException("项目摘要不少于 30 字，便于评审判断研究内容");
        }
        if (!StringUtils.hasText(project.getExpectedOutcome())) {
            throw new BizException("请填写预期成果，验收时将按此核对");
        }
        if (project.getBudgetTotal() == null || project.getBudgetTotal().signum() <= 0) {
            throw new BizException("预算总额必须大于 0");
        }

        String from = status;
        project.setStatus(Dicts.ProjectStatus.SUBMITTED.name());
        project.setSubmittedAt(LocalDateTime.now());
        project.setUpdatedAt(LocalDateTime.now());
        projectMapper.updateById(project);
        record(project, "SUBMIT", from, project.getStatus(), "提交申报", user);
        auditService.record("PROJECT_SUBMIT", "项目 " + project.getCode(), "提交申报：" + project.getName());
        return project;
    }

    /**
     * 两级审批：SUBMITTED 由部门审，DEPT_APPROVED 由科研处立项。
     */
    @Transactional
    public Project review(Long id, Dtos.ReviewRequest req) {
        Project project = projectService.load(id);
        LoginUser user = AuthContext.get();
        String status = project.getStatus();
        String action = req.action() == null ? "" : req.action().toUpperCase();
        if (!"APPROVE".equals(action) && !"REJECT".equals(action)) {
            throw new BizException("审批动作只能是 APPROVE 或 REJECT");
        }
        if ("REJECT".equals(action) && !StringUtils.hasText(req.opinion())) {
            throw new BizException("驳回必须填写意见");
        }

        String from = status;
        String to;
        if (Dicts.ProjectStatus.SUBMITTED.name().equals(status)) {
            if (!user.isDeptAdmin() && !user.isAdmin()) {
                throw BizException.forbidden("该环节由部门管理员审核");
            }
            if (user.isDeptAdmin() && !Objects.equals(project.getDept(), user.dept())) {
                throw BizException.forbidden("只能审核本部门项目");
            }
            to = "APPROVE".equals(action)
                    ? Dicts.ProjectStatus.DEPT_APPROVED.name()
                    : Dicts.ProjectStatus.REJECTED.name();
        } else if (Dicts.ProjectStatus.DEPT_APPROVED.name().equals(status)) {
            if (!user.isAdmin()) {
                throw BizException.forbidden("立项审批由科研处执行");
            }
            to = "APPROVE".equals(action)
                    ? Dicts.ProjectStatus.IN_PROGRESS.name()
                    : Dicts.ProjectStatus.REJECTED.name();
        } else {
            throw new BizException("当前状态（" + label(status) + "）不在审批环节");
        }

        project.setStatus(to);
        if (Dicts.ProjectStatus.IN_PROGRESS.name().equals(to)) {
            project.setApprovedAt(LocalDateTime.now());
        }
        project.setUpdatedAt(LocalDateTime.now());
        projectMapper.updateById(project);
        record(project, "APPROVE".equals(action) ? "APPROVE" : "REJECT", from, to,
                req.opinion() == null ? "" : req.opinion(), user);
        auditService.record("PROJECT_REVIEW", "项目 " + project.getCode(),
                ("APPROVE".equals(action) ? "审批通过：" : "驳回：") + project.getName());
        return project;
    }

    @Transactional
    public Project submitAcceptance(Long id) {
        Project project = projectService.requireOperate(id);
        if (!Dicts.ProjectStatus.IN_PROGRESS.name().equals(project.getStatus())) {
            throw new BizException("只有执行中的项目可以申请结题");
        }
        long achievements = achievementMapper.selectCount(new LambdaQueryWrapper<Achievement>()
                .eq(Achievement::getProjectId, id));
        if (achievements == 0) {
            throw new BizException("结题前请先登记至少 1 项成果");
        }
        long pendingExpenses = expenseMapper.selectCount(new LambdaQueryWrapper<Expense>()
                .eq(Expense::getProjectId, id).eq(Expense::getStatus, Dicts.ExpenseStatus.PENDING.name()));
        if (pendingExpenses > 0) {
            throw new BizException("还有 " + pendingExpenses + " 笔经费待审批，结题前需处理完毕");
        }

        String from = project.getStatus();
        project.setStatus(Dicts.ProjectStatus.PENDING_ACCEPTANCE.name());
        project.setUpdatedAt(LocalDateTime.now());
        projectMapper.updateById(project);
        record(project, "SUBMIT_ACCEPTANCE", from, project.getStatus(), "提交结题验收", AuthContext.get());
        auditService.record("PROJECT_ACCEPTANCE_SUBMIT", "项目 " + project.getCode(), "提交结题：" + project.getName());
        return project;
    }

    @Transactional
    public Project acceptance(Long id, Dtos.AcceptanceRequest req) {
        Project project = projectService.load(id);
        AuthContext.requireRole(Dicts.Role.ADMIN.name());
        if (!Dicts.ProjectStatus.PENDING_ACCEPTANCE.name().equals(project.getStatus())) {
            throw new BizException("当前状态不在验收环节");
        }
        String from = project.getStatus();
        String to = req.passed() ? Dicts.ProjectStatus.COMPLETED.name() : Dicts.ProjectStatus.IN_PROGRESS.name();
        project.setStatus(to);
        if (req.passed()) {
            project.setCompletedAt(LocalDateTime.now());
        }
        project.setUpdatedAt(LocalDateTime.now());
        projectMapper.updateById(project);
        record(project, req.passed() ? "ACCEPT_PASS" : "ACCEPT_REJECT", from, to,
                req.opinion() == null ? "" : req.opinion(), AuthContext.get());
        auditService.record("PROJECT_ACCEPTANCE", "项目 " + project.getCode(),
                (req.passed() ? "验收通过：" : "验收不通过：") + project.getName());
        return project;
    }

    @Transactional
    public Project terminate(Long id, String opinion) {
        Project project = projectService.load(id);
        AuthContext.requireRole(Dicts.Role.ADMIN.name());
        if (!Dicts.ProjectStatus.IN_PROGRESS.name().equals(project.getStatus())
                && !Dicts.ProjectStatus.PENDING_ACCEPTANCE.name().equals(project.getStatus())) {
            throw new BizException("只有执行中或待验收的项目可以终止");
        }
        if (!StringUtils.hasText(opinion)) {
            throw new BizException("终止项目必须填写原因");
        }
        String from = project.getStatus();
        project.setStatus(Dicts.ProjectStatus.TERMINATED.name());
        project.setUpdatedAt(LocalDateTime.now());
        projectMapper.updateById(project);
        record(project, "TERMINATE", from, project.getStatus(), opinion, AuthContext.get());
        auditService.record("PROJECT_TERMINATE", "项目 " + project.getCode(), "终止项目：" + opinion);
        return project;
    }

    private void record(Project project, String action, String from, String to, String opinion, LoginUser user) {
        ApprovalRecord record = new ApprovalRecord();
        record.setBizType("PROJECT");
        record.setBizId(project.getId());
        record.setAction(action);
        record.setFromStatus(from);
        record.setToStatus(to);
        record.setOperatorId(user.id());
        record.setOperatorName(user.realName());
        record.setOpinion(opinion);
        record.setCreatedAt(LocalDateTime.now());
        approvalMapper.insert(record);
    }

    private String label(String status) {
        return ProjectService.label(Dicts.ProjectStatus.class, status);
    }
}
