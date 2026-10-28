<template>
  <div class="page form-page">
    <div class="head">
      <div>
        <h1 class="page-title">{{ isEdit ? '编辑申报' : '新建项目申报' }}</h1>
        <div class="page-subtitle">填写项目基本信息、团队与预算科目，保存后可随时修改，提交后进入审批流程</div>
      </div>
      <el-button @click="router.back()">返回</el-button>
    </div>

    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" v-loading="loading">
      <div class="card card-pad block">
        <div class="section-title"><span>基本信息</span></div>
        <div class="grid-2">
          <el-form-item label="项目名称" prop="name" class="span-2">
            <el-input v-model="form.name" maxlength="120" show-word-limit placeholder="例如：面向复杂工况的工业机器人视觉检测关键技术研究" />
          </el-form-item>
          <el-form-item label="项目类别" prop="category">
            <el-select v-model="form.category" placeholder="请选择" style="width: 100%">
              <el-option v-for="item in categoryOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="项目类型" prop="type">
            <el-select v-model="form.type" placeholder="请选择" style="width: 100%">
              <el-option v-for="item in projectTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="项目负责人" prop="leaderId">
            <el-select v-model="form.leaderId" placeholder="请选择负责人" style="width: 100%" filterable>
              <el-option v-for="user in leaderOptions" :key="user.id" :value="user.id"
                :label="`${user.realName}（${user.dept} · ${user.title || '—'}）`" />
            </el-select>
          </el-form-item>
          <el-form-item label="经费来源">
            <el-input v-model="form.source" placeholder="例如：省重点研发计划 / 国家自然科学基金 / 企业委托" />
          </el-form-item>
          <el-form-item label="开始日期" prop="startDate">
            <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width: 100%" />
          </el-form-item>
          <el-form-item label="结束日期" prop="endDate">
            <el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width: 100%" />
          </el-form-item>
          <el-form-item label="预算总额（元）" prop="budgetTotal">
            <el-input-number v-model="form.budgetTotal" :min="0" :step="10000" :precision="2" controls-position="right" style="width: 100%" />
            <div class="tiny" style="margin-top: 4px">约 {{ wan(form.budgetTotal) }} 元，需与下方科目合计一致</div>
          </el-form-item>
        </div>
      </div>

      <div class="card card-pad block">
        <div class="section-title">
          <span>研究内容</span>
          <span class="tiny">{{ (form.summary || '').length }} 字（建议 300 字以上）</span>
        </div>
        <el-form-item prop="summary">
          <el-input v-model="form.summary" type="textarea" :rows="5" maxlength="2000" show-word-limit
            placeholder="研究背景、关键科学问题、技术路线与预期突破" />
        </el-form-item>
        <el-form-item label="预期成果" prop="expectedOutcome">
          <el-input v-model="form.expectedOutcome" type="textarea" :rows="3" maxlength="500" show-word-limit
            placeholder="例如：申请发明专利 2 项，发表论文 3 篇，研制样机 1 套。结题验收将按此核对。" />
        </el-form-item>
      </div>

      <div class="grid-2 block">
        <div class="card card-pad">
          <div class="section-title">
            <span>项目团队</span>
            <span class="tiny">负责人自动加入</span>
          </div>
          <el-form-item label="参与成员">
            <el-select v-model="form.memberIds" multiple filterable placeholder="选择项目组成员" style="width: 100%">
              <el-option v-for="user in memberOptions" :key="user.id" :value="user.id"
                :label="`${user.realName} · ${user.dept}`" />
            </el-select>
          </el-form-item>
          <div class="member-preview">
            <div class="member-chip" v-if="leaderUser">
              <span class="avatar-sm leader">{{ leaderUser.realName.slice(-2) }}</span>
              <div>
                <div>{{ leaderUser.realName }}</div>
                <div class="tiny">负责人 · {{ leaderUser.dept }}</div>
              </div>
            </div>
            <div class="member-chip" v-for="user in selectedMembers" :key="user.id">
              <span class="avatar-sm">{{ user.realName.slice(-2) }}</span>
              <div>
                <div>{{ user.realName }}</div>
                <div class="tiny">参与人 · {{ user.dept }}</div>
              </div>
            </div>
          </div>
        </div>

        <div class="card card-pad">
          <div class="section-title">
            <span>预算科目</span>
            <span class="tiny" :class="{ 'sum-error': !budgetBalanced }">
              合计 {{ wan(budgetSum) }} / 总额 {{ wan(form.budgetTotal) }}
            </span>
          </div>
          <div class="budget-row" v-for="item in expenseCategoryOptions" :key="item.value">
            <span class="budget-label">{{ item.label }}</span>
            <el-input-number v-model="budgetMap[item.value]" :min="0" :step="10000" :precision="2"
              controls-position="right" style="width: 190px" />
            <span class="tiny budget-rate">{{ budgetRate(item.value) }}%</span>
          </div>
          <el-alert v-if="!budgetBalanced" type="warning" :closable="false" show-icon style="margin-top: 12px"
            :title="`科目合计与预算总额相差 ${wan(Math.abs(budgetSum - (form.budgetTotal || 0)))} 元，保存前请调整`" />
        </div>
      </div>

      <div class="actions card card-pad">
        <div class="tiny">
          保存后状态为草稿；提交后依次进入「部门审核 → 科研处立项」，审批过程可在项目详情中查看。
        </div>
        <div class="action-btns">
          <el-button @click="router.back()">取消</el-button>
          <el-button :loading="saving" @click="save(false)">保存草稿</el-button>
          <el-button type="primary" :loading="saving" @click="save(true)">保存并提交</el-button>
        </div>
      </div>
    </el-form>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { authApi, projectApi } from '@/api';
