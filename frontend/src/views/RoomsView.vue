<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import FullCalendar from '@fullcalendar/vue3'
import timeGridPlugin from '@fullcalendar/timegrid'
import interactionPlugin from '@fullcalendar/interaction'
import zhCnLocale from '@fullcalendar/core/locales/zh-cn'
import type { CalendarOptions, DatesSetArg, EventClickArg } from '@fullcalendar/core'
import { Location, User, Plus } from '@element-plus/icons-vue'
import { api, showError } from '../api/client'
import type { Meeting, Room, ScheduleEvent, BookingRules } from '../types'
import MeetingDetail from '../components/MeetingDetail.vue'
const router = useRouter()
const rooms = ref<Room[]>([])
const selectedRoom = ref('ALL')
const events = ref<ScheduleEvent[]>([])
const detail = ref<Meeting | null>(null)
const rules=ref<BookingRules>()
const deviceLabels: Record<string,string> = { PROJECTOR: '投影仪', MIC: '麦克风', VIDEO: '视频会议', DISPLAY: '显示器' }
const range = ref<{start: string; end: string}>()
const filteredRooms = computed(() => rooms.value.filter(r => selectedRoom.value === 'ALL' || r.id === selectedRoom.value))
const options = computed<CalendarOptions>(() => ({
  plugins: [timeGridPlugin, interactionPlugin], initialView: 'timeGridWeek', locale: zhCnLocale, firstDay: 1,
  timeZone:'UTC', now:rules.value?.serverNow,
  headerToolbar: { left: 'prev,next today', center: 'title', right: '' }, buttonText: { today: '本周' },
  buttonHints: { prev: '上一周', next: '下一周', today: '回到本周' },
  allDaySlot: false, slotMinTime: rules.value?.openTime || '09:00:00', slotMaxTime: rules.value?.closeTime || '18:00:00', slotDuration: '00:30:00',
  height: 'auto', nowIndicator: true, selectable: true, expandRows: true,
  events: events.value.filter(e => selectedRoom.value === 'ALL' || e.roomId === selectedRoom.value).map(e => ({ id: e.id, title: `${e.roomName} · ${e.title}`, start: e.startTime, end: e.endTime, backgroundColor: e.status === 'PENDING' ? '#a46b1d' : '#16836d', borderColor: 'transparent', extendedProps: { detailVisible: e.detailVisible } })),
  datesSet: loadRange,
  eventClick: openMeeting,
  select: info => router.push({ path: '/booking', query: { start: localTime(info.start), end: localTime(info.end), ...(selectedRoom.value !== 'ALL' ? { room: selectedRoom.value } : {}) } }),
}))
// 日历把接口中的北京时间墙上时刻按 UTC 分量展示，避免浏览器时区再次偏移。
function localTime(date: Date) { return date.toISOString().slice(0,19) }
async function loadRange(arg: DatesSetArg) { range.value = { start: localTime(arg.start), end: localTime(arg.end) }; try { events.value = await api(`/schedule?start=${range.value.start}&end=${range.value.end}`) } catch (e) { showError(e) } }
async function openMeeting(arg: EventClickArg) { if (!arg.event.extendedProps.detailVisible) return; try { detail.value = await api(`/meetings/${arg.event.id}`) } catch (e) { showError(e) } }
onMounted(async () => { try { [rooms.value,rules.value]=await Promise.all([api<Room[]>('/resources/rooms'),api<BookingRules>('/resources/rules')]) } catch (e) { showError(e) } })
</script>
<template>
<section><div class="page-heading"><div><h1>会议室与排期</h1><p class="muted">{{ rooms.length }} 间会议室 · 周排期</p></div><el-button type="primary" :icon="Plus" @click="router.push('/booking')">发起预约</el-button></div>
<div class="table-toolbar"><el-select v-model="selectedRoom" style="width:200px"><el-option label="全部会议室" value="ALL" /><el-option v-for="room in rooms" :key="room.id" :label="`${room.roomName} · ${room.floorNo}层`" :value="room.id" /></el-select><div class="schedule-legend"><span><i class="dot approved"></i>预约成功</span><span><i class="dot pending"></i>待审批</span></div></div>
<div class="room-list"><article v-for="room in filteredRooms" :key="room.id" class="room-item"><div class="room-number">{{ room.floorNo }}<small>F</small></div><div><h3>{{ room.roomName }}<span v-if="!room.active" class="muted"> · 停用</span></h3><p><el-icon><Location /></el-icon>{{ room.location }} <el-icon><User /></el-icon>{{ room.capacity }} 人</p><div class="device-list"><span v-for="device in room.devices" :key="device.id">{{ deviceLabels[device.deviceType] || device.deviceName }} × {{ device.quantity }}</span></div></div></article></div>
<div class="calendar-surface"><FullCalendar :options="options" /></div>
<el-dialog :model-value="!!detail" title="会议详情" width="min(600px,94vw)" @close="detail=null"><MeetingDetail :meeting="detail" /></el-dialog>
</section>
</template>
