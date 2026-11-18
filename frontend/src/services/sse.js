import { getToken } from './api'

const eventNames = [
  'order.created',
  'order.accepted',
  'order.status',
  'order.cancelled',
  'order.paid',
  'pay.info',
]

const pools = new Map()

function parse(data) {
  try {
    return JSON.parse(data)
  } catch {
    return data
  }
}

function open(role, pool) {
  const token = getToken(role)
  if (!token || pool.closed || pool.source) return

  const source = new EventSource(`/api/sse/connect?token=${encodeURIComponent(token)}`)
  pool.source = source

  eventNames.forEach(name => {
    source.addEventListener(name, event => {
      const payload = parse(event.data)
      for (const handler of pool.handlers) {
        try {
          handler({ type: name, payload })
        } catch (error) {
          console.error('SSE handler failed', error)
        }
      }
    })
  })

  source.onopen = () => {
    pool.retry = 0
  }

  source.onerror = () => {
    source.close()
    if (pool.source === source) pool.source = null
    schedule(role, pool)
  }
}

function schedule(role, pool) {
  if (pool.closed || pool.timer) return
  pool.retry = (pool.retry || 0) + 1
  const delay = Math.min(30000, 1000 * 2 ** Math.min(pool.retry, 5))
  pool.timer = window.setTimeout(() => {
    pool.timer = null
    open(role, pool)
  }, delay)
}

/**
 * One EventSource per role per tab, shared by every component that subscribes.
 * Returns an unsubscribe function.
 */
export function subscribeSse(role, handler) {
  let pool = pools.get(role)
  if (!pool) {
    pool = { source: null, handlers: new Set(), timer: null, retry: 0, closed: false }
    pools.set(role, pool)
  }
  pool.handlers.add(handler)
  open(role, pool)

  return () => {
    pool.handlers.delete(handler)
    if (pool.handlers.size === 0) {
      pool.closed = true
      if (pool.timer) window.clearTimeout(pool.timer)
      if (pool.source) pool.source.close()
      pools.delete(role)
    }
  }
}