import { categoryOptions, expenseCategoryOptions, projectTypeOptions } from '@/utils/dict';
import { wan } from '@/utils/format';

const route = useRoute();
const router = useRouter();
const formRef = ref();
const loading = ref(false);
const saving = ref(false);
const users = ref([]);

const isEdit = computed(() => Boolean(route.params.id));

const form = reactive({
  name: '',
  category: 'PROVINCIAL',
  type: 'APPLIED',
  leaderId: null,
  source: '',
  startDate: '',
  endDate: '',
  budgetTotal: 300000,
  summary: '',
  expectedOutcome: '',
  memberIds: [],
});

const budgetMap = reactive({
  EQUIPMENT: 0,
  MATERIAL: 0,
  TRAVEL: 0,
  LABOR: 0,
  OTHER: 0,
});

const rules = {
  name: [{ required: true, message: '请填写项目名称', trigger: 'blur' }],
  category: [{ required: true, message: '请选择项目类别', trigger: 'change' }],
  type: [{ required: true, message: '请选择项目类型', trigger: 'change' }],
  leaderId: [{ required: true, message: '请选择项目负责人', trigger: 'change' }],
  startDate: [{ required: true, message: '请选择开始日期', trigger: 'change' }],
  endDate: [{ required: true, message: '请选择结束日期', trigger: 'change' }],
  budgetTotal: [{ required: true, message: '请填写预算总额', trigger: 'blur' }],
};

const leaderOptions = computed(() => users.value.filter((u) => ['RESEARCHER', 'DEPT_ADMIN'].includes(u.role)));
const memberOptions = computed(() => users.value.filter((u) => u.role === 'RESEARCHER'));
const leaderUser = computed(() => users.value.find((u) => u.id === form.leaderId));
const selectedMembers = computed(() => users.value.filter((u) => form.memberIds.includes(u.id)));

const budgetSum = computed(() =>
  Object.values(budgetMap).reduce((sum, v) => sum + (Number(v) || 0), 0)
);
const budgetBalanced = computed(() => Math.abs(budgetSum.value - (Number(form.budgetTotal) || 0)) < 0.01);
const budgetRate = (key) => {
  const total = Number(form.budgetTotal) || 0;
  if (!total) return 0;
  return Math.round(((Number(budgetMap[key]) || 0) / total) * 100);
};

