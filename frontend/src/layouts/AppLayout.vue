<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Calendar, Grid, OfficeBuilding, Plus, Tickets, CircleCheck, Document, SwitchButton, Bell, Setting } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'
import { api, showError } from '../api/client'
const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const roleName = computed(() => auth.user ? ({ SYSTEM_ADMIN: '系统管理员', DEPT_ADMIN: '部门管理员', EMPLOYEE: '普通员工' }[auth.user.role]) : '')
const unreadCount = ref(0)
async function loadUnreadCount() {
  if (!auth.user) return
  try { unreadCount.value = Number(await api<string | number>('/notices/unread-count')) } catch { unreadCount.value = 0 }
}
function refreshUnreadCount() { void loadUnreadCount() }
const menus = computed(() => [
  { path: '/', label: '工作台', icon: Grid }, { path: '/rooms', label: '会议室与排期', icon: Calendar },
  { path: '/booking', label: '发起预约', icon: Plus }, { path: '/meetings', label: '我的会议', icon: Tickets },
  ...(auth.canApprove ? [{ path: '/approval', label: '会议审批', icon: CircleCheck }] : []),
  ...(auth.isAdmin ? [{ path: '/admin', label: '基础数据', icon: OfficeBuilding }, { path: '/settings', label: '系统参数', icon: Setting }, { path: '/audit', label: '审计日志', icon: Document }] : []),
  { path: '/notices', label: '消息中心', icon: Bell, badge: unreadCount.value },
  { path: '/account', label: '账号设置', icon: Tickets },
])
onMounted(() => window.addEventListener('notice-count-changed', refreshUnreadCount))
onBeforeUnmount(() => window.removeEventListener('notice-count-changed', refreshUnreadCount))
async function logout() { try { await auth.logout(); router.push('/login') } catch (e) { showError(e) } }
</script>
<template>
  <div class="app-shell">
    <aside class="sidebar">
      <router-link to="/" class="brand"><el-icon><OfficeBuilding /></el-icon><div>会序<span>企业会议室调度</span></div></router-link>
      <div class="nav-caption">工作空间</div>
      <nav><router-link v-for="menu in menus" :key="menu.path" :to="menu.path" :aria-label="menu.label" :class="{ active: route.path === menu.path }"><el-icon><component :is="menu.icon" /></el-icon><span>{{ menu.label }}</span><el-badge v-if="menu.badge" :value="menu.badge" :max="99" class="menu-badge" /></router-link></nav>
      <div class="sidebar-bottom"><span class="connection-dot"></span>本机演示环境<span class="version">MVP</span></div>
    </aside>
    <main class="workspace">
      <header class="topbar"><div class="breadcrumb">会序 <span>/</span> {{ route.meta.title }}</div><div class="userbar"><div class="avatar">{{ auth.user?.realName.slice(0, 1) }}</div><div><strong>{{ auth.user?.realName }}</strong><small>{{ roleName }} · {{ auth.user?.deptName }}</small></div><el-tooltip content="退出登录"><el-button :icon="SwitchButton" circle text aria-label="退出登录" @click="logout" /></el-tooltip></div></header>
      <div class="page-content"><router-view /></div>
    </main>
  </div>
</template>
