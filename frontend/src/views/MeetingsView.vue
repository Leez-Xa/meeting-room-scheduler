<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, showError } from '../api/client'
import { useAuthStore } from '../stores/auth'
import type { Meeting, Page } from '../types'
import StatusTag from '../components/StatusTag.vue'
import MeetingDetail from '../components/MeetingDetail.vue'
const auth = useAuthStore()
const router = useRouter()
const meetings = ref<Meeting[]>([])
const status = ref('ALL')
const detail = ref<Meeting | null>(null)
const loading = ref(false)
const scope=ref('mine')
const page=ref(1)
const total=ref(0)
const filtered = computed(() => meetings.value)
async function load() { loading.value = true; try { const result=await api<Page<Meeting>>(`/meetings?scope=${scope.value}&page=${page.value}&pageSize=20${status.value==='ALL'?'':`&status=${status.value}`}`); meetings.value=result.records; total.value=Number(result.total) } catch (e) { showError(e) } finally { loading.value = false } }
function changeFilter() { page.value=1; load() }
async function cancelSeries(meeting: Meeting) {
  if (!meeting.seriesId) return
  let scope: 'ALL' | 'ONE' = 'ALL'
  try {
    await ElMessageBox.confirm(`“${meeting.subject}”是周期预约。确认取消全部未开始场次吗？`, '取消周期预约', { confirmButtonText: '取消全部未开始场次', cancelButtonText: '仅取消本场' })
  } catch (error) {
    if (error === 'cancel') scope = 'ONE'
    else return
  }
  try {
    await api(`/meetings/series/${meeting.seriesId}/cancel`, { method: 'POST', body: JSON.stringify({ scope, meetingId: scope === 'ONE' ? meeting.id : null, reason: '申请人取消周期预约' }) })
    ElMessage.success(scope === 'ALL' ? '已取消全部未开始场次' : '已取消本场')
    await load()
  } catch (e) { showError(e) }
}

async function cancel(meeting: Meeting) {
  if (meeting.seriesId && meeting.userId === auth.user?.id && meeting.status !== 'IN_PROGRESS') { await cancelSeries(meeting); return }
  try {
    let reason='申请人主动取消'
    if (meeting.userId!==auth.user?.id || meeting.status==='IN_PROGRESS') {
      const result=await ElMessageBox.prompt(`为资源维护取消“${meeting.subject}”`,'资源维护取消',{inputPlaceholder:'必须填写原因',inputValidator:value=>!!value?.trim() || '请填写取消原因',confirmButtonText:'取消会议',cancelButtonText:'返回'}); reason=result.value
    } else await ElMessageBox.confirm(`取消“${meeting.subject}”？`, '取消预约', { confirmButtonText: '确认取消', cancelButtonText: '保留预约' })
    await api(`/meetings/${meeting.id}/cancel`, { method: 'POST', body: JSON.stringify({ reason }) }); ElMessage.success('预约已取消'); load()
  } catch (e) { if (e instanceof Error) showError(e) }
}
onMounted(load)
</script>
<template>
  <section><div class="page-heading"><div><h1>我的会议</h1><p class="muted">申请与参会记录</p></div><el-button type="primary" :icon="Plus" @click="router.push('/booking')">发起预约</el-button></div>
    <div class="table-toolbar"><el-select v-model="status" style="width: 160px" @change="changeFilter"><el-option label="全部状态" value="ALL" /><el-option label="草稿" value="DRAFT" /><el-option label="待审批" value="PENDING" /><el-option label="预约成功" value="APPROVED" /><el-option label="进行中" value="IN_PROGRESS" /><el-option label="已结束" value="COMPLETED" /><el-option label="已过期" value="EXPIRED" /><el-option label="已取消" value="CANCELED" /><el-option label="已拒绝" value="REJECTED" /></el-select><el-radio-group v-if="auth.canApprove" v-model="scope" @change="changeFilter"><el-radio-button value="mine">我的会议</el-radio-button><el-radio-button value="visible">{{ auth.isAdmin?'全部会议':'本部门及参会会议' }}</el-radio-button></el-radio-group><el-button :icon="Refresh" circle aria-label="刷新" @click="load" /></div>
    <el-table :data="filtered" v-loading="loading" empty-text="暂无会议" class="data-table">
      <el-table-column prop="subject" label="会议主题" min-width="200" />
      <el-table-column prop="roomName" label="会议室" min-width="120" />
      <el-table-column label="时间" min-width="210"><template #default="{row}">{{ row.startTime.replace('T', ' ').slice(0, 16) }} - {{ row.endTime.slice(11,16) }}</template></el-table-column>
      <el-table-column label="周期" width="100"><template #default="{row}">{{ row.seriesId ? `${row.seriesIndex}/${row.seriesTotal} 场` : '单次' }}</template></el-table-column>
      <el-table-column label="状态" width="130"><template #default="{row}"><StatusTag :status="row.status" /></template></el-table-column>
      <el-table-column label="操作" min-width="180"><template #default="{row}">
        <el-button text @click="detail=row">详情</el-button>
        <el-button v-if="row.status==='DRAFT' && row.userId===auth.user?.id" text type="primary" @click="router.push(`/booking?draft=${row.id}`)">编辑</el-button>
        <el-button v-if="['PENDING','APPROVED','IN_PROGRESS'].includes(row.status) && (auth.isAdmin || (row.userId===auth.user?.id && row.status!=='IN_PROGRESS'))" text type="danger" @click="cancel(row)">{{ row.userId===auth.user?.id && row.status!=='IN_PROGRESS'?'取消':'维护取消' }}</el-button>
      </template></el-table-column>
    </el-table>
    <el-pagination :current-page="page" :total="total" :page-size="20" layout="total, prev, pager, next" style="margin-top:20px" @current-change="(value:number)=>{page=value;load()}" />
    <el-dialog :model-value="!!detail" title="会议详情" width="min(600px, 94vw)" @close="detail=null"><MeetingDetail :meeting="detail" /></el-dialog>
  </section>
</template>
