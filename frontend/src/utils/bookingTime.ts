import type { BookingRules } from '../types'

// Use UTC components only as a calendar representation of Beijing wall time.
// Neither the user's browser timezone nor its clock determines availability.
export function serverTimeAfter(serverNow: string, elapsedMs: number): string {
  return new Date(Date.parse(`${serverNow}Z`) + Math.max(0, elapsedMs)).toISOString().slice(0, 19)
}

export function calendarDate(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

export function minuteLabel(minute: number): string {
  return `${String(Math.floor(minute / 60)).padStart(2, '0')}:${String(minute % 60).padStart(2, '0')}`
}

function secondsOfDay(value: string): number {
  const [hours = 0, minutes = 0, seconds = 0] = value.split(':').map(Number)
  return hours * 3600 + minutes * 60 + seconds
}

function withinBookingDates(date: string, rules: BookingRules, now: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) return false
  const day = new Date(`${date}T00:00:00Z`)
  if (!Number.isFinite(day.getTime()) || day.toISOString().slice(0, 10) !== date) return false
  if (date < now.slice(0, 10)) return false
  const weekday = day.getUTCDay() || 7
  if (!rules.workdays.includes(weekday)) return false
  const today = new Date(`${now.slice(0, 10)}T00:00:00Z`)
  const todayWeekday = today.getUTCDay() || 7
  const end = new Date(today)
  end.setUTCDate(today.getUTCDate() - todayWeekday + 1 + (todayWeekday >= 6 ? 14 : 7))
  return date < end.toISOString().slice(0, 10)
}

export function availableStartMinutes(date: string, rules: BookingRules, now: string): number[] {
  if (!withinBookingDates(date, rules, now)) return []
  const first = Math.ceil(secondsOfDay(rules.openTime) / 60)
  // Leave at least one selectable end minute before or at closing.
  const last = Math.floor(secondsOfDay(rules.closeTime) / 60)
  const result: number[] = []
  for (let minute = first; minute < last; minute++) {
    if (`${date}T${minuteLabel(minute)}:00` > now) result.push(minute)
  }
  return result
}

export function availableEndMinutes(start: string, rules: BookingRules, now: string): number[] {
  if (!start || !withinBookingDates(start.slice(0, 10), rules, now) || start <= now) return []
  const startSecond = secondsOfDay(start.slice(11))
  if (startSecond < secondsOfDay(rules.openTime)) return []
  const last = Math.floor(secondsOfDay(rules.closeTime) / 60)
  const result: number[] = []
  for (let minute = Math.floor(startSecond / 60) + 1; minute <= last; minute++) result.push(minute)
  return result
}

export function bookingTimeError(start: string, end: string, rules: BookingRules, now: string): string {
  if (!start || !end) return '请选择会议日期、开始时间和结束时间'
  const pattern = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?$/
  if (!pattern.test(start) || !pattern.test(end) || !Number.isFinite(Date.parse(`${start}Z`)) || !Number.isFinite(Date.parse(`${end}Z`))) return '请选择有效的预约时间'
  if (start <= now) return '开始时间已过，请重新选择当前时间之后的时段'
  if (start.slice(0, 10) !== end.slice(0, 10)) return '会议不能跨天，请选择同一天的起止时间'
  if (!withinBookingDates(start.slice(0, 10), rules, now)) return '所选日期不在开放预约范围内，请重新选择'
  if (start >= end) return '结束时间必须晚于开始时间'
  if (secondsOfDay(start.slice(11)) < secondsOfDay(rules.openTime) || secondsOfDay(end.slice(11)) > secondsOfDay(rules.closeTime)) return `预约时间须在 ${rules.openTime.slice(0, 5)}–${rules.closeTime.slice(0, 5)} 内`
  return ''
}
