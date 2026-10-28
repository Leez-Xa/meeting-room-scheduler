<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch, h } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Calendar, Check, Document, Promotion, Refresh } from '@element-plus/icons-vue'
import { api, ApiError, showError } from '../api/client'
import BookingTimePicker from '../components/BookingTimePicker.vue'
import { bookingTimeError, serverTimeAfter } from '../utils/bookingTime'
import { useAuthStore } from '../stores/auth'
import type { Meeting, Recommendation, Room, User, BookingRules, RecurringSubmissionResult, ConflictPreview } from '../types'
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const users = ref<User[]>([])
const rooms = ref<Room[]>([])
const recommendations = ref<Recommendation[]>([])
const busy = ref(false)
const conflictDates = ref<string[]>([])
const loadingRecommendations = ref(false)
const rules=ref<BookingRules>()
let rulesReceivedAt = performance.now()
const currentServerTime = () => serverTimeAfter(rules.value!.serverNow, performance.now() - rulesReceivedAt)
const timeError = () => rules.value ? bookingTimeError(form.startTime, form.endTime, rules.value, currentServerTime()) : '正在获取预约规则，请稍后再试'
let recommendTimer: ReturnType<typeof setTimeout> | undefined
const form = reactive({ id: null as string|null, subject: '', startTime: '', endTime: '', roomId: '', attendeeIds: [] as string[], recurring: false, occurrenceCount: 2, devices: { PROJECTOR: 0, MIC: 0, VIDEO: 0, DISPLAY: 0 } })
const attendeeCount = computed(() => new Set([auth.user?.id, ...form.attendeeIds]).size)
const selectedRoom = computed(() => rooms.value.find(r => r.id === form.roomId))
const deviceLabels: Record<string,string> = { PROJECTOR: '投影仪', MIC: '麦克风', VIDEO: '视频会议设备', DISPLAY: '显示器' }
const payload = () => ({ id: form.id, subject: form.subject, startTime: form.startTime, endTime: form.endTime, roomId: form.roomId || null, attendeeIds: [...form.attendeeIds], attendNum: attendeeCount.value, devices: Object.fromEntries(Object.entries(form.devices).filter(([,quantity]) => quantity > 0)) })
async function recommend() {
  if (timeError()) { recommendations.value = []; return }
  loadingRecommendations.value = true
  try { const params = new URLSearchParams({ attendNum: String(attendeeCount.value), floorNo: String(auth.user!.floorNo) }); if (form.startTime) params.set('start', form.startTime); if (form.endTime) params.set('end', form.endTime); for (const [type,quantity] of Object.entries(form.devices)) if (quantity > 0) params.set(`device_${type}`, String(quantity)); recommendations.value = await api(`/meetings/recommendations?${params}`) } catch (e) { showError(e) } finally { loadingRecommendations.value = false }
}
async function confirmConflicts(preview: ConflictPreview, changed = false): Promise<boolean> {
  if (!preview.warnings.length) return true
  try {
    await ElMessageBox.confirm(h('div', [
      ...(changed ? [h('p', '确认期间人员安排发生变化，请核对最新冲突后再次确认。')] : []),
      h('p', '以下人员在所选时段已有会议，可能无法同时参加：'),
      h('ul', { style: 'max-height: 240px; overflow-y: auto; padding-left: 20px; overflow-wrap: anywhere' }, preview.warnings.map(warning => h('li', warning))),
      h('p', '本次预约尚未提交。确认继续后将提交审批，请自行协调人员安排。'),
    ]), changed ? '人员安排已变化，请重新确认' : '人员时间可能冲突，是否继续提交？', {
      type: 'warning', confirmButtonText: '确认继续提交', cancelButtonText: '返回修改',
      distinguishCancelAndClose: true, closeOnClickModal: true,
    })
    return true
  } catch (action) {
    if (action === 'cancel' || action === 'close') return false
    throw action
  }
}
async function save(submit: boolean) {
  if (busy.value) return
  if (!submit && form.recurring) { ElMessage.warning('周期预约暂不支持保存草稿，请直接提交'); return }
  if (!form.subject || !form.startTime || !form.endTime) { ElMessage.warning('请填写会议主题和起止时间'); return }
  const error = timeError(); if (error) { ElMessage.warning(error); return }
  if (submit && !form.roomId) { ElMessage.warning('请选择会议室'); return }
  busy.value = true
  conflictDates.value = []
  try {
    if (submit) {
      const recurring = form.recurring
      const count = form.occurrenceCount
      const request = { ...payload(), ...(recurring ? { occurrenceCount: count } : {}) }
      const basePath = recurring ? '/meetings/series' : '/meetings'
      let preview = await api<ConflictPreview>(`${basePath}/check-conflicts`, { method: 'POST', body: JSON.stringify(request) })
      let conflictsChanged = false
      while (true) {
        if (!await confirmConflicts(preview, conflictsChanged)) return
        try {
          await api<RecurringSubmissionResult | { meeting: Meeting; warnings: string[] }>(`${basePath}/submit`, {
            method: 'POST', body: JSON.stringify({ ...request, conflictConfirmation: preview.confirmationToken }),
          })
          break
        } catch (error) {
          if (!(error instanceof ApiError) || error.code !== 'ATTENDEE_CONFIRMATION_REQUIRED'
              || !error.details?.warnings?.length || !error.details.confirmationToken) throw error
          preview = { warnings: error.details.warnings, confirmationToken: error.details.confirmationToken }
          conflictsChanged = true
        }
      }
      ElMessage.success(recurring ? `周期预约已提交，共 ${count} 场，等待审批` : '预约已提交，等待审批')
      router.push('/meetings')
    }
    else { const draft = await api<Meeting>('/meetings/drafts', { method: 'POST', body: JSON.stringify(payload()) }); form.id = draft.id; ElMessage.success('草稿已保存') }
  } catch (e) { if (e instanceof ApiError) conflictDates.value = e.details?.conflictDates || []; showError(e) } finally { busy.value = false }
}
onMounted(async () => {
  try { [users.value, rooms.value, rules.value] = await Promise.all([api<User[]>('/resources/users'), api<Room[]>('/resources/rooms'),api<BookingRules>('/resources/rules')]);
    rulesReceivedAt = performance.now()
    if (route.query.draft) { const draft = await api<Meeting>(`/meetings/${route.query.draft}`); if (draft.status !== 'DRAFT') throw new Error('只有草稿可编辑'); Object.assign(form, { id: draft.id, subject: draft.subject, startTime: draft.startTime, endTime: draft.endTime, roomId: draft.roomId || '', attendeeIds: draft.attendees.map(a => a.userId) }); draft.devices.forEach(d => { if (d.deviceType in form.devices) (form.devices as Record<string,number>)[d.deviceType] = d.quantity }) }
    else { form.startTime = String(route.query.start || ''); form.endTime = String(route.query.end || ''); form.roomId = String(route.query.room || '') }
    if ((form.startTime || form.endTime) && timeError()) { form.startTime = ''; form.endTime = ''; ElMessage.warning('原预约时间已不可用，请重新选择日期和时间') }
    await recommend()
  } catch (e) { showError(e) }
})
watch(()=>[form.startTime,form.endTime,form.attendeeIds.join(','),JSON.stringify(form.devices)],()=>{
  recommendations.value=[]
  clearTimeout(recommendTimer)
  if(form.startTime && form.endTime) recommendTimer=setTimeout(recommend,350)
})
onUnmounted(()=>clearTimeout(recommendTimer))
</script>
<template><section>
<div class="page-heading"><div><h1>{{ form.id ? '编辑预约草稿' : '发起预约' }}</h1><p class="muted">{{ form.recurring ? '每周周期预约' : '单次会议申请' }} · 北京时间 · {{ rules?.openTime || '09:00' }}–{{ rules?.closeTime || '18:00' }}</p></div><el-button @click="router.push('/meetings')">返回会议列表</el-button></div>
<div class="booking-layout"><div class="booking-form"><h2><span class="step-number">01</span>会议安排</h2><el-form label-position="top">
<el-form-item label="预约方式"><el-radio-group v-model="form.recurring" :disabled="!!form.id"><el-radio-button :value="false">单次预约</el-radio-button><el-radio-button :value="true">每周预约</el-radio-button></el-radio-group><div class="field-note">首场必须在当前开放周，后续场次按周重复</div></el-form-item>
<el-form-item v-if="form.recurring" label="重复次数" required><el-input-number v-model="form.occurrenceCount" :min="2" :max="12" /></el-form-item>
<el-form-item label="会议主题" required><el-input v-model="form.subject" maxlength="200" show-word-limit placeholder="会议主题" /></el-form-item>
<BookingTimePicker v-model:start-time="form.startTime" v-model:end-time="form.endTime" :rules="rules" />
<el-form-item :label="`参会人 · ${attendeeCount} 人`"><el-select v-model="form.attendeeIds" multiple filterable placeholder="选择内部员工" style="width:100%"><el-option v-for="user in users" :key="user.id" :value="user.id" :label="`${user.realName} · ${user.deptName}`" :disabled="user.id === auth.user?.id" /></el-select><div class="field-note">申请人：{{ auth.user?.realName }}</div></el-form-item>
<h2><span class="step-number">02</span>设备与会议室</h2><div class="device-requirements"><div v-for="(label,type) in deviceLabels" :key="type"><label>{{ label }}</label><el-input-number v-model="(form.devices as Record<string,number>)[type]" :min="0" :max="20" size="small" /></div></div>
<el-form-item label="会议室" required><el-select v-model="form.roomId" placeholder="选择会议室" style="width:100%"><el-option v-for="room in rooms" :key="room.id" :value="room.id" :label="`${room.roomName} · ${room.floorNo}层 · ${room.capacity}人`" :disabled="!room.active" /></el-select></el-form-item>
</el-form><el-alert v-if="conflictDates.length" title="以下日期存在资源冲突，请调整会议室或时间后重新提交" type="error" :closable="false" show-icon><p>{{ conflictDates.join("、") }}</p><p>本次整组未提交，表单内容已保留。</p></el-alert><div class="booking-actions"><el-button v-if="!form.recurring" :icon="Document" :loading="busy" @click="save(false)">保存草稿</el-button><el-button type="primary" :icon="Promotion" :loading="busy" @click="save(true)">提交申请</el-button></div></div>
<aside class="recommendation-panel"><div class="section-heading"><h2>推荐会议室</h2><el-tooltip content="重新推荐"><el-button :icon="Refresh" circle aria-label="重新推荐" :loading="loadingRecommendations" @click="recommend" /></el-tooltip></div><p v-if="form.recurring" class="field-note">推荐仅检查首场，提交时会校验全部场次。</p><div class="recommendation-weights"><span>人数 {{ Math.round((rules?.weights.capacity ?? 0.5)*100) }}%</span><span>设备 {{ Math.round((rules?.weights.device ?? 0.3)*100) }}%</span><span>楼层 {{ Math.round((rules?.weights.floor ?? 0.2)*100) }}%</span></div>
<div v-loading="loadingRecommendations"><el-empty v-if="!recommendations.length" :description="!form.startTime || !form.endTime ? '先选择日期和起止时间，查看可用会议室' : '没有符合条件的会议室'" :image-size="70" /><article v-for="(rec,index) in recommendations" :key="rec.roomId" class="recommendation-item" :class="{selected:form.roomId===rec.roomId}" @click="form.roomId=rec.roomId" tabindex="0" role="button" @keydown.enter="form.roomId=rec.roomId"><div class="recommendation-title"><div><span class="rank">{{ String(index+1).padStart(2,'0') }}</span><h3>{{ rec.roomName }}</h3></div><strong>{{ rec.totalScore }}<small>分</small></strong></div><p class="muted">{{ rec.floorNo }}层 · {{ rec.capacity }}人 · 相差{{ rec.distance }}层</p><div class="score-parts"><span>人数 {{ rec.capacityScore }}</span><span>设备 {{ rec.deviceScore }}</span><span>楼层 {{ rec.floorScore }}</span></div><p class="rec-reason">{{ rec.reason }}</p><div v-if="form.roomId===rec.roomId" class="selected-label"><el-icon><Check /></el-icon>已选择</div></article></div></aside></div>
</section></template>
