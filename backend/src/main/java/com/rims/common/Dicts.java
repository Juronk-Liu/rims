package com.rims.common;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 字典集中定义：后端做校验、前端取标签，避免两侧各写一份中文。
 */
public final class Dicts {

    private Dicts() {
    }

    public enum ProjectStatus {
        DRAFT("草稿"),
        SUBMITTED("待部门审核"),
        DEPT_APPROVED("待科研处立项"),
        IN_PROGRESS("执行中"),
        PENDING_ACCEPTANCE("待验收"),
        COMPLETED("已结题"),
        REJECTED("已驳回"),
        TERMINATED("已终止");

        private final String label;

        ProjectStatus(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public enum Role {
        RESEARCHER("科研人员"),
        DEPT_ADMIN("部门管理员"),
        ADMIN("科研处管理员"),
        FINANCE("财务"),
        LEADER("院领导");

        private final String label;

        Role(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public enum ProjectCategory {
        NATIONAL("国家级"),
        PROVINCIAL("省部级"),
        INSTITUTIONAL("院所级"),
        ENTERPRISE("横向课题");

        private final String label;

        ProjectCategory(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public enum ProjectType {
        BASIC("基础研究"),
        APPLIED("应用研究"),
        DEVELOPMENT("技术开发"),
        SOFT_SCIENCE("软科学");

        private final String label;

        ProjectType(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public enum ExpenseCategory {
        EQUIPMENT("设备费"),
        MATERIAL("材料费"),
        TRAVEL("差旅/会议费"),
        LABOR("劳务费"),
        OTHER("其他");

        private final String label;

        ExpenseCategory(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public enum ExpenseStatus {
        PENDING("待审批"),
        APPROVED("已通过"),
        REJECTED("已驳回");

        private final String label;

        ExpenseStatus(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public enum MilestoneStatus {
        PENDING("待完成"),
        DONE("已完成"),
        DELAYED("已逾期");

        private final String label;

        MilestoneStatus(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public enum AchievementType {
        PAPER("论文"),
        PATENT("专利"),
        SOFTWARE("软件著作权"),
        STANDARD("标准"),
        REPORT("研究报告");

        private final String label;

        AchievementType(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    private static List<Map<String, String>> options(Enum<?>[] values, java.util.function.Function<Enum<?>, String> labeler) {
        return Arrays.stream(values).map(v -> {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("value", v.name());
            item.put("label", labeler.apply(v));
            return item;
        }).toList();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Map<String, Object> all() {
        Map<String, Object> dict = new LinkedHashMap<>();
        dict.put("projectStatus", options(ProjectStatus.values(), v -> ((ProjectStatus) v).getLabel()));
        dict.put("role", options(Role.values(), v -> ((Role) v).getLabel()));
        dict.put("projectCategory", options(ProjectCategory.values(), v -> ((ProjectCategory) v).getLabel()));
        dict.put("projectType", options(ProjectType.values(), v -> ((ProjectType) v).getLabel()));
        dict.put("expenseCategory", options(ExpenseCategory.values(), v -> ((ExpenseCategory) v).getLabel()));
        dict.put("expenseStatus", options(ExpenseStatus.values(), v -> ((ExpenseStatus) v).getLabel()));
        dict.put("milestoneStatus", options(MilestoneStatus.values(), v -> ((MilestoneStatus) v).getLabel()));
        dict.put("achievementType", options(AchievementType.values(), v -> ((AchievementType) v).getLabel()));
        return dict;
    }
}
