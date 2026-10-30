<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  Activity,
  ArrowRight,
  CarFront,
  Check,
  ChevronDown,
  CircleHelp,
  ClipboardList,
  CloudCog,
  CreditCard,
  Database,
  Gauge,
  LayoutDashboard,
  LogIn,
  LogOut,
  MapPin,
  Menu,
  Navigation,
  Plus,
  RefreshCw,
  Send,
  Settings2,
  ShieldCheck,
  Smartphone,
  UserRound,
  Users,
  X,
  Zap,
} from 'lucide-vue-next'
import { bossApi, checkInfrastructure, clearTokens, driverApi, getToken, passengerApi, saveTokens } from './services/api'
import ActivityFeed from './components/ActivityFeed.vue'
import DriverOrders from './components/DriverOrders.vue'
import PassengerPayment from './components/PassengerPayment.vue'
import BossDrivers from './components/BossDrivers.vue'
import DriverVehicle from './components/DriverVehicle.vue'

const role = ref('passenger')
const mobileMenu = ref(false)
const activePanel = ref('overview')
const notice = ref({ type: '', text: '' })
const logs = ref([])
const infra = reactive({ api: true, nacos: true, redis: true })
const busy = ref(false)
const authVersion = ref(0)
const estimatingPrice = ref(false)
const estimateError = ref('')
const estimateNeedsLogin = ref(false)
const resumeEstimateAfterLogin = ref(false)
let tripRevision = 0
let resolvedTrip = null

function getPassengerDeviceCode() {
  const key = 'flytaxi.passenger.deviceCode'
  let deviceCode = localStorage.getItem(key)
  if (!deviceCode) {
    const suffix = typeof crypto !== 'undefined' && crypto.randomUUID
      ? crypto.randomUUID()
      : `${Date.now()}-${Math.random().toString(16).slice(2)}`
    deviceCode = `web-${suffix}`
    localStorage.setItem(key, deviceCode)
  }
  return deviceCode
}

const passengerVehicleTypes = [
  { value: '1', label: '快车 / 普通型' },
  { value: '2', label: '舒适型' },
  { value: '3', label: '商务型' },
]

const passenger = reactive({
  phone: '',
  code: '',
  codeSent: false,
  user: null,
  estimate: null,
  order: {
    departure: '北京市朝阳区望京SOHO',
    destination: '北京首都国际机场 T3',
    address: '',
    depLongitude: '',
    depLatitude: '',
    destLongitude: '',
    destLatitude: '',
    vehicleType: '1',
    fareType: '',
    fareVersion: null,
    deviceCode: getPassengerDeviceCode(),
    encrypt: 1,
  },
})

const driver = reactive({
  phone: '',
  code: '',
  codeSent: false,
  user: null,
  workStatus: 0,
  binding: null,
  bindingLoaded: false,
})

const boss = reactive({
  driver: { driverName: '', driverPhone: '', address: '', driverGender: 1, state: 1 },
  car: { address: '', vehicleNo: '', plateColor: '1', seats: 4, brand: '', model: '', vehicleType: '1', ownerName: '', vehicleColor: '1', state: 0 },
  binding: { driverId: '', carId: '', bindState: 1 },
})

const roleMeta = computed(() => ({
  passenger: { label: '乘客工作台', short: '乘客', icon: Smartphone, accent: 'cyan' },
  driver: { label: '司机工作台', short: '司机', icon: CarFront, accent: 'lime' },
  boss: { label: '运营管理台', short: '管理', icon: ShieldCheck, accent: 'orange' },
}[role.value]))

const isLoggedIn = computed(() => {
  authVersion.value
  return Boolean(getToken(role.value))
})
const currentUser = computed(() => role.value === 'passenger' ? passenger.user : driver.user)

const panels = computed(() => {
  if (role.value === 'passenger') return [
    { id: 'overview', label: '行程概览', icon: LayoutDashboard },
    { id: 'ride', label: '发起行程', icon: Navigation },
    { id: 'payment', label: '订单与支付', icon: CreditCard },
    { id: 'activity', label: '请求记录', icon: Activity },
  ]
  if (role.value === 'driver') return [
    { id: 'overview', label: '接单概览', icon: LayoutDashboard },
    { id: 'dispatch', label: '订单操作', icon: ClipboardList },
    { id: 'vehicle', label: '车辆与定位', icon: CarFront },
    { id: 'account', label: '司机账号', icon: UserRound },
  ]
  return [
    { id: 'overview', label: '运营概览', icon: LayoutDashboard },
    { id: 'drivers', label: '司机列表', icon: ClipboardList },
    { id: 'driver', label: '司机档案', icon: Users },
    { id: 'vehicle', label: '车辆档案', icon: CarFront },
    { id: 'binding', label: '绑定关系', icon: Settings2 },
  ]
})

