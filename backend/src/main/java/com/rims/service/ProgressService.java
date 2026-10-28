package com.rims.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rims.common.BizException;
import com.rims.common.Dicts;
import com.rims.dto.Dtos;
import com.rims.entity.Achievement;
import com.rims.entity.Milestone;
import com.rims.entity.ProgressReport;
import com.rims.mapper.AchievementMapper;
import com.rims.mapper.MilestoneMapper;
import com.rims.mapper.ProgressReportMapper;
import com.rims.security.AuthContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 执行期管理：里程碑、进度填报、成果登记。
 */
@Service
public class ProgressService {

    private final MilestoneMapper milestoneMapper;
    private final ProgressReportMapper progressMapper;
    private final AchievementMapper achievementMapper;
    private final ProjectService projectService;
    private final AuditService auditService;

    public ProgressService(MilestoneMapper milestoneMapper, ProgressReportMapper progressMapper,
                           AchievementMapper achievementMapper, ProjectService projectService,
                           AuditService auditService) {
        this.milestoneMapper = milestoneMapper;
        this.progressMapper = progressMapper;
        this.achievementMapper = achievementMapper;
        this.projectService = projectService;
        this.auditService = auditService;
    }

    // ---------------- 里程碑 ----------------

    public List<Milestone> listMilestones(Long projectId) {
        projectService.requireView(projectId);
        return milestoneMapper.selectList(new LambdaQueryWrapper<Milestone>()
                .eq(Milestone::getProjectId, projectId)
                .orderByAsc(Milestone::getSortNo).orderByAsc(Milestone::getId));
    }

    @Transactional
    public Milestone addMilestone(Long projectId, Dtos.MilestoneRequest req) {        projectService.requireOperate(projectId);
        Milestone milestone = new Milestone();
        milestone.setProjectId(projectId);
        milestone.setName(req.name().trim());
        milestone.setPlanDate(req.planDate());
        milestone.setStatus(Dicts.MilestoneStatus.PENDING.name());
        milestone.setSortNo(req.sortNo() == null ? nextSortNo(projectId) : req.sortNo());
        milestoneMapper.insert(milestone);
        return milestone;
    }

    @Transactional
    public Milestone updateMilestone(Long id, Dtos.MilestoneRequest req) {
        Milestone milestone = loadMilestone(id);
        projectService.requireOperate(milestone.getProjectId());
        milestone.setName(req.name().trim());
        milestone.setPlanDate(req.planDate());
        if (req.sortNo() != null) {
            milestone.setSortNo(req.sortNo());
        }
        milestoneMapper.updateById(milestone);
        return milestone;
    }

    @Transactional
    public Milestone completeMilestone(Long id) {
        Milestone milestone = loadMilestone(id);
        projectService.requireOperate(milestone.getProjectId());
        if (Dicts.MilestoneStatus.DONE.name().equals(milestone.getStatus())) {
            throw new BizException("该里程碑已完成");
        }
        milestone.setStatus(Dicts.MilestoneStatus.DONE.name());
        milestone.setActualDate(LocalDate.now());
        milestoneMapper.updateById(milestone);
        auditService.record("MILESTONE_DONE", "里程碑 #" + id, "完成里程碑：" + milestone.getName());
        return milestone;
    }

    @Transactional
    public void deleteMilestone(Long id) {
        Milestone milestone = loadMilestone(id);
        projectService.requireOperate(milestone.getProjectId());
        milestoneMapper.deleteById(id);
    }

    private Milestone loadMilestone(Long id) {
        Milestone milestone = milestoneMapper.selectById(id);
        if (milestone == null) {
            throw BizException.notFound("里程碑不存在");
        }
        return milestone;
    }

    private int nextSortNo(Long projectId) {
        return (int) (milestoneMapper.selectCount(new LambdaQueryWrapper<Milestone>()
                .eq(Milestone::getProjectId, projectId)) + 1);
    }

