import { ElMessage } from 'element-plus'

let csrfToken = ''

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
  const json = await response.json().catch(() => ({ success: false, message: response.status === 401 || response.status === 403 ? '请先登录或检查权限' : '服务暂时不可用' }))
  if (!response.ok || !json.success) {
    if (response.status === 401) { csrfToken=''; window.dispatchEvent(new Event('session-expired')) }
    throw new Error(json.message || '请求失败')
  }
  return json.data
}

export function showError(error: unknown) { ElMessage.error(error instanceof Error ? error.message : '操作失败') }
