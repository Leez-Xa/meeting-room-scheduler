import type { ScheduleEvent } from '../types'

export const roomPalette = [
  { ink: '#246856', wash: '#eaf5ef', line: '#369a77' },
  { ink: '#285f92', wash: '#edf3fc', line: '#5489cc' },
  { ink: '#754f96', wash: '#f4eef9', line: '#9874b9' },
  { ink: '#945727', wash: '#fdf1e5', line: '#c18b4e' },
  { ink: '#246879', wash: '#eaf5f8', line: '#3c92aa' },
  { ink: '#92415e', wash: '#fbecf2', line: '#bf7593' },
  { ink: '#626f29', wash: '#f3f5e8', line: '#8d9c51' },
  { ink: '#4e607c', wash: '#eef1f7', line: '#8192b0' },
]

export function beijingDate(now = new Date()): string {
  const parts = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(now)
  const part = (type: string) => parts.find(p => p.type === type)?.value
  return `${part('year')}-${part('month')}-${part('day')}`
}
export function shiftDate(date: string, days: number): string {
  const value = new Date(`${date}T00:00:00Z`)
  value.setUTCDate(value.getUTCDate() + days)
  return value.toISOString().slice(0, 10)
}
export function weekDates(date: string): string[] {
  const weekday = new Date(`${date}T00:00:00Z`).getUTCDay() || 7
  const monday = shiftDate(date, 1 - weekday)
  return Array.from({ length: 7 }, (_, index) => shiftDate(monday, index))
}
export function weekdayLabel(date: string): string {
  return ['周日', '周一', '周二', '周三', '周四', '周五', '周六'][new Date(`${date}T00:00:00Z`).getUTCDay()] || ''
}
export function timeMinutes(time: string): number {
  const [hours = 0, minutes = 0, seconds = 0] = time.split(':').map(Number)
  return hours * 60 + minutes + seconds / 60
}
export function statusLabel(status: string): string {
  return ({ PENDING: '待审批', APPROVED: '预约成功', IN_PROGRESS: '进行中', COMPLETED: '已结束', EXPIRED: '已过期', CANCELED: '已取消', REJECTED: '已拒绝', DRAFT: '草稿' } as Record<string, string>)[status] || '已占用'
}
export function eventTime(event: ScheduleEvent): string {
  return `${event.startTime.slice(11, 16)}–${event.endTime.slice(11, 16)}`
}

export interface DayEvent {
  event: ScheduleEvent
  start: number
  end: number
  lane: number
  lanes: number
}
// Keep half-open intervals: adjacent bookings share a lane, intersecting ones never cover each other.
export function dayEvents(events: ScheduleEvent[], date: string, roomId: string, open: number, close: number): DayEvent[] {
  const items = events.filter(event => event.roomId === roomId && event.startTime < `${date}T24:00:00` && event.endTime > `${date}T00:00:00`)
    .map(event => ({ event, start: Math.max(open, event.startTime.slice(0, 10) < date ? 0 : timeMinutes(event.startTime.slice(11))), end: Math.min(close, event.endTime.slice(0, 10) > date ? 1440 : timeMinutes(event.endTime.slice(11))), lane: 0, lanes: 1 }))
    .filter(item => item.start < item.end)
    .sort((a, b) => a.start - b.start || b.end - a.end || a.event.id.localeCompare(b.event.id))
  let group: DayEvent[] = []
  let laneEnds: number[] = []
  let groupEnd = -1
  const finish = () => { for (const item of group) item.lanes = laneEnds.length }
  for (const item of items) {
    if (item.start >= groupEnd) { finish(); group = []; laneEnds = [] }
    let lane = laneEnds.findIndex(end => end <= item.start)
    if (lane === -1) lane = laneEnds.length
    item.lane = lane
    laneEnds[lane] = item.end
    group.push(item)
    groupEnd = Math.max(...laneEnds)
  }
  finish()
  return items
}
