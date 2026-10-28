<template>
  <div class="page">
    <div class="head">
      <div>
        <h1 class="page-title">审批中心</h1>
        <div class="page-subtitle">按角色聚合待处理事项，审批动作全部留痕</div>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="loadAll">刷新</el-button>
    </div>

    <div class="summary-row">
      <div class="card summary-item">
        <div class="tiny">待审核项目</div>
        <div class="stat-number">{{ submittedList.length }}</div>
      </div>
      <div class="card summary-item">
        <div class="tiny">待立项项目</div>
        <div class="stat-number">{{ approvedList.length }}</div>
      </div>
      <div class="card summary-item">
        <div class="tiny">待验收项目</div>
        <div class="stat-number">{{ acceptanceList.length }}</div>
      </div>
      <div class="card summary-item">
        <div class="tiny">待审批经费</div>
        <div class="stat-number">{{ expenseList.length }}</div>
      </div>
    </div>

    <div class="card tabs-card">
      <el-tabs v-model="activeTab">
        <el-tab-pane name="projects">
          <template #label>项目审批 <span class="tab-count">{{ submittedList.length + approvedList.length }}</span></template>
          <div class="pane">
            <div class="group-title">
              <span>部门审核</span>
              <span class="tiny">提交待部门审核的项目</span>
            </div>
            <el-table :data="submittedList" size="default" v-loading="loading">
              <el-table-column prop="code" label="编号" width="140" />
              <el-table-column prop="name" label="项目名称" min-width="280" show-overflow-tooltip />
              <el-table-column prop="leaderName" label="负责人" width="100" />
              <el-table-column label="类别" width="100">
                <template #default="{ row }">{{ row.categoryLabel }}</template>
              </el-table-column>
              <el-table-column label="预算" width="120">
                <template #default="{ row }"><span class="mono">{{ money(row.budgetTotal) }}</span></template>
              </el-table-column>
              <el-table-column label="操作" width="170" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click="router.push(`/projects/${row.id}`)">查看</el-button>
                  <el-button link type="success" @click="openReview(row, 'APPROVE')">通过</el-button>
                  <el-button link type="danger" @click="openReview(row, 'REJECT')">驳回</el-button>
                </template>
              </el-table-column>
              <template #empty><div class="empty-block">没有待部门审核的项目</div></template>
            </el-table>

            <div class="group-title" style="margin-top: 22px">
              <span>立项审批</span>
              <span class="tiny">部门已通过、等待科研处立项</span>
            </div>
            <el-table :data="approvedList" size="default" v-loading="loading">
              <el-table-column prop="code" label="编号" width="140" />
              <el-table-column prop="name" label="项目名称" min-width="280" show-overflow-tooltip />
              <el-table-column prop="leaderName" label="负责人" width="100" />
              <el-table-column prop="dept" label="部门" width="150" />
              <el-table-column label="预算" width="120">
                <template #default="{ row }"><span class="mono">{{ money(row.budgetTotal) }}</span></template>
              </el-table-column>
              <el-table-column label="操作" width="170" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click="router.push(`/projects/${row.id}`)">查看</el-button>
                  <el-button v-if="auth.isAdmin" link type="success" @click="openReview(row, 'APPROVE')">立项</el-button>
                  <el-button v-if="auth.isAdmin" link type="danger" @click="openReview(row, 'REJECT')">驳回</el-button>
                </template>
              </el-table-column>
              <template #empty><div class="empty-block">没有待立项的项目</div></template>
            </el-table>
          </div>
        </el-tab-pane>

        <el-tab-pane name="acceptance">
          <template #label>结题验收 <span class="tab-count">{{ acceptanceList.length }}</span></template>
          <div class="pane">
            <el-table :data="acceptanceList" size="default" v-loading="loading">
              <el-table-column prop="code" label="编号" width="140" />
              <el-table-column prop="name" label="项目名称" min-width="260" show-overflow-tooltip />
              <el-table-column prop="leaderName" label="负责人" width="100" />
              <el-table-column label="结束日期" width="120">
                <template #default="{ row }">{{ date(row.endDate) }}</template>
              </el-table-column>
              <el-table-column label="执行率" width="110">
                <template #default="{ row }">
                  <span class="mono">{{ row.budgetTotal ? Math.round((row.approvedExpense / row.budgetTotal) * 100) : 0 }}%</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="180" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click="router.push(`/projects/${row.id}`)">查看材料</el-button>
                  <el-button v-if="auth.isAdmin" link type="success" @click="openAcceptance(row)">验收</el-button>
                </template>
              </el-table-column>
              <template #empty><div class="empty-block">没有待验收的项目</div></template>
            </el-table>
          </div>
        </el-tab-pane>

        <el-tab-pane name="expenses">
          <template #label>经费审批 <span class="tab-count">{{ expenseList.length }}</span></template>
          <div class="pane">
            <el-table :data="expenseList" size="default" v-loading="loading">
              <el-table-column label="项目" min-width="240">
                <template #default="{ row }">{{ projectName(row.projectId) }}</template>
              </el-table-column>
              <el-table-column label="科目" width="100">
                <template #default="{ row }">{{ expenseCategoryMap[row.category]?.label }}</template>
              </el-table-column>
              <el-table-column label="金额" width="120">
                <template #default="{ row }"><span class="mono">{{ money(row.amount) }}</span></template>
              </el-table-column>
              <el-table-column prop="purpose" label="用途" min-width="200" show-overflow-tooltip />
              <el-table-column prop="applicantName" label="申请人" width="90" />
              <el-table-column label="申请日期" width="110">
                <template #default="{ row }">{{ date(row.expenseDate) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="170" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click="router.push(`/projects/${row.projectId}`)">详情</el-button>
                  <el-button link type="success" @click="openExpense(row, true)">通过</el-button>
                  <el-button link type="danger" @click="openExpense(row, false)">驳回</el-button>
                </template>
              </el-table-column>
              <template #empty><div class="empty-block">没有待审批的经费单据</div></template>
            </el-table>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <el-dialog v-model="reviewDialog" :title="reviewForm.action === 'APPROVE' ? '审批通过' : '驳回项目'" width="480px">
      <div class="dialog-context">
        <div class="context-name">{{ currentProject?.name }}</div>
        <div class="tiny">{{ currentProject?.code }} · {{ currentProject?.leaderName }} · 预算 {{ money(currentProject?.budgetTotal) }}</div>
      </div>
      <el-form label-position="top">
        <el-form-item :label="reviewForm.action === 'APPROVE' ? '审批意见（选填）' : '驳回原因（必填）'">
          <el-input v-model="reviewForm.opinion" type="textarea" :rows="4" maxlength="300" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reviewDialog = false">取消</el-button>
        <el-button :type="reviewForm.action === 'APPROVE' ? 'success' : 'danger'" :loading="submitting" @click="submitReview">
          确认
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="acceptanceDialog" title="结题验收" width="500px">
      <div class="dialog-context">
        <div class="context-name">{{ currentProject?.name }}</div>
        <div class="tiny">{{ currentProject?.code }} · {{ currentProject?.leaderName }}</div>
      </div>
      <el-form label-position="top">
        <el-form-item label="验收结论">
          <el-radio-group v-model="acceptanceForm.passed">
            <el-radio :value="true">验收通过</el-radio>
            <el-radio :value="false">不通过，退回整改</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="验收意见">
          <el-input v-model="acceptanceForm.opinion" type="textarea" :rows="4" maxlength="300" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="acceptanceDialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitAcceptance">提交结论</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="expenseDialog" :title="expenseForm.approved ? '通过经费申请' : '驳回经费申请'" width="460px">
      <div class="dialog-context">
        <div class="context-name">{{ expenseCategoryMap[currentExpense?.category]?.label }} · {{ money(currentExpense?.amount) }}</div>
        <div class="tiny">{{ currentExpense?.purpose }} · {{ currentExpense?.applicantName }}</div>
      </div>
      <el-form label-position="top">
        <el-form-item label="审批意见">
          <el-input v-model="expenseForm.opinion" type="textarea" :rows="3" maxlength="200" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="expenseDialog = false">取消</el-button>
        <el-button :type="expenseForm.approved ? 'success' : 'danger'" :loading="submitting" @click="submitExpense">
          确认
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { Refresh } from '@element-plus/icons-vue';
import { financeApi, projectApi } from '@/api';
import { useAuthStore } from '@/stores/auth';
import { expenseCategoryMap } from '@/utils/dict';
import { date, money } from '@/utils/format';

