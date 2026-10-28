<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import FullCalendar from '@fullcalendar/vue3'
import timeGridPlugin from '@fullcalendar/timegrid'
import interactionPlugin from '@fullcalendar/interaction'
import zhCnLocale from '@fullcalendar/core/locales/zh-cn'
import type { CalendarOptions, EventClickArg } from '@fullcalendar/core'
import { ArrowLeft, ArrowRight, Plus } from '@element-plus/icons-vue'
import { api, showError } from '../api/client'
import type { Meeting, Room, ScheduleEvent, BookingRules } from '../types'
import MeetingDetail from '../components/MeetingDetail.vue'
import RoomDaySchedule from '../components/RoomDaySchedule.vue'
import { availableStartMinutes, bookingTimeError, serverTimeAfter } from '../utils/bookingTime'
import { beijingDate, eventTime, roomPalette, shiftDate, statusLabel, weekDates, weekdayLabel } from '../utils/schedule'

const router = useRouter()
const rooms = ref<Room[]>([])
const selectedRoom = ref('ALL')
const events = ref<ScheduleEvent[]>([])
const detail = ref<Meeting | null>(null)
const rules = ref<BookingRules>()
const selectedDate = ref(beijingDate())
const view = ref<'rooms' | 'week'>('rooms')
const loading = ref(false)
const resourcesReady = ref(false)
const errorMessage = ref('')
const clock = ref(Date.now())
let rulesReceivedAt = Date.now()
let requestVersion = 0
let clockTimer: ReturnType<typeof setInterval> | undefined
const now = computed(() => rules.value ? serverTimeAfter(rules.value.serverNow, clock.value - rulesReceivedAt) : '')
const today = computed(() => now.value.slice(0, 10) || beijingDate())
const dates = computed(() => weekDates(selectedDate.value))
const weekStart = computed(() => dates.value[0]!)
const selectedDateTitle = computed(() => `${selectedDate.value.replace('-', '年').replace('-', '月')}日 · ${weekdayLabel(selectedDate.value)}`)
const weekTitle = computed(() => `${dates.value[0]!.replaceAll('-', '/')} — ${dates.value[6]!.replaceAll('-', '/')}`)
const filteredRooms = computed(() => rooms.value.filter(room => selectedRoom.value === 'ALL' || room.id === selectedRoom.value))
const filteredEvents = computed(() => events.value.filter(event => selectedRoom.value === 'ALL' || event.roomId === selectedRoom.value))
const colors = computed(() => Object.fromEntries([...rooms.value].sort((a, b) => BigInt(a.id) < BigInt(b.id) ? -1 : BigInt(a.id) > BigInt(b.id) ? 1 : 0).map((room, index) => [room.id, roomPalette[index % roomPalette.length]!])) )
const dateBookable = computed(() => !!rules.value && availableStartMinutes(selectedDate.value, rules.value, now.value).length > 0)
const options = computed<CalendarOptions>(() => ({
  plugins: [timeGridPlugin, interactionPlugin], initialView: 'timeGridWeek', initialDate: weekStart.value, locale: zhCnLocale, firstDay: 1,
  timeZone: 'UTC', now: now.value || undefined, headerToolbar: false,
  allDaySlot: false, slotMinTime: rules.value?.openTime || '09:00:00', slotMaxTime: rules.value?.closeTime || '18:00:00', slotDuration: '00:30:00',
  height: 'auto', nowIndicator: true, selectable: true, expandRows: true, slotEventOverlap: false,
  eventMinHeight: 22, eventShortHeight: 62, eventOrder: 'start,-duration,id',
  dayHeaderFormat: { weekday: 'short', month: 'numeric', day: 'numeric' },
  slotLabelFormat: { hour: '2-digit', minute: '2-digit', hour12: false },
  events: filteredEvents.value.map(event => ({
    id: event.id, title: event.title, start: event.startTime, end: event.endTime,
    backgroundColor: colors.value[event.roomId]?.wash, borderColor: colors.value[event.roomId]?.line, textColor: colors.value[event.roomId]?.ink,
    extendedProps: { schedule: event, short: Date.parse(event.endTime + 'Z') - Date.parse(event.startTime + 'Z') < 60 * 60 * 1000 },
  })),
  eventDidMount: info => {
    const event = info.event.extendedProps.schedule as ScheduleEvent
    info.el.title = `${event.roomName}，${eventTime(event)}，${event.title}，${statusLabel(event.status)}${event.detailVisible ? '，点击查看详情' : '，仅显示占用信息'}`
    info.el.setAttribute('aria-label', info.el.title)
    info.el.style.cursor = event.detailVisible ? 'pointer' : 'default'
  },
  eventClick: (arg: EventClickArg) => openMeeting(arg.event.extendedProps.schedule),
  select: info => {
    const room = selectedRoom.value === 'ALL' ? undefined : rooms.value.find(room => room.id === selectedRoom.value)
    createBooking(localTime(info.start), localTime(info.end), room)
  },
}))
// UTC components represent the API's Beijing wall clock; browser timezone must not shift the date.
function localTime(date: Date) { return date.toISOString().slice(0, 19) }
function canBook(start: string, end: string) { return !!rules.value && !bookingTimeError(start, end, rules.value, now.value) }
function createBooking(start: string, end: string, room?: Room) {
  if (!rules.value || loading.value || errorMessage.value) return
  const error = bookingTimeError(start, end, rules.value, now.value)
  if (error) { ElMessage.warning(error); return }
  if (room && !room.active) { ElMessage.warning('会议室已停用'); return }
  router.push({ path: '/booking', query: { start, end, ...(room ? { room: room.id } : {}) } })
}
async function loadRange() {
  const version = ++requestVersion
  loading.value = true
  errorMessage.value = ''
  events.value = []
  try {
    const result = await api<ScheduleEvent[]>(`/schedule?start=${weekStart.value}T00:00:00&end=${shiftDate(weekStart.value, 7)}T00:00:00`)
    if (version === requestVersion) events.value = result
  } catch (error) {
    if (version === requestVersion) errorMessage.value = error instanceof Error ? error.message : '排期加载失败，请重试'
  } finally { if (version === requestVersion) loading.value = false }
}
async function initialize() {
  resourcesReady.value = false
  errorMessage.value = ''
  try {
    const [resourceRooms, bookingRules] = await Promise.all([api<Room[]>('/resources/rooms'), api<BookingRules>('/resources/rules')])
    rooms.value = resourceRooms
    rules.value = bookingRules
    rulesReceivedAt = Date.now()
    clock.value = rulesReceivedAt
    selectedDate.value = bookingRules.serverNow.slice(0, 10)
  } catch (error) { errorMessage.value = error instanceof Error ? error.message : '会议室加载失败，请重试' }
  finally { resourcesReady.value = true }
}
async function openMeeting(event: ScheduleEvent) {
  if (!event.detailVisible) return
  try { detail.value = await api<Meeting>(`/meetings/${event.id}`) } catch (error) { showError(error) }
}
watch([weekStart, resourcesReady], ([, ready]) => { if (ready && rules.value) void loadRange() })
onMounted(() => { void initialize(); clockTimer = setInterval(() => { clock.value = Date.now() }, 30000) })
onUnmounted(() => { if (clockTimer) clearInterval(clockTimer); requestVersion++ })
</script>
<template>
<section class="schedule-page">
  <div class="page-heading"><div><h1>会议室与排期</h1><p class="muted">{{ rooms.length }} 间会议室 · 北京时间</p></div><el-button type="primary" :icon="Plus" @click="router.push('/booking')">发起预约</el-button></div>
  <div class="schedule-controls">
    <el-radio-group v-model="view" class="view-switch" aria-label="排期视图"><el-radio-button value="rooms">按会议室</el-radio-button><el-radio-button value="week">周历</el-radio-button></el-radio-group>
    <el-select v-model="selectedRoom" class="room-filter" aria-label="筛选会议室"><el-option label="全部会议室" value="ALL" /><el-option v-for="room in rooms" :key="room.id" :label="`${room.roomName} · ${room.floorNo}层`" :value="room.id" /></el-select>
    <span class="color-explanation">会议室固定分色 · 状态单独标注</span>
  </div>
  <div class="room-color-legend" aria-label="会议室颜色图例"><span v-for="room in filteredRooms" :key="room.id"><i :style="{ backgroundColor: colors[room.id]?.line }"></i>{{ room.roomName }}</span></div>
  <div class="schedule-surface" v-loading="loading || !resourcesReady" element-loading-text="正在加载排期…">
    <div class="schedule-date-toolbar">
      <div><h2>{{ view === 'rooms' ? selectedDateTitle : weekTitle }}</h2><p v-if="view === 'rooms'">{{ weekTitle }}</p></div>
      <div class="schedule-date-actions"><el-button :icon="ArrowLeft" circle aria-label="上一周" @click="selectedDate = shiftDate(selectedDate, -7)" /><el-button @click="selectedDate = today">今天</el-button><el-button :icon="ArrowRight" circle aria-label="下一周" @click="selectedDate = shiftDate(selectedDate, 7)" /><el-date-picker v-model="selectedDate" type="date" value-format="YYYY-MM-DD" format="YYYY/MM/DD" :clearable="false" :editable="false" aria-label="选择排期日期" /></div>
    </div>
    <div v-if="view === 'rooms'" class="schedule-week-strip" aria-label="本周日期"><button v-for="date in dates" :key="date" type="button" :class="{ selected: date === selectedDate, today: date === today }" :aria-pressed="date === selectedDate" @click="selectedDate = date"><span>{{ weekdayLabel(date) }}</span><strong>{{ Number(date.slice(8, 10)) }}</strong><small v-if="date === today">今天</small></button></div>
    <div v-if="errorMessage" class="schedule-error"><el-alert :title="errorMessage" description="暂时无法确认会议室占用情况，请重新加载后再预约。" type="error" :closable="false" /><el-button @click="rules ? loadRange() : initialize()">重新加载</el-button></div>
    <el-empty v-else-if="resourcesReady && !filteredRooms.length" description="暂无可展示的会议室" />
    <template v-else-if="rules">
      <p v-if="view === 'rooms' && !dateBookable" class="date-readonly-note">当前日期没有可预约的未来开放时段，可继续查看已有排期。</p>
      <RoomDaySchedule v-if="view === 'rooms'" :rooms="filteredRooms" :events="filteredEvents" :date="selectedDate" :rules="rules" :now="now" :colors="colors" :can-book="canBook" @open="openMeeting" @select="(room, start, end) => createBooking(start, end, room)" />
      <div v-else class="schedule-week-calendar"><FullCalendar :key="weekStart" :options="options"><template #eventContent="{ event }"><div class="week-event" :class="{ short: event.extendedProps.short }"><span class="week-event-time">{{ eventTime(event.extendedProps.schedule) }}</span><strong>{{ event.title }}</strong><span class="week-event-room">{{ event.extendedProps.schedule.roomName }}</span><span class="week-event-status" :class="{ pending: event.extendedProps.schedule.status === 'PENDING' }">{{ statusLabel(event.extendedProps.schedule.status) }}</span></div></template></FullCalendar><p class="week-footnote">同一时段会议并排显示，悬停查看完整信息。选择具体会议室后，可框选未来空闲时段发起预约。</p></div>
    </template>
  </div>
  <el-dialog :model-value="!!detail" title="会议详情" width="min(600px,94vw)" @close="detail = null"><MeetingDetail :meeting="detail" /></el-dialog>
