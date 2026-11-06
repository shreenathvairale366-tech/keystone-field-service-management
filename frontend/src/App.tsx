import { useCallback, useEffect, useState, FormEvent } from 'react'
import { api, setToken, Session, WorkOrder, Dashboard, Status } from './api'

const NEXT: Record<Status, Status[]> = {
  OPEN: [], ASSIGNED: ['IN_PROGRESS'], IN_PROGRESS: ['ON_HOLD', 'COMPLETED'], ON_HOLD: ['IN_PROGRESS'], COMPLETED: ['CLOSED'], CLOSED: [],
}
const label = (s: string) => s.replace('_', ' ').toLowerCase()

function Login({ onLogin }: { onLogin: (s: Session) => void }) {
  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [err, setErr] = useState('')
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    const f = Object.fromEntries(new FormData(e.currentTarget)) as Record<string, string>
    try { onLogin(await api<Session>(`/auth/${mode}`, 'POST', f)) } catch (x) { setErr((x as Error).message) }
  }
  return (
    <form className="login" onSubmit={submit}>
      <h1>KEYSTONE</h1>
      <p>Field service management</p>
      <input name="username" placeholder="Username" required />
      {mode === 'register' && <input name="fullName" placeholder="Company or full name" required />}
      <input name="password" type="password" placeholder="Password" required />
      {err && <div className="err">{err}</div>}
      <button>{mode === 'login' ? 'Sign in' : 'Create customer account'}</button>
      <a href="#" onClick={e => { e.preventDefault(); setErr(''); setMode(mode === 'login' ? 'register' : 'login') }}>
        {mode === 'login' ? 'New customer? Create an account' : 'Back to sign in'}
      </a>
    </form>
  )
}

export default function App() {
  const [s, setS] = useState<Session | null>(null)
  const [orders, setOrders] = useState<WorkOrder[]>([])
  const [dash, setDash] = useState<Dashboard | null>(null)
  const [techs, setTechs] = useState<{ username: string; fullName: string }[]>([])
  const [err, setErr] = useState('')
  const staff = s?.role === 'ADMIN' || s?.role === 'DISPATCHER'

  const load = useCallback(async () => {
    try {
      setOrders(await api<WorkOrder[]>('/work-orders'))
      if (staff) { setDash(await api<Dashboard>('/dashboard')); setTechs(await api('/technicians')) }
    } catch (x) { setErr((x as Error).message) }
  }, [staff])
  useEffect(() => { if (s) load() }, [s, load])

  const act = async (fn: () => Promise<unknown>) => { setErr(''); try { await fn(); await load() } catch (x) { setErr((x as Error).message) } }

  if (!s) return <Login onLogin={x => { setToken(x.token); setS(x) }} />

  async function create(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); const form = e.currentTarget
    const f = Object.fromEntries(new FormData(form))
    await act(() => api('/work-orders', 'POST', f)); form.reset()
  }

  return (
    <div className="shell">
      <header><b>KEYSTONE</b><span>{s.fullName} ({label(s.role)})</span>
        <button className="ghost" onClick={() => { setToken(''); setS(null) }}>Sign out</button></header>
      {err && <div className="err">{err}</div>}

      {dash && (
        <section className="stats">
          <div><big>{dash.total}</big>work orders</div>
          <div><big>{dash.byStatus.IN_PROGRESS ?? 0}</big>in progress</div>
          <div className={dash.slaBreached ? 'bad' : ''}><big>{dash.slaBreached}</big>SLA breached</div>
          <div><big>{dash.slaCompliancePct}%</big>SLA compliance</div>
        </section>
      )}

      {s.role !== 'TECHNICIAN' && (
        <form className="new" onSubmit={create}>
          <input name="title" placeholder="What needs fixing?" required />
          <input name="facility" placeholder="Facility / location" />
          <select name="priority" defaultValue="MEDIUM"><option>LOW</option><option>MEDIUM</option><option>HIGH</option><option>CRITICAL</option></select>
          <button>Create work order</button>
        </form>
      )}

      <table>
        <thead><tr><th>#</th><th>Title</th><th>Facility</th><th>Priority</th><th>Status</th><th>Technician</th><th>SLA due</th><th></th></tr></thead>
        <tbody>
          {orders.length === 0 && <tr><td colSpan={8} className="empty">No work orders yet. Create one above.</td></tr>}
          {orders.map(w => (
            <tr key={w.id}>
              <td>{w.id}</td><td>{w.title}</td><td>{w.facility}</td>
              <td className={'p-' + w.priority}>{label(w.priority)}</td><td>{label(w.status)}</td>
              <td>{w.technician ?? '-'}</td>
              <td className={w.slaBreached ? 'bad' : ''}>{new Date(w.slaDueAt).toLocaleString()}</td>
              <td className="actions">
                {staff && (w.status === 'OPEN' || w.status === 'ASSIGNED') && (
                  <select value="" onChange={e => e.target.value && act(() => api(`/work-orders/${w.id}/assign`, 'POST', { technician: e.target.value }))}>
                    <option value="">Assign...</option>{techs.map(t => <option key={t.username} value={t.username}>{t.fullName}</option>)}
                  </select>)}
                {s.role !== 'CUSTOMER' && NEXT[w.status].filter(n => n !== 'CLOSED' || staff).map(n => (
                  <button key={n} onClick={() => act(() => api(`/work-orders/${w.id}/status`, 'POST', { status: n }))}>Move to {label(n)}</button>))}
                {s.role !== 'CUSTOMER' && w.status === 'IN_PROGRESS' && (
                  <button className="ghost" onClick={() => { const m = Number(prompt('Minutes worked?')); if (m > 0) act(() => api(`/work-orders/${w.id}/time`, 'POST', { minutes: m })) }}>Log time</button>)}
                {s.role !== 'CUSTOMER' && w.status === 'IN_PROGRESS' && (
                  <button className="ghost" onClick={() => { const n = prompt('Part name?'); const c = Number(prompt('Unit cost?')); if (n) act(() => api(`/work-orders/${w.id}/parts`, 'POST', { partName: n, quantity: 1, unitCost: c || 0 })) }}>Add part</button>)}
              </td>
            </tr>))}
        </tbody>
      </table>
    </div>
  )
}
