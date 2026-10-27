import { ElMessage } from 'element-plus'

let csrfToken = ''

export class ApiError extends Error {
  constructor(message: string, public code?: string, public details?: { conflictDates?: string[] }) { super(message) }
}

async function responseError(response: Response): Promise<never> {
  if (response.status === 401) { csrfToken = ''; window.dispatchEvent(new Event('session-expired')) }
  const json = await response.json().catch(() => ({}))
  throw new ApiError(json.message || (response.status === 401 ? '登录已过期，请重新登录' : response.status === 403 ? '没有操作权限' : '服务暂时不可用'), json.data?.code, json.data?.details)
}

export async function refreshCsrf() {
  const response = await fetch('/api/v1/auth/csrf', { credentials: 'include' })
  const json = await response.json()
  if (!response.ok || !json.success) throw new Error('无法获取安全令牌，请确认后端已启动')
  csrfToken = json.data.token
  return csrfToken
}

export async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  const method = options.method ?? 'GET'
  if (method !== 'GET' && !csrfToken) await refreshCsrf()
  const response = await fetch(`/api/v1${path}`, {
    ...options,
    credentials: 'include',
    headers: { 'Content-Type': 'application/json', ...(method !== 'GET' ? { 'X-CSRF-TOKEN': csrfToken } : {}), ...options.headers },
  })
  if (!response.ok) return responseError(response)
  const json = await response.json().catch(() => ({ success: false, message: '服务暂时不可用' }))
  if (!response.ok || !json.success) {
    throw new ApiError(json.message || '请求失败', json.data?.code, json.data?.details)
  }
  return json.data
}

export async function download(path: string, filename: string) {
  const response = await fetch(`/api/v1${path}`, { credentials: 'include' })
  if (!response.ok) return responseError(response)
  if (!response.headers.get('content-type')?.toLowerCase().startsWith('text/csv')) throw new Error('服务未返回有效的 CSV，请刷新后重试')
  const blob = await response.blob()
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  URL.revokeObjectURL(url)
}

export function showError(error: unknown) { ElMessage.error(error instanceof Error ? error.message : '操作失败') }
