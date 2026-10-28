<template>
  <div class="page" v-loading="loading">
    <template v-if="detail">
      <div class="back-line">
        <el-button link :icon="ArrowLeft" @click="router.push('/projects')">返回项目库</el-button>
      </div>

      <div class="card header-card">
        <div class="header-main">
          <div class="header-tags">
            <el-tag :type="statusMeta.type" effect="light" size="small">{{ statusMeta.label }}</el-tag>
            <span class="code mono">{{ project.code }}</span>
            <span class="dot-sep">·</span>
            <span class="tiny">{{ categoryMap[project.category]?.label }} / {{ projectTypeMap[project.type]?.label }}</span>
          </div>
          <h1 class="title">{{ project.name }}</h1>
          <div class="header-meta">
            <span><el-icon><User /></el-icon>{{ project.leaderName }}</span>
            <span><el-icon><OfficeBuilding /></el-icon>{{ project.dept }}</span>
            <span><el-icon><Calendar /></el-icon>{{ date(project.startDate) }} ~ {{ date(project.endDate) }}</span>
            <span><el-icon><Coin /></el-icon>预算 {{ money(project.budgetTotal) }}</span>
          </div>
        </div>
        <div class="header-actions">
          <el-button v-if="canEditProject" @click="router.push(`/projects/${project.id}/edit`)">编辑</el-button>
          <el-button v-if="canSubmit" type="primary" @click="submitProject">提交申报</el-button>
          <el-button v-if="canDeptReview" type="success" @click="openReview('APPROVE')">部门审核</el-button>
          <el-button v-if="canDeptReview" type="danger" plain @click="openReview('REJECT')">驳回</el-button>
          <el-button v-if="canAdminApprove" type="success" @click="openReview('APPROVE')">立项审批</el-button>
          <el-button v-if="canAdminApprove" type="danger" plain @click="openReview('REJECT')">驳回</el-button>
          <el-button v-if="canSubmitAcceptance" type="primary" @click="submitAcceptance">申请结题</el-button>
          <el-button v-if="canAccept" type="success" @click="openAcceptance">结题验收</el-button>
          <el-dropdown v-if="canTerminate" @command="openTerminate">
            <el-button>更多<el-icon class="el-icon--right"><ArrowDown /></el-icon></el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="terminate">终止项目</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>

      <div class="body-grid">
        <div class="main-col">
          <div class="card tabs-card">
            <el-tabs v-model="activeTab">
              <el-tab-pane label="项目概览" name="overview">
                <div class="pane">
                  <div class="field-block">
                    <div class="field-label">研究内容</div>
                    <div class="field-text">{{ project.summary || '未填写' }}</div>
                  </div>
                  <div class="field-block">
                    <div class="field-label">预期成果</div>
                    <div class="field-text">{{ project.expectedOutcome || '未填写' }}</div>
                  </div>

                  <div class="field-block">
                    <div class="field-label">审批流转</div>
                    <el-timeline class="timeline">
                      <el-timeline-item v-for="record in approvals" :key="record.id"
                        :timestamp="datetime(record.createdAt)" placement="top"
                        :type="isReject(record) ? 'danger' : 'primary'">
                        <div class="timeline-row">
                          <span class="timeline-action">{{ approvalActionMap[record.action] || record.action }}</span>
                          <span class="tiny">{{ record.operatorName }}</span>
                        </div>
                        <div v-if="record.opinion" class="timeline-opinion">{{ record.opinion }}</div>
                      </el-timeline-item>
                      <el-timeline-item v-if="!approvals.length" timestamp="" type="info">
                        <span class="muted">尚无审批记录</span>
                      </el-timeline-item>
                    </el-timeline>
                  </div>
                </div>
              </el-tab-pane>

              <el-tab-pane name="milestones">
                <template #label>里程碑 <span class="tab-count">{{ milestones.length }}</span></template>
                <div class="pane">
                  <div class="pane-toolbar">
                    <span class="tiny">按计划节点跟踪执行节奏，逾期节点会进入工作台预警</span>
                    <el-button v-if="canOperate" size="small" type="primary" plain @click="milestoneDialog = true">
                      新增节点
                    </el-button>
                  </div>
                  <div v-if="!milestones.length" class="empty-block">暂无里程碑</div>
                  <div v-else class="milestone-list">
                    <div v-for="(item, index) in milestones" :key="item.id" class="milestone-item">
                      <div class="milestone-index" :class="item.status.toLowerCase()">{{ index + 1 }}</div>
                      <div class="milestone-body">
                        <div class="milestone-name">{{ item.name }}</div>
                        <div class="tiny">
                          计划 {{ date(item.planDate) }}
                          <span v-if="item.actualDate">· 实际 {{ date(item.actualDate) }}</span>
                        </div>
                      </div>
                      <el-tag size="small" :type="milestoneType(item)" effect="light">
                        {{ milestoneLabel(item) }}
                      </el-tag>
                      <el-button v-if="canOperate && item.status !== 'DONE'" link type="primary" size="small"
                        @click="completeMilestone(item)">标记完成</el-button>
                    </div>
                  </div>
                </div>
              </el-tab-pane>

              <el-tab-pane name="progress">
                <template #label>进度填报 <span class="tab-count">{{ progressReports.length }}</span></template>
                <div class="pane">
                  <div class="pane-toolbar">
                    <span class="tiny">最近一次填报进度：{{ stats.progressPercent || 0 }}%</span>
                    <el-button v-if="canOperate" size="small" type="primary" plain @click="progressDialog = true">
                      填报本期进度
                    </el-button>
                  </div>
                  <div v-if="!progressReports.length" class="empty-block">暂无进度记录</div>
                  <div v-else class="progress-list">
                    <div v-for="report in progressReports" :key="report.id" class="progress-item">
                      <div class="progress-head">
                        <span class="progress-date">{{ date(report.reportDate) }}</span>
                        <el-progress :percentage="report.progressPercent" :stroke-width="6" style="width: 180px" />
                        <span class="tiny">{{ report.reporterName }}</span>
                      </div>
                      <div class="progress-content">{{ report.content }}</div>
                      <div v-if="report.issues" class="progress-sub"><b>存在问题：</b>{{ report.issues }}</div>
                      <div v-if="report.nextPlan" class="progress-sub"><b>下期计划：</b>{{ report.nextPlan }}</div>
                    </div>
                  </div>
                </div>
              </el-tab-pane>

              <el-tab-pane name="finance">
                <template #label>经费 <span class="tab-count">{{ expenses.length }}</span></template>
                <div class="pane">
                  <div class="budget-summary">
                    <div v-for="row in budgetRows" :key="row.category" class="budget-cell">
                      <div class="tiny">{{ row.label }}</div>
                      <div class="budget-amount mono">{{ money(row.used) }}</div>
                      <el-progress :percentage="row.rate" :stroke-width="5" :show-text="false"
                        :color="row.rate > 95 ? '#ef4444' : row.rate > 75 ? '#f59e0b' : '#2f5bff'" />
                      <div class="budget-foot">
                        <span>预算 {{ wan(row.budget) }}</span>
                        <span>剩余 {{ wan(row.remain) }}</span>
                      </div>
                    </div>
                  </div>

                  <div class="pane-toolbar" style="margin-top: 18px">
                    <span class="section-title" style="margin: 0">支出申请</span>
                    <div class="toolbar-right">
                      <el-button v-if="canCreateExpense" size="small" type="primary" plain @click="expenseDialog = true">
                        申请经费
                      </el-button>
                    </div>
                  </div>

                  <el-table :data="expenses" size="small">
                    <el-table-column label="科目" width="94">
                      <template #default="{ row }">{{ expenseCategoryMap[row.category]?.label }}</template>
                    </el-table-column>
                    <el-table-column label="金额" width="112">
                      <template #default="{ row }"><span class="mono">{{ money(row.amount) }}</span></template>
                    </el-table-column>
                    <el-table-column prop="purpose" label="用途" min-width="170" show-overflow-tooltip />
                    <el-table-column label="申请人" width="86" prop="applicantName" />
                    <el-table-column label="日期" width="100">
                      <template #default="{ row }">{{ date(row.expenseDate) }}</template>
                    </el-table-column>
                    <el-table-column label="状态" width="92">
                      <template #default="{ row }">
                        <el-tooltip :content="row.approverName ? `审批人：${row.approverName}` : '待财务审批'" placement="top">
                          <el-tag size="small" effect="light" :type="expenseStatusMap[row.status]?.type">
                            {{ expenseStatusMap[row.status]?.label }}
                          </el-tag>
                        </el-tooltip>
                      </template>
                    </el-table-column>
                    <el-table-column v-if="canApproveExpense" label="操作" width="120" fixed="right">
                      <template #default="{ row }">
                        <template v-if="row.status === 'PENDING'">
                          <el-button link type="success" size="small" @click="approveExpense(row, true)">通过</el-button>
                          <el-button link type="danger" size="small" @click="approveExpense(row, false)">驳回</el-button>
                        </template>
                      </template>
                    </el-table-column>
                    <template #empty><div class="empty-block">暂无支出记录</div></template>
                  </el-table>
                </div>
              </el-tab-pane>

              <el-tab-pane name="achievements">
                <template #label>成果 <span class="tab-count">{{ achievements.length }}</span></template>
                <div class="pane">
                  <div class="pane-toolbar">
                    <span class="tiny">结题前至少登记 1 项成果，验收时与预期成果核对</span>
                    <el-button v-if="canAddAchievement" size="small" type="primary" plain @click="achievementDialog = true">
                      登记成果
                    </el-button>
                  </div>
                  <el-table :data="achievements" size="small">
                    <el-table-column label="类型" width="110">
                      <template #default="{ row }">
                        <el-tag size="small" effect="plain">{{ achievementTypeMap[row.type]?.label }}</el-tag>
                      </template>
                    </el-table-column>
                    <el-table-column prop="title" label="成果名称" min-width="260" show-overflow-tooltip />
                    <el-table-column prop="authors" label="完成人" width="150" show-overflow-tooltip />
                    <el-table-column prop="levelName" label="级别/状态" width="160" show-overflow-tooltip />
                    <el-table-column label="日期" width="110">
                      <template #default="{ row }">{{ date(row.achievedDate) }}</template>
                    </el-table-column>
                    <el-table-column v-if="canOperate" label="操作" width="80">
                      <template #default="{ row }">
                        <el-button link type="danger" size="small" @click="removeAchievement(row)">删除</el-button>
                      </template>
                    </el-table-column>
                    <template #empty><div class="empty-block">暂未登记成果</div></template>
                  </el-table>
                </div>
              </el-tab-pane>
            </el-tabs>
          </div>
        </div>

        <aside class="side-col">
          <div class="card card-pad">
            <div class="section-title"><span>执行概况</span></div>
            <div class="ring-wrap">
              <el-progress type="dashboard" :percentage="stats.progressPercent || 0" :width="112" :stroke-width="9"
                color="#2f5bff">
                <template #default="{ percentage }">
                  <div class="ring-value">{{ percentage }}%</div>
                  <div class="tiny">整体进度</div>
                </template>
              </el-progress>
            </div>
            <div class="mini-stats">
              <div class="mini-stat">
                <div class="tiny">预算执行</div>
                <div class="mini-value">{{ stats.executionRate || 0 }}%</div>
              </div>
              <div class="mini-stat">
                <div class="tiny">里程碑</div>
                <div class="mini-value">{{ stats.milestoneDone }}/{{ stats.milestoneTotal }}</div>
              </div>
              <div class="mini-stat">
                <div class="tiny">成果</div>
                <div class="mini-value">{{ stats.achievementCount }}</div>
              </div>
            </div>
            <div class="exec-bar">
              <div class="exec-bar-head">
                <span class="tiny">已支出 {{ money(stats.approvedExpense) }}</span>
                <span class="tiny">待审批 {{ money(stats.pendingExpense) }}</span>
              </div>
              <el-progress :percentage="Math.min(100, stats.executionRate || 0)" :stroke-width="8" :show-text="false"
                :color="stats.executionRate > 90 ? '#ef4444' : '#2f5bff'" />
            </div>
            <div class="deadline" :class="{ danger: stats.overdue }">
              <el-icon><Clock /></el-icon>
              {{ stats.overdue ? `项目已逾期 ${Math.abs(stats.daysLeft)} 天` : daysLeftText(stats.daysLeft) }}
            </div>
          </div>

          <div class="card card-pad">
            <div class="section-title"><span>项目信息</span></div>
            <div class="info-list">
              <div class="info-row"><span>经费来源</span><b>{{ project.source || '—' }}</b></div>
              <div class="info-row"><span>负责人</span><b>{{ project.leaderName }}</b></div>
              <div class="info-row"><span>所属部门</span><b>{{ project.dept }}</b></div>
              <div class="info-row"><span>立项时间</span><b>{{ date(project.approvedAt) }}</b></div>
              <div class="info-row"><span>结题时间</span><b>{{ date(project.completedAt) }}</b></div>
            </div>
          </div>

          <div class="card card-pad">
            <div class="section-title">
              <span>项目团队</span>
              <span class="tiny">{{ members.length }} 人</span>
            </div>
            <div class="member-list">
              <div v-for="member in members" :key="member.id" class="member-row">
                <span class="avatar-sm" :class="{ leader: member.memberRole === '负责人' }">
                  {{ (member.userName || '?').slice(-2) }}
                </span>
                <div class="member-info">
                  <div class="member-name">{{ member.userName }}</div>
                  <div class="tiny">{{ member.memberRole }} · 投入 {{ member.workload || 0 }}%</div>
                </div>
              </div>
            </div>
          </div>
        </aside>
      </div>
    </template>

    <el-dialog v-model="reviewDialog" :title="reviewForm.action === 'APPROVE' ? '审批通过' : '驳回申请'" width="480px">
      <el-alert v-if="reviewForm.action === 'APPROVE'" type="info" :closable="false" show-icon
        :title="reviewHint" style="margin-bottom: 14px" />
      <el-form label-position="top">
        <el-form-item :label="reviewForm.action === 'APPROVE' ? '审批意见（选填）' : '驳回原因（必填）'">
          <el-input v-model="reviewForm.opinion" type="textarea" :rows="4" maxlength="300" show-word-limit
            placeholder="请填写审批意见，将记录在审批流水中" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reviewDialog = false">取消</el-button>
        <el-button :type="reviewForm.action === 'APPROVE' ? 'success' : 'danger'" :loading="submitting"
          @click="submitReview">确认</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="milestoneDialog" title="新增里程碑" width="460px">
      <el-form label-position="top">
        <el-form-item label="节点名称" required>
          <el-input v-model="milestoneForm.name" placeholder="例如：样机集成与联调" />
        </el-form-item>
        <el-form-item label="计划完成日期">
          <el-date-picker v-model="milestoneForm.planDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="milestoneDialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="addMilestone">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="progressDialog" title="填报本期进度" width="560px">
      <el-form label-position="top">
        <el-form-item label="整体进度（%）">
          <el-slider v-model="progressForm.progressPercent" :max="100" show-input />
        </el-form-item>
        <el-form-item label="本期工作内容" required>
          <el-input v-model="progressForm.content" type="textarea" :rows="3" maxlength="800" show-word-limit />
        </el-form-item>
        <el-form-item label="存在问题">
          <el-input v-model="progressForm.issues" type="textarea" :rows="2" maxlength="400" />
        </el-form-item>
        <el-form-item label="下期计划">
          <el-input v-model="progressForm.nextPlan" type="textarea" :rows="2" maxlength="400" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="progressDialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="addProgress">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="expenseDialog" title="经费申请" width="500px">
      <el-form label-position="top">
        <el-form-item label="费用科目" required>
          <el-select v-model="expenseForm.category" style="width: 100%">
            <el-option v-for="row in budgetRows" :key="row.category" :value="row.category"
              :label="`${row.label}（剩余 ${money(row.remain)}）`" :disabled="row.remain <= 0" />
          </el-select>
        </el-form-item>
        <el-form-item label="申请金额（元）" required>
          <el-input-number v-model="expenseForm.amount" :min="0" :step="1000" :precision="2"
            controls-position="right" style="width: 100%" />
          <div class="tiny" style="margin-top: 4px">当前科目剩余 {{ money(selectedRemain) }}，超出将被拦截</div>
        </el-form-item>
        <el-form-item label="用途说明">
          <el-input v-model="expenseForm.purpose" type="textarea" :rows="3" maxlength="200" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="expenseDialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="createExpense">提交申请</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="achievementDialog" title="登记成果" width="520px">
      <el-form label-position="top">
        <el-form-item label="成果类型" required>
          <el-select v-model="achievementForm.type" style="width: 100%">
            <el-option v-for="item in achievementTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="成果名称" required>
          <el-input v-model="achievementForm.title" maxlength="200" />
        </el-form-item>
        <el-form-item label="完成人">
          <el-input v-model="achievementForm.authors" placeholder="多人用顿号分隔" />
        </el-form-item>
        <el-form-item label="级别 / 状态">
          <el-input v-model="achievementForm.levelName" placeholder="例如：发明专利（已受理）/ SCI 二区" />
        </el-form-item>
        <el-form-item label="取得日期">
          <el-date-picker v-model="achievementForm.achievedDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="achievementDialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="addAchievement">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="acceptanceDialog" title="结题验收" width="500px">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom: 14px"
        title="请核对任务书指标、成果清单与经费执行情况后给出验收结论" />
      <el-form label-position="top">
        <el-form-item label="验收结论">
          <el-radio-group v-model="acceptanceForm.passed">
            <el-radio :value="true">验收通过</el-radio>
            <el-radio :value="false">验收不通过，退回整改</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="验收意见">
          <el-input v-model="acceptanceForm.opinion" type="textarea" :rows="4" maxlength="300" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="acceptanceDialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitAcceptanceResult">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="terminateDialog" title="终止项目" width="480px">
      <el-alert type="warning" :closable="false" show-icon style="margin-bottom: 14px"
        title="终止属不可逆操作，项目将停止经费与进度管理，请填写终止原因" />
      <el-input v-model="terminateReason" type="textarea" :rows="4" maxlength="300" show-word-limit />
      <template #footer>
        <el-button @click="terminateDialog = false">取消</el-button>
        <el-button type="danger" :loading="submitting" @click="terminate">确认终止</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { ArrowLeft, ArrowDown, Calendar, Clock, Coin, OfficeBuilding, User } from '@element-plus/icons-vue';