const router = useRouter();
const auth = useAuthStore();
const loading = ref(false);
const submitting = ref(false);
const activeTab = ref('projects');

const submittedList = ref([]);
const approvedList = ref([]);
const acceptanceList = ref([]);
const expenseList = ref([]);
const projectMap = ref({});

const reviewDialog = ref(false);
const currentProject = ref(null);
const reviewForm = reactive({ action: 'APPROVE', opinion: '' });
const acceptanceDialog = ref(false);
const acceptanceForm = reactive({ passed: true, opinion: '' });
const expenseDialog = ref(false);
const currentExpense = ref(null);
const expenseForm = reactive({ approved: true, opinion: '' });

const projectName = (id) => projectMap.value[id] || `项目 #${id}`;

const loadAll = async () => {
  loading.value = true;
  try {
    const [submitted, approved, acceptance, expenses, allProjects] = await Promise.all([
      projectApi.list({ page: 1, size: 50, status: 'SUBMITTED' }),
      projectApi.list({ page: 1, size: 50, status: 'DEPT_APPROVED' }),
      projectApi.list({ page: 1, size: 50, status: 'PENDING_ACCEPTANCE' }),
      financeApi.expenses({ page: 1, size: 50, status: 'PENDING' }),
      projectApi.list({ page: 1, size: 100 }),
    ]);
    submittedList.value = submitted.data.list;
    approvedList.value = approved.data.list;
    acceptanceList.value = acceptance.data.list;
    expenseList.value = expenses.data.list;
    projectMap.value = Object.fromEntries(allProjects.data.list.map((p) => [p.id, p.name]));
  } finally {
    loading.value = false;
  }
};

