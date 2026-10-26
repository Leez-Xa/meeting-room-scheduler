<script setup lang="ts">
import { computed, nextTick, onUnmounted, ref, watch } from 'vue'
import type { BookingRules } from '../types'
import { availableEndMinutes, availableStartMinutes, bookingTimeError, calendarDate, minuteLabel, serverTimeAfter } from '../utils/bookingTime'

const props = defineProps<{ startTime: string; endTime: string; rules?: BookingRules }>()
const emit = defineEmits<{ 'update:startTime': [value: string]; 'update:endTime': [value: string] }>()
const date = ref('')
const startClock = ref('')
const endClock = ref('')
const now = ref('')
const startPicker = ref<{ handleOpen: () => void }>()
const endPicker = ref<{ handleOpen: () => void }>()
let receivedAt = performance.now()
function updateNow() {
  if (props.rules) now.value = serverTimeAfter(props.rules.serverNow, performance.now() - receivedAt)
}
watch(() => props.rules, () => { receivedAt = performance.now(); updateNow() }, { immediate: true })
const clockTimer = setInterval(updateNow, 1000)
onUnmounted(() => clearInterval(clockTimer))

watch(() => [props.startTime, props.endTime], ([start, end]) => {
  if (start) date.value = start.slice(0, 10)
  startClock.value = start ? start.slice(11, 16) : ''
  endClock.value = end && end.slice(0, 10) === date.value ? end.slice(11, 16) : ''
}, { immediate: true })

// Preserve picker props while the clock ticks within the same minute.
function stableMinutes(next: number[], previous?: number[]): number[] {
  return previous && previous.length === next.length && previous[0] === next[0] && previous[previous.length - 1] === next[next.length - 1] ? previous : next
}
const starts = computed<number[]>(previous => stableMinutes(props.rules ? availableStartMinutes(date.value, props.rules, now.value) : [], previous))
const ends = computed<number[]>(previous => stableMinutes(props.rules ? availableEndMinutes(props.startTime, props.rules, now.value) : [], previous))
const firstAvailableDate = computed<Date>(previous => {
  if (!props.rules || !now.value) return previous ?? new Date()
  const candidate = new Date(`${now.value.slice(0, 10)}T12:00:00Z`)
  for (let offset = 0; offset < 14; offset++) {
    const key = candidate.toISOString().slice(0, 10)
    if (availableStartMinutes(key, props.rules, now.value).length) {
      const [year, month, day] = key.split('-').map(Number)
      return previous && calendarDate(previous) === key ? previous : new Date(year, month - 1, day)
    }
    candidate.setUTCDate(candidate.getUTCDate() + 1)
  }
  const [year, month, day] = now.value.slice(0, 10).split('-').map(Number)
  return previous && calendarDate(previous) === now.value.slice(0, 10) ? previous : new Date(year, month - 1, day)
})
const noDates = computed(() => !props.rules || !availableStartMinutes(calendarDate(firstAvailableDate.value), props.rules, now.value).length)
function disabledDate(value: Date) {
  return !props.rules || !availableStartMinutes(calendarDate(value), props.rules, now.value).length
}
function disabledHours(allowed: number[]): number[] {
  const hours = new Set(allowed.map(minute => Math.floor(minute / 60)))
  return Array.from({ length: 24 }, (_, hour) => hour).filter(hour => !hours.has(hour))
}
function disabledMinutes(hour: number, allowed: number[]): number[] {
  const minutes = new Set(allowed.filter(minute => Math.floor(minute / 60) === hour).map(minute => minute % 60))
  return Array.from({ length: 60 }, (_, minute) => minute).filter(minute => !minutes.has(minute))
}
function defaultTime(allowed: number[], preferred?: number): Date {
  const first = allowed[0] ?? 0
  const minute = preferred !== undefined && allowed.includes(preferred) ? preferred : allowed.find(value => value % 15 === 0) ?? first
  return new Date(2000, 0, 1, Math.floor(minute / 60), minute % 60, 0)
}
const defaultStartTime = computed(() => defaultTime(starts.value))
const defaultEndTime = computed(() => defaultTime(ends.value))
const disabledStartHours = () => disabledHours(starts.value)
const disabledEndHours = () => disabledHours(ends.value)
const disabledStartMinutes = (hour: number) => disabledMinutes(hour, starts.value)
const disabledEndMinutes = (hour: number) => disabledMinutes(hour, ends.value)
function timestamp(clock: string): string { return date.value && clock ? `${date.value}T${clock}:00` : '' }
async function changeDate(value: string | null) {
  date.value = value || ''
  startClock.value = ''; endClock.value = ''
  emit('update:startTime', ''); emit('update:endTime', '')
  if (date.value && starts.value.length) {
    await nextTick()
    startPicker.value?.handleOpen()
  }
}
async function changeStart(value: string | null) {
  startClock.value = value || ''
  emit('update:startTime', timestamp(startClock.value))
  if (!value) { endClock.value = ''; emit('update:endTime', ''); return }
  await nextTick()
  if (!ends.value.length) { endClock.value = ''; emit('update:endTime', ''); return }
  const startMinute = Number(value.slice(0, 2)) * 60 + Number(value.slice(3, 5))
  const previousEnd = endClock.value ? Number(endClock.value.slice(0, 2)) * 60 + Number(endClock.value.slice(3, 5)) : -1
  if (!ends.value.includes(previousEnd)) {
    const suggested = Math.min(startMinute + 60, ends.value[ends.value.length - 1])
    endClock.value = minuteLabel(suggested)
    emit('update:endTime', timestamp(endClock.value))
  }
  await nextTick()
  endPicker.value?.handleOpen()
}
function changeEnd(value: string | null) {
  endClock.value = value || ''
  emit('update:endTime', timestamp(endClock.value))
}
const timeError = computed(() => props.rules && props.startTime && props.endTime ? bookingTimeError(props.startTime, props.endTime, props.rules, now.value) : '')
const duration = computed(() => {
  if (!props.startTime || !props.endTime || timeError.value) return ''
  const minutes = Math.round((Date.parse(`${props.endTime}Z`) - Date.parse(`${props.startTime}Z`)) / 60000)
  return `会议时长 ${minutes >= 60 ? `${Math.floor(minutes / 60)} 小时${minutes % 60 ? ` ${minutes % 60} 分钟` : ''}` : `${minutes} 分钟`}`
})
const hint = computed(() => {
  if (!props.rules) return '正在获取可预约时间…'
  if (noDates.value) return '当前开放范围内已无可预约时段，请在下次开放后预约。'
  if (timeError.value) return timeError.value
  return `${now.value && (new Date(`${now.value.slice(0, 10)}T00:00:00Z`).getUTCDay() || 7) >= 6 ? '周末可预约本周剩余时段及下周' : '可预约本周未来时段'}，仅可选择开放日期和时间。`
})
</script>

