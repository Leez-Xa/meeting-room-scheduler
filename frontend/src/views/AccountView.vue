<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api, showError } from '../api/client'
import { useAuthStore } from '../stores/auth'
const auth=useAuthStore()
const router=useRouter()
const loading=ref(false)
const form=reactive({currentPassword:'',newPassword:'',confirmPassword:''})
async function save() {
  if(form.newPassword!==form.confirmPassword) { ElMessage.warning('两次新密码不一致'); return }
  loading.value=true
  try { await api('/auth/password',{method:'POST',body:JSON.stringify(form)}); auth.user=null; await router.replace('/login'); ElMessage.success('密码已修改，请重新登录') } catch(e) { showError(e) } finally { loading.value=false }
}
</script>
<template><section><div class="page-heading"><div><h1>账号设置</h1><p class="muted">{{ auth.user?.username }} · {{ auth.user?.deptName }}</p></div></div><el-form label-position="top" style="max-width:420px" @submit.prevent="save"><el-form-item label="当前密码" required><el-input v-model="form.currentPassword" type="password" show-password autocomplete="current-password" /></el-form-item><el-form-item label="新密码" required><el-input v-model="form.newPassword" type="password" show-password autocomplete="new-password" placeholder="至少8字符" /></el-form-item><el-form-item label="确认新密码" required><el-input v-model="form.confirmPassword" type="password" show-password autocomplete="new-password" /></el-form-item><el-button type="primary" native-type="submit" :loading="loading">修改密码</el-button></el-form></section></template>
