export type Role = 'ADMIN' | 'DISPATCHER' | 'TECHNICIAN' | 'CUSTOMER'
export type Status = 'OPEN' | 'ASSIGNED' | 'IN_PROGRESS' | 'ON_HOLD' | 'COMPLETED' | 'CLOSED'
export interface Session { token: string; role: Role; fullName: string; username: string }
export interface WorkOrder { id: number; title: string; description?: string; facility?: string; customer: string; technician?: string
  priority: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'; status: Status; slaDueAt: string; slaBreached: boolean }
export interface Dashboard { total: number; byStatus: Record<string, number>; slaBreached: number; slaCompliancePct: number }

let token = ''
export const setToken = (t: string) => { token = t }

export async function api<T>(path: string, method = 'GET', body?: unknown): Promise<T> {
  const res = await fetch('/api' + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  })
  if (!res.ok) {
    const e = await res.json().catch(() => ({}))
    throw new Error(e.message || e.error || `Request failed (${res.status})`)
  }
  return res.json()
}
