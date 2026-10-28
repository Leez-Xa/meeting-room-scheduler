<script setup lang="ts">
import { computed, ref } from 'vue'
import type { BookingRules, Room, ScheduleEvent } from '../types'
import { minuteLabel } from '../utils/bookingTime'
import { dayEvents, eventTime, statusLabel, timeMinutes, type DayEvent, type roomPalette } from '../utils/schedule'

type Palette = (typeof roomPalette)[number]
const props = defineProps<{
  rooms: Room[]
  events: ScheduleEvent[]
  date: string
  rules: BookingRules
  now: string
  colors: Record<string, Palette>
  canBook: (start: string, end: string) => boolean
}>()
const emit = defineEmits<{ open: [event: ScheduleEvent]; select: [room: Room, start: string, end: string] }>()
const scroller = ref<HTMLElement>()
const headerScroller = ref<HTMLElement>()
function syncHeaders() {
  if (headerScroller.value && scroller.value) headerScroller.value.scrollLeft = scroller.value.scrollLeft
}
const open = computed(() => timeMinutes(props.rules.openTime))
const close = computed(() => timeMinutes(props.rules.closeTime))
const minuteHeight = 1.6
const gridHeight = computed(() => (close.value - open.value) * minuteHeight)
const columns = computed(() => ({ gridTemplateColumns: `64px repeat(${props.rooms.length}, minmax(242px, 1fr))` }))
const slots = computed(() => {
  const result: { start: number; end: number }[] = []
  for (let start = open.value; start < close.value; start += 30) result.push({ start, end: Math.min(start + 30, close.value) })
  return result
})
const layouts = computed(() => Object.fromEntries(props.rooms.map(room => [room.id, dayEvents(props.events, props.date, room.id, open.value, close.value)])))
const nowMinute = computed(() => props.now.slice(0, 10) === props.date ? timeMinutes(props.now.slice(11)) : -1)
const showNow = computed(() => nowMinute.value >= open.value && nowMinute.value < close.value)
const deviceLabels: Record<string, string> = { PROJECTOR: '投影仪', MIC: '麦克风', VIDEO: '视频会议', DISPLAY: '显示器' }
function devices(room: Room) { return room.devices.filter(device => device.active).map(device => `${deviceLabels[device.deviceType] || device.deviceName} × ${device.quantity}`).join(' · ') || '暂无固定设备' }
function wallTime(minute: number) { return `${props.date}T${minuteLabel(Math.round(minute))}:00` }
function selectable(room: Room, slot: { start: number; end: number }) {
  return room.active && props.canBook(wallTime(slot.start), wallTime(slot.end)) && !layouts.value[room.id]?.some(item => item.start < slot.end && slot.start < item.end)
}
function eventStyle(item: DayEvent) {
  return { top: `${(item.start - open.value) * minuteHeight}px`, height: `${(item.end - item.start) * minuteHeight}px`, left: `calc(${item.lane / item.lanes * 100}% + 5px)`, width: `calc(${100 / item.lanes}% - 10px)` }
}
function describe(event: ScheduleEvent) { return `${event.roomName}，${eventTime(event)}，${event.title}，${statusLabel(event.status)}${event.detailVisible ? '，点击查看详情' : '，仅显示占用信息'}` }
function scrollRooms(direction: number) { scroller.value?.scrollBy({ left: direction * 500, behavior: 'smooth' }) }
</script>