import { financeApi, progressApi, projectApi } from '@/api';
import { useAuthStore } from '@/stores/auth';
import {
  achievementTypeMap, achievementTypeOptions, approvalActionMap, categoryMap, expenseCategoryMap,
  expenseStatusMap, projectStatusMap, projectTypeMap,
} from '@/utils/dict';
import { date, datetime, daysLeftText, money, wan } from '@/utils/format';

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();

const loading = ref(false);
const submitting = ref(false);
const detail = ref(null);
const activeTab = ref('overview');

const reviewDialog = ref(false);
const reviewForm = reactive({ action: 'APPROVE', opinion: '' });
const milestoneDialog = ref(false);
const milestoneForm = reactive({ name: '', planDate: '' });
const progressDialog = ref(false);
const progressForm = reactive({ progressPercent: 0, content: '', issues: '', nextPlan: '' });
const expenseDialog = ref(false);
const expenseForm = reactive({ category: 'MATERIAL', amount: 0, purpose: '' });
const achievementDialog = ref(false);
const achievementForm = reactive({ type: 'PAPER', title: '', authors: '', levelName: '', achievedDate: '' });
const acceptanceDialog = ref(false);
const acceptanceForm = reactive({ passed: true, opinion: '' });
const terminateDialog = ref(false);
const terminateReason = ref('');

