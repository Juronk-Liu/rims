<template>
  <div class="page">
    <div class="head">
      <div>
        <h1 class="page-title">审计日志</h1>
        <div class="page-subtitle">记录关键业务动作与操作人，用于内部审计与问题追溯</div>
      </div>
      <el-input v-model="keyword" placeholder="搜索操作人 / 动作 / 对象" clearable style="width: 260px"
        :prefix-icon="Search" @keyup.enter="load(1)" @clear="load(1)" />
    </div>

    <div class="card table-card">
      <el-table :data="list" v-loading="loading">
        <el-table-column label="时间" width="170">
          <template #default="{ row }"><span class="mono tiny">{{ datetime(row.createdAt) }}</span></template>
        </el-table-column>
        <el-table-column prop="username" label="操作人" width="120" />
        <el-table-column label="动作" width="170">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.action }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="target" label="对象" min-width="200" show-overflow-tooltip />
        <el-table-column prop="detail" label="详情" min-width="280" show-overflow-tooltip />
        <el-table-column prop="ip" label="IP" width="140" />
        <template #empty><div class="empty-block">暂无日志</div></template>
      </el-table>
      <div class="pager">
        <el-pagination v-model:current-page="page" v-model:page-size="size" :total="total"
          layout="total, prev, pager, next" background @current-change="load()" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { Search } from '@element-plus/icons-vue';
import { auditApi } from '@/api';
import { datetime } from '@/utils/format';

const loading = ref(false);
const list = ref([]);
const total = ref(0);
const page = ref(1);
const size = ref(20);
const keyword = ref('');

const load = async (toPage) => {
  if (toPage) page.value = toPage;
  loading.value = true;
  try {
    const res = await auditApi.list({ page: page.value, size: size.value, keyword: keyword.value });
    list.value = res.data.list;
    total.value = res.data.total;
  } finally {
    loading.value = false;
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

.table-card {
  padding: 6px 16px 16px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  padding-top: 14px;
}
</style>
