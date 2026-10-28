import { defineStore } from 'pinia';
import { authApi } from '@/api';

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('rims_token') || '',
    user: JSON.parse(localStorage.getItem('rims_user') || 'null'),
  }),
  getters: {
    role: (state) => state.user?.role || '',
    isAdmin: (state) => state.user?.role === 'ADMIN',
    isDeptAdmin: (state) => state.user?.role === 'DEPT_ADMIN',
    isFinance: (state) => state.user?.role === 'FINANCE',
    isResearcher: (state) => state.user?.role === 'RESEARCHER',
    canApproveProject: (state) => ['ADMIN', 'DEPT_ADMIN'].includes(state.user?.role),
    canApproveExpense: (state) => ['ADMIN', 'FINANCE'].includes(state.user?.role),
  },
  actions: {
    async login(payload) {
      const res = await authApi.login(payload);
      this.token = res.data.token;
      this.user = res.data.user;
      localStorage.setItem('rims_token', this.token);
      localStorage.setItem('rims_user', JSON.stringify(this.user));
      return this.user;
    },
    async fetchMe() {
      const res = await authApi.me();
      this.user = res.data;
      localStorage.setItem('rims_user', JSON.stringify(this.user));
      return this.user;
    },
    logout() {
      this.token = '';
      this.user = null;
      localStorage.removeItem('rims_token');
      localStorage.removeItem('rims_user');
    },
  },
});
