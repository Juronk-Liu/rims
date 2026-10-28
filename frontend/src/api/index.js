import http from './http';

export const authApi = {
  login: (data) => http.post('/auth/login', data),
  me: () => http.get('/auth/me'),
  users: () => http.get('/auth/users'),
  dicts: () => http.get('/auth/dicts'),
};

export const projectApi = {
  list: (params) => http.get('/projects', { params }),
  detail: (id) => http.get(`/projects/${id}`),
  create: (data) => http.post('/projects', data),
  update: (id, data) => http.put(`/projects/${id}`, data),
  remove: (id) => http.delete(`/projects/${id}`),
  submit: (id) => http.post(`/projects/${id}/submit`),
  review: (id, data) => http.post(`/projects/${id}/review`, data),
  acceptanceSubmit: (id) => http.post(`/projects/${id}/acceptance-submit`),
  acceptance: (id, data) => http.post(`/projects/${id}/acceptance`, data),
  terminate: (id, data) => http.post(`/projects/${id}/terminate`, data),
};

export const progressApi = {
  milestones: (projectId) => http.get(`/projects/${projectId}/milestones`),
  addMilestone: (projectId, data) => http.post(`/projects/${projectId}/milestones`, data),
  updateMilestone: (id, data) => http.put(`/milestones/${id}`, data),
  completeMilestone: (id) => http.post(`/milestones/${id}/complete`),
  removeMilestone: (id) => http.delete(`/milestones/${id}`),
  reports: (projectId) => http.get(`/projects/${projectId}/progress`),
  addReport: (projectId, data) => http.post(`/projects/${projectId}/progress`, data),
  achievements: (projectId) => http.get(`/projects/${projectId}/achievements`),
  addAchievement: (projectId, data) => http.post(`/projects/${projectId}/achievements`, data),
  removeAchievement: (id) => http.delete(`/achievements/${id}`),
};

export const financeApi = {
  budgets: (projectId) => http.get(`/projects/${projectId}/budgets`),
  saveBudgets: (projectId, data) => http.put(`/projects/${projectId}/budgets`, data),
  expensesByProject: (projectId) => http.get(`/projects/${projectId}/expenses`),
  expenses: (params) => http.get('/expenses', { params }),
  createExpense: (projectId, data) => http.post(`/projects/${projectId}/expenses`, data),
  approve: (id, data) => http.post(`/expenses/${id}/approve`, data),
};

export const dashboardApi = {
  overview: () => http.get('/dashboard/overview'),
};

export const auditApi = {
  list: (params) => http.get('/audit-logs', { params }),
};