function writeLog(label, status = 'success', detail = '') {
  logs.value.unshift({ label, status, detail, time: new Date().toLocaleTimeString('zh-CN', { hour12: false }) })
  logs.value = logs.value.slice(0, 12)
}

function notify(text, type = 'success') {
  notice.value = { text, type }
  window.clearTimeout(notify.timer)
  notify.timer = window.setTimeout(() => { notice.value = { type: '', text: '' } }, 4200)
}

async function run(label, action) {
  busy.value = true
  try {
    const result = await action()
    writeLog(label, 'success', result?.message || '请求成功')
    notify(`${label}成功`)
    return result
  } catch (error) {
    writeLog(label, 'error', error.message)
    notify(error.message || `${label}失败`, 'error')
  } finally {
    busy.value = false
  }
}

function responseData(result) {
  return result?.data ?? result
}

watch(
  () => [passenger.order.departure, passenger.order.destination, passenger.order.vehicleType],
  () => {
    tripRevision += 1
    resolvedTrip = null
    passenger.estimate = null
    estimateError.value = ''
    estimateNeedsLogin.value = false
    Object.assign(passenger.order, {
      address: '', depLongitude: '', depLatitude: '', destLongitude: '', destLatitude: '', fareType: '', fareVersion: null,
    })
  },
  { flush: 'sync' },
)

async function resolveTripLocations() {
  const revision = tripRevision
  const departure = passenger.order.departure.trim()
  const destination = passenger.order.destination.trim()
  if (!departure || !destination) throw new Error('请填写出发地和目的地')
  if (departure === destination) throw new Error('出发地和目的地不能相同')
  if (resolvedTrip?.revision === revision) return resolvedTrip

  async function resolve(address, label) {
    try {
      const data = responseData(await passengerApi.geocode(address))
      if (!data?.longitude || !data?.latitude || !/^\d{6}$/.test(data.cityCode) || !/^\d{6}$/.test(data.districtCode)) {
        throw new Error('未获取到有效地点，请补充城市和详细地址')
      }
      return data
    } catch (error) {
      if (error.status !== 401) error.message = `${label}：${error.message}`
      throw error
    }
  }

  const origin = await resolve(departure, '出发地')
  if (revision !== tripRevision) return null
  const target = await resolve(destination, '目的地')
  if (revision !== tripRevision) return null
  if (origin.longitude === target.longitude && origin.latitude === target.latitude) {
    throw new Error('出发地和目的地解析为同一地点，请填写更具体的地址')
  }
  const coordinates = {
    depLongitude: origin.longitude, depLatitude: origin.latitude,
    destLongitude: target.longitude, destLatitude: target.latitude,
  }
  Object.assign(passenger.order, coordinates, {
    address: origin.districtCode,
    fareType: `${origin.cityCode}$${passenger.order.vehicleType}`,
  })
  resolvedTrip = {
    revision,
    priceRequest: { ...coordinates, cityCode: origin.cityCode, vehicleType: passenger.order.vehicleType },
  }
  return resolvedTrip
}

function persistTokens(roleName, data) {
  saveTokens(roleName, data)
  authVersion.value += 1
}

async function loginAsPassenger() {
  const result = await run('乘客登录', () => passengerApi.login(passenger.phone, passenger.code))
  const data = responseData(result)
  if (data?.accessToken) {
    persistTokens('passenger', data)
    await loadPassengerUser()
    if (resumeEstimateAfterLogin.value) {
      resumeEstimateAfterLogin.value = false
      activePanel.value = 'ride'
      await estimatePrice()
    }
  }
}

async function sendPassengerCode() {
  if (!passenger.phone) return notify('请先填写手机号', 'error')
  await run('发送乘客验证码', () => passengerApi.requestCode(passenger.phone))
  passenger.codeSent = true
}

async function loadPassengerUser() {
  const result = await run('读取乘客资料', passengerApi.me)
  passenger.user = responseData(result)
}

