<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { OfficeBuilding, User, Lock } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'
import { showError } from '../api/client'
const form = reactive({ username: '', password: '' })
const loading = ref(false)
const auth = useAuthStore()
const router = useRouter()
async function login() {
  loading.value = true
  try { await auth.login(form.username, form.password); router.push('/') } catch (e) { showError(e) } finally { loading.value = false }
}
</script>
<template>
  <main class="login-page">
    <form class="login-form" @submit.prevent="login">
      <div class="login-brand"><el-icon><OfficeBuilding /></el-icon><span>会序</span></div>
      <h1>企业会议室调度</h1><p class="muted">登录工作空间</p>
      <el-form label-position="top"><el-form-item label="账号"><el-input v-model="form.username" :prefix-icon="User" autocomplete="username" placeholder="请输入账号" size="large" /></el-form-item><el-form-item label="密码"><el-input v-model="form.password" :prefix-icon="Lock" type="password" show-password autocomplete="current-password" placeholder="请输入密码" size="large" /></el-form-item></el-form>
      <el-button type="primary" native-type="submit" size="large" :loading="loading" class="login-submit">登录</el-button>
      <p class="login-footer">企业内部系统 · 账号由系统管理员分配</p>
    </form>
  </main>
</template>