const project = computed(() => detail.value?.project || {});
const members = computed(() => detail.value?.members || []);
const milestones = computed(() => detail.value?.milestones || []);
const progressReports = computed(() => detail.value?.progressReports || []);
const budgets = computed(() => detail.value?.budgets || []);
const expenses = computed(() => detail.value?.expenses || []);
const achievements = computed(() => detail.value?.achievements || []);
const approvals = computed(() => detail.value?.approvals || []);
const stats = computed(() => detail.value?.stats || {});
const statusMeta = computed(() => projectStatusMap[project.value.status] || { label: project.value.status, type: 'info' });

const isManager = computed(() => auth.isAdmin || project.value.leaderId === auth.user?.id);
const canEditProject = computed(() => isManager.value && ['DRAFT', 'REJECTED'].includes(project.value.status));
const canSubmit = computed(() => canEditProject.value);
const canDeptReview = computed(() => project.value.status === 'SUBMITTED'
  && (auth.isAdmin || (auth.isDeptAdmin && project.value.dept === auth.user?.dept)));
const canAdminApprove = computed(() => project.value.status === 'DEPT_APPROVED' && auth.isAdmin);
const canSubmitAcceptance = computed(() => isManager.value && project.value.status === 'IN_PROGRESS'
  && !auth.isFinance);
