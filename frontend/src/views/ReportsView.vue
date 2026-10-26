<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Download, Refresh } from '@element-plus/icons-vue'
import { api, download, showError } from '../api/client'
import type { Room } from '../types'

const rooms = ref<Room[]>([])
const dateRange = ref<[string, string] | null>(null)
const roomId = ref('')
const status = ref('')
const loading = ref(false)
const downloading = ref(false)
const statuses = [
  ['PENDING', '待审批'], ['APPROVED', '预约成功'], ['IN_PROGRESS', '进行中'],
  ['COMPLETED', '已结束'], ['CANCELED', '已取消'], ['REJECTED', '已拒绝'], ['EXPIRED', '已过期'],
]

function queryString() {
  const params = new URLSearchParams()
  if (dateRange.value?.[0]) params.set('start', dateRange.value[0])
  if (dateRange.value?.[1]) params.set('end', dateRange.value[1])
  if (roomId.value) params.set('roomId', roomId.value)
  if (status.value) params.set('status', status.value)
  return params.toString()
}
async function exportReport() {
  downloading.value = true
  try {
    await download(`/reports/meetings.csv${queryString() ? `?${queryString()}` : ''}`, '会议报表.csv')
    ElMessage.success('报表已下载')
  } catch (e) { showError(e) } finally { downloading.value = false }
}
async function load() {
  loading.value = true
  try { rooms.value = await api<Room[]>('/resources/rooms') } catch (e) { showError(e) } finally { loading.value = false }
}
onMounted(load)
</script>
<template>
  <section>
    <div class="page-heading"><div><h1>会议报表</h1><p class="muted">按日期、会议室和状态导出会议记录 · 部门管理员仅可导出本部门</p></div><el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button></div>
    <div class="report-panel">
      <el-form label-position="top" class="report-form">
        <el-form-item label="日期范围"><el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" /></el-form-item>
        <el-form-item label="会议室"><el-select v-model="roomId" clearable placeholder="全部会议室"><el-option v-for="room in rooms" :key="room.id" :value="room.id" :label="`${room.roomName} · ${room.floorNo}层`" /></el-select></el-form-item>
        <el-form-item label="状态"><el-select v-model="status" clearable placeholder="全部状态"><el-option v-for="item in statuses" :key="item[0]" :value="item[0]" :label="item[1]" /></el-select></el-form-item>
        <el-form-item label=" "><el-button type="primary" :icon="Download" :loading="downloading" @click="exportReport">下载 CSV</el-button></el-form-item>
      </el-form>
      <p class="field-note">CSV 文件包含周期组、场次序号、申请人、会议室、状态和审批/取消信息，可直接用 Excel 打开。</p>
    </div>
  </section>
</template>
