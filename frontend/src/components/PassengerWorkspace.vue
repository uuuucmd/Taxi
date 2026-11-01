<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  ArrowRight,
  CalendarDays,
  CarFront,
  CheckCircle2,
  Clock3,
  CreditCard,
  History,
  LoaderCircle,
  LocateFixed,
  MapPin,
  Navigation,
  RefreshCw,
  Route,
  ShieldCheck,
  UserRound,
} from 'lucide-vue-next'
import WorkspaceShell from './WorkspaceShell.vue'
import { passengerApi } from '../services/api'

const props = defineProps({ identity: { type: String, required: true } })
const emit = defineEmits(['logout'])

const active = ref('ride')
const busy = ref(false)
const currentLoading = ref(false)
const currentOrder = ref(null)
const estimate = ref(null)
const resolvedTrip = ref(null)
const feedback = reactive({ type: '', text: '' })

const navItems = [
  { id: 'ride', label: '立即叫车', icon: Route },
  { id: 'trips', label: '我的行程', icon: History },
  { id: 'account', label: '账户与安全', icon: UserRound },
]

const trip = reactive({
  origin: '北京市朝阳区望京 SOHO',
  destination: '北京首都国际机场 T3',
  vehicleType: '1',
})

const vehicleTypes = [
  { id: '1', name: '快车', description: '经济出行' },
  { id: '2', name: '舒适型', description: '宽敞座舱' },
  { id: '3', name: '商务型', description: '品质服务' },
]

const currentStatus = computed(() => currentOrder.value?.orderStatus ?? currentOrder.value?.status ?? '待命中')
const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 11) return '早上好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

function responseData(result) {
  return result?.data ?? result
}

function notify(text, type = 'success') {
  feedback.text = text
  feedback.type = type
  window.clearTimeout(notify.timer)
  notify.timer = window.setTimeout(() => {
    feedback.text = ''
    feedback.type = ''
  }, 4200)
}

function routeLabel(order) {
  if (!order) return ''
  return `${order.departure || order.depAddress || '当前位置'} → ${order.destination || order.destAddress || '目的地'}`
}

function getDeviceCode() {
  const key = 'flytaxi.passenger.deviceCode'
  let value = localStorage.getItem(key)
  if (!value) {
    value = `web-${crypto.randomUUID?.() || `${Date.now()}-${Math.random().toString(16).slice(2)}`}`
    localStorage.setItem(key, value)
  }
  return value
}

async function loadCurrentOrder() {
  currentLoading.value = true
  try {
    const data = responseData(await passengerApi.currentOrder())
    currentOrder.value = Array.isArray(data) ? data[0] || null : data?.order || data || null
  } catch (error) {
    if (error.status === 401) {
      emit('logout')
      return
    }
    currentOrder.value = null
  } finally {
    currentLoading.value = false
  }
}

async function geocode(address, label) {
  const data = responseData(await passengerApi.geocode(address))
  if (!data?.longitude || !data?.latitude || !data?.cityCode || !data?.districtCode) {
    throw new Error(`${label}地址无法解析，请补充城市和详细地址`)
  }
  return data
}

async function resolveTrip() {
  const originText = trip.origin.trim()
  const destinationText = trip.destination.trim()
  if (!originText || !destinationText) throw new Error('请填写出发地和目的地')
  if (originText === destinationText) throw new Error('出发地和目的地不能相同')

  const origin = await geocode(originText, '出发地')
  const destination = await geocode(destinationText, '目的地')
  const resolved = {
    origin,
    destination,
    priceRequest: {
      depLongitude: origin.longitude,
      depLatitude: origin.latitude,
      destLongitude: destination.longitude,
      destLatitude: destination.latitude,
      cityCode: origin.cityCode,
      vehicleType: trip.vehicleType,
    },
  }
  resolvedTrip.value = resolved
  return resolved
}