const canAccept = computed(() => project.value.status === 'PENDING_ACCEPTANCE' && auth.isAdmin);
const canTerminate = computed(() => auth.isAdmin && ['IN_PROGRESS', 'PENDING_ACCEPTANCE'].includes(project.value.status));
const canOperate = computed(() => isManager.value
  && ['IN_PROGRESS', 'PENDING_ACCEPTANCE'].includes(project.value.status));
const canAddAchievement = computed(() => isManager.value && project.value.status !== 'DRAFT');
const canCreateExpense = computed(() => canOperate.value);
const canApproveExpense = computed(() => (auth.isFinance || auth.isAdmin)
  && ['IN_PROGRESS', 'PENDING_ACCEPTANCE'].includes(project.value.status));

const reviewHint = computed(() => project.value.status === 'SUBMITTED'
  ? '通过后将进入科研处立项审批'
  : '通过后项目正式立项，进入执行阶段');

const budgetRows = computed(() => {
  const approved = {};
  const pending = {};
  expenses.value.forEach((e) => {
    const target = e.status === 'APPROVED' ? approved : e.status === 'PENDING' ? pending : null;
    if (target) target[e.category] = (target[e.category] || 0) + Number(e.amount);
  });
  return budgets.value.map((b) => {
    const used = approved[b.category] || 0;
    const pendingAmount = pending[b.category] || 0;
    const budget = Number(b.amount);
    return {
      category: b.category,
      label: expenseCategoryMap[b.category]?.label || b.category,
      budget,
      used,
      pending: pendingAmount,
      remain: Math.max(0, budget - used - pendingAmount),
      rate: budget ? Math.min(100, Math.round((used / budget) * 100)) : 0,
    };
  });
});