</section>
</template>


<style scoped>
.schedule-controls { display:flex; flex-wrap:wrap; gap:14px; align-items:center; margin-bottom:16px; }
.view-switch :deep(.el-radio-button__inner) { padding:10px 24px; }
.room-filter { width:190px; }
.color-explanation { margin-left:auto; font-size:11px; color:#5c7264; }
.room-color-legend { display:flex; flex-wrap:wrap; gap:10px 23px; margin:0 0 18px; font-size:12px; color:#4d6556; }
.room-color-legend span { display:flex; align-items:center; gap:7px; }
.room-color-legend i { width:9px; height:9px; border-radius:3px; }
.schedule-surface { background:#fff; border:1px solid #dce5df; border-radius:8px; overflow:clip; min-height:240px; }
.schedule-date-toolbar { display:flex; align-items:center; justify-content:space-between; gap:18px; padding:20px; border-bottom:1px solid #e1e9e3; }
.schedule-date-toolbar h2 { font:600 18px/1.5 "Microsoft YaHei",sans-serif; margin:0; color:#2d4537; }
.schedule-date-toolbar p { margin:4px 0 0; font-size:11px; color:#64796b; }
.schedule-date-actions { display:flex; align-items:center; gap:7px; }
.schedule-date-actions :deep(.el-button+.el-button) { margin-left:0; }
.schedule-date-actions :deep(.el-date-editor.el-input) { width:141px; margin-left:6px; }
.schedule-week-strip { display:grid; grid-template-columns:repeat(7,minmax(0,1fr)); gap:6px; padding:10px 18px; border-bottom:1px solid #e1e9e3; background:#fcfdfc; }
.schedule-week-strip button { display:flex; align-items:center; justify-content:center; gap:9px; border:0; background:transparent; color:#526b5c; border-radius:6px; min-height:42px; cursor:pointer; font-family:inherit; position:relative; }
.schedule-week-strip strong { font-size:16px; font-weight:600; }
.schedule-week-strip span { font-size:12px; }
.schedule-week-strip small { font-size:9px; }
.schedule-week-strip button.today:not(.selected) { background:#eaf4ee; color:#226746; }
.schedule-week-strip button:hover { background:#e9f2ec; }
.schedule-week-strip button.selected { background:#157763; color:#fff; }
.schedule-week-strip button:focus-visible { outline:2px solid #218261; outline-offset:2px; }
.date-readonly-note { color:#6c633e; background:#fffbed; font-size:11px; margin:0; padding:10px 18px; border-bottom:1px solid #eee4c5; }
.schedule-error { padding:25px 20px; display:flex; flex-direction:column; gap:14px; align-items:flex-start; }
.schedule-week-calendar { padding:14px 16px 0; overflow:auto; }
.schedule-week-calendar :deep(.fc) { min-width:760px; }
.schedule-week-calendar :deep(.fc-timegrid-slot) { height:46px; }
.schedule-week-calendar :deep(.fc-timegrid-event) { border-radius:4px; border-left-width:3px; box-shadow:none; }
.schedule-week-calendar :deep(.fc-event-main) { color:inherit; }
.week-event { padding:5px; display:flex; flex-direction:column; gap:3px; overflow:hidden; height:100%; color:inherit; }
.week-event-time { font-size:10px; font-weight:600; font-variant-numeric:tabular-nums; white-space:nowrap; flex-shrink:0; }
.week-event strong { font-size:12px; font-weight:600; line-height:1.5; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; flex-shrink:0; }
.week-event-room { font-size:10px; line-height:1.4; white-space:nowrap; flex-shrink:0; }
.week-event-status { font-size:9px; width:max-content; line-height:16px; padding:0 4px; border:1px solid #bfd9c9; background:#f3faf5; color:#356c4b; border-radius:3px; flex-shrink:0; }
.week-event-status.pending { color:#7a5516; background:#fff9e9; border-color:#e6d29d; }
.week-event.short .week-event-room,.week-event.short .week-event-status { display:none; }
.week-footnote { color:#5e7566; font-size:11px; line-height:1.8; margin:13px 0; }
@media(max-width:760px) { .schedule-date-toolbar { flex-direction:column; align-items:flex-start; padding:15px 12px; gap:12px; }.schedule-date-toolbar h2 { font-size:16px; }.schedule-date-actions { width:100%; }.schedule-date-actions :deep(.el-date-editor.el-input) { margin-left:auto; }.schedule-week-strip { padding:8px; gap:3px; }.schedule-week-strip button { flex-direction:column; gap:2px; min-height:52px; }.schedule-week-strip small { display:none; }.schedule-week-strip span { font-size:10px; }.color-explanation { margin-left:0; width:100%; }.room-color-legend { gap:10px 16px; }.schedule-week-calendar { padding:10px 8px 0; }.view-switch :deep(.el-radio-button__inner) { padding:10px 18px; }.room-filter { width:160px; } }
</style>