async function estimatePrice() {
  busy.value = true
  feedback.text = ''
  estimate.value = null
  try {
    const resolved = await resolveTrip()
    const data = responseData(await passengerApi.predictPrice(resolved.priceRequest))
    if (typeof data?.price !== 'number') throw new Error('未获取到有效预估价')
    estimate.value = data
    notify('价格预估完成')
  } catch (error) {
    if (error.status === 401) return emit('logout')
    notify(error.message || '价格预估失败', 'error')
  } finally {
    busy.value = false
  }
}

async function createOrder() {
  if (!estimate.value) {
    notify('请先完成价格预估', 'error')
    return
  }
  busy.value = true
  try {
    const resolved = resolvedTrip.value || await resolveTrip()
    const orderTime = new Date().toISOString().slice(0, 19).replace('T', ' ')
    const payload = {
      departure: trip.origin.trim(),
      destination: trip.destination.trim(),
      address: resolved.origin.districtCode,
      depLongitude: resolved.origin.longitude,
      depLatitude: resolved.origin.latitude,
      destLongitude: resolved.destination.longitude,
      destLatitude: resolved.destination.latitude,
      vehicleType: trip.vehicleType,
      fareType: estimate.value.fareType || `${resolved.origin.cityCode}$${trip.vehicleType}`,
      fareVersion: estimate.value.fareVersion,
      deviceCode: getDeviceCode(),
      encrypt: 1,
      passengerPhone: props.identity,
      orderTime,
      idempotencyKey: crypto.randomUUID?.() || `${Date.now()}-${Math.random().toString(16).slice(2)}`,
    }
    await passengerApi.addOrder(payload)
    notify('行程已创建，正在为你寻找司机')
    estimate.value = null
    await loadCurrentOrder()
  } catch (error) {
    if (error.status === 401) return emit('logout')
    notify(error.message || '创建行程失败', 'error')
  } finally {
    busy.value = false
  }
}

watch(
  () => [trip.origin, trip.destination, trip.vehicleType],
  () => {
    estimate.value = null
    resolvedTrip.value = null
  },
)

onMounted(loadCurrentOrder)
</script>