async function refreshPassengerToken() {
  const token = localStorage.getItem('flytaxi.passenger.refreshToken')
  if (!token) return notify('当前没有可用的刷新令牌', 'error')
  const result = await run('刷新乘客令牌', () => passengerApi.refresh(token))
  persistTokens('passenger', responseData(result))
}

async function estimatePrice() {
  if (estimatingPrice.value) return
  passenger.estimate = null
  estimateError.value = ''
  estimateNeedsLogin.value = false
  if (!getToken('passenger')) {
    estimateError.value = '请先登录乘客账号，登录后即可预测价格'
    estimateNeedsLogin.value = true
    return
  }
  const revision = tripRevision
  estimatingPrice.value = true
  busy.value = true
  try {
    const trip = await resolveTripLocations()
    if (!trip || revision !== tripRevision) return
    const result = await passengerApi.predictPrice(trip.priceRequest)
    if (revision !== tripRevision) return
    const data = responseData(result)
    if (typeof data?.price !== 'number' || !Number.isFinite(data.price) || data.price < 0) {
      throw new Error('未获取到有效的预估费用，请重新预测')
    }
    passenger.estimate = data
    Object.assign(passenger.order, {
      fareType: data.fareType || passenger.order.fareType,
      fareVersion: data.fareVersion,
    })
    writeLog('预测行程价格', 'success', result.message || '请求成功')
  } catch (error) {
    if (revision !== tripRevision && error.status !== 401) return
    estimateError.value = error.message || '暂时无法预测价格，请稍后重试'
    estimateNeedsLogin.value = error.status === 401
    if (estimateNeedsLogin.value) {
      clearTokens('passenger')
      authVersion.value += 1
      passenger.user = null
    }
    writeLog('预测行程价格', 'error', estimateError.value)
  } finally {
    estimatingPrice.value = false
    busy.value = false
  }
}

function loginForEstimate() {
  resumeEstimateAfterLogin.value = true
  openLogin()
}

async function createOrder() {
  if (!isLoggedIn.value) return notify('请先登录乘客账号', 'error')
  const result = await run('创建订单', async () => {
    const trip = await resolveTripLocations()
    if (!trip) throw new Error('行程地址已修改，请重新确认下单')
    if (!Number.isInteger(passenger.estimate?.fareVersion)) {
      throw new Error('请先点击预测价格，确认当前行程费用后再下单')
    }
    const orderPayload = {
      ...passenger.order,
      fareType: passenger.estimate.fareType || passenger.order.fareType,
      fareVersion: passenger.estimate.fareVersion,
      passengerPhone: passenger.phone,
      orderTime: new Date().toISOString().slice(0, 19).replace('T', ' '),
    }
    const storageKey = `flytaxi.passenger.pendingOrder.${passenger.user?.id || passenger.phone}`
    const stablePayload = { ...orderPayload }
    delete stablePayload.orderTime
    const signatureSource = JSON.stringify(stablePayload)
    let signatureHash = 2166136261
    for (let index = 0; index < signatureSource.length; index += 1) {
      signatureHash = Math.imul(signatureHash ^ signatureSource.charCodeAt(index), 16777619)
    }
    const signature = (signatureHash >>> 0).toString(16)
    let pendingRequest
    try {
      pendingRequest = JSON.parse(sessionStorage.getItem(storageKey) || 'null')
    } catch {
      pendingRequest = null
    }
    if (pendingRequest?.signature !== signature || !pendingRequest?.idempotencyKey) {
      pendingRequest = {
        signature,
        orderTime: orderPayload.orderTime,
        idempotencyKey: typeof crypto !== 'undefined' && crypto.randomUUID
          ? crypto.randomUUID()
          : `${Date.now()}-${Math.random().toString(16).slice(2)}`,
      }
      sessionStorage.setItem(storageKey, JSON.stringify(pendingRequest))
    }
    orderPayload.orderTime = pendingRequest.orderTime
    const response = await passengerApi.addOrder({ ...orderPayload, idempotencyKey: pendingRequest.idempotencyKey })
    sessionStorage.removeItem(storageKey)
    return response
  })
  if (result) activePanel.value = 'payment'
}

async function loginAsDriver() {
  const result = await run('司机登录', () => driverApi.login(driver.phone, driver.code))
  const data = responseData(result)
  if (data?.accessToken) {
    driver.binding = null
    driver.bindingLoaded = false
    persistTokens('driver', data)
    notify('司机登录成功')
    activePanel.value = 'dispatch'
  }
}

