const tokenKey = (role) => `flytaxi.${role}.accessToken`

export function getToken(role) {
  return localStorage.getItem(tokenKey(role)) || ''
}

export function saveTokens(role, data) {
  if (data?.accessToken) localStorage.setItem(tokenKey(role), data.accessToken)
  if (data?.refreshToken) localStorage.setItem(`flytaxi.${role}.refreshToken`, data.refreshToken)
}

export function clearTokens(role) {
  localStorage.removeItem(tokenKey(role))
  localStorage.removeItem(`flytaxi.${role}.refreshToken`)
}

async function request(base, path, options = {}) {
  const headers = new Headers(options.headers || {})
  if (options.body && !(options.body instanceof FormData)) headers.set('Content-Type', 'application/json')

  const response = await fetch(`${base}${path}`, {
    ...options,
    headers,
    body: options.body && !(options.body instanceof FormData) ? JSON.stringify(options.body) : options.body,
  })

  const text = await response.text()
  let payload = text
  try {
    payload = JSON.parse(text)
  } catch {
    if ((response.headers.get('content-type') || '').includes('json')) {
      throw new Error('服务器返回了无效数据，请稍后重试')
    }
  }
  if (response.status === 401 || ['token invalid', 'access token invalid'].includes(payload?.data)
      || ['token invalid', 'access token invalid'].includes(payload?.message)) {
    const error = new Error('登录状态已失效，请重新登录')
    error.status = 401
    throw error
  }
  if (!response.ok) {
    throw new Error(typeof payload === 'string' ? payload : payload?.message || `请求失败 (${response.status})`)
  }
  if (payload && typeof payload === 'object' && 'code' in payload && payload.code !== 1) {
    throw new Error(payload.message || (typeof payload.data === 'string' ? payload.data : `请求失败 (${payload.code})`))
  }
  return payload
}

function withAuth(role, options = {}) {
  const token = getToken(role)
  return {
    ...options,
    headers: {
      ...(options.headers || {}),
      ...(token ? { Authorization: token } : {}),
    },
  }
}

export const passengerApi = {
  requestCode(phone) {
    return request('/api/passenger', `/verification-code?passengerPhone=${encodeURIComponent(phone)}`, { method: 'GET' })
  },
  login(phone, code) {
    return request('/api/passenger', '/verification-code_check', { method: 'POST', body: { passengerPhone: phone, verificationCode: code } })
  },
  me() {
    return request('/api/passenger', '/users', withAuth('passenger'))
  },
  refresh(refreshToken) {
    return request('/api/passenger', '/token-refresh', { method: 'POST', body: { refreshToken } })
  },
  predictPrice(body) {
    return request('/api/passenger', '/predict-price', withAuth('passenger', { method: 'POST', body }))
  },
  geocode(address) {
    return request('/api/passenger', `/locations/geocode?address=${encodeURIComponent(address)}`, withAuth('passenger'))
  },
  addOrder(body) {
    return request('/api/passenger', '/order/add', withAuth('passenger', { method: 'POST', body }))
  },
  cancelOrder(orderId) {
    return request('/api/passenger', `/order/cancel?orderId=${encodeURIComponent(orderId)}`, withAuth('passenger', { method: 'POST' }))
  },
  currentOrder() {
    return request('/api/passenger', '/order/current', withAuth('passenger'))
  },
  payOrder(orderId) {
    return request('/api/passenger', '/order/pay', withAuth('passenger', { method: 'POST', body: { orderId } }))
  },
}

export const driverApi = {
  geocode(address) {
    return request('/api/driver', `/locations/geocode?address=${encodeURIComponent(address)}`, withAuth('driver'))
  },
  vehicle() {
    return request('/api/driver', '/vehicle', withAuth('driver'))
  },
  registerVehicle(body) {
    return request('/api/driver', '/vehicle', withAuth('driver', { method: 'POST', body }))
  },
  lockVehicle(vehicleNo) {
    return request('/api/driver', `/vehicle/lock?vehicleNo=${encodeURIComponent(vehicleNo)}`, withAuth('driver', { method: 'POST' }))
  },
  initializeVehicleMap() {
    return request('/api/driver', '/vehicle/map', withAuth('driver', { method: 'POST' }))
  },
  orders() {
    return request('/api/driver', '/order/list', withAuth('driver'))
  },
  orderDetail(orderId) {
    return request('/api/driver', `/order/${encodeURIComponent(orderId)}`, withAuth('driver'))
  },
  acceptOrder(orderId) {
    return request('/api/driver', `/order/accept?orderId=${encodeURIComponent(orderId)}`, withAuth('driver', { method: 'POST' }))
  },
  requestCode(phone) {
    return request('/api/driver', '/verification-code', { method: 'POST', body: { driverPhone: phone } })
  },
  login(phone, code) {
    return request('/api/driver', '/verification-code-check', { method: 'POST', body: { driverPhone: phone, verificationCode: code } })
  },
  updateUser(body) {
    return request('/api/driver', '/user', withAuth('driver', { method: 'PUT', body }))
  },
  workStatus(body) {
    return request('/api/driver', '/driver-user-work-status', withAuth('driver', { method: 'POST', body }))
  },
  binding() {
    return request('/api/driver', '/driver-car-binding-relationship', withAuth('driver'))
  },
  status(driverId) {
    return request('/api/driver', `/work-status?driverId=${encodeURIComponent(driverId)}`, withAuth('driver'))
  },
  advanceOrder(path, body) {
    return request('/api/driver', `/order/${path}`, withAuth('driver', { method: 'POST', body }))
  },
  cancelOrder(orderId) {
    return request('/api/driver', `/order/cancel?orderId=${encodeURIComponent(orderId)}`, withAuth('driver', { method: 'POST' }))
  },
  payInfo(orderId, price, passengerId) {
    return request('/api/driver', `/pay/push-pay-info?orderId=${orderId}&price=${encodeURIComponent(price)}&passengerId=${passengerId}`, withAuth('driver', { method: 'POST' }))
  },
  uploadPoints(body) {
    return request('/api/driver', '/point/upload', withAuth('driver', { method: 'POST', body }))
  },
}

export const bossApi = {
  login(username, password) {
    return request('/api/boss', '/admin-auth/login', { method: 'POST', body: { username, password } })
  },
  drivers({ page = 1, pageSize = 10, keyword = '', state = '' } = {}) {
    const params = new URLSearchParams({ page: String(page), pageSize: String(pageSize) })
    if (keyword.trim()) params.set('keyword', keyword.trim())
    if (state !== '') params.set('state', String(state))
    return request('/api/boss', `/driver-users?${params}`, withAuth('boss'))
  },
  addDriver(body) {
    return request('/api/boss', '/driver-user', withAuth('boss', { method: 'POST', body }))
  },
  updateDriver(body) {
    return request('/api/boss', '/driver-user', withAuth('boss', { method: 'PUT', body }))
  },
  addCar(body) {
    return request('/api/boss', '/car', withAuth('boss', { method: 'POST', body }))
  },
  bind(body) {
    return request('/api/boss', '/driver-car-binding-relationship/bind', withAuth('boss', { method: 'POST', body }))
  },
  unbind(body) {
    return request('/api/boss', '/driver-car-binding-relationship/unbind', withAuth('boss', { method: 'POST', body }))
  },
}

export async function checkInfrastructure() {
  const checks = await Promise.allSettled([
    fetch('/api/passenger/verification-code', { method: 'OPTIONS' }),
    fetch('http://localhost:8848/nacos/v1/console/server/state'),
  ])
  return { api: checks[0].status === 'fulfilled', nacos: checks[1].status === 'fulfilled' }
}
