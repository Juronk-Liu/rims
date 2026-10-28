<template>
  <div class="page">
    <div class="head">
      <div>
        <h1 class="page-title">{{ isMineView ? '我的项目' : '项目库' }}</h1>
        <div class="page-subtitle">
          {{ isMineView ? '我负责或参与的项目，共 ' + total + ' 个' : '全院科研项目，共 ' + total + ' 个' }}
        </div>
      </div>
    </div>

    <div class="card filter-bar">
      <el-input v-model="query.keyword" placeholder="搜索项目名称 / 编号 / 负责人" clearable class="search"
        :prefix-icon="Search" @keyup.enter="load(1)" @clear="load(1)" />
      <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 150px" @change="load(1)">
        <el-option v-for="item in projectStatusOptions" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-select v-model="query.category" placeholder="全部类别" clearable style="width: 140px" @change="load(1)">
        <el-option v-for="item in categoryOptions" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-radio-group v-if="showScopeSwitch" v-model="query.onlyMine" @change="load(1)">
        <el-radio-button :value="false">全院</el-radio-button>
        <el-radio-button :value="true">我的</el-radio-button>
      </el-radio-group>
      <el-button @click="load(1)">查询</el-button>
      <el-button text @click="reset">重置</el-button>
    </div>

    <div class="card table-card">
      <el-table v-loading="loading" :data="list" @row-click="(row) => router.push(`/projects/${row.id}`)">
        <el-table-column prop="code" label="项目编号" width="118">
          <template #default="{ row }">
            <span class="mono code">{{ row.code }}</span>
          </template>
        </el-table-column>
        <el-table-column label="项目名称" min-width="200">
          <template #default="{ row }">
            <div class="name">{{ row.name }}</div>
            <div class="meta">
              <el-tag size="small" effect="plain">{{ row.categoryLabel }}</el-tag>
              <span class="meta-text">{{ row.typeLabel }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="负责人" width="108">
          <template #default="{ row }">
            <div>{{ row.leaderName }}</div>
            <div class="tiny">{{ row.dept }}</div>
          </template>
        </el-table-column>
        <el-table-column label="经费（万）" width="120">
          <template #default="{ row }">
            <div class="mono">{{ wan(row.approvedExpense) }} / {{ wan(row.budgetTotal) }}</div>
            <el-progress :percentage="execRate(row)" :stroke-width="4" :show-text="false"
              :color="execRate(row) > 90 ? '#ef4444' : '#2f5bff'" style="margin-top: 6px" />
          </template>
        </el-table-column>
        <el-table-column label="进度" width="88">
          <template #default="{ row }">
            <div class="progress-cell">
              <el-progress type="circle" :percentage="row.progressPercent || 0" :width="36" :stroke-width="4" />
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="projectStatusMap[row.status]?.type || 'info'" effect="light" size="small">
              {{ row.statusLabel }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="结题倒计时" width="108">
          <template #default="{ row }">
            <span :class="{ danger: row.overdue }" class="mono">{{ daysLeftText(row.daysLeft) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click.stop="router.push(`/projects/${row.id}`)">详情</el-button>
            <el-button v-if="canEdit(row)" size="small" link type="primary" @click.stop="router.push(`/projects/${row.id}/edit`)">
              编辑
            </el-button>
            <el-button v-if="canSubmit(row)" size="small" link type="success" @click.stop="submitProject(row)">提交</el-button>
            <el-button v-if="canReview(row)" size="small" link type="warning" @click.stop="router.push('/approvals')">审批</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="empty-block">暂无匹配项目</div>
        </template>
      </el-table>

      <div class="pager">
        <el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :total="total"
          :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next" background
          @current-change="load()" @size-change="load(1)" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Plus, Search } from '@element-plus/icons-vue';
import { projectApi } from '@/api';
import { useAuthStore } from '@/stores/auth';
import { categoryOptions, projectStatusMap, projectStatusOptions } from '@/utils/dict';
import { daysLeftText, wan } from '@/utils/format';

const router = useRouter();
const route = useRoute();
const auth = useAuthStore();

const loading = ref(false);
const list = ref([]);
const total = ref(0);
const query = reactive({
  page: 1,
  size: 10,
  keyword: '',
  status: '',
  category: '',
  onlyMine: route.query.scope === 'mine' || auth.isResearcher,
});

const isMineView = computed(() => auth.isResearcher || route.query.scope === 'mine');
const showScopeSwitch = computed(() => !auth.isResearcher);
const canCreate = computed(() => !['LEADER', 'FINANCE'].includes(auth.role));

const canEdit = (row) => ['DRAFT', 'REJECTED'].includes(row.status)
  && (auth.isAdmin || row.leaderId === auth.user?.id);
const canSubmit = (row) => ['DRAFT', 'REJECTED'].includes(row.status)
  && (auth.isAdmin || row.leaderId === auth.user?.id);
const canReview = (row) => (row.status === 'SUBMITTED' && auth.isAdmin)
  || (row.status === 'SUBMITTED' && auth.isDeptAdmin)
  || (row.status === 'DEPT_APPROVED' && auth.isAdmin);

const execRate = (row) => {
  if (!row.budgetTotal) return 0;
  return Math.min(100, Math.round((row.approvedExpense / row.budgetTotal) * 100));
};

const load = async (page) => {
  if (page) query.page = page;
  loading.value = true;
  try {
    const res = await projectApi.list({ ...query });
    list.value = res.data.list;
    total.value = res.data.total;
  } finally {
    loading.value = false;
  }
};

const reset = () => {
  query.keyword = '';
  query.status = '';
  query.category = '';
  query.onlyMine = auth.isResearcher || isMineView.value;
  load(1);
};

const submitProject = async (row) => {
  await ElMessageBox.confirm(`确认提交「${row.name}」进入审批流程？提交后不可直接修改。`, '提交申报', {
    type: 'warning',
  });
  await projectApi.submit(row.id);
  ElMessage.success('已提交，等待部门审核');
  load();
};

watch(() => route.query.scope, (scope) => {
  query.onlyMine = scope === 'mine' || auth.isResearcher;
  load(1);
});

onMounted(load);
</script>

<style scoped>
.head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 18px;
}

.filter-bar {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 14px 16px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.search {
  width: 280px;
}

.table-card {
  padding: 6px 16px 16px;
}

.code {
  font-size: 12px;
  color: var(--rims-text-secondary);
}

.name {
  font-weight: 500;
  color: var(--rims-text);
}

.meta {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-top: 5px;
}

.meta-text {
  font-size: 12px;
  color: var(--rims-text-muted);
}

.progress-cell {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.progress-num {
  font-size: 12px;
  color: var(--rims-text-secondary);
}

.pager {
  display: flex;
  justify-content: flex-end;
  padding-top: 14px;
}

.danger {
  color: #ef4444;
}

:deep(.el-table__row) {
  cursor: pointer;
}

:deep(.el-table .cell .el-button.is-link) {
  margin-left: 6px;
  margin-right: 0;
}
</style>
