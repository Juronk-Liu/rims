import { createRouter, createWebHashHistory } from 'vue-router';
import { ElMessage } from 'element-plus';
import { useAuthStore } from '@/stores/auth';

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { public: true, title: '登录' },
  },
  {
    path: '/',
    component: () => import('@/layout/AppLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'dashboard',
        component: () => import('@/views/DashboardView.vue'),
        meta: { title: '工作台' },
      },
      {
        path: 'projects',
        name: 'projects',
        component: () => import('@/views/ProjectListView.vue'),
        meta: { title: '项目库' },
      },
      {
        path: 'projects/new',
        name: 'project-new',
        component: () => import('@/views/ProjectFormView.vue'),
        meta: { title: '新建申报' },
      },
      {
        path: 'projects/:id',
        name: 'project-detail',
        component: () => import('@/views/ProjectDetailView.vue'),
        meta: { title: '项目详情' },
      },
      {
        path: 'projects/:id/edit',
        name: 'project-edit',
        component: () => import('@/views/ProjectFormView.vue'),
        meta: { title: '编辑申报' },
      },
      {
        path: 'approvals',
        name: 'approvals',
        component: () => import('@/views/ApprovalCenterView.vue'),
        meta: { title: '审批中心', roles: ['ADMIN', 'DEPT_ADMIN', 'FINANCE', 'LEADER'] },
      },
      {
        path: 'expenses',
        name: 'expenses',
        component: () => import('@/views/ExpenseView.vue'),
        meta: { title: '经费管理' },
      },
      {
        path: 'audit',
        name: 'audit',
        component: () => import('@/views/AuditLogView.vue'),
        meta: { title: '审计日志', roles: ['ADMIN'] },
      },
    ],
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' },
];

const router = createRouter({
  history: createWebHashHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
});

router.beforeEach((to) => {
  const auth = useAuthStore();
  document.title = to.meta.title ? `${to.meta.title} · 科研云` : '科研云';
  if (to.meta.public) {
    return true;
  }
  if (!auth.token) {
    return { path: '/login', query: { redirect: to.fullPath } };
  }
  if (to.meta.roles && !to.meta.roles.includes(auth.role)) {
    ElMessage.warning('当前角色无权访问该页面');
    return { path: '/dashboard' };
  }
  return true;
});

export default router;
