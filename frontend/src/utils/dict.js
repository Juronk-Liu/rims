export const projectStatusOptions = [
  { value: 'DRAFT', label: '草稿', type: 'info' },
  { value: 'SUBMITTED', label: '待部门审核', type: 'warning' },
  { value: 'DEPT_APPROVED', label: '待科研处立项', type: 'warning' },
  { value: 'IN_PROGRESS', label: '执行中', type: 'primary' },
  { value: 'PENDING_ACCEPTANCE', label: '待验收', type: 'warning' },
  { value: 'COMPLETED', label: '已结题', type: 'success' },
  { value: 'REJECTED', label: '已驳回', type: 'danger' },
  { value: 'TERMINATED', label: '已终止', type: 'info' },
];

export const categoryOptions = [
  { value: 'NATIONAL', label: '国家级' },
  { value: 'PROVINCIAL', label: '省部级' },
  { value: 'INSTITUTIONAL', label: '院所级' },
  { value: 'ENTERPRISE', label: '横向课题' },
];

export const projectTypeOptions = [
  { value: 'BASIC', label: '基础研究' },
  { value: 'APPLIED', label: '应用研究' },
  { value: 'DEVELOPMENT', label: '技术开发' },
  { value: 'SOFT_SCIENCE', label: '软科学' },
];

export const expenseCategoryOptions = [
  { value: 'EQUIPMENT', label: '设备费' },
  { value: 'MATERIAL', label: '材料费' },
  { value: 'TRAVEL', label: '差旅/会议费' },
  { value: 'LABOR', label: '劳务费' },
  { value: 'OTHER', label: '其他' },
];

export const expenseStatusMap = {
  PENDING: { label: '待审批', type: 'warning' },
  APPROVED: { label: '已通过', type: 'success' },
  REJECTED: { label: '已驳回', type: 'danger' },
};

export const achievementTypeOptions = [
  { value: 'PAPER', label: '论文' },
  { value: 'PATENT', label: '专利' },
  { value: 'SOFTWARE', label: '软件著作权' },
  { value: 'STANDARD', label: '标准' },
  { value: 'REPORT', label: '研究报告' },
];

export const roleOptions = [
  { value: 'RESEARCHER', label: '科研人员' },
  { value: 'DEPT_ADMIN', label: '部门管理员' },
  { value: 'ADMIN', label: '科研处管理员' },
  { value: 'FINANCE', label: '财务' },
  { value: 'LEADER', label: '院领导' },
];

export const approvalActionMap = {
  SUBMIT: '提交申报',
  APPROVE: '审批通过',
  REJECT: '驳回',
  SUBMIT_ACCEPTANCE: '申请结题',
  ACCEPT_PASS: '验收通过',
  ACCEPT_REJECT: '验收不通过',
  TERMINATE: '终止项目',
};

const toMap = (options) => Object.fromEntries(options.map((o) => [o.value, o]));

export const projectStatusMap = toMap(projectStatusOptions);
export const categoryMap = toMap(categoryOptions);
export const projectTypeMap = toMap(projectTypeOptions);
export const expenseCategoryMap = toMap(expenseCategoryOptions);
export const achievementTypeMap = toMap(achievementTypeOptions);

export const roleLabel = (role) => roleOptions.find((r) => r.value === role)?.label || role;