const selectedRemain = computed(() => budgetRows.value.find((r) => r.category === expenseForm.category)?.remain || 0);

const milestoneLabel = (item) => {
  if (item.status === 'DONE') return '已完成';
  if (item.planDate && new Date(item.planDate) < new Date() && item.status === 'PENDING') return '已逾期';
  return '进行中';
};
const milestoneType = (item) => {
  if (item.status === 'DONE') return 'success';
  if (item.planDate && new Date(item.planDate) < new Date()) return 'danger';
  return 'info';
};
const isReject = (record) => ['REJECT', 'ACCEPT_REJECT', 'TERMINATE'].includes(record.action);

const load = async () => {
  loading.value = true;
  try {
    const res = await projectApi.detail(route.params.id);
    detail.value = res.data;
    progressForm.progressPercent = res.data.stats.progressPercent || 0;
    expenseForm.category = res.data.budgets.find((b) => Number(b.amount) > 0)?.category || 'MATERIAL';
  } finally {
    loading.value = false;
  }
};

const submitProject = async () => {
  await ElMessageBox.confirm('提交后进入审批流程，审批期间不可修改，确认提交？', '提交申报', { type: 'warning' });
  await projectApi.submit(project.value.id);
  ElMessage.success('提交成功，等待部门审核');
  load();
};

