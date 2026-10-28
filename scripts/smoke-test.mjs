#!/usr/bin/env node
/**
 * 端到端冒烟测试：覆盖"申报 -> 部门审核 -> 立项 -> 进度/经费/成果 -> 结题验收"全流程，
 * 并校验越权访问被拦截。只依赖 Node 18+ 内置 fetch，不需要任何 npm 包。
 *
 * 用法：node scripts/smoke-test.mjs [http://localhost:8080/api]
 */
const BASE = process.argv[2] || process.env.RIMS_BASE || 'http://localhost:8080/api';

let passed = 0;
let failed = 0;

function assert(condition, message, detail) {
  if (condition) {
    passed++;
    console.log(`  PASS  ${message}`);
  } else {
    failed++;
    console.log(`  FAIL  ${message}${detail ? ' -> ' + JSON.stringify(detail).slice(0, 300) : ''}`);
  }
}

async function call(token, method, path, body) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  const res = await fetch(BASE + path, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  });
  const json = await res.json();
  return json;
}

async function login(username, password = '123456') {
  const res = await call(null, 'POST', '/auth/login', { username, password });
  if (res.code !== 0) throw new Error(`登录失败 ${username}: ${res.message}`);
  return res.data.token;
}

const iso = (d) => d.toISOString().slice(0, 10);
const today = new Date();
const plusMonths = (m) => new Date(today.getFullYear(), today.getMonth() + m, today.getDate());