<template>
<div class="room-day-schedule">
  <div class="day-instructions"><span>全天时段完整展开 · 上下滚动页面，左右查看会议室</span><div class="scroll-controls"><button type="button" aria-label="向左查看会议室" @click="scrollRooms(-1)">‹</button><span>左右滚动查看更多</span><button type="button" aria-label="向右查看会议室" @click="scrollRooms(1)">›</button></div></div>
  <div ref="headerScroller" class="day-header-scroller">
      <div class="day-headers" :style="{ ...columns, minWidth: `${64 + rooms.length * 242}px` }">
        <div class="time-corner">时间</div>
        <div v-for="room in rooms" :key="room.id" class="day-room-header" :style="{ '--room-ink': colors[room.id]?.ink, '--room-line': colors[room.id]?.line }">
          <div><strong><i></i>{{ room.roomName }}</strong><span v-if="!room.active" class="inactive-label">停用</span></div>
          <p>{{ room.floorNo }} 层 · {{ room.capacity }} 人 <span :title="room.location">· {{ room.location }}</span></p>
          <p class="header-devices" :title="devices(room)">{{ devices(room) }}</p>
        </div>
      </div>
  </div>
  <div ref="scroller" class="day-scroller" tabindex="0" aria-label="按会议室展示的日排期，左右滚动查看会议室，上下滚动页面查看全天时段" @scroll="syncHeaders">
    <div class="day-grid" :style="{ minWidth: `${64 + rooms.length * 242}px` }">
      <div class="day-body" :style="{ ...columns, height: `${gridHeight}px` }">
        <div class="day-times"><span v-for="(slot, index) in slots.filter((_, index) => index % 2 === 0)" :key="slot.start" :style="{ top: `${(slot.start - open) * minuteHeight}px` }" :class="{ first: index === 0 }">{{ minuteLabel(Math.round(slot.start)) }}</span></div>
        <div v-for="room in rooms" :key="room.id" class="day-room-column" :class="{ inactive: !room.active }" :style="{ '--room-ink': colors[room.id]?.ink, '--room-wash': colors[room.id]?.wash, '--room-line': colors[room.id]?.line }">
          <button v-for="slot in slots" :key="slot.start" type="button" class="empty-slot" :style="{ height: `${(slot.end - slot.start) * minuteHeight}px` }" :disabled="!selectable(room, slot)" :aria-label="`${date} ${room.roomName} ${minuteLabel(Math.round(slot.start))}至${minuteLabel(Math.round(slot.end))}，发起预约`" @click="emit('select', room, wallTime(slot.start), wallTime(slot.end))"><span>＋ 预约此时段</span></button>
          <span v-if="!layouts[room.id]?.length" class="day-empty">{{ room.active ? '当天暂无会议' : '会议室已停用' }}</span>
          <div v-if="showNow" class="now-line" :style="{ top: `${(nowMinute - open) * minuteHeight}px` }" aria-hidden="true"></div>
          <el-tooltip v-for="item in layouts[room.id]" :key="item.event.id" :content="describe(item.event)" placement="top" :show-after="180">
            <button type="button" class="day-event" :class="{ compact: (item.end - item.start) * minuteHeight < 80, tiny: (item.end - item.start) * minuteHeight < 42, private: !item.event.detailVisible, narrow: item.lanes > 1 }" :style="eventStyle(item)" :aria-label="describe(item.event)" :aria-disabled="!item.event.detailVisible" @click="item.event.detailVisible && emit('open', item.event)">
              <span class="day-event-time">{{ eventTime(item.event) }}</span><span class="meeting-status" :class="{ pending: item.event.status === 'PENDING' }">{{ statusLabel(item.event.status) }}</span><strong>{{ item.event.title }}</strong><span class="day-event-note">{{ item.event.detailVisible ? '查看会议详情' : '仅显示占用时段' }}</span>
            </button>
          </el-tooltip>
        </div>
      </div>
    </div>
  </div>
  <div class="day-footnote">颜色区分会议室，文字标签区分状态。待审批会议也占用资源；仅能查看有权限的会议详情。</div>
</div>
</template>