async function sendDriverCode() {
  if (!driver.phone) return notify('请先填写司机手机号', 'error')
  await run('发送司机验证码', () => driverApi.requestCode(driver.phone))
  driver.codeSent = true
}

async function loadDriverBinding() {
  if (!getToken('driver')) return notify('请先登录司机账号', 'error')
  const result = await run('读取车辆绑定', driverApi.binding)
  driver.bindingLoaded = Boolean(result)
  if (result) {
    driver.binding = result.data?.carId ? result.data : null
    if (!driver.binding) {
      driver.workStatus = 0
      if (activePanel.value !== 'vehicle') notify('当前账号未绑定车辆，请到车辆与定位登记车辆', 'error')
    }
    if (driver.binding?.driverId) {
      try {
        const status = responseData(await driverApi.status(driver.binding.driverId))
        driver.workStatus = status.workStatus
      } catch (error) {
        notify(error.message, 'error')
      }
    }
  }
}

async function updateWorkStatus(status) {
  if (!getToken('driver')) return notify('请先登录司机账号', 'error')
  const result = await run(status === 1 ? '开始营运' : '暂停营运', async () => {
    if (!driver.binding?.driverId) driver.binding = responseData(await driverApi.binding())
    if (!driver.binding?.driverId) throw new Error('当前账号未绑定车辆，请联系管理员')
    return driverApi.workStatus({ driverId: driver.binding.driverId, workStatus: status })
  })
  if (result) driver.workStatus = status
}

function recordDriverOrderActivity({ label, status, detail }) {
  writeLog(label, status, detail)
  notify(status === 'success' ? `${label}成功` : detail, status)
}

function recordPassengerActivity({ label, status, detail }) {
  writeLog(label, status, detail)
  notify(status === 'success' ? `${label}成功` : detail, status)
}

function recordDriverVehicleActivity({ label, status, detail }) {
  writeLog(label, status, detail)
  if (status === 'success') notify(`${label}成功`, status)
}

function expireDriverSession() {
  clearTokens('driver')
  authVersion.value += 1
  driver.user = null
  driver.binding = null
  driver.bindingLoaded = false
  driver.workStatus = 0
}

function expirePassengerSession() {
  clearTokens('passenger')
  authVersion.value += 1
  passenger.user = null
  notify('登录状态已失效，请重新登录', 'error')
}

watch(() => [role.value, activePanel.value, authVersion.value], () => {
  if (role.value === 'driver' && activePanel.value === 'vehicle' && getToken('driver')) loadDriverBinding()
})

async function submitBoss(kind) {
  const actions = {
    driver: ['新增司机', () => bossApi.addDriver(boss.driver)],
    vehicle: ['新增车辆', () => bossApi.addCar(boss.car)],
    binding: [boss.binding.bindState === 1 ? '绑定车辆' : '解除绑定', () => boss.binding.bindState === 1 ? bossApi.bind(boss.binding) : bossApi.unbind(boss.binding)],
  }
  const result = await run(actions[kind][0], actions[kind][1])
  if (result && kind === 'driver') {
    Object.assign(boss.driver, { driverName: '', driverPhone: '', address: '', driverGender: 1, state: 1 })
    activePanel.value = 'drivers'
  }
}

function logout() {
  clearTokens(role.value)
  authVersion.value += 1
  if (role.value === 'passenger') passenger.user = null
  if (role.value === 'driver') {
    driver.user = null
    driver.binding = null
    driver.bindingLoaded = false
    driver.workStatus = 0
  }
  notify('已退出当前角色')
}

function switchRole(next) {
  role.value = next
  activePanel.value = 'overview'
  mobileMenu.value = false
  logs.value = []
}

function openLogin() {
  if (role.value === 'passenger') activePanel.value = 'activity'
  if (role.value === 'driver') activePanel.value = 'account'
  if (role.value === 'boss') activePanel.value = 'driver'
  mobileMenu.value = false
}

async function loadInfrastructure() {
  infra.redis = true
  const result = await checkInfrastructure()
  infra.api = result.api
  infra.nacos = result.nacos
}

