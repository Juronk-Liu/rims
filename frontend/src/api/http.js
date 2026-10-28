import axios from 'axios';
import { ElMessage } from 'element-plus';

const http = axios.create({
  baseURL: '/api',
  timeout: 20000,
});

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('rims_token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

http.interceptors.response.use(
  (response) => {
    const body = response.data;
    if (body && typeof body.code === 'number' && body.code !== 0) {
      if (body.code === 401) {
        localStorage.removeItem('rims_token');
        localStorage.removeItem('rims_user');
        if (!location.hash.includes('/login')) {
          location.hash = '#/login';
        }
      }
      ElMessage.error(body.message || '请求失败');
      return Promise.reject(new Error(body.message || '请求失败'));
    }
    return body;
  },
  (error) => {
    ElMessage.error(error.message === 'Network Error'
      ? '无法连接后端服务，请确认已启动 8080 端口'
      : `请求异常：${error.message}`);
    return Promise.reject(error);
  }
);

export default http;