const openReview = (action) => {
  reviewForm.action = action;
  reviewForm.opinion = '';
  reviewDialog.value = true;
};

const submitReview = async () => {
  if (reviewForm.action === 'REJECT' && !reviewForm.opinion.trim()) {
    ElMessage.warning('驳回必须填写原因');
    return;
  }
  submitting.value = true;
  try {
    await projectApi.review(project.value.id, { ...reviewForm });
    ElMessage.success(reviewForm.action === 'APPROVE' ? '审批已通过' : '已驳回');
    reviewDialog.value = false;
    load();
  } finally {
    submitting.value = false;
  }
};

const addMilestone = async () => {
  if (!milestoneForm.name.trim()) {
    ElMessage.warning('请填写节点名称');
    return;
  }
  submitting.value = true;
  try {
    await progressApi.addMilestone(project.value.id, { ...milestoneForm });
    ElMessage.success('已新增里程碑');
    milestoneDialog.value = false;
    milestoneForm.name = '';
    milestoneForm.planDate = '';
    load();
  } finally {
    submitting.value = false;
  }
};

const completeMilestone = async (item) => {
  await progressApi.completeMilestone(item.id);
  ElMessage.success('里程碑已完成');
  load();
};

const addProgress = async () => {
  if (!progressForm.content.trim()) {
    ElMessage.warning('请填写本期工作内容');
    return;
  }
  submitting.value = true;
  try {
    await progressApi.addReport(project.value.id, { ...progressForm });
    ElMessage.success('进度已填报');
    progressDialog.value = false;
    progressForm.content = '';
    progressForm.issues = '';
    progressForm.nextPlan = '';
    load();
  } finally {
    submitting.value = false;
  }
};

