package com.rims.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rims.common.Dicts;
import com.rims.entity.*;
import com.rims.mapper.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * 演示数据初始化：只在空库时执行一次，数据写入 H2 文件库，重启不会重复插入。
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private static final String DEFAULT_PASSWORD = "123456";

    private final SysUserMapper userMapper;
    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper memberMapper;
    private final MilestoneMapper milestoneMapper;
    private final BudgetItemMapper budgetMapper;
    private final ExpenseMapper expenseMapper;
    private final AchievementMapper achievementMapper;
    private final ProgressReportMapper progressMapper;
    private final ApprovalRecordMapper approvalMapper;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public DataInitializer(SysUserMapper userMapper, ProjectMapper projectMapper, ProjectMemberMapper memberMapper,
                           MilestoneMapper milestoneMapper, BudgetItemMapper budgetMapper, ExpenseMapper expenseMapper,
                           AchievementMapper achievementMapper, ProgressReportMapper progressMapper,
                           ApprovalRecordMapper approvalMapper) {
        this.userMapper = userMapper;
        this.projectMapper = projectMapper;
        this.memberMapper = memberMapper;
        this.milestoneMapper = milestoneMapper;
        this.budgetMapper = budgetMapper;
        this.expenseMapper = expenseMapper;
        this.achievementMapper = achievementMapper;
        this.progressMapper = progressMapper;
        this.approvalMapper = approvalMapper;
    }

    @Override
    public void run(String... args) {
        if (userMapper.selectCount(null) > 0) {
            log.info("已有数据，跳过演示数据初始化");
            return;
        }
        log.info("初始化演示数据...");
        SysUser zhang = user("zhangsan", "张岩", "RESEARCHER", "智能装备研究所", "副研究员", "zhangyan@rims.ac.cn");
        SysUser li = user("lisi", "李思远", "RESEARCHER", "信息技术研究所", "研究员", "lisiyuan@rims.ac.cn");
        SysUser wang = user("wangfang", "王芳", "RESEARCHER", "新材料研究所", "副研究员", "wangfang@rims.ac.cn");
        SysUser zhao = user("zhaomin", "赵敏", "DEPT_ADMIN", "智能装备研究所", "所长助理", "zhaomin@rims.ac.cn");
        SysUser chen = user("admin", "陈立", "ADMIN", "科研管理处", "处长", "chenli@rims.ac.cn");
        SysUser zhou = user("zhouying", "周颖", "FINANCE", "财务处", "高级会计师", "zhouying@rims.ac.cn");
        user("sunhao", "孙皓", "LEADER", "院办", "副院长", "sunhao@rims.ac.cn");

        LocalDate today = LocalDate.now();

        // 1. 执行中的重点项目：有进度、有经费、有逾期里程碑
        Project p1 = project("PRJ-" + today.getYear() + "-001", "面向复杂工况的工业机器人视觉检测关键技术研究",
                "PROVINCIAL", "APPLIED", zhang, "省重点研发计划", 1_200_000,
                today.minusMonths(14), today.plusMonths(7), "IN_PROGRESS",
                "针对铸造车间高温、粉尘、强反光工况下工业机器人视觉检测精度衰减问题，研究多光谱成像与抗干扰检测算法，"
                        + "构建面向复杂表面的缺陷样本库，形成可工程部署的视觉检测单元。",
                "申请发明专利 2 项，发表高水平论文 3 篇，研制视觉检测样机 1 套并在合作企业完成产线验证。",
                today.minusMonths(17).atTime(9, 30));
        member(p1, zhang, "负责人", 60);
        member(p1, li, "技术骨干", 30);
        member(p1, wang, "参与人", 20);
        budget(p1, 480_000, 260_000, 160_000, 250_000, 50_000);
        milestone(p1, "需求调研与方案设计", today.minusMonths(12), "DONE", today.minusMonths(12).plusDays(4));
        milestone(p1, "视觉算法原型开发", today.minusMonths(7), "DONE", today.minusMonths(7).plusDays(10));
        milestone(p1, "样机集成与联调", today.minusMonths(2), "PENDING", null);
        milestone(p1, "现场工况验证", today.plusMonths(5), "PENDING", null);
        expense(p1, "EQUIPMENT", 320_000, "工业级多光谱相机及配套光源采购", zhang, "APPROVED", chen, today.minusMonths(11));
        expense(p1, "MATERIAL", 128_000, "标准缺陷样件制作与夹具加工", zhang, "APPROVED", chen, today.minusMonths(8));
        expense(p1, "TRAVEL", 46_000, "合作企业现场调研与联调差旅", zhang, "APPROVED", chen, today.minusMonths(4));
        expense(p1, "LABOR", 60_000, "研究生助研劳务费（第三季度）", zhang, "PENDING", null, today.minusDays(6));
        achievement(p1, "PATENT", "一种强反光表面缺陷的多光谱成像检测方法", "张岩、李思远", today.minusMonths(5), "发明专利（已受理）");
        achievement(p1, "PAPER", "复杂工况下机器人视觉检测的抗干扰方法研究", "张岩、王芳", today.minusMonths(3), "中文核心（已录用）");
        progress(p1, zhang, today.minusMonths(3), 45, "完成多光谱成像平台搭建与 3 类缺陷样本采集，算法原型在实验室数据集上达到 96.2% 检出率。",
                "强反光工况下误检率仍偏高，需补充偏振光源。", "开展偏振成像实验，完成算法迭代。");
        progress(p1, zhang, today.minusMonths(1), 62, "完成偏振光源改造，误检率由 8.1% 降至 3.4%，样机机械结构装配完成。",
                "产线节拍要求 1.2s/件，当前算法耗时 1.8s，需做推理加速。", "模型量化与 TensorRT 部署，进入联调。");
        approvals(p1, zhang, zhao, chen, false, false);

        // 2. 待部门审核
        Project p2 = project("PRJ-" + today.getYear() + "-002", "基于大模型的知识图谱自动构建方法研究",
                "NATIONAL", "APPLIED", li, "国家自然科学基金", 800_000,
                today, today.plusMonths(36), "SUBMITTED",
                "研究面向科研文献的大模型驱动知识图谱构建方法，重点解决实体关系抽取中的长尾类别识别与事实一致性校验问题，"
                        + "并在院所科研数据平台上验证。",
                "发表 CCF-B 以上论文 2 篇，申请发明专利 1 项，形成知识图谱构建工具 1 套。",
                today.minusDays(5).atTime(10, 15));
        member(p2, li, "负责人", 50);
        member(p2, wang, "参与人", 30);
        budget(p2, 260_000, 120_000, 100_000, 280_000, 40_000);
        milestone(p2, "文献语料清洗与标注规范", today.plusMonths(6), "PENDING", null);
        milestone(p2, "抽取模型训练与评测", today.plusMonths(18), "PENDING", null);
        milestone(p2, "平台集成与验证", today.plusMonths(32), "PENDING", null);
        approvals(p2, li, null, null, false, false);

        // 3. 待科研处立项
        Project p3 = project("PRJ-" + today.getYear() + "-003", "高性能储能电池界面调控机理研究",
                "PROVINCIAL", "BASIC", wang, "省自然科学基金", 600_000,
                today.plusMonths(1), today.plusMonths(37), "DEPT_APPROVED",
                "围绕高镍正极/硅碳负极界面失效问题，研究界面包覆层结构与循环稳定性之间的构效关系，"
                        + "为长寿命储能电池设计提供理论依据。",
                "发表 SCI 论文 3 篇，培养硕士研究生 2 名，形成界面调控工艺方案 1 套。",
                today.minusDays(12).atTime(14, 0));
        member(p3, wang, "负责人", 60);
        member(p3, zhang, "参与人", 20);
        budget(p3, 240_000, 150_000, 60_000, 130_000, 20_000);
        milestone(p3, "界面包覆工艺开发", today.plusMonths(10), "PENDING", null);
        milestone(p3, "电化学性能评测", today.plusMonths(24), "PENDING", null);
        approvals(p3, wang, zhao, null, false, false);

        // 4. 待验收
        Project p4 = project("PRJ-" + (today.getYear() - 1) + "-014", "城市地下管网智能巡检机器人系统",
                "INSTITUTIONAL", "DEVELOPMENT", zhang, "院所自筹", 950_000,
                today.minusMonths(20), today.minusMonths(1), "PENDING_ACCEPTANCE",
                "研制适应 DN300-DN800 管径的地下管网巡检机器人，具备淤泥环境行走、缺陷图像采集与离线定位能力，"
                        + "解决人工下井作业风险高的问题。",
                "完成巡检机器人样机 2 台，申请专利 2 项，形成企业标准 1 项。",
                today.minusMonths(21).atTime(9, 0));
        member(p4, zhang, "负责人", 40);
        member(p4, li, "技术骨干", 30);
        budget(p4, 400_000, 220_000, 90_000, 200_000, 40_000);
        milestone(p4, "总体方案设计", today.minusMonths(18), "DONE", today.minusMonths(18));
        milestone(p4, "样机试制", today.minusMonths(13), "DONE", today.minusMonths(12));
        milestone(p4, "管道环境测试", today.minusMonths(6), "DONE", today.minusMonths(6));
        milestone(p4, "示范应用", today.minusMonths(2), "DONE", today.minusMonths(2));
        expense(p4, "EQUIPMENT", 380_000, "机器人本体与驱动模组", zhang, "APPROVED", chen, today.minusMonths(16));
        expense(p4, "MATERIAL", 210_000, "耐腐蚀外壳与密封件", zhang, "APPROVED", chen, today.minusMonths(11));
        expense(p4, "TRAVEL", 85_000, "现场测试差旅", zhang, "APPROVED", chen, today.minusMonths(5));
        expense(p4, "LABOR", 195_000, "研发人员劳务", zhang, "APPROVED", chen, today.minusMonths(2));
        achievement(p4, "PATENT", "一种管道机器人淤泥环境行走机构", "张岩、李思远", today.minusMonths(9), "发明专利（已授权）");
        achievement(p4, "PATENT", "管道缺陷图像离线定位方法", "李思远", today.minusMonths(7), "发明专利（已受理）");
        achievement(p4, "STANDARD", "城市排水管道智能巡检作业规范", "张岩", today.minusMonths(3), "企业标准");
        progress(p4, zhang, today.minusMonths(2), 100, "完成 12 公里示范管段巡检，缺陷识别准确率 93.5%，达到任务书指标。",
                "无", "整理验收材料。");
        approvals(p4, zhang, zhao, chen, false, true);

        // 5. 已结题
        Project p5 = project("PRJ-" + (today.getYear() - 2) + "-008", "面向双碳的工业园区能耗优化平台",
                "PROVINCIAL", "APPLIED", li, "省科技成果转化专项", 1_500_000,
                today.minusMonths(32), today.minusMonths(5), "COMPLETED",
                "构建工业园区多能源协同优化模型，打通电、气、热计量数据，实现负荷预测与用能策略优化，"
                        + "在 3 个园区完成部署。",
                "平台 1 套，接入企业 60 家，年综合能耗下降 6% 以上，申请软著 2 项。",
                today.minusMonths(34).atTime(11, 0));
        member(p5, li, "负责人", 40);
        member(p5, wang, "参与人", 20);
        budget(p5, 520_000, 180_000, 200_000, 520_000, 80_000);
        milestone(p5, "数据接入与治理", today.minusMonths(28), "DONE", today.minusMonths(28));
        milestone(p5, "优化模型开发", today.minusMonths(20), "DONE", today.minusMonths(20));
        milestone(p5, "园区试点", today.minusMonths(12), "DONE", today.minusMonths(11));
        milestone(p5, "成果验收", today.minusMonths(5), "DONE", today.minusMonths(5));
        expense(p5, "EQUIPMENT", 500_000, "边缘采集网关与服务器", li, "APPROVED", chen, today.minusMonths(28));
        expense(p5, "MATERIAL", 165_000, "传感器与安装辅材", li, "APPROVED", chen, today.minusMonths(24));
        expense(p5, "TRAVEL", 190_000, "园区现场实施差旅", li, "APPROVED", chen, today.minusMonths(14));
        expense(p5, "LABOR", 500_000, "外协开发与劳务", li, "APPROVED", zhou, today.minusMonths(8));
        expense(p5, "OTHER", 65_000, "专家咨询与评审费", li, "APPROVED", zhou, today.minusMonths(6));
        achievement(p5, "SOFTWARE", "工业园区多能源协同优化平台 V1.0", "李思远、王芳", today.minusMonths(13), "软件著作权");
        achievement(p5, "SOFTWARE", "园区能耗数据采集网关嵌入式软件", "李思远", today.minusMonths(11), "软件著作权");
        achievement(p5, "PAPER", "基于负荷预测的园区多能源协同调度方法", "李思远", today.minusMonths(9), "SCI 三区");
        achievement(p5, "REPORT", "工业园区能耗优化实施效果评估报告", "李思远、王芳", today.minusMonths(5), "结题报告");
        progress(p5, li, today.minusMonths(5), 100, "完成 3 个园区部署，年综合能耗下降 6.8%，通过第三方节能量审核。", "无", "无");
        approvals(p5, li, zhao, chen, true, false);

        // 6. 草稿
        Project p6 = project("PRJ-" + today.getYear() + "-004", "多模态医疗影像辅助诊断算法研究",
                "INSTITUTIONAL", "APPLIED", wang, "院所青年基金", 300_000,
                today.plusMonths(1), today.plusMonths(25), "DRAFT",
                "探索 CT 与超声影像融合的辅助诊断方法，重点研究小样本条件下的跨模态特征对齐。",
                "发表论文 2 篇，形成算法原型 1 套。",
                today.minusDays(3).atTime(16, 20));
        member(p6, wang, "负责人", 50);
        budget(p6, 120_000, 80_000, 40_000, 50_000, 10_000);
        milestone(p6, "数据脱敏与标注", today.plusMonths(6), "PENDING", null);
        milestone(p6, "跨模态模型训练", today.plusMonths(18), "PENDING", null);

        // 7. 横向课题：经费接近上限 + 项目逾期
        Project p7 = project("PRJ-" + (today.getYear() - 1) + "-021", "农业无人机变量施肥决策系统",
                "ENTERPRISE", "DEVELOPMENT", zhang, "企业委托（中科智农）", 400_000,
                today.minusMonths(16), today.minusMonths(1), "IN_PROGRESS",
                "面向大田作物，基于多光谱遥感与处方图生成变量施肥决策模型，与无人机飞控系统对接，"
                        + "实现按需施肥、减少氮肥浪费。",
                "决策系统 1 套，示范面积 5000 亩，氮肥用量降低 10%。",
                today.minusMonths(17).atTime(10, 0));
        member(p7, zhang, "负责人", 40);
        member(p7, wang, "参与人", 20);
        budget(p7, 120_000, 100_000, 60_000, 100_000, 20_000);
        milestone(p7, "处方图算法开发", today.minusMonths(10), "DONE", today.minusMonths(10));
        milestone(p7, "飞控对接联调", today.minusMonths(3), "PENDING", null);
        expense(p7, "EQUIPMENT", 112_000, "多光谱相机", zhang, "APPROVED", chen, today.minusMonths(14));
        expense(p7, "MATERIAL", 92_000, "试验用肥与耗材", zhang, "APPROVED", chen, today.minusMonths(9));
        expense(p7, "TRAVEL", 56_000, "示范基地差旅", zhang, "APPROVED", chen, today.minusMonths(6));
        expense(p7, "LABOR", 92_000, "外聘人员劳务", zhang, "APPROVED", zhou, today.minusMonths(3));
        progress(p7, zhang, today.minusMonths(3), 85, "处方图算法完成，与飞控协议对接中，受企业方固件升级影响进度延后。",
                "企业固件版本变更导致接口返工。", "10 月完成联调并开展示范。");
        approvals(p7, zhang, zhao, chen, false, false);

        // 8. 被驳回
        Project p8 = project("PRJ-" + today.getYear() + "-005", "面向边缘计算的低功耗芯片架构研究",
                "NATIONAL", "BASIC", li, "国家自然科学基金", 700_000,
                today.plusMonths(2), today.plusMonths(38), "REJECTED",
                "研究面向边缘推理的低功耗可重构芯片架构，探索存算一体单元在卷积加速中的应用。",
                "发表论文 2 篇，流片 1 次。",
                today.minusDays(20).atTime(9, 45));
        member(p8, li, "负责人", 50);
        budget(p8, 300_000, 100_000, 80_000, 180_000, 40_000);
        record(p8, "SUBMIT", "DRAFT", "SUBMITTED", li, "提交申报", p8.getSubmittedAt());
        record(p8, "APPROVE", "SUBMITTED", "DEPT_APPROVED", zhao, "方向符合部门规划，同意推荐",
                p8.getSubmittedAt().plusDays(3));
        record(p8, "REJECT", "DEPT_APPROVED", "REJECTED", chen,
                "与本年度已立项的「边缘智能芯片」方向重复，建议调整研究内容后另行申报。",
                p8.getSubmittedAt().plusDays(10));

        reconcileBudgetReserved();
        log.info("演示数据初始化完成，共 {} 个项目", projectMapper.selectCount(null));
    }

    // ---------------- helpers ----------------

    private SysUser user(String username, String realName, String role, String dept, String title, String email) {
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(encoder.encode(DEFAULT_PASSWORD));
        user.setRealName(realName);
        user.setRole(role);
        user.setDept(dept);
        user.setTitle(title);
        user.setEmail(email);
        user.setStatus(1);
        user.setCreatedAt(LocalDateTime.now().minusMonths(20));
        userMapper.insert(user);
        return user;
    }

    private Project project(String code, String name, String category, String type, SysUser leader, String source,
                            double budgetTotal, LocalDate start, LocalDate end, String status,
                            String summary, String expectedOutcome, LocalDateTime submittedAt) {
        Project p = new Project();
        p.setCode(code);
        p.setName(name);
        p.setCategory(category);
        p.setType(type);
        p.setLeaderId(leader.getId());
        p.setLeaderName(leader.getRealName());
        p.setDept(leader.getDept());
        p.setSource(source);
        p.setBudgetTotal(BigDecimal.valueOf(budgetTotal));
        p.setStartDate(start);
        p.setEndDate(end);
        p.setStatus(status);
        p.setSummary(summary);
        p.setExpectedOutcome(expectedOutcome);
        p.setCreatedBy(leader.getId());
        p.setCreatedAt(submittedAt.minusDays(1));
        p.setUpdatedAt(submittedAt.plusDays(2));
        if (!"DRAFT".equals(status)) {
            p.setSubmittedAt(submittedAt);
        }
        if ("IN_PROGRESS".equals(status) || "PENDING_ACCEPTANCE".equals(status) || "COMPLETED".equals(status)) {
            p.setApprovedAt(submittedAt.plusDays(12));
        }
        if ("COMPLETED".equals(status)) {
            p.setCompletedAt(end.atTime(15, 0));
        }
        p.setDeleted(0);
        p.setVersion(0L);
        projectMapper.insert(p);
        return p;
    }

    private void member(Project project, SysUser user, String role, int workload) {
        ProjectMember member = new ProjectMember();
        member.setProjectId(project.getId());
        member.setUserId(user.getId());
        member.setUserName(user.getRealName());
        member.setMemberRole(role);
        member.setWorkload(workload);
        memberMapper.insert(member);
    }

    private void budget(Project project, double equipment, double material, double travel, double labor, double other) {
        String[] categories = {"EQUIPMENT", "MATERIAL", "TRAVEL", "LABOR", "OTHER"};
        double[] amounts = {equipment, material, travel, labor, other};
        for (int i = 0; i < categories.length; i++) {
            BudgetItem item = new BudgetItem();
            item.setProjectId(project.getId());
            item.setCategory(categories[i]);
            item.setAmount(BigDecimal.valueOf(amounts[i]));
            item.setReservedAmount(BigDecimal.ZERO);
            budgetMapper.insert(item);
        }
    }

    private void milestone(Project project, String name, LocalDate planDate, String status, LocalDate actualDate) {
        Milestone m = new Milestone();
        m.setProjectId(project.getId());
        m.setName(name);
        m.setPlanDate(planDate);
        m.setStatus(status);
        m.setActualDate(actualDate);
        m.setSortNo((int) (milestoneMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Milestone>()
                        .eq(Milestone::getProjectId, project.getId())) + 1));
        milestoneMapper.insert(m);
    }

    private void expense(Project project, String category, double amount, String purpose, SysUser applicant,
                         String status, SysUser approver, LocalDate date) {
        Expense e = new Expense();
        e.setProjectId(project.getId());
        e.setCategory(category);
        e.setAmount(BigDecimal.valueOf(amount));
        e.setPurpose(purpose);
        e.setExpenseDate(date);
        e.setApplicantId(applicant.getId());
        e.setApplicantName(applicant.getRealName());
        e.setStatus(status);
        if (!"PENDING".equals(status)) {
            e.setApproverId(approver.getId());
            e.setApproverName(approver.getRealName());
            e.setOpinion("APPROVED".equals(status) ? "票据齐全，同意报销" : "超出预算科目额度，退回调整");
            e.setApprovedAt(date.plusDays(2).atTime(10, 0));
        }
        e.setCreatedAt(date.atTime(9, 0));
        e.setVersion(0L);
        expenseMapper.insert(e);
    }

    private void achievement(Project project, String type, String title, String authors, LocalDate date, String level) {
        Achievement a = new Achievement();
        a.setProjectId(project.getId());
        a.setType(type);
        a.setTitle(title);
        a.setAuthors(authors);
        a.setAchievedDate(date);
        a.setLevelName(level);
        a.setCreatedAt(date.atTime(16, 0));
        achievementMapper.insert(a);
    }

    private void progress(Project project, SysUser reporter, LocalDate date, int percent, String content,
                          String issues, String nextPlan) {
        ProgressReport report = new ProgressReport();
        report.setProjectId(project.getId());
        report.setReporterId(reporter.getId());
        report.setReporterName(reporter.getRealName());
        report.setReportDate(date);
        report.setProgressPercent(percent);
        report.setContent(content);
        report.setIssues(issues);
        report.setNextPlan(nextPlan);
        report.setCreatedAt(date.atTime(17, 0));
        progressMapper.insert(report);
    }

    /**
     * 按项目状态补齐审批流水；deptApproved/adminApproved 控制是否已走到相应环节。
     */
    private void approvals(Project project, SysUser leader, SysUser deptAdmin, SysUser admin,
                           boolean accepted, boolean pendingAcceptance) {
        LocalDateTime base = project.getSubmittedAt() == null ? LocalDateTime.now() : project.getSubmittedAt();
        record(project, "SUBMIT", "DRAFT", "SUBMITTED", leader, "提交申报", base);

        boolean deptApproved = !Dicts.ProjectStatus.SUBMITTED.name().equals(project.getStatus())
                && !Dicts.ProjectStatus.REJECTED.name().equals(project.getStatus());
        if (deptApproved && deptAdmin != null) {
            record(project, "APPROVE", "SUBMITTED", "DEPT_APPROVED", deptAdmin,
                    "研究内容与部门方向一致，同意推荐", base.plusDays(3));
        }
        boolean adminApproved = "IN_PROGRESS".equals(project.getStatus())
                || "PENDING_ACCEPTANCE".equals(project.getStatus()) || "COMPLETED".equals(project.getStatus());
        if (adminApproved && admin != null) {
            record(project, "APPROVE", "DEPT_APPROVED", "IN_PROGRESS", admin,
                    "预算结构合理，同意立项", base.plusDays(9));
        }
        if (accepted) {
            record(project, "SUBMIT_ACCEPTANCE", "IN_PROGRESS", "PENDING_ACCEPTANCE", leader,
                    "提交结题材料", project.getEndDate().atTime(10, 0));
            record(project, "ACCEPT_PASS", "PENDING_ACCEPTANCE", "COMPLETED", admin,
                    "任务书指标全部完成，验收通过", project.getEndDate().plusDays(10).atTime(15, 0));
        }
        if (pendingAcceptance) {
            record(project, "SUBMIT_ACCEPTANCE", "IN_PROGRESS", "PENDING_ACCEPTANCE", leader,
                    "已提交结题报告与成果清单，请予验收", LocalDate.now().minusDays(4).atTime(10, 30));
        }
    }

    private void record(Project project, String action, String from, String to, SysUser operator,
                        String opinion, LocalDateTime at) {
        ApprovalRecord r = new ApprovalRecord();
        r.setBizType("PROJECT");
        r.setBizId(project.getId());
        r.setAction(action);
        r.setFromStatus(from);
        r.setToStatus(to);
        r.setOperatorId(operator == null ? null : operator.getId());
        r.setOperatorName(operator == null ? "系统" : operator.getRealName());
        r.setOpinion(opinion);
        r.setCreatedAt(at);
        approvalMapper.insert(r);
    }

    /** 初始化演示数据后，对账预算占用字段。 */
    private void reconcileBudgetReserved() {
        List<BudgetItem> items = budgetMapper.selectList(new LambdaQueryWrapper<>());
        for (BudgetItem item : items) {
            BigDecimal used = expenseMapper.selectList(new LambdaQueryWrapper<Expense>()
                            .eq(Expense::getProjectId, item.getProjectId())
                            .eq(Expense::getCategory, item.getCategory())
                            .in(Expense::getStatus, List.of(Dicts.ExpenseStatus.APPROVED.name(),
                                    Dicts.ExpenseStatus.PENDING.name())))
                    .stream().map(Expense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            item.setReservedAmount(used);
            budgetMapper.updateById(item);
        }
    }
}