<style scoped>
.room-day-schedule { --schedule-border: #e1e9e3; }
.day-instructions { display:flex; justify-content:space-between; align-items:center; gap:12px; padding:11px 19px; color:#536a5c; background:#fafcfb; border-bottom:1px solid var(--schedule-border); font-size:12px; }
.scroll-controls { display:flex; align-items:center; gap:10px; white-space:nowrap; }
.scroll-controls button { border:1px solid #d7e3db; border-radius:5px; background:#fff; color:#39634e; width:28px; height:25px; cursor:pointer; font-size:18px; }
.day-scroller { max-width:100%; overflow-x:auto; overflow-y:hidden; scrollbar-width:auto; scrollbar-color:#718679 #edf2ef; }
.day-header-scroller { position:sticky; top:0; z-index:5; overflow:hidden; background:#fff; }
.day-headers,.day-body { display:grid; }
.day-headers { background:#fff; }
.time-corner { position:sticky; left:0; z-index:7; background:#fbfcfb; display:grid; place-items:center; color:#61766a; font-size:11px; border-right:1px solid var(--schedule-border); border-bottom:1px solid var(--schedule-border); }
.day-room-header { height:104px; padding:15px 16px 10px; border-right:1px solid var(--schedule-border); border-bottom:1px solid var(--schedule-border); min-width:0; background:#fff; }
.day-room-header>div { display:flex; align-items:center; gap:9px; }
.day-room-header strong { display:flex; align-items:center; gap:8px; font-size:15px; color:var(--room-ink); min-width:0; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.day-room-header i { width:4px; height:17px; background:var(--room-line); flex-shrink:0; border-radius:2px; }
.day-room-header p { margin:7px 0 0; font-size:11px; color:#566d5e; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
.day-room-header .header-devices { font-size:10px; color:#62756a; }
.inactive-label { font-size:10px; border:1px solid #d5ded7; padding:1px 5px; border-radius:3px; color:#64726a; }
.day-times { position:sticky; left:0; z-index:4; background:#fff; border-right:1px solid var(--schedule-border); }
.day-times span { position:absolute; right:11px; color:#60766a; font-size:11px; transform:translateY(-50%); font-variant-numeric:tabular-nums; }
.day-times span.first { transform:translateY(5px); }
.day-room-column { position:relative; min-width:0; border-right:1px solid var(--schedule-border); }
.empty-slot { display:block; padding:0; width:100%; border:0; border-bottom:1px solid #e8eeea; background:#fff; text-align:center; cursor:pointer; color:#2d6d50; }
.empty-slot:nth-child(even) { border-bottom-color:#dce6df; }
.empty-slot span { opacity:0; font:11px "Microsoft YaHei",sans-serif; }
.empty-slot:hover:not(:disabled),.empty-slot:focus-visible { background:#edf6f0; }
.empty-slot:hover:not(:disabled) span,.empty-slot:focus-visible span { opacity:1; }
.empty-slot:disabled { cursor:default; background:#fafcfb; }
.inactive .empty-slot { background:#f3f5f4; }
.day-empty { position:absolute; top:155px; left:0; right:0; text-align:center; font-size:12px; color:#6a7b70; pointer-events:none; }
.now-line { position:absolute; left:0; right:0; height:1px; background:#ca6570; z-index:2; pointer-events:none; }
.day-event { position:absolute; z-index:3; display:flex; flex-direction:column; align-items:flex-start; gap:6px; min-height:3px; padding:11px 12px; border:1px solid color-mix(in srgb,var(--room-line) 32%,white); border-left:3px solid var(--room-line); border-radius:5px; background:var(--room-wash); color:var(--room-ink); text-align:left; cursor:pointer; overflow:hidden; font-family:inherit; }
.day-event:hover { filter:brightness(.975); box-shadow:0 2px 7px #243c3014; }
.day-event.private { cursor:default; }
.day-event-time { font-size:12px; font-weight:600; line-height:17px; font-variant-numeric:tabular-nums; padding-right:63px; white-space:nowrap; }
.day-event strong { font-size:14px; line-height:21px; width:100%; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.day-event-note { font-size:10px; line-height:15px; }
.meeting-status { position:absolute; right:8px; top:9px; border:1px solid #c5ddce; border-radius:4px; background:#f4faf6; color:#326848; padding:2px 5px; font-size:10px; line-height:15px; white-space:nowrap; }
.meeting-status.pending { background:#fff9e9; border-color:#e8d49f; color:#7b5719; }
.day-event.compact { padding:6px 9px; gap:3px; }
.day-event.compact .day-event-note { display:none; }
.day-event.compact .meeting-status { top:5px; right:5px; }
.day-event.tiny { flex-direction:row; align-items:center; padding:1px 5px; gap:7px; }
.day-event.tiny .meeting-status,.day-event.tiny .day-event-note { display:none; }
.day-event.tiny .day-event-time { padding:0; font-size:10px; }
.day-event.tiny strong { font-size:11px; }
.day-event.narrow .meeting-status { position:static; font-size:9px; padding:0 4px; }
.day-event.narrow .day-event-note { display:none; }
.day-event.narrow .day-event-time { padding:0; font-size:10px; }
.day-event.narrow strong { font-size:11px; line-height:16px; }
.day-footnote { border-top:1px solid var(--schedule-border); padding:12px 18px; font-size:11px; line-height:1.7; color:#5b7263; }
button:focus-visible { outline:2px solid #218261; outline-offset:-2px; }
@media(max-width:760px) { .day-instructions { align-items:flex-start; flex-direction:column; padding:10px 12px; }.day-footnote { padding:10px 12px; } }
</style>