<template>
  <div class="booking-time-picker">
    <el-form-item label="会议日期" required>
      <el-date-picker
        v-model="date" type="date" value-format="YYYY-MM-DD" format="YYYY-MM-DD"
        placeholder="先选择会议日期" aria-label="会议日期" :editable="false"
        :disabled="!rules || noDates" :disabled-date="disabledDate" :default-value="firstAvailableDate"
        :show-now="false" @change="changeDate"
      />
    </el-form-item>
    <div class="booking-time-columns">
      <el-form-item label="开始时间" required>
        <el-time-picker
          ref="startPicker" v-model="startClock" format="HH:mm" value-format="HH:mm"
          :placeholder="date ? '选择开始时间' : '请先选择日期'" aria-label="开始时间"
          :editable="false" :clearable="false" :disabled="!date || !starts.length"
          :default-value="defaultStartTime" :disabled-hours="disabledStartHours"
          :disabled-minutes="disabledStartMinutes" @change="changeStart"
        />
      </el-form-item>
      <el-form-item label="结束时间" required>
        <el-time-picker
          ref="endPicker" v-model="endClock" format="HH:mm" value-format="HH:mm"
          :placeholder="startClock ? '选择结束时间' : '请先选择开始时间'" aria-label="结束时间"
          :editable="false" :clearable="false" :disabled="!startClock || !ends.length"
          :default-value="defaultEndTime" :disabled-hours="disabledEndHours"
          :disabled-minutes="disabledEndMinutes" @change="changeEnd"
        />
      </el-form-item>
    </div>
    <div class="booking-time-guidance" :class="{ 'has-error': timeError }" role="status" aria-live="polite">
      <span>{{ hint }}</span><strong v-if="duration">{{ duration }}</strong>
    </div>
  </div>
</template>

<style scoped>
.booking-time-picker { margin-bottom: 22px; }
.booking-time-columns { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 18px; }
.booking-time-picker :deep(.el-date-editor.el-input) { width: 100%; }
.booking-time-columns :deep(.el-form-item) { margin-bottom: 12px; }
.booking-time-guidance { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 6px 16px; color: #586b61; font-size: 12px; line-height: 1.7; }
.booking-time-guidance strong { color: #157763; font-weight: 500; white-space: nowrap; }
.booking-time-guidance.has-error { color: #a43d34; }
@media (max-width: 480px) { .booking-time-columns { grid-template-columns: 1fr; gap: 0; } }
</style>
