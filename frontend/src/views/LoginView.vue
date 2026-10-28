<template>
  <div class="login-page">
    <section class="intro">
      <div class="intro-inner">
        <div class="brand">
          <div class="brand-mark">
            <svg viewBox="0 0 32 32" width="34" height="34">
              <rect width="32" height="32" rx="9" fill="#ffffff" fill-opacity="0.16" />
              <rect x="8.5" y="17" width="3.6" height="7" rx="1.4" fill="#ffffff" fill-opacity="0.7" />
              <rect x="14.2" y="12.5" width="3.6" height="11.5" rx="1.4" fill="#ffffff" fill-opacity="0.88" />
              <rect x="19.9" y="8" width="3.6" height="16" rx="1.4" fill="#ffffff" />
            </svg>
          </div>
          <span>科研云 · RIMS</span>
        </div>

        <h1>把科研项目的<br />全生命周期管清楚</h1>
        <p class="intro-desc">
          从申报、立项、进度、经费到结题验收，一套系统贯通。<br />
          审批留痕、分科目控额、风险预警，让科研管理有据可依。
        </p>

        <ul class="feature-list">
          <li v-for="item in features" :key="item.title">
            <span class="feature-dot"></span>
            <div>
              <div class="feature-title">{{ item.title }}</div>
              <div class="feature-desc">{{ item.desc }}</div>
            </div>
          </li>
        </ul>

        <div class="intro-foot">科研院所科研项目管理系统 · MVP</div>
      </div>
      <div class="glow"></div>
    </section>

    <section class="form-side">
      <div class="form-card">
        <h2>登录</h2>
        <p class="form-sub">使用院所统一账号登录科研项目管理平台</p>

        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" size="large" @keyup.enter="onSubmit">
          <el-form-item label="账号" prop="username">
            <el-input v-model="form.username" placeholder="请输入账号" :prefix-icon="User" clearable />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input v-model="form.password" type="password" show-password placeholder="请输入密码" :prefix-icon="Lock" />
          </el-form-item>
          <el-button type="primary" class="submit-btn" :loading="loading" @click="onSubmit">登 录</el-button>
        </el-form>

        <div class="demo-block">
          <div class="demo-title">演示账号（点击填充，密码均为 123456）</div>
          <div class="demo-chips">
            <button v-for="account in demoAccounts" :key="account.username" class="demo-chip"
              :class="{ active: form.username === account.username }" @click="fill(account)">
              <span class="chip-role">{{ account.role }}</span>
              <span class="chip-name">{{ account.name }} · {{ account.username }}</span>
            </button>
          </div>
        </div>
      </div>

      <div class="copyright">© {{ year }} 科研云 RIMS · 仅供演示</div>
    </section>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { Lock, User } from '@element-plus/icons-vue';
import { useAuthStore } from '@/stores/auth';

const router = useRouter();
const route = useRoute();
const auth = useAuthStore();
const formRef = ref();
const loading = ref(false);

const form = reactive({ username: 'zhangsan', password: '123456' });
const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
};

const features = [
  { title: '全流程贯通', desc: '申报 → 立项 → 执行 → 验收，状态机驱动，每一步都有留痕' },
  { title: '经费分科目管控', desc: '预算科目额度校验，超支在申请与审批两道关口拦截' },
  { title: '风险前置预警', desc: '项目逾期、里程碑延期、经费消耗过快自动提示' },
];

const demoAccounts = [
  { role: '科研人员', name: '张岩', username: 'zhangsan' },
  { role: '部门管理员', name: '赵敏', username: 'zhaomin' },
  { role: '科研处', name: '陈立', username: 'admin' },
  { role: '财务', name: '周颖', username: 'zhouying' },
  { role: '院领导', name: '孙皓', username: 'sunhao' },
];

const year = new Date().getFullYear();

const fill = (account) => {
  form.username = account.username;
  form.password = '123456';
};

const onSubmit = async () => {
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) return;
  loading.value = true;
  try {
    const user = await auth.login({ ...form });
    ElMessage.success(`欢迎回来，${user.realName}`);
    router.push(route.query.redirect || '/dashboard');
  } catch (e) {
    /* 错误提示已在拦截器统一处理 */
  } finally {
    loading.value = false;
  }
};
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 1.05fr 1fr;
  background: #fff;
}

.intro {
  position: relative;
  overflow: hidden;
  background: radial-gradient(120% 120% at 10% 10%, #2b4fd6 0%, #1b2f6b 45%, #131c3a 100%);
  color: #fff;
  padding: 64px 72px;
  display: flex;
  align-items: center;
}

.intro-inner {
  position: relative;
  z-index: 2;
  max-width: 520px;
}

.glow {
  position: absolute;
  width: 460px;
  height: 460px;
  right: -160px;
  bottom: -180px;
  background: radial-gradient(circle, rgba(111, 139, 255, 0.55), transparent 65%);
  filter: blur(10px);
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 2px;
  opacity: 0.95;
}

h1 {
  font-size: 40px;
  line-height: 1.28;
  letter-spacing: 1px;
  margin: 40px 0 18px;
  font-weight: 650;
}

.intro-desc {
  font-size: 14px;
  line-height: 1.9;
  color: rgba(255, 255, 255, 0.72);
  margin: 0 0 40px;
}

.feature-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: grid;
  gap: 20px;
}

.feature-list li {
  display: flex;
  gap: 12px;
}

.feature-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #8fb0ff;
  margin-top: 7px;
  flex: none;
  box-shadow: 0 0 0 4px rgba(143, 176, 255, 0.16);
}

.feature-title {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 4px;
}

.feature-desc {
  font-size: 12.5px;
  color: rgba(255, 255, 255, 0.6);
  line-height: 1.7;
}

.intro-foot {
  margin-top: 56px;
  font-size: 11px;
  letter-spacing: 1px;
  color: rgba(255, 255, 255, 0.38);
}

.form-side {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px;
  background: #fff;
}

.form-card {
  width: 100%;
  max-width: 380px;
}

h2 {
  font-size: 26px;
  margin: 0 0 8px;
  letter-spacing: 0.5px;
}

.form-sub {
  color: var(--rims-text-muted);
  font-size: 13px;
  margin: 0 0 28px;
}

.submit-btn {
  width: 100%;
  height: 44px;
  font-size: 15px;
  letter-spacing: 4px;
  margin-top: 4px;
}

.demo-block {
  margin-top: 28px;
  padding-top: 20px;
  border-top: 1px dashed var(--rims-border);
}

.demo-title {
  font-size: 12px;
  color: var(--rims-text-muted);
  margin-bottom: 12px;
}

.demo-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.demo-chip {
  border: 1px solid var(--rims-border);
  background: #fff;
  border-radius: 8px;
  padding: 8px 10px;
  cursor: pointer;
  text-align: left;
  transition: all 0.15s ease;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.demo-chip:hover {
  border-color: var(--rims-primary);
  background: #f7f9ff;
}

.demo-chip.active {
  border-color: var(--rims-primary);
  background: #eef3ff;
}

.chip-role {
  font-size: 10px;
  color: var(--rims-primary);
  font-weight: 600;
}

.chip-name {
  font-size: 12px;
  color: var(--rims-text-secondary);
}

.copyright {
  margin-top: 40px;
  font-size: 11px;
  color: #b6bfd0;
}

@media (max-width: 960px) {
  .login-page {
    grid-template-columns: 1fr;
  }

  .intro {
    display: none;
  }
}
</style>