<template>
  <WorkspaceShell
    role="passenger"
    :identity="identity"
    :items="navItems"
    :active="active"
    @update:active="active = $event"
    @logout="emit('logout')"
  >
    <div v-if="feedback.text" class="workspace-toast" :class="feedback.type">{{ feedback.text }}</div>

    <section class="page-intro">
      <div>
        <p class="page-kicker"><CalendarDays :size="14" /> {{ new Date().toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' }) }}</p>
        <h1>{{ greeting }}，准备去哪里？</h1>
        <p class="page-description">输入行程信息，比较预估价格，然后一键叫车。</p>
      </div>
      <div class="service-state"><span></span> 乘客服务在线</div>
    </section>

    <template v-if="active === 'ride'">
      <div class="ride-layout">
        <section class="ride-panel">
          <header class="panel-heading">
            <div>
              <span class="panel-kicker">CREATE A RIDE</span>
              <h2>规划本次行程</h2>
            </div>
            <span class="panel-icon"><Navigation :size="20" /></span>
          </header>

          <div class="route-form">
            <label class="location-field">
              <span class="location-marker start"><LocateFixed :size="17" /></span>
              <span class="location-copy"><small>出发地</small><input v-model="trip.origin" placeholder="输入上车地点" /></span>
            </label>
            <span class="route-line"></span>
            <label class="location-field">
              <span class="location-marker end"><MapPin :size="17" /></span>
              <span class="location-copy"><small>目的地</small><input v-model="trip.destination" placeholder="输入下车地点" /></span>
            </label>
          </div>

          <div class="vehicle-section">
            <div class="section-label"><CarFront :size="15" /><span>选择车型</span></div>
            <div class="vehicle-picker">
              <button
                v-for="vehicle in vehicleTypes"
                :key="vehicle.id"
                type="button"
                :class="{ active: trip.vehicleType === vehicle.id }"
                @click="trip.vehicleType = vehicle.id"
              >
                <CarFront :size="19" />
                <span><strong>{{ vehicle.name }}</strong><small>{{ vehicle.description }}</small></span>
              </button>
            </div>
          </div>

          <div v-if="estimate" class="fare-summary">
            <div><span>预估车费</span><strong>¥ {{ Number(estimate.price).toFixed(2) }}</strong></div>
            <span class="fare-badge"><ShieldCheck :size="14" /> 价格已确认</span>
          </div>

          <div class="ride-actions">
            <button class="secondary-action" type="button" :disabled="busy" @click="estimatePrice">
              <LoaderCircle v-if="busy" class="spin" :size="17" />
              <RefreshCw v-else :size="17" />
              <span>{{ estimate ? '重新估价' : '预估价格' }}</span>
            </button>
            <button class="primary-action" type="button" :disabled="busy || !estimate" @click="createOrder">
              <span>立即叫车</span>
              <ArrowRight :size="18" />
            </button>
          </div>
        </section>

        <aside class="trip-side">
          <section class="current-trip-panel">
            <header>
              <div><span class="panel-kicker">CURRENT TRIP</span><h2>当前行程</h2></div>
              <button class="icon-action" type="button" title="刷新当前行程" @click="loadCurrentOrder">
                <LoaderCircle v-if="currentLoading" class="spin" :size="16" />
                <RefreshCw v-else :size="16" />
              </button>
            </header>
            <template v-if="currentOrder">
              <div class="trip-status"><span></span>{{ currentStatus }}</div>
              <p class="trip-route">{{ routeLabel(currentOrder) }}</p>
              <div class="trip-meta">
                <span><Clock3 :size="14" />行程进行中</span>
                <span><CarFront :size="14" />司机匹配中</span>
              </div>
            </template>
            <div v-else class="empty-trip">
              <span class="empty-icon"><CheckCircle2 :size="22" /></span>
              <strong>暂无进行中的行程</strong>
              <p>完成行程规划后，这里会显示实时状态。</p>
            </div>
          </section>

          <section class="mini-summary">
            <div><CreditCard :size="18" /><span>支付方式</span><strong>支付宝沙箱</strong></div>
            <div><ShieldCheck :size="18" /><span>账户状态</span><strong>安全登录</strong></div>
          </section>
        </aside>
      </div>
    </template>

    <template v-else-if="active === 'trips'">
      <section class="content-panel">
        <header class="panel-heading">
          <div><span class="panel-kicker">TRIP HISTORY</span><h2>我的行程</h2></div>
          <button class="secondary-action compact" type="button" @click="loadCurrentOrder"><RefreshCw :size="15" />刷新</button>
        </header>
        <div v-if="currentOrder" class="trip-detail">
          <div class="trip-detail-route"><span class="route-node"></span><div><small>当前行程</small><strong>{{ routeLabel(currentOrder) }}</strong></div><span class="status-tag">{{ currentStatus }}</span></div>
          <div class="trip-detail-grid">
            <div><span>订单编号</span><strong>{{ currentOrder.orderId || currentOrder.id || '待生成' }}</strong></div>
            <div><span>车型</span><strong>{{ vehicleTypes.find((item) => item.id === String(currentOrder.vehicleType))?.name || '标准车型' }}</strong></div>
            <div><span>预估费用</span><strong>{{ currentOrder.price ? `¥ ${currentOrder.price}` : '以最终账单为准' }}</strong></div>
          </div>
        </div>
        <div v-else class="large-empty"><History :size="34" /><h3>还没有进行中的行程</h3><p>新的行程会在这里持续更新状态。</p><button class="primary-action compact" type="button" @click="active = 'ride'"><Navigation :size="16" />发起行程</button></div>
      </section>
    </template>

    <template v-else>
      <section class="account-grid">
        <article class="profile-panel">
          <span class="profile-avatar">{{ identity.slice(-4) }}</span>
          <div><span class="panel-kicker">PASSENGER ACCOUNT</span><h2>{{ identity }}</h2><p>乘客账户 · 已验证</p></div>
          <span class="verified-badge"><ShieldCheck :size="15" /> 已认证</span>
        </article>
        <article class="content-panel compact-panel">
          <h3>账户信息</h3>
          <div class="account-row"><span>登录角色</span><strong>客户端乘客</strong></div>
          <div class="account-row"><span>手机号码</span><strong>{{ identity }}</strong></div>
          <div class="account-row"><span>会话状态</span><strong class="safe-text">安全</strong></div>
        </article>
      </section>
    </template>
  </WorkspaceShell>
</template>

<style scoped>
.workspace-toast { position: fixed; top: 82px; right: 28px; z-index: 80; max-width: 360px; padding: 12px 15px; color: #fff; background: #23684e; border-radius: 5px; box-shadow: 0 12px 30px rgba(20, 35, 28, .18); font-size: 11px; }
.workspace-toast.error { background: #a63d34; }
.page-intro, .panel-heading, .service-state, .page-kicker, .section-label, .panel-icon, .ride-actions, .secondary-action, .primary-action, .trip-status, .trip-meta, .mini-summary div, .verified-badge, .large-empty { display: flex; align-items: center; }
.page-intro { justify-content: space-between; gap: 24px; margin-bottom: 30px; }
.page-kicker { gap: 7px; margin: 0 0 12px; color: #748079; font-size: 10px; }
.page-intro h1 { margin: 0; font-size: clamp(30px, 4vw, 46px); line-height: 1.1; font-weight: 650; letter-spacing: 0; }
.page-description { margin: 12px 0 0; color: #7f8882; font-size: 12px; }
.service-state { gap: 8px; flex: none; padding: 9px 12px; color: #446553; background: #e9f1eb; border: 1px solid #cfdfd3; border-radius: 5px; font-size: 10px; }
.service-state span { width: 7px; height: 7px; background: #4b9b68; border-radius: 50%; }
.ride-layout { display: grid; grid-template-columns: minmax(0, 1.65fr) minmax(290px, .75fr); gap: 22px; }
.ride-panel, .current-trip-panel, .content-panel, .profile-panel { background: #fffdf8; border: 1px solid #ddd8cc; border-radius: 7px; }
.ride-panel { padding: clamp(22px, 3vw, 34px); }
.panel-heading { justify-content: space-between; gap: 18px; }
.panel-kicker { display: block; margin-bottom: 7px; color: #929890; font-size: 8px; font-weight: 700; letter-spacing: .15em; }
.panel-heading h2 { margin: 0; font-size: 21px; line-height: 1.2; font-weight: 650; }
.panel-icon { width: 40px; height: 40px; justify-content: center; color: #0f8b7f; background: #e7f2ef; border-radius: 5px; }
.route-form { margin-top: 30px; position: relative; display: grid; gap: 12px; }
.location-field { min-height: 62px; display: flex; align-items: center; gap: 12px; padding: 9px 13px; background: #f7f5ef; border: 1px solid #dfdbd0; border-radius: 6px; }
.location-field:focus-within { border-color: #0f8b7f; box-shadow: 0 0 0 3px rgba(15, 139, 127, .08); }
.location-marker { width: 31px; height: 31px; flex: none; display: grid; place-items: center; border-radius: 50%; }
.location-marker.start { color: #0f8b7f; background: #dff0ec; }
.location-marker.end { color: #c8463a; background: #f6e5e2; }
.location-copy { min-width: 0; flex: 1; }
.location-copy small, .location-copy input { display: block; width: 100%; }
.location-copy small { margin-bottom: 4px; color: #8d938e; font-size: 9px; }
.location-copy input { height: 24px; padding: 0; border: 0; outline: 0; color: #202a26; background: transparent; font-size: 13px; font-weight: 600; }
.route-line { width: 1px; height: 8px; position: absolute; left: 29px; top: 67px; background: #c9cec9; }
.vehicle-section { margin-top: 28px; }
.section-label { gap: 7px; margin-bottom: 11px; color: #646d68; font-size: 10px; font-weight: 650; }
.vehicle-picker { display: grid; grid-template-columns: repeat(3, 1fr); gap: 9px; }
.vehicle-picker button { min-height: 72px; display: flex; align-items: center; gap: 9px; padding: 11px; border: 1px solid #dedad0; border-radius: 5px; color: #707873; background: #faf8f3; text-align: left; }
.vehicle-picker button:hover { border-color: #b7c7c0; }
.vehicle-picker button.active { color: #173d36; border-color: #0f8b7f; background: #eaf4f1; }
.vehicle-picker span { min-width: 0; }
.vehicle-picker strong, .vehicle-picker small { display: block; }
.vehicle-picker strong { font-size: 11px; font-weight: 650; }
.vehicle-picker small { margin-top: 4px; color: #8d948f; font-size: 9px; }
.fare-summary { display: flex; align-items: center; justify-content: space-between; gap: 18px; margin-top: 23px; padding: 17px 18px; color: #284239; background: #edf4ef; border: 1px solid #d1e2d6; border-radius: 5px; }
.fare-summary div span, .fare-summary div strong { display: block; }
.fare-summary div span { color: #6d7d74; font-size: 9px; }
.fare-summary div strong { margin-top: 4px; font-size: 25px; font-weight: 650; }
.fare-badge { display: flex; align-items: center; gap: 6px; color: #4f725e; font-size: 9px; }
.ride-actions { justify-content: flex-end; gap: 10px; margin-top: 26px; }
.primary-action, .secondary-action { min-height: 45px; justify-content: center; gap: 8px; padding: 0 17px; border-radius: 5px; font-size: 11px; font-weight: 650; }
.primary-action { color: #fff; background: #1b2823; border: 1px solid #1b2823; }
.primary-action:hover:not(:disabled) { background: #2b3c34; }
.secondary-action { color: #434c47; background: #f2efe7; border: 1px solid #d6d1c5; }
.secondary-action:hover:not(:disabled) { background: #e9e5dc; }
.primary-action.compact, .secondary-action.compact { min-height: 38px; padding: 0 12px; }
.trip-side { display: grid; align-content: start; gap: 16px; }
.current-trip-panel { padding: 22px; }
.current-trip-panel header { display: flex; justify-content: space-between; gap: 12px; }
.current-trip-panel h2 { margin: 0; font-size: 17px; font-weight: 650; }
.icon-action { width: 33px; height: 33px; display: grid; place-items: center; border: 1px solid #d9d5cb; border-radius: 4px; color: #636d67; background: #f5f2eb; }
.icon-action:hover { color: #0f8b7f; border-color: #9fbbb3; }
.trip-status { gap: 7px; margin-top: 22px; color: #27634d; font-size: 10px; font-weight: 650; }
.trip-status span { width: 7px; height: 7px; background: #4b9b68; border-radius: 50%; }
.trip-route { margin: 12px 0 17px; color: #333d38; font-size: 12px; line-height: 1.65; overflow-wrap: anywhere; }
.trip-meta { justify-content: space-between; gap: 8px; padding-top: 14px; border-top: 1px solid #e5e0d6; color: #7c847f; font-size: 9px; }
.trip-meta span { display: flex; align-items: center; gap: 5px; }
.empty-trip { min-height: 210px; display: flex; flex-direction: column; align-items: center; justify-content: center; text-align: center; }
.empty-icon { width: 45px; height: 45px; display: grid; place-items: center; color: #0f8b7f; background: #e8f3f0; border-radius: 50%; }
.empty-trip strong { margin-top: 13px; font-size: 12px; }
.empty-trip p { max-width: 180px; margin: 7px 0 0; color: #8b928d; font-size: 10px; line-height: 1.6; }
.mini-summary { display: grid; gap: 1px; overflow: hidden; border: 1px solid #ddd8cc; border-radius: 6px; background: #ddd8cc; }
.mini-summary div { gap: 9px; min-height: 55px; padding: 0 15px; background: #fffdf8; }
.mini-summary svg { color: #717a74; }
.mini-summary span { flex: 1; color: #777f7a; font-size: 9px; }
.mini-summary strong { color: #303a35; font-size: 10px; font-weight: 650; }
.content-panel { padding: clamp(21px, 3vw, 32px); }
.trip-detail { margin-top: 26px; border: 1px solid #e0dcd2; border-radius: 6px; overflow: hidden; }
.trip-detail-route { display: flex; align-items: center; gap: 12px; padding: 19px; background: #f8f6f0; }
.route-node { width: 10px; height: 10px; flex: none; background: #0f8b7f; border-radius: 50%; box-shadow: 0 0 0 5px #e2efeb; }
.trip-detail-route div { min-width: 0; flex: 1; }
.trip-detail-route small, .trip-detail-route strong { display: block; }
.trip-detail-route small { color: #8a918c; font-size: 9px; }
.trip-detail-route strong { margin-top: 4px; font-size: 12px; overflow-wrap: anywhere; }
.status-tag { padding: 5px 8px; color: #34604c; background: #e4efe8; border-radius: 4px; font-size: 9px; }
.trip-detail-grid { display: grid; grid-template-columns: repeat(3, 1fr); }
.trip-detail-grid div { padding: 17px 19px; border-top: 1px solid #e6e1d7; }
.trip-detail-grid div + div { border-left: 1px solid #e6e1d7; }
.trip-detail-grid span, .trip-detail-grid strong { display: block; }
.trip-detail-grid span { color: #8b928d; font-size: 9px; }
.trip-detail-grid strong { margin-top: 6px; color: #303a35; font-size: 11px; font-weight: 650; overflow-wrap: anywhere; }
.large-empty { min-height: 420px; flex-direction: column; justify-content: center; text-align: center; color: #9aa19c; }
.large-empty h3 { margin: 15px 0 0; color: #303a35; font-size: 17px; }
.large-empty p { margin: 8px 0 20px; color: #858d88; font-size: 11px; }
.account-grid { display: grid; grid-template-columns: minmax(0, 1.2fr) minmax(280px, .8fr); gap: 18px; }
.profile-panel { display: flex; align-items: center; gap: 18px; padding: 28px; }
.profile-avatar { width: 64px; height: 64px; display: grid; place-items: center; flex: none; color: #fff; background: #17201d; border-radius: 7px; font-size: 16px; font-weight: 700; }
.profile-panel > div { flex: 1; min-width: 0; }
.profile-panel h2 { margin: 0; font-size: 20px; font-weight: 650; }
.profile-panel p { margin: 6px 0 0; color: #858d88; font-size: 10px; }
.verified-badge { gap: 5px; padding: 7px 9px; color: #347054; background: #e4f0e8; border-radius: 4px; font-size: 9px; }
.compact-panel h3 { margin: 0 0 18px; font-size: 15px; }
.account-row { display: flex; justify-content: space-between; gap: 15px; padding: 14px 0; border-top: 1px solid #e6e1d7; font-size: 10px; }
.account-row span { color: #858d88; }
.account-row strong { color: #35403a; font-weight: 650; }
.safe-text { color: #397356 !important; }
.spin { animation: spin .8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

@media (max-width: 1080px) {
  .ride-layout { grid-template-columns: 1fr; }
  .trip-side { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}

@media (max-width: 700px) {
  .page-intro { align-items: flex-start; flex-direction: column; }
  .service-state { align-self: flex-start; }
  .vehicle-picker { grid-template-columns: 1fr; }
  .vehicle-picker button { min-height: 60px; }
  .trip-side, .account-grid { grid-template-columns: 1fr; }
  .trip-detail-grid { grid-template-columns: 1fr; }
  .trip-detail-grid div + div { border-left: 0; }
  .profile-panel { align-items: flex-start; flex-wrap: wrap; }
  .verified-badge { margin-left: auto; }
}

@media (max-width: 480px) {
  .ride-panel, .current-trip-panel, .content-panel, .profile-panel { padding: 18px 15px; }
  .ride-actions { flex-direction: column-reverse; }
  .ride-actions button { width: 100%; }
  .fare-summary { align-items: flex-start; flex-direction: column; }
  .workspace-toast { top: 72px; right: 12px; left: 12px; max-width: none; }
}
</style>
