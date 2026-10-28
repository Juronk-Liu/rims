<template>
  <div class="page">
    <div class="head">
      <div>
        <h1 class="page-title">{{ greeting }}，{{ auth.user?.realName }}</h1>
        <div class="page-subtitle">
          {{ auth.user?.dept }} · {{ roleLabel(auth.role) }}
          <span v-if="data" class="sync">· 数据更新于 {{ updatedAt }}</span>
        </div>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
    </div>

    <div class="kpi-grid">
      <div v-for="card in kpiCards" :key="card.label" class="card kpi" @click="card.to && router.push(card.to)">
        <div class="kpi-top">
          <span class="kpi-label">{{ card.label }}</span>
          <div class="kpi-icon" :style="{ background: card.bg, color: card.color }">
            <el-icon><component :is="card.icon" /></el-icon>
          </div>
        </div>
        <div class="stat-number">{{ card.value }}</div>
        <div class="kpi-hint">{{ card.hint }}</div>
      </div>
    </div>

    <div class="grid-2">
      <div class="card card-pad">
        <div class="section-title">
          <span>项目状态分布</span>
          <span class="tiny">共 {{ stats.projectTotal || 0 }} 个项目</span>
        </div>
        <div ref="statusChart" class="chart chart-sm"></div>
      </div>

      <div class="card card-pad">
        <div class="section-title">
          <span>经费执行（按项目类别）</span>
          <span class="tiny">单位：元</span>
        </div>
        <div ref="budgetChart" class="chart chart-sm"></div>
      </div>
    </div>

    <div class="grid-3">
      <div class="card card-pad alerts-card">
        <div class="section-title">
          <span>风险预警</span>
          <el-tag v-if="alerts.length" type="danger" effect="light" size="small">{{ alerts.length }} 条</el-tag>
        </div>
        <div v-if="!alerts.length" class="empty-block">
          <el-icon style="font-size: 26px; color: #b9c3d6"><CircleCheck /></el-icon>
          <div style="margin-top: 8px">暂无风险事项</div>
        </div>
        <div v-else class="alert-list">
          <div v-for="(alert, index) in alerts" :key="index" class="alert-item" @click="openProject(alert.projectId)">
            <span class="level" :class="alert.level.toLowerCase()"></span>
            <div class="alert-body">
              <div class="alert-title">
                {{ alert.type }}
                <span class="alert-project">{{ alert.projectName }}</span>
              </div>
              <div class="alert-desc">{{ alert.desc }}</div>
            </div>
            <el-icon class="arrow"><ArrowRight /></el-icon>
          </div>
        </div>
      </div>

      <div class="card card-pad">
        <div class="section-title">
          <span>近 6 个月经费支出</span>
          <span class="tiny">按审批通过月份</span>
        </div>
        <div ref="trendChart" class="chart chart-sm"></div>
      </div>
    </div>

    <div class="card card-pad recent">
      <div class="section-title">
        <span>最近更新的项目</span>
        <span class="link" @click="router.push('/projects')">查看全部</span>
      </div>
      <el-table :data="recentProjects" size="default" @row-click="(row) => openProject(row.id)">
        <el-table-column prop="code" label="编号" width="140" />
        <el-table-column prop="name" label="项目名称" min-width="280" show-overflow-tooltip />
        <el-table-column prop="leaderName" label="负责人" width="100" />
        <el-table-column label="进度" width="150">
          <template #default="{ row }">
            <el-progress :percentage="row.progressPercent || 0" :stroke-width="6" :show-text="false" />
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="projectStatusMap[row.status]?.type || 'info'" effect="light" size="small">
              {{ row.statusLabel }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="结题倒计时" width="120">
          <template #default="{ row }">
            <span :class="{ danger: row.overdue }">{{ daysLeftText(row.daysLeft) }}</span>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import * as echarts from 'echarts';
import { dashboardApi, projectApi } from '@/api';
import { useAuthStore } from '@/stores/auth';
import { projectStatusMap, roleLabel } from '@/utils/dict';
import { daysLeftText } from '@/utils/format';
import { Refresh, ArrowRight } from '@element-plus/icons-vue';

const router = useRouter();
const auth = useAuthStore();
const loading = ref(false);
const data = ref(null);
const recentProjects = ref([]);
const updatedAt = ref('');
const statusChart = ref(null);
const budgetChart = ref(null);
const trendChart = ref(null);
let charts = [];

const stats = computed(() => data.value?.stats || {});
const alerts = computed(() => data.value?.alerts || []);

const greeting = computed(() => {
  const hour = new Date().getHours();
  if (hour < 6) return '凌晨好';
  if (hour < 12) return '上午好';
  if (hour < 14) return '中午好';
  if (hour < 18) return '下午好';
  return '晚上好';
});

const kpiCards = computed(() => [
  {
    label: '项目总数',
    value: stats.value.projectTotal || 0,
    hint: `草稿 ${stats.value.draftCount || 0} · 已结题 ${stats.value.completed || 0}`,
    icon: 'Folder',
    color: '#2f5bff',
    bg: '#eef3ff',
    to: '/projects',
  },
  {
    label: '在研项目',
    value: stats.value.inProgress || 0,
    hint: `待验收 ${stats.value.pendingAcceptance || 0} 个`,
    icon: 'DataLine',
    color: '#0f9d76',
    bg: '#e9f8f2',
    to: '/projects',
  },
  {
    label: '待办事项',
    value: stats.value.todoCount || 0,
    hint: auth.isAdmin ? '含项目审批与经费审批' : '待处理的项目事项',
    icon: 'Bell',
    color: '#d97706',
    bg: '#fff5e6',
    to: auth.isFinance ? '/expenses' : '/approvals',
  },
  {
    label: '预算执行率',
    value: `${stats.value.execRate || 0}%`,
    hint: `已支出 ${formatWan(stats.value.expenseApproved)} / ${formatWan(stats.value.budgetTotal)}`,
    icon: 'Wallet',
    color: '#7c3aed',
    bg: '#f3edff',
    to: '/expenses',
  },
]);

function formatWan(value) {
  const num = Number(value || 0);
  return num >= 10000 ? `${(num / 10000).toFixed(1)} 万` : `${num}`;
}

const openProject = (id) => {
  if (id) router.push(`/projects/${id}`);
};

const renderCharts = () => {
  charts.forEach((c) => c.dispose());
  charts = [];

  if (statusChart.value && data.value) {
    const chart = echarts.init(statusChart.value);
    const colors = {
      DRAFT: '#94a3b8',
      SUBMITTED: '#f59e0b',
      DEPT_APPROVED: '#eab308',
      IN_PROGRESS: '#2f5bff',
      PENDING_ACCEPTANCE: '#f97316',
      COMPLETED: '#10b981',
      REJECTED: '#ef4444',
      TERMINATED: '#64748b',
    };
    chart.setOption({
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      legend: { bottom: 0, icon: 'circle', itemWidth: 8, itemHeight: 8, textStyle: { fontSize: 12, color: '#5b6577' } },
      series: [
        {
          type: 'pie',
          radius: ['52%', '74%'],
          center: ['50%', '44%'],
          avoidLabelOverlap: true,
          itemStyle: { borderColor: '#fff', borderWidth: 2 },
          label: { show: false },
          data: data.value.statusDist.map((item) => ({
            name: item.name,
            value: item.value,
            itemStyle: { color: colors[item.status] || '#94a3b8' },
          })),
        },
      ],
    });
    charts.push(chart);
  }

  if (budgetChart.value && data.value) {
    const chart = echarts.init(budgetChart.value);
    const items = data.value.categoryStats || [];
    chart.setOption({
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      legend: { top: 0, right: 0, icon: 'roundRect', itemWidth: 10, itemHeight: 10, textStyle: { fontSize: 12 } },
      grid: { left: 8, right: 12, bottom: 4, top: 36, containLabel: true },
      xAxis: {
        type: 'category',
        data: items.map((i) => i.name),
        axisLine: { lineStyle: { color: '#e6eaf2' } },
        axisTick: { show: false },
        axisLabel: { color: '#5b6577', fontSize: 12 },
      },
      yAxis: {
        type: 'value',
        splitLine: { lineStyle: { color: '#eef1f7' } },
        axisLabel: { color: '#8c96a8', fontSize: 11, formatter: (v) => (v >= 10000 ? `${v / 10000}万` : v) },
      },
      series: [
        {
          name: '预算',
          type: 'bar',
          barWidth: 18,
          itemStyle: { color: '#d9e2ff', borderRadius: [4, 4, 0, 0] },
          data: items.map((i) => i.budget),
        },
        {
          name: '已支出',
          type: 'bar',
          barWidth: 18,
          itemStyle: { color: '#2f5bff', borderRadius: [4, 4, 0, 0] },
          data: items.map((i) => i.expense),
        },
      ],
    });
    charts.push(chart);
  }

  if (trendChart.value && data.value) {
    const chart = echarts.init(trendChart.value);
    const items = data.value.monthlyExpense || [];
    chart.setOption({
      tooltip: {
        trigger: 'axis',
        formatter: (params) => `${params[0].name}<br/>支出：${(params[0].value * 10000).toLocaleString('zh-CN')} 元`,
      },
      grid: { left: 4, right: 12, bottom: 0, top: 22, containLabel: true },
      xAxis: {
        type: 'category',
        data: items.map((i) => i.month),
        axisLine: { lineStyle: { color: '#e6eaf2' } },
        axisTick: { show: false },
        axisLabel: { color: '#5b6577', fontSize: 12 },
      },
      yAxis: {
        type: 'value',
        splitLine: { lineStyle: { color: '#eef1f7' } },
        axisLabel: { color: '#8c96a8', fontSize: 11, formatter: (v) => `${v}万` },
      },
      series: [
        {
          type: 'bar',
          barWidth: 22,
          data: items.map((i) => Number((Number(i.amount) / 10000).toFixed(1))),
          itemStyle: {
            borderRadius: [5, 5, 0, 0],
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: '#5b7cff' },
              { offset: 1, color: '#c9d5ff' },
            ]),
          },
        },
      ],
    });
    charts.push(chart);
  }
};