const loadDict = async () => {
  const res = await authApi.users();
  users.value = res.data;
  if (!form.leaderId) {
    form.leaderId = res.data.find((u) => u.username === 'zhangsan')?.id || null;
  }
};

const loadDetail = async () => {
  loading.value = true;
  try {
    const res = await projectApi.detail(route.params.id);
    const p = res.data.project;
    Object.assign(form, {
      name: p.name,
      category: p.category,
      type: p.type,
      leaderId: p.leaderId,
      source: p.source,
      startDate: p.startDate,
      endDate: p.endDate,
      budgetTotal: Number(p.budgetTotal),
      summary: p.summary,
      expectedOutcome: p.expectedOutcome,
      memberIds: res.data.members.filter((m) => m.memberRole !== '负责人').map((m) => m.userId),
    });
    Object.keys(budgetMap).forEach((k) => (budgetMap[k] = 0));
    res.data.budgets.forEach((b) => {
      budgetMap[b.category] = Number(b.amount);
    });
  } finally {
    loading.value = false;
  }
};

const buildPayload = () => ({
  name: form.name,
  category: form.category,
  type: form.type,
  leaderId: form.leaderId,
  source: form.source,
  budgetTotal: form.budgetTotal,
  startDate: form.startDate,
  endDate: form.endDate,
  summary: form.summary,
  expectedOutcome: form.expectedOutcome,
  members: form.memberIds.map((id) => ({
    userId: id,
    memberRole: '参与人',
    workload: 20,
  })),
  budgets: expenseCategoryOptions.map((item) => ({
    category: item.value,
    amount: Number(budgetMap[item.value]) || 0,
  })),
});

const save = async (andSubmit) => {
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) return;
  if (!form.endDate || !form.startDate) return;
  if (form.endDate < form.startDate) {
    ElMessage.error('结束日期不能早于开始日期');
    return;
  }
  if (!budgetBalanced.value) {
    ElMessage.error('预算科目合计需等于预算总额');
    return;
  }
  saving.value = true;
  try {
    let projectId = route.params.id;
    if (isEdit.value) {
      await projectApi.update(projectId, buildPayload());
    } else {
      const res = await projectApi.create(buildPayload());
      projectId = res.data.id;
    }
    if (!andSubmit) {
      ElMessage.success('草稿已保存');
      router.push(`/projects/${projectId}`);
      return;
    }
    try {
      await projectApi.submit(projectId);
      ElMessage.success('已提交，等待部门审核');
      router.push(`/projects/${projectId}`);
    } catch (e) {
      ElMessage.warning('草稿已保存，但提交未通过校验，请在详情页按提示补全');
      router.push(`/projects/${projectId}`);
    }
  } finally {
    saving.value = false;
  }
};

onMounted(async () => {
  await loadDict();
  if (isEdit.value) {
    await loadDetail();
  }
});
</script>

<style scoped>
.form-page {
  max-width: 1280px;
}

.head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 18px;
}

.block {
  margin-bottom: 16px;
}

.grid-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.span-2 {
  grid-column: span 2;
}

.member-preview {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 4px;
}

.member-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  border: 1px solid var(--rims-border);
  border-radius: 8px;
  padding: 6px 10px;
  font-size: 13px;
  min-width: 150px;
}

.avatar-sm {
  width: 28px;
  height: 28px;
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

.budget-row {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 6px 0;
}

.budget-label {
  width: 92px;
  font-size: 13px;
  color: var(--rims-text-secondary);
}

.budget-rate {
  width: 50px;
  text-align: right;
}

.sum-error {
  color: #ef4444;
}

.actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.action-btns {
  display: flex;
  gap: 10px;
}

@media (max-width: 1200px) {
  .grid-2 {
    grid-template-columns: 1fr;
  }

  .span-2 {
    grid-column: span 1;
  }
}
</style>
