export type Role = 'SYSTEM_ADMIN' | 'DEPT_ADMIN' | 'EMPLOYEE'
export type MeetingStatus = 'DRAFT' | 'PENDING' | 'APPROVED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELED' | 'REJECTED' | 'EXPIRED'
export interface User { id: string; username: string; realName: string; deptId: string; deptName: string; role: Role; floorNo: number }
export interface Device { id: string; deviceName: string; deviceType: string; quantity: number; active: boolean }
export interface Room { id: string; roomName: string; floorNo: number; capacity: number; location: string; active: boolean; devices: Device[] }
export interface Attendee { userId: string; realName: string; username: string; deptName: string }
export interface Meeting { id: string; userId: string; applicantName: string; deptId: string; deptName: string; roomId: string; roomName: string; floorNo: number; subject: string; attendNum: number; startTime: string; endTime: string; status: MeetingStatus; auditComment?: string; cancelReason?: string; attendees: Attendee[]; devices: { deviceType: string; quantity: number }[]; seriesId?: string; seriesIndex?: number; seriesTotal?: number }
export interface RecurringSubmitRequest { subject: string; attendNum: number; startTime: string; endTime: string; roomId: string; occurrenceCount: number; attendeeIds: string[]; devices: Record<string, number> }
export interface RecurringSubmissionResult { seriesId: string; meetings: Meeting[]; warnings: string[] }
export interface SeriesActionResult { seriesId: string; meetings: Meeting[] }
export interface Recommendation { roomId: string; roomName: string; floorNo: number; capacity: number; distance: number; capacityScore: number; deviceScore: number; floorScore: number; totalScore: number; reason: string }
export interface ScheduleEvent { id: string; roomId: string; roomName: string; title: string; startTime: string; endTime: string; status: MeetingStatus; detailVisible: boolean }
export interface Dashboard { totalRooms: string; availableRooms: string; myPending: string; myUpcoming: string; pendingApproval: string }
export interface Audit { id: string; operatorName: string; operateType: string; content: string; operateTime: string }
export interface Page<T> { records: T[]; total: string | number; page: number; pageSize: number }
export interface BookingRules { zone: string; serverNow: string; openTime: string; closeTime: string; workdays: number[]; weights: {capacity: number; device: number; floor: number} }

export interface Notice { id: string; bookId?: string; title: string; content: string; read: boolean; createTime: string }
export interface NoticePage { records: Notice[]; total: string | number; unread: string | number; page: number; pageSize: number }