    // ---------------- 进度填报 ----------------

    @Transactional
    public ProgressReport addProgress(Long projectId, Dtos.ProgressRequest req) {
        projectService.requireOperate(projectId);
        var project = projectService.load(projectId);
        if (!Dicts.ProjectStatus.IN_PROGRESS.name().equals(project.getStatus())
                && !Dicts.ProjectStatus.PENDING_ACCEPTANCE.name().equals(project.getStatus())) {
            throw new BizException("只有执行中的项目可以填报进度");
        }
        int percent = req.progressPercent() == null ? 0 : req.progressPercent();
        if (percent < 0 || percent > 100) {
            throw new BizException("进度百分比需在 0-100 之间");
        }
        if (req.content() == null || req.content().isBlank()) {
            throw new BizException("请填写本期工作内容");
        }
        ProgressReport report = new ProgressReport();
        report.setProjectId(projectId);
        report.setReporterId(AuthContext.get().id());
        report.setReporterName(AuthContext.get().realName());
        report.setReportDate(req.reportDate() == null ? LocalDate.now() : req.reportDate());
        report.setProgressPercent(percent);
        report.setContent(req.content());
        report.setIssues(req.issues());
        report.setNextPlan(req.nextPlan());
        report.setCreatedAt(LocalDateTime.now());
        progressMapper.insert(report);
        auditService.record("PROGRESS_REPORT", "项目 " + project.getCode(), "填报进度 " + percent + "%");
        return report;
    }

    public List<ProgressReport> listProgress(Long projectId) {
        projectService.requireView(projectId);
        return progressMapper.selectList(new LambdaQueryWrapper<ProgressReport>()
                .eq(ProgressReport::getProjectId, projectId)
                .orderByDesc(ProgressReport::getReportDate).orderByDesc(ProgressReport::getId));
    }

    // ---------------- 成果 ----------------

    @Transactional
    public Achievement addAchievement(Long projectId, Dtos.AchievementRequest req) {
        var project = projectService.load(projectId);
        if (Dicts.ProjectStatus.DRAFT.name().equals(project.getStatus())) {
            throw new BizException("草稿项目暂不能登记成果");
        }
        var user = AuthContext.get();
        boolean allowed = user.isAdmin() || java.util.Objects.equals(project.getLeaderId(), user.id())
                || projectService.isMember(projectId, user.id());
        if (!allowed) {
            throw BizException.forbidden("只有项目组成员可以登记成果");
        }
        String type;
        try {
            type = Dicts.AchievementType.valueOf(req.type()).name();
        } catch (Exception e) {
            throw new BizException("非法的成果类型");
        }
        Achievement achievement = new Achievement();
        achievement.setProjectId(projectId);
        achievement.setType(type);
        achievement.setTitle(req.title().trim());
        achievement.setAuthors(req.authors());
        achievement.setAchievedDate(req.achievedDate() == null ? LocalDate.now() : req.achievedDate());
        achievement.setLevelName(req.levelName());
        achievement.setRemark(req.remark());
        achievement.setCreatedAt(LocalDateTime.now());
        achievementMapper.insert(achievement);
        auditService.record("ACHIEVEMENT_ADD", "项目 " + project.getCode(), "登记成果：" + achievement.getTitle());
        return achievement;
    }

    @Transactional
    public void deleteAchievement(Long id) {
        Achievement achievement = achievementMapper.selectById(id);
        if (achievement == null) {
            throw BizException.notFound("成果不存在");
        }
        projectService.requireOperate(achievement.getProjectId());
        achievementMapper.deleteById(id);
    }

    public List<Achievement> listAchievements(Long projectId) {
        projectService.requireView(projectId);
        return achievementMapper.selectList(new LambdaQueryWrapper<Achievement>()
                .eq(Achievement::getProjectId, projectId).orderByDesc(Achievement::getId));
    }
}
