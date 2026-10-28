<template>
  <div class="page">
    <div class="head">
      <div>
        <h1 class="page-title">经费管理</h1>
        <div class="page-subtitle">按科目审批支出，超预算在申请与审批环节双重拦截</div>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
    </div>

    <div class="stat-row">
      <div class="card stat-item">
        <div class="tiny">待审批金额</div>
        <div class="stat-number warning">{{ money(summary.pending) }}</div>
        <div class="tiny">{{ summary.pendingCount }} 笔单据</div>
      </div>
      <div class="card stat-item">
        <div class="tiny">已通过金额</div>
        <div class="stat-number">{{ money(summary.approved) }}</div>
        <div class="tiny">{{ summary.approvedCount }} 笔单据</div>
      </div>
      <div class="card stat-item">
        <div class="tiny">已驳回</div>
        <div class="stat-number">{{ summary.rejectedCount }}</div>
        <div class="tiny">笔单据</div>
      </div>
    </div>

    <div class="card filter-bar">
      <el-radio-group v-model="query.status" @change="load(1)">
        <el-radio-button value="">全部</el-radio-button>
        <el-radio-button value="PENDING">待审批</el-radio-button>
        <el-radio-button value="APPROVED">已通过</el-radio-button>
        <el-radio-button value="REJECTED">已驳回</el-radio-button>
      </el-radio-group>
      <div class="spacer"></div>
      <span class="tiny">共 {{ total }} 笔</span>
    </div>

    <div class="card table-card">
      <el-table :data="list" v-loading="loading">
        <el-table-column label="项目" min-width="200">
          <template #default="{ row }">
            <div class="project-name" @click="router.push(`/projects/${row.projectId}`)">
              {{ projectName(row.projectId) }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="科目" width="100">
          <template #default="{ row }">{{ expenseCategoryMap[row.category]?.label }}</template>
        </el-table-column>
        <el-table-column label="金额" width="120">
          <template #default="{ row }"><span class="mono amount">{{ money(row.amount) }}</span></template>
        </el-table-column>
        <el-table-column prop="purpose" label="用途" min-width="160" show-overflow-tooltip />
        <el-table-column prop="applicantName" label="申请人" width="90" />
        <el-table-column label="申请日期" width="110">
          <template #default="{ row }">{{ date(row.expenseDate) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" effect="light" :type="expenseStatusMap[row.status]?.type">
              {{ expenseStatusMap[row.status]?.label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="审批信息" width="150">
          <template #default="{ row }">
            <div v-if="row.approverName" class="tiny">{{ row.approverName }}</div>
            <div v-if="row.approvedAt" class="tiny opinion">{{ date(row.approvedAt) }}</div>
            <div v-if="row.opinion" class="tiny opinion">{{ row.opinion }}</div>
            <span v-else class="tiny">—</span>
          </template>
        </el-table-column>
        <el-table-column v-if="canApprove" label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 'PENDING'">
              <el-button link type="success" @click="openApprove(row, true)">通过</el-button>
              <el-button link type="danger" @click="openApprove(row, false)">驳回</el-button>
            </template>
            <span v-else class="tiny">已处理</span>
          </template>
        </el-table-column>
        <template #empty><div class="empty-block">暂无经费单据</div></template>
      </el-table>

      <div class="pager">
        <el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :total="total"
          layout="total, prev, pager, next" background @current-change="load()" />
      </div>
    </div>

    <el-dialog v-model="dialog" :title="form.approved ? '通过经费申请' : '驳回经费申请'" width="460px">
      <div class="dialog-context">
        <div class="context-name">{{ current ? money(current.amount) : '' }} · {{ current ? expenseCategoryMap[current.category]?.label : '' }}</div>
        <div class="tiny">{{ current?.purpose }}</div>
      </div>
      <el-form label-position="top">
        <el-form-item label="审批意见">
          <el-input v-model="form.opinion" type="textarea" :rows="3" maxlength="200" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button :type="form.approved ? 'success' : 'danger'" :loading="submitting" @click="submit">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { Refresh } from '@element-plus/icons-vue';
import { financeApi, projectApi } from '@/api';
import { useAuthStore } from '@/stores/auth';
import { expenseCategoryMap, expenseStatusMap } from '@/utils/dict';
import { date, money } from '@/utils/format';

const router = useRouter();
const auth = useAuthStore();
const loading = ref(false);
const submitting = ref(false);
const list = ref([]);
const total = ref(0);
const projectMap = ref({});
const allSummary = ref([]);
const query = reactive({ page: 1, size: 10, status: 'PENDING' });

const canApprove = computed(() => auth.isFinance || auth.isAdmin);

const summary = computed(() => {
  const items = allSummary.value;
  const of = (status) => items.filter((i) => i.status === status);
  const sum = (arr) => arr.reduce((s, i) => s + Number(i.amount), 0);
  return {
    pending: sum(of('PENDING')),
    pendingCount: of('PENDING').length,
    approved: sum(of('APPROVED')),
    approvedCount: of('APPROVED').length,
    rejectedCount: of('REJECTED').length,
  };
});

const projectName = (id) => projectMap.value[id] || `项目 #${id}`;

const dialog = ref(false);
const current = ref(null);
const form = reactive({ approved: true, opinion: '' });

const load = async (page) => {
  if (page) query.page = page;
  loading.value = true;
  try {
    const [res, projects, summaryRes] = await Promise.all([
      financeApi.expenses({ ...query }),
      projectApi.list({ page: 1, size: 100 }),
      financeApi.expenses({ page: 1, size: 200 }),
    ]);
    list.value = res.data.list;
    total.value = res.data.total;
    projectMap.value = Object.fromEntries(projects.data.list.map((p) => [p.id, p.name]));
    allSummary.value = summaryRes.data.list;
  } finally {
    loading.value = false;
  }
};

const openApprove = (row, approved) => {
  current.value = row;
  form.approved = approved;
  form.opinion = approved ? '票据齐全，同意报销' : '';
  dialog.value = true;
};

const submit = async () => {
  submitting.value = true;
  try {
    await financeApi.approve(current.value.id, { ...form });
    ElMessage.success('已处理');
    dialog.value = false;
    load();
  } finally {
    submitting.value = false;
  }
};

onMounted(load);
</script>

<style scoped>
.head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 18px;
}

.stat-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}

.stat-item {
  padding: 16px 20px;
}

.warning {
  color: #d97706;
}

.filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  margin-bottom: 16px;
}

.spacer {
  flex: 1;
}

.table-card {
  padding: 6px 16px 16px;
}

.project-name {
  font-weight: 500;
  cursor: pointer;
}

.project-name:hover {
  color: var(--rims-primary);
}

.amount {
  font-weight: 600;
}

.opinion {
  color: var(--rims-text-muted);
  max-width: 170px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pager {
  display: flex;
  justify-content: flex-end;
  padding-top: 14px;
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