const createExpense = async () => {
  if (!expenseForm.amount || expenseForm.amount <= 0) {
    ElMessage.warning('请填写申请金额');
    return;
  }
  submitting.value = true;
  try {
    await financeApi.createExpense(project.value.id, { ...expenseForm });
    ElMessage.success('申请已提交，等待财务审批');
    expenseDialog.value = false;
    expenseForm.amount = 0;
    expenseForm.purpose = '';
    load();
  } finally {
    submitting.value = false;
  }
};

const approveExpense = async (row, approved) => {
  const { value } = await ElMessageBox.prompt(
    approved ? '确认通过该笔支出？' : '请填写驳回原因',
    approved ? '通过支出' : '驳回支出',
    { inputValue: approved ? '票据齐全，同意报销' : '', inputPlaceholder: '审批意见' }
  );
  await financeApi.approve(row.id, { approved, opinion: value });
  ElMessage.success('已处理');
  load();
};

const addAchievement = async () => {
  if (!achievementForm.title.trim()) {
    ElMessage.warning('请填写成果名称');
    return;
  }
  submitting.value = true;
  try {
    await progressApi.addAchievement(project.value.id, { ...achievementForm });
    ElMessage.success('成果已登记');
    achievementDialog.value = false;
    achievementForm.title = '';
    achievementForm.authors = '';
    achievementForm.levelName = '';
    load();
  } finally {
    submitting.value = false;
  }
};

const removeAchievement = async (row) => {
  await ElMessageBox.confirm(`确认删除「${row.title}」？`, '删除成果', { type: 'warning' });
  await progressApi.removeAchievement(row.id);
  ElMessage.success('已删除');
  load();
};

const submitAcceptance = async () => {
  await ElMessageBox.confirm('确认提交结题申请？提交后进入科研处验收环节。', '申请结题', { type: 'warning' });
  await projectApi.acceptanceSubmit(project.value.id);
  ElMessage.success('已提交结题申请');
  load();
};

const openAcceptance = () => {
  acceptanceForm.passed = true;
  acceptanceForm.opinion = '';
  acceptanceDialog.value = true;
};

const submitAcceptanceResult = async () => {
  submitting.value = true;
  try {
    await projectApi.acceptance(project.value.id, { ...acceptanceForm });
    ElMessage.success('验收结论已记录');
    acceptanceDialog.value = false;
    load();
  } finally {
    submitting.value = false;
  }
};

const openTerminate = () => {
  terminateReason.value = '';
  terminateDialog.value = true;
};

const terminate = async () => {
  if (!terminateReason.value.trim()) {
    ElMessage.warning('请填写终止原因');
    return;
  }
  submitting.value = true;
  try {
    await projectApi.terminate(project.value.id, { action: 'TERMINATE', opinion: terminateReason.value });
    ElMessage.success('项目已终止');
    terminateDialog.value = false;
    load();
  } finally {
    submitting.value = false;
  }
};

onMounted(load);
</script>

<style scoped>
.back-line {
  margin-bottom: 10px;
}