const openReview = (row, action) => {
  currentProject.value = row;
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
    await projectApi.review(currentProject.value.id, { ...reviewForm });
    ElMessage.success(reviewForm.action === 'APPROVE' ? '已通过' : '已驳回');
    reviewDialog.value = false;
    loadAll();
  } finally {
    submitting.value = false;
  }
};

const openAcceptance = (row) => {
  currentProject.value = row;
  acceptanceForm.passed = true;
  acceptanceForm.opinion = '';
  acceptanceDialog.value = true;
};

const submitAcceptance = async () => {
  submitting.value = true;
  try {
    await projectApi.acceptance(currentProject.value.id, { ...acceptanceForm });
    ElMessage.success('验收结论已提交');
    acceptanceDialog.value = false;
    loadAll();
  } finally {
    submitting.value = false;
  }
};

const openExpense = (row, approved) => {
  currentExpense.value = row;
  expenseForm.approved = approved;
  expenseForm.opinion = approved ? '票据齐全，同意报销' : '';
  expenseDialog.value = true;
};

const submitExpense = async () => {
  submitting.value = true;
  try {
    await financeApi.approve(currentExpense.value.id, { ...expenseForm });
    ElMessage.success('已处理');
    expenseDialog.value = false;
    loadAll();
  } finally {
    submitting.value = false;
  }
};

onMounted(loadAll);
</script>

<style scoped>
.head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 18px;
}

.summary-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}

.summary-item {
  padding: 14px 18px;
}

.tabs-card {
  padding: 6px 20px 20px;
}

.pane {
  padding-top: 6px;
}

.group-title {
  display: flex;
  align-items: baseline;
  gap: 10px;
  font-size: 14px;
  font-weight: 600;
  margin: 6px 0 10px;
}

.tab-count {
  font-size: 11px;
  color: var(--rims-text-muted);
  background: #f1f4fa;
  border-radius: 8px;
  padding: 1px 6px;
}

.dialog-context {
  background: #f7f9ff;
  border-radius: 8px;
  padding: 10px 12px;
  margin-bottom: 14px;
}

.context-name {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 4px;
}
</style>
