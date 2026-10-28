<template>
  <div class="layout">
    <aside class="sidebar" :class="{ collapsed }">
      <div class="brand" @click="router.push('/dashboard')">
        <div class="brand-mark">
          <svg viewBox="0 0 32 32" width="30" height="30">
            <rect width="32" height="32" rx="9" fill="#2f5bff" />
            <rect x="8.5" y="17" width="3.6" height="7" rx="1.4" fill="#ffffff" fill-opacity="0.65" />
            <rect x="14.2" y="12.5" width="3.6" height="11.5" rx="1.4" fill="#ffffff" fill-opacity="0.85" />
            <rect x="19.9" y="8" width="3.6" height="16" rx="1.4" fill="#ffffff" />
          </svg>
        </div>
        <div v-show="!collapsed" class="brand-text">
          <div class="brand-name">科研云</div>
          <div class="brand-sub">RIMS · 科研项目管理</div>
        </div>
      </div>

      <el-menu :default-active="activeMenu" :collapse="collapsed" :collapse-transition="false" router class="side-menu">
        <el-menu-item index="/dashboard">
          <el-icon><DataLine /></el-icon>
          <template #title>工作台</template>
        </el-menu-item>
        <el-menu-item v-if="auth.isResearcher" index="/projects?scope=mine">
          <el-icon><Star /></el-icon>
          <template #title>我的项目</template>
        </el-menu-item>
        <el-menu-item v-else index="/projects">
          <el-icon><Folder /></el-icon>
          <template #title>项目库</template>
        </el-menu-item>
        <el-menu-item v-if="showApproval" index="/approvals">
          <el-icon><CircleCheck /></el-icon>
          <template #title>审批中心</template>
        </el-menu-item>
        <el-menu-item index="/expenses">
          <el-icon><Wallet /></el-icon>
          <template #title>经费管理</template>
        </el-menu-item>
        <el-menu-item v-if="auth.isAdmin" index="/audit">
          <el-icon><Document /></el-icon>
          <template #title>审计日志</template>
        </el-menu-item>
      </el-menu>

      <div v-show="!collapsed" class="side-footer">
        <div class="side-footer-line">科研项目全生命周期管理</div>
        <div class="side-footer-line muted">MVP · v1.0.0</div>
      </div>
    </aside>

    <div class="main">
      <header class="topbar">
        <div class="topbar-left">
          <el-icon class="collapse-btn" @click="collapsed = !collapsed">
            <Fold v-if="!collapsed" />
            <Expand v-else />
          </el-icon>
          <div>
            <div class="crumb">
              <span class="crumb-root">科研管理</span>
              <span class="crumb-sep">/</span>
              <span>{{ route.meta.title }}</span>
            </div>
          </div>
        </div>
        <div class="topbar-right">
          <el-button v-if="canCreate && !isFormPage" type="primary" @click="router.push('/projects/new')">
            <el-icon style="margin-right: 4px"><Plus /></el-icon>
            新建申报
          </el-button>
          <el-dropdown trigger="click" @command="onCommand">
            <div class="user-chip">
              <div class="avatar">{{ avatarText }}</div>
              <div class="user-meta">
                <div class="user-name">{{ auth.user?.realName }}</div>
                <div class="user-role">{{ roleLabel(auth.role) }} · {{ auth.user?.dept }}</div>
              </div>
              <el-icon class="chev"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="refresh">刷新身份信息</el-dropdown-item>
                <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main class="content">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useAuthStore } from '@/stores/auth';
import { roleLabel } from '@/utils/dict';

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const collapsed = ref(false);

const showApproval = computed(() => ['ADMIN', 'DEPT_ADMIN', 'FINANCE', 'LEADER'].includes(auth.role));
const canCreate = computed(() => !['LEADER', 'FINANCE'].includes(auth.role));
const isFormPage = computed(() => ['project-new', 'project-edit'].includes(route.name));
const avatarText = computed(() => (auth.user?.realName || '?').slice(-2));
const activeMenu = computed(() => {
  if (route.path.startsWith('/projects') && (auth.isResearcher || route.query.scope === 'mine')) {
    return '/projects?scope=mine';
  }
  if (route.path.startsWith('/projects')) return '/projects';
  return route.path;
});

const onCommand = async (command) => {
  if (command === 'logout') {
    await ElMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning' });
    auth.logout();
    router.push('/login');
    return;
  }
  if (command === 'refresh') {
    await auth.fetchMe();
    ElMessage.success('身份信息已刷新');
  }
};
</script>

<style scoped>
.layout {
  display: flex;
  min-height: 100vh;
  background: var(--rims-bg);
}

.sidebar {
  width: 224px;
  background: #fff;
  border-right: 1px solid var(--rims-border);
  display: flex;
  flex-direction: column;
  transition: width 0.2s ease;
  position: sticky;
  top: 0;
  height: 100vh;
}

.sidebar.collapsed {
  width: 68px;
}

.brand {
  height: 64px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 16px;
  cursor: pointer;
  border-bottom: 1px solid var(--rims-border);
}

.brand-text {
  line-height: 1.2;
}

.brand-name {
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 1px;
}

.brand-sub {
  font-size: 10px;
  color: var(--rims-text-muted);
  margin-top: 3px;
  white-space: nowrap;
}

.side-menu {
  border-right: none;
  flex: 1;
  padding: 10px 8px;
  --el-menu-item-height: 44px;
  --el-menu-bg-color: transparent;
}

.side-menu :deep(.el-menu-item) {
  border-radius: 8px;
  margin-bottom: 4px;
  color: #4a5468;
  font-size: 14px;
}

.side-menu :deep(.el-menu-item.is-active) {
  background: #eef3ff;
  color: var(--rims-primary);
  font-weight: 600;
}

.side-menu :deep(.el-menu-item:hover) {
  background: #f5f7fd;
}

.side-footer {
  padding: 14px 18px;
  border-top: 1px solid var(--rims-border);
  font-size: 11px;
  line-height: 1.7;
}

.main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.topbar {
  height: 64px;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(8px);
  border-bottom: 1px solid var(--rims-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  position: sticky;
  top: 0;
  z-index: 10;
}

.topbar-left {
  display: flex;
  align-items: center;
  gap: 14px;
}

.collapse-btn {
  font-size: 18px;
  color: var(--rims-text-secondary);
  cursor: pointer;
}

.crumb {
  font-size: 13px;
  color: var(--rims-text);
  font-weight: 500;
}

.crumb-root {
  color: var(--rims-text-muted);
}

.crumb-sep {
  margin: 0 6px;
  color: #c3cad8;
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.user-chip {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  padding: 6px 10px 6px 6px;
  border-radius: 10px;
}

.user-chip:hover {
  background: #f4f6fb;
}

.avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: linear-gradient(135deg, #2f5bff, #6f8bff);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
}

.user-meta {
  line-height: 1.25;
}

.user-name {
  font-size: 13px;
  font-weight: 600;
}

.user-role {
  font-size: 11px;
  color: var(--rims-text-muted);
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chev {
  font-size: 12px;
  color: var(--rims-text-muted);
}

.content {
  flex: 1;
  min-width: 0;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.16s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
