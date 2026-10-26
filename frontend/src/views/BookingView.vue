<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Calendar, Check, Document, Promotion, Refresh } from '@element-plus/icons-vue'
import { api, showError } from '../api/client'
import BookingTimePicker from '../components/BookingTimePicker.vue'
import { bookingTimeError, serverTimeAfter } from '../utils/bookingTime'
import { useAuthStore } from '../stores/auth'
import type { Meeting, Recommendation, Room, User, BookingRules, RecurringSubmissionResult } from '../types'
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const users = ref<User[]>([])
const rooms = ref<Room[]>([])
const recommendations = ref<Recommendation[]>([])
const busy = ref(false)
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
const payload = () => ({ id: form.id, subject: form.subject, startTime: form.startTime, endTime: form.endTime, roomId: form.roomId || null, attendeeIds: form.attendeeIds, attendNum: attendeeCount.value, devices: Object.fromEntries(Object.entries(form.devices).filter(([,quantity]) => quantity > 0)) })
async function recommend() {
  if (timeError()) { recommendations.value = []; return }
  loadingRecommendations.value = true
  try { const params = new URLSearchParams({ attendNum: String(attendeeCount.value), floorNo: String(auth.user!.floorNo) }); if (form.startTime) params.set('start', form.startTime); if (form.endTime) params.set('end', form.endTime); for (const [type,quantity] of Object.entries(form.devices)) if (quantity > 0) params.set(`device_${type}`, String(quantity)); recommendations.value = await api(`/meetings/recommendations?${params}`) } catch (e) { showError(e) } finally { loadingRecommendations.value = false }
}
async function save(submit: boolean) {
  if (!form.subject || !form.startTime || !form.endTime) { ElMessage.warning('请填写会议主题和起止时间'); return }
  const error = timeError(); if (error) { ElMessage.warning(error); return }
  if (submit && !form.roomId) { ElMessage.warning('请选择会议室'); return }
  busy.value = true
  try {
    if (submit) {
      const result = form.recurring
        ? await api<RecurringSubmissionResult>('/meetings/series/submit', { method: 'POST', body: JSON.stringify({ ...payload(), occurrenceCount: form.occurrenceCount }) })
        : await api<{meeting: Meeting; warnings: string[]}>('/meetings/submit', { method: 'POST', body: JSON.stringify(payload()) })
      ElMessage.success(form.recurring ? `周期预约已提交，共 ${form.occurrenceCount} 场，等待审批` : '预约已提交，等待审批')
      result.warnings.forEach(warning => ElMessage.warning({ message: warning, duration: 7000 }))
      router.push('/meetings')
    }
    else { const draft = await api<Meeting>('/meetings/drafts', { method: 'POST', body: JSON.stringify(payload()) }); form.id = draft.id; ElMessage.success('草稿已保存') }
  } catch (e) { showError(e) } finally { busy.value = false }
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
</el-form><div class="booking-actions"><el-button :icon="Document" :loading="busy" @click="save(false)">保存草稿</el-button><el-button type="primary" :icon="Promotion" :loading="busy" @click="save(true)">提交申请</el-button></div></div>
<aside class="recommendation-panel"><div class="section-heading"><h2>推荐会议室</h2><el-tooltip content="重新推荐"><el-button :icon="Refresh" circle aria-label="重新推荐" :loading="loadingRecommendations" @click="recommend" /></el-tooltip></div><div class="recommendation-weights"><span>人数 {{ Math.round((rules?.weights.capacity ?? 0.5)*100) }}%</span><span>设备 {{ Math.round((rules?.weights.device ?? 0.3)*100) }}%</span><span>楼层 {{ Math.round((rules?.weights.floor ?? 0.2)*100) }}%</span></div>
<div v-loading="loadingRecommendations"><el-empty v-if="!recommendations.length" :description="!form.startTime || !form.endTime ? '先选择日期和起止时间，查看可用会议室' : '没有符合条件的会议室'" :image-size="70" /><article v-for="(rec,index) in recommendations" :key="rec.roomId" class="recommendation-item" :class="{selected:form.roomId===rec.roomId}" @click="form.roomId=rec.roomId" tabindex="0" role="button" @keydown.enter="form.roomId=rec.roomId"><div class="recommendation-title"><div><span class="rank">{{ String(index+1).padStart(2,'0') }}</span><h3>{{ rec.roomName }}</h3></div><strong>{{ rec.totalScore }}<small>分</small></strong></div><p class="muted">{{ rec.floorNo }}层 · {{ rec.capacity }}人 · 相差{{ rec.distance }}层</p><div class="score-parts"><span>人数 {{ rec.capacityScore }}</span><span>设备 {{ rec.deviceScore }}</span><span>楼层 {{ rec.floorScore }}</span></div><p class="rec-reason">{{ rec.reason }}</p><div v-if="form.roomId===rec.roomId" class="selected-label"><el-icon><Check /></el-icon>已选择</div></article></div></aside></div>
</section></template>