onMounted(loadInfrastructure)
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar" :class="{ open: mobileMenu }">
      <div class="brand-lockup">
        <div class="brand-mark"><Zap :size="19" :stroke-width="2.6" /></div>
        <div>
          <strong>FLYTAXI</strong>
          <span>运营控制台</span>
        </div>
      </div>

      <div class="sidebar-label">工作空间</div>
      <nav class="role-switcher" aria-label="角色切换">
        <button :class="{ active: role === 'passenger' }" @click="switchRole('passenger')"><Smartphone :size="17" /><span>乘客端</span><ChevronDown v-if="role === 'passenger'" :size="14" /></button>
        <button :class="{ active: role === 'driver' }" @click="switchRole('driver')"><CarFront :size="17" /><span>司机端</span><ChevronDown v-if="role === 'driver'" :size="14" /></button>
        <button :class="{ active: role === 'boss' }" @click="switchRole('boss')"><ShieldCheck :size="17" /><span>管理端</span><ChevronDown v-if="role === 'boss'" :size="14" /></button>
      </nav>

      <div class="sidebar-label">功能导航</div>
      <nav class="main-nav">
        <button v-for="panel in panels" :key="panel.id" :class="{ active: activePanel === panel.id }" @click="activePanel = panel.id; mobileMenu = false">
          <component :is="panel.icon" :size="17" /><span>{{ panel.label }}</span>
        </button>
      </nav>

      <div class="sidebar-spacer" />
      <div class="runtime-panel">
        <div class="runtime-title"><span class="pulse-dot" />运行环境</div>
        <div class="runtime-row"><span>后端 API</span><b :class="{ off: !infra.api }">{{ infra.api ? '在线' : '离线' }}</b></div>
        <div class="runtime-row"><span>Nacos</span><b :class="{ off: !infra.nacos }">{{ infra.nacos ? '在线' : '离线' }}</b></div>
        <div class="runtime-row"><span>Redis</span><b :class="{ off: !infra.redis }">{{ infra.redis ? '在线' : '离线' }}</b></div>
      </div>
      <div class="sidebar-foot">LOCAL DEV · 2026</div>
    </aside>

    <main class="main-content">
      <header class="topbar">
        <button class="icon-button mobile-toggle" title="打开导航" @click="mobileMenu = !mobileMenu"><Menu :size="20" /></button>
        <div class="breadcrumb"><span>FlyTaxi</span><ArrowRight :size="14" /><strong>{{ roleMeta.label }}</strong></div>
        <div class="topbar-actions">
          <button class="icon-button" title="刷新基础服务状态" @click="loadInfrastructure"><RefreshCw :size="17" /></button>
          <div class="connection-state"><span class="pulse-dot" />本地服务集群</div>
          <button v-if="isLoggedIn" class="profile-chip" @click="logout"><span class="avatar">{{ roleMeta.short.slice(0, 1) }}</span><span>{{ currentUser?.passengerName || currentUser?.driverName || roleMeta.short + '账号' }}</span><LogOut :size="14" /></button>
          <button v-else class="login-chip" @click="openLogin"><LogIn :size="15" />登录</button>
        </div>
      </header>

      <section class="content-wrap">
        <div v-if="notice.text" class="toast" :class="notice.type"><Check v-if="notice.type === 'success'" :size="16" /><X v-else :size="16" />{{ notice.text }}</div>

        <div class="page-heading">
          <div>
            <div class="eyebrow"><span class="eyebrow-line" />{{ roleMeta.short }}端 · LOCAL WORKSPACE</div>
            <h1>{{ roleMeta.label }}</h1>
            <p v-if="role === 'passenger'">从估价、下单到行程状态，串起一次完整的出行体验。</p>
            <p v-else-if="role === 'driver'">掌握营运状态、订单节点和车辆位置，快速完成接驾流程。</p>
            <p v-else>维护司机、车辆与绑定关系，让车队数据随时可用。</p>
          </div>
          <div class="date-stamp"><span>系统时间</span><strong>{{ new Date().toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' }) }}</strong></div>
        </div>

        <div v-if="role === 'passenger'" class="role-view">
          <section v-if="activePanel === 'overview'" class="dashboard-grid">
            <article class="hero-panel passenger-hero">
              <div class="hero-copy"><span class="section-kicker">RIDE CENTER / 01</span><h2>下一段路，<br /><em>从这里开始。</em></h2><p>填写出发地和目的地，先看预估价格，再决定是否出发。</p><button class="primary-button" @click="activePanel = 'ride'">开始规划行程 <ArrowRight :size="16" /></button></div>
              <div class="route-visual"><div class="map-grid" /><div class="route-line"><span class="route-start" /><span class="route-end" /></div><div class="map-label start">望京 SOHO</div><div class="map-label end">首都机场 T3</div></div>
            </article>
            <article class="metric-card"><div class="metric-icon cyan"><Gauge :size="18" /></div><span>当前预估</span><strong>{{ passenger.estimate?.price ?? passenger.estimate?.amount ?? '--' }}<small v-if="passenger.estimate"> 元</small></strong><p>根据实时计价规则</p></article>
            <article class="metric-card"><div class="metric-icon green"><MapPin :size="18" /></div><span>默认出发地</span><strong class="metric-text">{{ passenger.order.departure }}</strong></article>
            <article class="status-strip"><div><span class="pulse-dot" />服务链路正常</div><span>MySQL · Redis · Nacos</span><button class="text-button" @click="activePanel = 'activity'">查看请求记录 <ArrowRight :size="14" /></button></article>
          </section>

          <section v-else-if="activePanel === 'ride'" class="form-layout">
            <article class="surface form-surface wide-form"><div class="surface-heading"><div><span class="section-kicker">RIDE PLANNER / 02</span><h2>规划一段行程</h2></div><Navigation :size="22" /></div>
              <div class="route-fields"><label class="field"><span>出发地</span><input v-model="passenger.order.departure" placeholder="输入出发地址" /></label><div class="route-connector"><span /><i /></div><label class="field"><span>目的地</span><input v-model="passenger.order.destination" placeholder="输入目的地" /></label></div>
              <div class="field-grid"><label class="field"><span>车辆类型</span><select v-model="passenger.order.vehicleType"><option v-for="vehicle in passengerVehicleTypes" :key="vehicle.value" :value="vehicle.value">{{ vehicle.label }}</option></select></label></div>
              <div class="form-actions"><button class="secondary-button" :disabled="busy" @click="estimatePrice"><Gauge :size="16" />{{ estimatingPrice ? '预测中...' : '预测价格' }}</button><button class="primary-button" :disabled="busy || !isLoggedIn" @click="createOrder"><Send :size="16" />{{ isLoggedIn ? '确认下单' : '登录后下单' }}</button></div>
            </article>
            <article class="surface quote-surface" :aria-busy="estimatingPrice"><div class="surface-heading"><div><span class="section-kicker">LIVE QUOTE</span><h3>预估费用</h3></div><Zap :size="19" /></div><div class="quote-amount" aria-live="polite"><strong>{{ passenger.estimate?.price?.toFixed(2) ?? '--' }}</strong><span>元</span></div><p v-if="estimatingPrice" class="quote-status" role="status">正在计算预估费用...</p><p v-else-if="estimateError" class="quote-status error" role="alert">{{ estimateError }}</p><button v-if="estimateNeedsLogin" class="secondary-button" @click="loginForEstimate"><LogIn :size="16" />登录并预测</button><div class="quote-meta"><span>车辆类型</span><b>{{ passengerVehicleTypes.find(vehicle => vehicle.value === passenger.order.vehicleType)?.label }}</b></div><p class="muted-note">实际费用以行程结束后的账单为准。</p></article>
          </section>

          <PassengerPayment v-else-if="activePanel === 'payment'" :authenticated="isLoggedIn" :active="activePanel === 'payment'" @login="openLogin" @activity="recordPassengerActivity" @auth-expired="expirePassengerSession" />

          <section v-else class="activity-layout"><article class="surface auth-surface"><div class="surface-heading"><div><span class="section-kicker">ACCOUNT ACCESS</span><h2>乘客登录</h2></div><UserRound :size="20" /></div><p class="surface-desc">使用手机号和验证码进入乘客工作台。验证码会保存到 Redis，可在 RedisInsight 中查看。</p><div class="field"><span>手机号</span><input v-model="passenger.phone" placeholder="请输入手机号" inputmode="tel" /></div><div class="inline-field"><div class="field"><span>验证码</span><input v-model="passenger.code" placeholder="6 位验证码" inputmode="numeric" /></div><button class="secondary-button code-button" @click="sendPassengerCode">{{ passenger.codeSent ? '重新发送' : '获取验证码' }}</button></div><button class="primary-button full-button" :disabled="busy" @click="loginAsPassenger"><LogIn :size="16" />进入乘客工作台</button><button v-if="isLoggedIn" class="quiet-button" @click="refreshPassengerToken"><RefreshCw :size="14" />刷新令牌</button></article><ActivityFeed :logs="logs" /></section>
        </div>

        <div v-else-if="role === 'driver'" class="role-view">
          <section v-if="activePanel === 'overview'" class="dashboard-grid">
            <article class="hero-panel driver-hero"><div class="hero-copy"><span class="section-kicker">DRIVER OPS / 01</span><h2>今天，<br /><em>准时出发。</em></h2><p>切换营运状态，接收订单并沿着节点完成服务。</p><button class="primary-button" @click="activePanel = 'dispatch'">进入订单操作 <ArrowRight :size="16" /></button></div><div class="driver-orbit"><div class="orbit-ring ring-one" /><div class="orbit-ring ring-two" /><CarFront :size="48" /><span>ONLINE</span></div></article>
            <article class="metric-card"><div class="metric-icon lime"><Activity :size="18" /></div><span>营运状态</span><strong class="metric-text">{{ driver.workStatus === 1 ? '营运中' : '已暂停' }}</strong><p>司机工作状态</p></article>
            <article class="metric-card"><div class="metric-icon orange"><CarFront :size="18" /></div><span>绑定车辆</span><strong class="metric-text">{{ driver.binding?.vehicleNo || '未读取' }}</strong><p>{{ driver.binding?.brand || '等待车辆信息' }}</p></article>
            <article class="status-strip"><div><span class="pulse-dot lime-dot" />司机 API 就绪</div><span>订单端口 8088</span><button class="text-button" @click="loadDriverBinding">刷新车辆 <RefreshCw :size="14" /></button></article>
          </section>
          <section v-else-if="activePanel === 'dispatch'" class="driver-dispatch">
            <div class="driver-dispatch-toolbar"><span><span class="pulse-dot lime-dot" />{{ driver.workStatus === 1 ? '营运中' : '已暂停营运' }}</span><button class="secondary-button" :disabled="busy || !isLoggedIn" @click="updateWorkStatus(driver.workStatus === 1 ? 0 : 1)"><Activity :size="16" />{{ driver.workStatus === 1 ? '暂停营运' : '开始营运' }}</button></div>
            <DriverOrders :authenticated="isLoggedIn" @login="openLogin" @activity="recordDriverOrderActivity" @auth-expired="expireDriverSession" />
          </section>
          <section v-else-if="activePanel === 'account'" class="single-form"><article class="surface auth-surface"><div class="surface-heading"><div><span class="section-kicker">DRIVER ACCESS</span><h2>司机登录</h2></div><LogIn :size="20" /></div><label class="field"><span>司机手机号</span><input v-model="driver.phone" placeholder="请输入已登记手机号" inputmode="tel" /></label><div class="inline-field"><label class="field"><span>验证码</span><input v-model="driver.code" placeholder="6 位验证码" inputmode="numeric" /></label><button class="secondary-button code-button" :disabled="busy" @click="sendDriverCode">{{ driver.codeSent ? '重新发送' : '获取验证码' }}</button></div><button class="primary-button full-button" :disabled="busy" @click="loginAsDriver"><LogIn :size="16" />进入司机工作台</button></article></section>
          <DriverVehicle v-else :key="authVersion" :authenticated="isLoggedIn" @login="openLogin" @activity="recordDriverVehicleActivity" @registered="loadDriverBinding" @expired="expireDriverSession" />
        </div>

        <div v-else class="role-view">
          <section v-if="activePanel === 'overview'" class="dashboard-grid admin-grid"><article class="hero-panel admin-hero"><div class="hero-copy"><span class="section-kicker">FLEET CONTROL / 01</span><h2>让车队，<br /><em>保持清晰。</em></h2><p>集中维护司机档案、车辆档案和绑定关系。</p><div class="hero-stats"><span><b>5</b> 数据库</span><span><b>11</b> 服务节点</span></div></div><div class="admin-graphic"><Users :size="31" /><span class="graphic-line" /><CarFront :size="31" /><span class="graphic-line" /><ShieldCheck :size="31" /></div></article><article class="metric-card"><div class="metric-icon orange"><Users :size="18" /></div><span>司机档案</span><strong>管理</strong><p>新增或更新驾驶员资料</p></article><article class="metric-card"><div class="metric-icon cyan"><CarFront :size="18" /></div><span>车辆档案</span><strong>管理</strong><p>建立车辆基础信息</p></article><article class="status-strip"><div><span class="pulse-dot orange-dot" />运营管理 API 就绪</div><span>后台端口 8087</span><button class="text-button" @click="activePanel = 'driver'">开始维护 <ArrowRight :size="14" /></button></article></section>
          <BossDrivers v-else-if="activePanel === 'drivers'" @add="activePanel = 'driver'" @activity="recordDriverOrderActivity" />
          <section v-else-if="activePanel === 'driver'" class="single-form"><article class="surface form-surface"><div class="surface-heading"><div><span class="section-kicker">DRIVER DIRECTORY / 02</span><h2>新增司机档案</h2></div><button class="secondary-button" @click="activePanel = 'drivers'"><ClipboardList :size="16" />司机列表</button></div><div class="field-grid three"><label class="field"><span>司机姓名</span><input v-model="boss.driver.driverName" placeholder="张师傅" /></label><label class="field"><span>手机号</span><input v-model="boss.driver.driverPhone" placeholder="13800000000" /></label><label class="field"><span>所在城市</span><input v-model="boss.driver.address" placeholder="北京市" /></label><label class="field"><span>性别</span><select v-model="boss.driver.driverGender"><option :value="1">男</option><option :value="2">女</option></select></label><label class="field"><span>司机状态</span><select v-model="boss.driver.state"><option :value="1">启用</option><option :value="0">停用</option></select></label></div><div class="form-actions"><button class="primary-button" @click="submitBoss('driver')"><Plus :size="16" />保存司机档案</button></div></article></section>
          <section v-else-if="activePanel === 'vehicle'" class="single-form"><article class="surface form-surface"><div class="surface-heading"><div><span class="section-kicker">VEHICLE DIRECTORY / 03</span><h2>新增车辆档案</h2></div><CarFront :size="22" /></div><div class="field-grid three"><label class="field"><span>车牌号</span><input v-model="boss.car.vehicleNo" placeholder="京A·12345" /></label><label class="field"><span>品牌</span><input v-model="boss.car.brand" placeholder="比亚迪" /></label><label class="field"><span>车型</span><input v-model="boss.car.model" placeholder="秦 PLUS" /></label><label class="field"><span>所在城市</span><input v-model="boss.car.address" placeholder="北京市" /></label><label class="field"><span>座位数</span><input v-model.number="boss.car.seats" type="number" min="1" /></label><label class="field"><span>车主</span><input v-model="boss.car.ownerName" placeholder="车主姓名" /></label><label class="field"><span>车辆颜色</span><select v-model="boss.car.vehicleColor"><option value="1">白色</option><option value="2">黑色</option></select></label><label class="field"><span>运营状态</span><select v-model="boss.car.state"><option :value="0">启用</option><option :value="1">停用</option></select></label></div><div class="form-actions"><button class="primary-button" @click="submitBoss('vehicle')"><Plus :size="16" />保存车辆档案</button></div></article></section>
          <section v-else class="single-form"><article class="surface form-surface"><div class="surface-heading"><div><span class="section-kicker">RELATIONSHIP / 04</span><h2>司机车辆绑定</h2></div><Settings2 :size="22" /></div><div class="field-grid three"><label class="field"><span>司机 ID</span><input v-model="boss.binding.driverId" placeholder="例如 1" /></label><label class="field"><span>车辆 ID</span><input v-model="boss.binding.carId" placeholder="例如 1" /></label><label class="field"><span>操作</span><select v-model.number="boss.binding.bindState"><option :value="1">建立绑定</option><option :value="0">解除绑定</option></select></label></div><div class="binding-preview"><span class="binding-node"><UserRound :size="17" />司机 {{ boss.binding.driverId || '—' }}</span><ArrowRight :size="17" /><span class="binding-node"><CarFront :size="17" />车辆 {{ boss.binding.carId || '—' }}</span></div><div class="form-actions"><button class="primary-button" @click="submitBoss('binding')"><Check :size="16" />提交关系变更</button></div></article></section>
        </div>

        <footer class="page-footer"><span>FlyTaxi backend services</span><span>Vue 3 · Vite · Spring Boot</span></footer>
      </section>
    </main>
  </div>
</template>