const onResize = () => charts.forEach((c) => c.resize());

const load = async () => {
  loading.value = true;
  try {
    const [overview, projects] = await Promise.all([
      dashboardApi.overview(),
      projectApi.list({ page: 1, size: 5 }),
    ]);
    data.value = overview.data;
    recentProjects.value = projects.data.list;
    updatedAt.value = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
    await nextTick();
    renderCharts();
    setTimeout(onResize, 60);
  } finally {
    loading.value = false;
  }
};

onMounted(load);
window.addEventListener('resize', onResize);
onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize);
  charts.forEach((c) => c.dispose());
});
</script>

<style scoped>
.head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 18px;
}

.sync {
  color: #a8b1c2;
}

.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}

.kpi {
  padding: 18px 20px;
  cursor: pointer;
  transition: box-shadow 0.18s ease, transform 0.18s ease;
}

.kpi:hover {
  box-shadow: 0 8px 24px rgba(31, 45, 90, 0.07);
  transform: translateY(-1px);
}

.kpi-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.kpi-label {
  font-size: 13px;
  color: var(--rims-text-secondary);
}

.kpi-icon {
  width: 32px;
  height: 32px;
  border-radius: 9px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
}

.kpi-hint {
  margin-top: 8px;
  font-size: 12px;
  color: var(--rims-text-muted);
}