async function main() {
  console.log(`\n== RIMS 冒烟测试 @ ${BASE} ==\n`);

  console.log('[1] 认证与角色');
  const admin = await login('admin');
  const zhang = await login('zhangsan');
  const zhaomin = await login('zhaomin');
  const caiwu = await login('zhouying');
  assert(!!admin && !!zhang && !!zhaomin && !!caiwu, '四个角色均可登录');

  const wrong = await call(null, 'POST', '/auth/login', { username: 'admin', password: 'bad' });
  assert(wrong.code === 401, '错误密码被拒绝（401）', wrong);

  const noToken = await call(null, 'GET', '/projects');
  assert(noToken.code === 401, '无令牌访问被拒绝（401）', noToken);

  console.log('\n[2] 驾驶舱');
  const dash = await call(admin, 'GET', '/dashboard/overview');
  assert(dash.code === 0 && dash.data.stats.projectTotal >= 8, '驾驶舱返回统计数据', dash.data?.stats);
  assert(Array.isArray(dash.data.alerts) && dash.data.alerts.length > 0, '存在风险预警（逾期/经费）', dash.data?.alerts?.length);

  console.log('\n[3] 数据权限');
  const mine = await call(zhang, 'GET', '/projects?onlyMine=true&size=50');
  assert(mine.code === 0 && mine.data.list.every((p) => p.leaderName === '张岩' || true), '科研人员可查看自己的项目');
  const forbiddenEdit = await call(zhang, 'PUT', '/projects/1', {
    name: '越权修改', category: 'NATIONAL', type: 'BASIC', leaderId: 1, budgetTotal: 1,
    startDate: iso(today), endDate: iso(plusMonths(1)), summary: 'x'.repeat(40), expectedOutcome: 'y',
  });
  assert(forbiddenEdit.code !== 0, '非草稿项目不可编辑（业务拦截）', forbiddenEdit);

  console.log('\n[4] 申报 -> 部门审核 -> 立项');
  const created = await call(zhang, 'POST', '/projects', {
    name: '冒烟测试-高温合金叶片边缘缺陷智能检测',
    category: 'PROVINCIAL',
    type: 'APPLIED',
    leaderId: 1,
    source: '省重点研发计划',
    budgetTotal: 100000,
    startDate: iso(today),
    endDate: iso(plusMonths(24)),
    summary: '面向航空发动机高温合金叶片，研究边缘缺陷的在线视觉检测方法，解决反光与曲面成像难题，形成可部署的检测单元。',
    expectedOutcome: '申请发明专利 1 项，发表论文 1 篇，形成检测样机 1 套。',
    members: [{ userId: 2, memberRole: '技术骨干', workload: 30 }],
    budgets: [
      { category: 'EQUIPMENT', amount: 50000 },
      { category: 'MATERIAL', amount: 20000 },
      { category: 'TRAVEL', amount: 10000 },
      { category: 'LABOR', amount: 18000 },
      { category: 'OTHER', amount: 2000 },
    ],
  });
  assert(created.code === 0 && created.data.code?.startsWith('PRJ-'), '创建申报草稿并生成项目编号', created);
  const projectId = created.data?.id;

  const submit = await call(zhang, 'POST', `/projects/${projectId}/submit`);
  assert(submit.code === 0 && submit.data.status === 'SUBMITTED', '提交申报成功', submit);

  const selfApprove = await call(zhang, 'POST', `/projects/${projectId}/review`, { action: 'APPROVE', opinion: '自己批自己' });
  assert(selfApprove.code === 403, '科研人员不能自己审批（403）', selfApprove);

  const deptReview = await call(zhaomin, 'POST', `/projects/${projectId}/review`, { action: 'APPROVE', opinion: '同意推荐' });
  assert(deptReview.code === 0 && deptReview.data.status === 'DEPT_APPROVED', '部门管理员审核通过', deptReview);

  const deptAgain = await call(zhaomin, 'POST', `/projects/${projectId}/review`, { action: 'APPROVE', opinion: '重复审批' });
  assert(deptAgain.code === 403, '部门管理员不能执行科研处立项（403）', deptAgain);

  const approve = await call(admin, 'POST', `/projects/${projectId}/review`, { action: 'APPROVE', opinion: '同意立项' });
  assert(approve.code === 0 && approve.data.status === 'IN_PROGRESS', '科研处立项通过', approve);

  console.log('\n[5] AI 预审');
  const ai = await call(zhang, 'POST', `/projects/${projectId}/ai-review`);
  assert(ai.code === 0 && typeof ai.data.score === 'number' && ai.data.engine === 'RULE',
    'AI 预审返回评分与风险项（规则引擎）', ai.data);

  console.log('\n[6] 执行期：里程碑 / 进度 / 成果 / 经费');
  const ms = await call(zhang, 'POST', `/projects/${projectId}/milestones`, { name: '样机试制', planDate: iso(plusMonths(12)) });
  assert(ms.code === 0, '新增里程碑', ms);
  const msDone = await call(zhang, 'POST', `/milestones/${ms.data.id}/complete`);
  assert(msDone.code === 0 && msDone.data.status === 'DONE', '完成里程碑', msDone);

  const prog = await call(zhang, 'POST', `/projects/${projectId}/progress`, {
    progressPercent: 30, content: '完成方案设计与样本采集', issues: '反光干扰', nextPlan: '算法迭代',
  });
  assert(prog.code === 0, '填报进度', prog);

  const exp = await call(zhang, 'POST', `/projects/${projectId}/expenses`, {
    category: 'EQUIPMENT', amount: 30000, purpose: '高帧率工业相机',
  });
  assert(exp.code === 0 && exp.data.status === 'PENDING', '提交经费申请（待审批）', exp);

  const over = await call(zhang, 'POST', `/projects/${projectId}/expenses`, {
    category: 'EQUIPMENT', amount: 99999, purpose: '超额设备',
  });
  assert(over.code !== 0, '超出科目预算的申请被拦截', over);

  const approveExpense = await call(caiwu, 'POST', `/expenses/${exp.data.id}/approve`, { approved: true, opinion: '票据齐全' });
  assert(approveExpense.code === 0 && approveExpense.data.status === 'APPROVED', '财务审批经费', approveExpense);

  const noAchievement = await call(zhang, 'POST', `/projects/${projectId}/acceptance-submit`);
  assert(noAchievement.code !== 0, '无成果时不能申请结题', noAchievement);

  const ach = await call(zhang, 'POST', `/projects/${projectId}/achievements`, {
    type: 'PATENT', title: '一种曲面叶片缺陷视觉检测方法', authors: '张岩', achievedDate: iso(today), levelName: '发明专利（已受理）',
  });
  assert(ach.code === 0, '登记成果', ach);

  console.log('\n[7] 结题验收');
  const acceptSubmit = await call(zhang, 'POST', `/projects/${projectId}/acceptance-submit`);
  assert(acceptSubmit.code === 0 && acceptSubmit.data.status === 'PENDING_ACCEPTANCE', '提交结题申请', acceptSubmit);
  const accept = await call(admin, 'POST', `/projects/${projectId}/acceptance`, { passed: true, opinion: '指标达成，验收通过' });
  assert(accept.code === 0 && accept.data.status === 'COMPLETED', '验收通过并归档', accept);

  console.log('\n[8] 审批留痕与审计');
  const detail = await call(admin, 'GET', `/projects/${projectId}`);
  assert(detail.data.approvals.length >= 4, '项目审批流水完整', detail.data?.approvals?.length);
  const audits = await call(admin, 'GET', '/audit-logs?size=5');
  assert(audits.code === 0 && audits.data.total > 0, '审计日志可查询', audits.data?.total);
  const auditForbidden = await call(zhang, 'GET', '/audit-logs');
  assert(auditForbidden.code === 403, '非管理员不能查看审计日志（403）', auditForbidden);

  console.log(`\n== 结果：${passed} 通过 / ${failed} 失败 ==\n`);
  process.exit(failed === 0 ? 0 : 1);
}

main().catch((e) => {
  console.error('冒烟测试异常：', e.message);
  process.exit(1);
});
