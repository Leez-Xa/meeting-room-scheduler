<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Check, Close } from '@element-plus/icons-vue'
import { api, showError } from '../api/client'
import { useAuthStore } from '../stores/auth'
import type { Meeting, Page } from '../types'
import MeetingDetail from '../components/MeetingDetail.vue'
const auth = useAuthStore()
const meetings = ref<Meeting[]>([])
const detail = ref<Meeting | null>(null)
const loading = ref(false)
const page=ref(1)
const total=ref(0)
async function load() { loading.value = true; try { const result=await api<Page<Meeting>>(`/meetings?scope=pending&page=${page.value}&pageSize=20`); meetings.value=result.records; total.value=Number(result.total) } catch (e) { showError(e) } finally { loading.value = false } }
function isFirstSeriesOccurrence(meeting: Meeting) { return !meeting.seriesId || meeting.seriesIndex === 1 }
async function approveSeries(meeting: Meeting, approved: boolean) {
  if (!meeting.seriesId) return approve(meeting, approved)
  try {
    const { value } = await ElMessageBox.prompt(approved ? '整组审批意见（选填）' : '整组拒绝原因', approved ? '整组通过' : '整组拒绝', { confirmButtonText: approved ? '整组通过' : '整组拒绝', cancelButtonText: '取消', inputPlaceholder: '填写处理意见' })
    await api(`/meetings/series/${meeting.seriesId}/approval`, { method: 'POST', body: JSON.stringify({ approved, comment: value }) })
    ElMessage.success(approved ? '周期预约已整组通过' : '周期预约已整组拒绝')
    await load()
  } catch (e) { if (e instanceof Error) showError(e) }
}
async function approve(meeting: Meeting, approved: boolean) {
  try { const { value } = await ElMessageBox.prompt(approved ? '审批意见（选填）' : '拒绝原因', approved ? '审批通过' : '拒绝申请', { confirmButtonText: approved ? '通过' : '拒绝', cancelButtonText: '取消', inputPlaceholder: '填写处理意见' }); await api(`/meetings/${meeting.id}/approval`, { method: 'POST', body: JSON.stringify({ approved, comment: value }) }); ElMessage.success(approved ? '审批通过' : '已拒绝'); load() } catch (e) { if (e instanceof Error) showError(e) }
}
onMounted(load)
</script>
<template><section><div class="page-heading"><div><h1>会议审批</h1><p class="muted">{{ auth.isAdmin ? '全公司' : auth.user?.deptName }} · {{ meetings.length }} 项待处理</p></div><el-button :icon="Refresh" @click="load">刷新</el-button></div>
<el-table :data="meetings" v-loading="loading" empty-text="暂无待审批申请" class="data-table"><el-table-column prop="subject" label="会议主题" min-width="180" /><el-table-column prop="applicantName" label="申请人" width="130" /><el-table-column prop="roomName" label="会议室" width="120" /><el-table-column label="时间" min-width="210"><template #default="{row}">{{ row.startTime.replace('T',' ').slice(0,16) }} - {{ row.endTime.slice(11,16) }}</template></el-table-column>
<el-table-column label="周期" width="100"><template #default="{row}">{{ row.seriesId ? `${row.seriesIndex}/${row.seriesTotal} 场` : '单次' }}</template></el-table-column><el-table-column label="操作" min-width="250"><template #default="{row}"><el-button text @click="detail=row">详情</el-button><template v-if="row.seriesId && isFirstSeriesOccurrence(row)"><el-button type="primary" :icon="Check" size="small" @click="approveSeries(row,true)">整组通过</el-button><el-button :icon="Close" size="small" @click="approveSeries(row,false)">整组拒绝</el-button></template><template v-else-if="!row.seriesId"><el-button type="primary" :icon="Check" size="small" @click="approve(row,true)">通过</el-button><el-button :icon="Close" size="small" @click="approve(row,false)">拒绝</el-button></template><span v-else class="muted">整组操作请处理第 1 场</span></template></el-table-column></el-table>
<el-pagination :current-page="page" :total="total" :page-size="20" layout="total, prev, pager, next" style="margin-top:20px" @current-change="(value:number)=>{page=value;load()}" />
<el-dialog :model-value="!!detail" title="申请详情" width="min(600px,94vw)" @close="detail=null"><MeetingDetail :meeting="detail" /></el-dialog></section></template>