.grid-2 {
  display: grid;
  grid-template-columns: 0.9fr 1.1fr;
  gap: 16px;
  margin-bottom: 16px;
}

.grid-3 {
  display: grid;
  grid-template-columns: 1.25fr 0.75fr;
  gap: 16px;
  margin-bottom: 16px;
}

.chart {
  width: 100%;
}

.chart-sm {
  height: 220px;
}

.alerts-card {
  min-height: 260px;
}

.alert-list {
  display: grid;
  gap: 4px;
  max-height: 236px;
  overflow: auto;
}

.alert-item {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  padding: 10px 8px;
  border-radius: 8px;
  cursor: pointer;
}

.alert-item:hover {
  background: #f7f9ff;
}

.level {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-top: 6px;
  flex: none;
}

.level.high {
  background: #ef4444;
  box-shadow: 0 0 0 4px rgba(239, 68, 68, 0.12);
}

.level.medium {
  background: #f59e0b;
  box-shadow: 0 0 0 4px rgba(245, 158, 11, 0.12);
}

.level.low {
  background: #94a3b8;
}

.alert-body {
  flex: 1;
  min-width: 0;
}

.alert-title {
  font-size: 13px;
  font-weight: 600;
  display: flex;
  gap: 8px;
  align-items: center;
}

.alert-project {
  font-weight: 400;
  color: var(--rims-text-secondary);
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.alert-desc {
  font-size: 12px;
  color: var(--rims-text-muted);
  margin-top: 3px;
}

.arrow {
  color: #c6cddb;
  margin-top: 4px;
}

.recent :deep(.el-table__row) {
  cursor: pointer;
}

.danger {
  color: #ef4444;
}

@media (max-width: 1280px) {
  .kpi-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .grid-2,
  .grid-3 {
    grid-template-columns: 1fr;
  }
}
</style>