.header-card {
  padding: 22px 24px;
  display: flex;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 16px;
  background: linear-gradient(180deg, #fbfcff 0%, #ffffff 55%);
}

.header-tags {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.code {
  font-size: 12px;
  color: var(--rims-text-muted);
}

.dot-sep {
  color: #ccd4e2;
}

.title {
  font-size: 22px;
  font-weight: 650;
  margin: 12px 0 12px;
  line-height: 1.4;
}

.header-meta {
  display: flex;
  gap: 20px;
  flex-wrap: wrap;
  font-size: 12.5px;
  color: var(--rims-text-secondary);
}

.header-meta span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.header-actions {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
  max-width: 520px;
}

.body-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 16px;
  align-items: start;
}

.tabs-card {
  padding: 6px 20px 20px;
}

.pane {
  padding-top: 6px;
}

.field-block {
  margin-bottom: 22px;
}

.field-label {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 8px;
  color: var(--rims-text);
}

.field-text {
  font-size: 13.5px;
  line-height: 1.9;
  color: var(--rims-text-secondary);
  white-space: pre-wrap;
}

.timeline {
  padding-left: 4px;
  margin-top: 10px;
}

.timeline-row {
  display: flex;
  gap: 10px;
  align-items: baseline;
}

.timeline-action {
  font-size: 13px;
  font-weight: 600;
}

.timeline-opinion {
  font-size: 12.5px;
  color: var(--rims-text-secondary);
  margin-top: 4px;
  line-height: 1.7;
}

.tab-count {
  font-size: 11px;
  color: var(--rims-text-muted);
  background: #f1f4fa;
  border-radius: 8px;
  padding: 1px 6px;
  margin-left: 2px;
}

.pane-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.toolbar-right {
  display: flex;
  gap: 8px;
}

.milestone-list {
  display: grid;
  gap: 8px;
}

.milestone-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 14px;
  border: 1px solid var(--rims-border);
  border-radius: 10px;
}

.milestone-index {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: #f1f4fa;
  color: var(--rims-text-secondary);
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  flex: none;
}

.milestone-index.done {
  background: #e9f8f2;
  color: #0f9d76;
}

.milestone-body {
  flex: 1;
  min-width: 0;
}

.milestone-name {
  font-size: 13.5px;
  font-weight: 500;
  margin-bottom: 3px;
}

.progress-list {
  display: grid;
  gap: 12px;
}

.progress-item {
  border: 1px solid var(--rims-border);
  border-radius: 10px;
  padding: 14px 16px;
}

.progress-head {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 8px;
}

.progress-date {
  font-size: 13px;
  font-weight: 600;
}

.progress-content {
  font-size: 13px;
  line-height: 1.8;
  color: var(--rims-text-secondary);
}

.progress-sub {
  font-size: 12.5px;
  color: var(--rims-text-muted);
  margin-top: 6px;
  line-height: 1.7;
}

.budget-summary {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
}

.budget-cell {
  border: 1px solid var(--rims-border);
  border-radius: 10px;
  padding: 10px 12px;
}

.budget-amount {
  font-size: 16px;
  font-weight: 650;
  margin: 4px 0 6px;
}

.budget-foot {
  display: flex;
  justify-content: space-between;
  gap: 4px;
  margin-top: 8px;
  font-size: 11px;
  color: var(--rims-text-muted);
  white-space: nowrap;
}

.side-col {
  display: grid;
  gap: 16px;
}

.ring-wrap {
  display: flex;
  justify-content: center;
  padding: 0 0 8px;
}

.ring-value {
  font-size: 22px;
  font-weight: 650;
}

.mini-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  padding: 12px 0;
  border-top: 1px dashed var(--rims-border);
  border-bottom: 1px dashed var(--rims-border);
}

.mini-stat {
  text-align: center;
}

.mini-value {
  font-size: 15px;
  font-weight: 650;
  margin-top: 4px;
}

.exec-bar {
  padding-top: 12px;
}

.exec-bar :deep(.el-progress) {
  margin-top: 8px;
}

.exec-bar-head {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
}

.deadline {
  margin-top: 12px;
  font-size: 12.5px;
  color: var(--rims-text-secondary);
  display: flex;
  align-items: center;
  gap: 6px;
}

.deadline.danger {
  color: #ef4444;
}

.info-list {
  display: grid;
  gap: 10px;
}

.info-row {
  display: flex;
  justify-content: space-between;
  font-size: 12.5px;
  color: var(--rims-text-muted);
}

.info-row b {
  color: var(--rims-text);
  font-weight: 500;
  max-width: 190px;
  text-align: right;
}

.member-list {
  display: grid;
  gap: 12px;
}

.member-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.avatar-sm {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: #eef3ff;
  color: var(--rims-primary);
  font-size: 11px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
}

.avatar-sm.leader {
  background: var(--rims-primary);
  color: #fff;
}

.member-name {
  font-size: 13px;
  font-weight: 500;
}

@media (max-width: 1200px) {
  .body-grid {
    grid-template-columns: 1fr;
  }

  .budget-summary {
    grid-template-columns: repeat(2, 1fr);
  }

  .header-card {
    flex-direction: column;
  }

  .header-actions {
    justify-content: flex-start;
    max-width: none;
  }
}
</style>
