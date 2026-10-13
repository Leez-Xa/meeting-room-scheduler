<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Plus, ArrowRight, Calendar, OfficeBuilding, Clock, CircleCheck } from '@element-plus/icons-vue'
import { api, showError } from '../api/client'
import { useAuthStore } from '../stores/auth'
import type { Dashboard, Meeting, Page } from '../types'
import StatusTag from '../components/StatusTag.vue'
const auth = useAuthStore()
const router = useRouter()
const stats = ref<Dashboard>()
const meetings = ref<Meeting[]>([])
const loading = ref(true)
onMounted(async () => { try { const [dashboard,page] = await Promise.all([api<Dashboard>('/dashboard'), api<Page<Meeting>>('/meetings?pageSize=8')]); stats.value=dashboard; meetings.value=page.records } catch (e) { showError(e) } finally { loading.value = false } })
</script>
<template>
  <section v-loading="loading">
    <div class="page-heading"><div><div class="eyebrow">{{ new Date().toLocaleDateString('zh-CN', { month: 'long', day: 'numeric', weekday: 'long' }) }}</div><h1>{{ auth.user?.realName }}，工作台</h1><p class="muted">{{ auth.user?.deptName }} · {{ auth.user?.floorNo }} 层</p></div><el-button type="primary" :icon="Plus" @click="router.push('/booking')">发起预约</el-button></div>
    <div class="stat-row"><div class="stat"><el-icon><OfficeBuilding /></el-icon><span>启用会议室</span><strong>{{ stats?.availableRooms ?? '—' }}<small> / {{ stats?.totalRooms ?? '—' }}</small></strong></div><div class="stat"><el-icon><Calendar /></el-icon><span>即将开始</span><strong>{{ stats?.myUpcoming ?? '—' }}</strong></div><div class="stat"><el-icon><Clock /></el-icon><span>我的待审批</span><strong>{{ stats?.myPending ?? '—' }}</strong></div><div class="stat"><el-icon><CircleCheck /></el-icon><span>待我审批</span><strong>{{ stats?.pendingApproval ?? '—' }}</strong></div></div>
    <div class="section-heading"><h2>近期会议</h2><el-button text :icon="ArrowRight" @click="router.push('/meetings')">全部会议</el-button></div>
    <el-table :data="meetings.slice(0, 8)" empty-text="暂无会议" class="data-table"><el-table-column prop="subject" label="会议主题" min-width="200" /><el-table-column prop="roomName" label="会议室" min-width="120" /><el-table-column label="开始时间" min-width="170"><template #default="{row}">{{ row.startTime.replace('T', ' ').slice(0, 16) }}</template></el-table-column><el-table-column label="状态" width="130"><template #default="{row}"><StatusTag :status="row.status" /></template></el-table-column><el-table-column label="人数" width="80"><template #default="{row}">{{ row.attendNum }} 人</template></el-table-column></el-table>
    <div class="workspace-links"><router-link to="/rooms"><el-icon><Calendar /></el-icon><div><h3>会议室周排期</h3><span>本周会议室占用</span></div><el-icon><ArrowRight /></el-icon></router-link><router-link v-if="auth.canApprove" to="/approval"><el-icon><CircleCheck /></el-icon><div><h3>待审批申请</h3><span>{{ stats?.pendingApproval ?? 0 }} 项待处理</span></div><el-icon><ArrowRight /></el-icon></router-link></div>
  </section>
</template>
