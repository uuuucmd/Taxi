<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import {
  ArrowRight,
  BriefcaseBusiness,
  CarFront,
  CheckCircle2,
  ClipboardList,
  Clock3,
  Flag,
  Gauge,
  LoaderCircle,
  MapPin,
  Navigation,
  Phone,
  RefreshCw,
  Route,
  ShieldCheck,
  UserRound,
  WalletCards,
  X,
} from 'lucide-vue-next'
import WorkspaceShell from './WorkspaceShell.vue'
import DriverVehicle from './DriverVehicle.vue'
import { driverApi } from '../services/api'

const props = defineProps({ identity: { type: String, required: true } })
const emit = defineEmits(['logout'])

const active = ref('workbench')
const loading = ref(false)
const orderLoading = ref(false)
const statusLoading = ref(false)
const detailLoading = ref(false)
const orderActionLoading = ref(false)
const acceptingId = ref('')
const workStatus = ref(0)
const binding = ref(null)
const vehicle = ref(null)
const orders = ref([])
const currentOrder = ref(null)
const notice = ref('')
const noticeType = ref('success')
let detailTimer

const orderStatuses = {
  0: '已失效', 1: '待接单', 2: '已接单', 3: '前往接驾', 4: '到达上车点',
  5: '行程中', 6: '待支付', 7: '收款中', 8: '已完成', 9: '已取消',
  10: '结算处理中', 11: '结算异常',
}
const journeySteps = [
  { label: '已接单', statuses: [2] },
  { label: '前往接驾', statuses: [3] },
  { label: '到达上车点', statuses: [4] },
  { label: '行程中', statuses: [5] },
  { label: '待结算', statuses: [6, 7, 10, 11] },
  { label: '已完成', statuses: [8] },
]
const orderActions = [
  { status: 2, path: 'to-pick-up-passenger', label: '前往接驾', hint: '开始驶向乘客上车点，抵达后更新状态。' },
  { status: 3, path: 'arrived-departure', label: '已到达出发点', hint: '确认车辆已抵达乘客上车点。' },
  { status: 4, path: 'pick-up-passenger', label: '乘客已上车', hint: '确认乘客上车，开始本次行程。' },
  { status: 5, path: 'passenger-getoff', label: '乘客已下车', hint: '确认乘客到达目的地并结束行程。' },
]
const ongoingStatuses = [2, 3, 4, 5, 6, 7, 10, 11]
const cancellableStatuses = [2, 3, 4]
const vehicleTypes = { '1': '经济型', '2': '舒适型', '3': '商务型' }

const navItems = [
  { id: 'workbench', label: '司机工作台', icon: Gauge },
  { id: 'current', label: '当前订单', icon: Navigation },
  { id: 'orders', label: '订单大厅', icon: ClipboardList },
  { id: 'vehicle', label: '车辆与资质', icon: CarFront },
  { id: 'account', label: '司机账户', icon: UserRound },
]

const online = computed(() => Number(workStatus.value) === 1)
const driverId = computed(() => binding.value?.driverId || vehicle.value?.driverId || '')
const plateNumber = computed(() => vehicle.value?.vehicleNo || vehicle.value?.plateNumber || binding.value?.vehicleNo || '未绑定车辆')
const availableOrders = computed(() => online.value
  ? orders.value.filter((order) => normalizedStatus(order) === 1 && !order.driverPhone)
  : [])
const orderCount = computed(() => availableOrders.value.length)
const activeOrder = computed(() => {
  const candidates = [currentOrder.value, ...orders.value]
  return candidates.find((order) => order
    && order.driverPhone === props.identity
    && ongoingStatuses.includes(normalizedStatus(order))) || null
})
const currentStepIndex = computed(() => {
  const status = normalizedStatus(currentOrder.value)
  const index = journeySteps.findIndex((step) => step.statuses.includes(status))
  return index < 0 ? 0 : index
})
const currentAction = computed(() => orderActions.find((action) => action.status === normalizedStatus(currentOrder.value)) || null)
const currentStatusLabel = computed(() => statusLabel(currentOrder.value))
const canCancelOrder = computed(() => cancellableStatuses.includes(normalizedStatus(currentOrder.value)))

function responseData(result) {
  return result?.data ?? result
}

function notify(text, type = 'success') {
  notice.value = text
  noticeType.value = type
  window.clearTimeout(notify.timer)
  notify.timer = window.setTimeout(() => (notice.value = ''), 4200)
}

function extractOrders(data) {
  if (Array.isArray(data)) return data
  if (Array.isArray(data?.orders)) return data.orders
  if (Array.isArray(data?.records)) return data.records
  if (Array.isArray(data?.list)) return data.list
  return []
}

function orderId(order) {
  return order?.orderId || order?.id || '--'
}

function orderRoute(order) {
  return `${order?.departure || order?.depAddress || '待定出发地'} → ${order?.destination || order?.destAddress || '待定目的地'}`
}

function orderPrice(order) {
  const value = order?.price ?? order?.estimatePrice ?? order?.actualPrice
  const number = Number(value)
  return value === undefined || value === null || !Number.isFinite(number) ? '待计价' : `¥ ${number.toFixed(2)}`
}

function normalizedStatus(order) {
  return Number(order?.orderStatus ?? order?.status ?? 0)
}

function statusLabel(order) {
  return orderStatuses[normalizedStatus(order)] || '状态未知'
}

function formatDateTime(value) {
  if (!value) return '待同步'
  const date = new Date(String(value).replace(' ', 'T'))
  if (Number.isNaN(date.getTime())) return String(value)
  return date.toLocaleString('zh-CN', {
    month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false,
  })
}

function formatDistance(order) {
  const meters = Number(order?.driveMile)
  if (!Number.isFinite(meters) || meters <= 0) return '--'
  return meters >= 1000 ? `${(meters / 1000).toFixed(1)} km` : `${Math.round(meters)} m`
}

function formatDuration(order) {
  const seconds = Number(order?.driveTime)
  if (!Number.isFinite(seconds) || seconds <= 0) return '--'
  const minutes = Math.max(1, Math.round(seconds / 60))
  return minutes < 60 ? `${minutes} 分钟` : `${Math.floor(minutes / 60)} 小时 ${minutes % 60} 分钟`
}

function vehicleTypeLabel(value) {
  return vehicleTypes[String(value)] || '待同步'
}

function paymentLabel(order) {
  const status = normalizedStatus(order)
  if (status === 8) return '已支付'
  if ([6, 7, 10, 11].includes(status)) return '待结算'
  if (status === 9) return '已取消'
  return '行程结束后结算'
}

function actionHint(order) {
  switch (normalizedStatus(order)) {
    case 6: return '行程已结束，正在等待乘客完成支付。'
    case 7: return '收款已发起，请等待乘客支付结果。'
    case 8: return '订单已完成，可以继续接单。'
    case 9: return '订单已取消，可返回订单大厅继续接单。'
    case 10: return '订单正在结算处理中，请稍候刷新。'
    case 11: return '订单结算异常，请联系平台管理员。'
    default: return '请根据实际行程进度更新订单状态。'
  }
}

function handleRequestError(error, fallback) {
  if (error.status === 401) {
    emit('logout')
    return
  }
  notify(error.message || fallback, 'error')
}

function handleVehicleActivity(activity) {
  if (!activity?.label) return
  const failed = activity.status === 'error'
  notify(failed ? `${activity.label}：${activity.detail || '请稍后重试'}` : `${activity.label}成功`, failed ? 'error' : 'success')
}

async function loadBinding() {
  try {
    const data = responseData(await driverApi.binding())
    binding.value = data?.carId ? data : null
  } catch (error) {
    handleRequestError(error, '车辆绑定信息读取失败')
  }
}

async function loadVehicle() {
  try {
    vehicle.value = responseData(await driverApi.vehicle())
  } catch (error) {
    if (error.status === 401) handleRequestError(error, '')
  }
}

async function loadStatus() {
  if (!driverId.value) return
  try {
    const data = responseData(await driverApi.status(driverId.value))
    workStatus.value = Number(data?.workStatus ?? 0)
  } catch (error) {
    if (error.status === 401) handleRequestError(error, '')
  }
}

async function loadOrders() {
  orderLoading.value = true
  try {
    const list = extractOrders(responseData(await driverApi.orders()))
    orders.value = list
    if (currentOrder.value) {
      const fresh = list.find((order) => String(orderId(order)) === String(orderId(currentOrder.value)))
      if (fresh) currentOrder.value = fresh
    }
  } catch (error) {
    handleRequestError(error, '订单列表读取失败')
  } finally {
    orderLoading.value = false
  }
}

async function loadOrderDetail(id, { silent = false } = {}) {
  if (!id || id === '--') return null
  if (!silent) detailLoading.value = true
  try {
    const data = responseData(await driverApi.orderDetail(id))
    if (data) currentOrder.value = data
    return data
  } catch (error) {
    if (!silent) handleRequestError(error, '订单详情读取失败')
    else if (error.status === 401) handleRequestError(error, '')
    return null
  } finally {
    if (!silent) detailLoading.value = false
  }
}

async function openOrderDetail(order) {
  if (!order) return
  currentOrder.value = order
  active.value = 'current'
  await loadOrderDetail(orderId(order))
}

async function refreshWorkspace() {
  loading.value = true
  await Promise.allSettled([loadBinding(), loadVehicle()])
  await loadStatus()
  await loadOrders()
  if (!currentOrder.value) {
    const ongoing = orders.value.find((order) => order.driverPhone === props.identity
      && ongoingStatuses.includes(normalizedStatus(order)))
    if (ongoing) {
      currentOrder.value = ongoing
      await loadOrderDetail(orderId(ongoing), { silent: true })
    }
  }
  loading.value = false
}

async function setWorkStatus(nextStatus) {
  if (!driverId.value) {
    notify('当前账号尚未锁定车辆，请先到“车辆与资质”输入车辆信息', 'error')
    return
  }
  statusLoading.value = true
  try {
    await driverApi.workStatus({ driverId: driverId.value, workStatus: nextStatus })
    workStatus.value = nextStatus
    notify(nextStatus === 1 ? '已开始接单' : '已暂停接单')
    if (nextStatus === 1) await loadOrders()
  } catch (error) {
    handleRequestError(error, '营运状态更新失败')
  } finally {
    statusLoading.value = false
  }
}

async function acceptOrder(order) {
  const id = orderId(order)
  if (!id || id === '--' || acceptingId.value) return
  acceptingId.value = String(id)
  try {
    await driverApi.acceptOrder(id)
    notify(`订单 ${id} 已接单`)
    currentOrder.value = { ...order, driverPhone: props.identity, orderStatus: 2 }
    active.value = 'current'
    await Promise.all([loadOrderDetail(id, { silent: true }), loadOrders()])
  } catch (error) {
    handleRequestError(error, '接单失败')
  } finally {
    acceptingId.value = ''
  }
}

async function advanceCurrentOrder() {
  const action = currentAction.value
  const order = currentOrder.value
  if (!action || !order || orderActionLoading.value) return
  orderActionLoading.value = true
  try {
    await driverApi.advanceOrder(action.path, { orderId: orderId(order) })
    notify(`${action.label}成功`)
    await Promise.all([loadOrderDetail(orderId(order), { silent: true }), loadOrders()])
  } catch (error) {
    handleRequestError(error, '订单状态更新失败')
  } finally {
    orderActionLoading.value = false
  }
}

async function cancelCurrentOrder() {
  const order = currentOrder.value
  if (!order || !canCancelOrder.value || orderActionLoading.value) return
  if (!window.confirm('确认取消当前订单？')) return
  orderActionLoading.value = true
  try {
    await driverApi.cancelOrder(orderId(order))
    notify('订单已取消')
    currentOrder.value = null
    active.value = 'workbench'
    await loadOrders()
  } catch (error) {
    handleRequestError(error, '取消订单失败')
  } finally {
    orderActionLoading.value = false
  }
}

onMounted(async () => {
  await refreshWorkspace()
  detailTimer = window.setInterval(() => {
    if (active.value === 'current' && currentOrder.value) {
      loadOrderDetail(orderId(currentOrder.value), { silent: true })
    }
  }, 8000)
})

onBeforeUnmount(() => window.clearInterval(detailTimer))
</script>

<template>
  <WorkspaceShell
    role="driver"
    :identity="identity"
    :items="navItems"
    :active="active"
    @update:active="active = $event"
    @logout="emit('logout')"
  >
    <div v-if="notice" class="driver-notice" :class="noticeType">{{ notice }}</div>

    <section class="driver-intro">
      <div>
        <p class="intro-kicker"><BriefcaseBusiness :size="14" /> DRIVER OPERATIONS</p>
        <h1>{{ online ? '接单中，请注意路面安全。' : '今天准备好出车了吗？' }}</h1>
        <p>车辆、订单与营运状态都集中在司机工作台。</p>
      </div>
      <button
        class="duty-toggle"
        :class="{ online }"
        type="button"
        :disabled="statusLoading"
        @click="setWorkStatus(online ? 0 : 1)"
      >
        <LoaderCircle v-if="statusLoading" class="spin" :size="18" />
        <span v-else class="duty-dot"></span>
        <span>{{ online ? '收车休息' : '开始接单' }}</span>
      </button>
    </section>

    <template v-if="active === 'workbench'">
      <article v-if="activeOrder" class="active-trip-banner">
        <span class="active-trip-icon"><Navigation :size="19" /></span>
        <div>
          <span class="section-kicker">CURRENT ASSIGNMENT</span>
          <strong>{{ orderRoute(activeOrder) }}</strong>
          <p>订单 {{ orderId(activeOrder) }} · {{ statusLabel(activeOrder) }}</p>
        </div>
        <button type="button" class="trip-banner-action" @click="openOrderDetail(activeOrder)">
          查看订单详情 <ArrowRight :size="15" />
        </button>
      </article>
      <div class="driver-grid">
        <section class="duty-panel" :class="{ online }">
          <div class="duty-panel-head">
            <span class="duty-icon"><Gauge :size="25" /></span>
            <span class="duty-status">{{ online ? '营运中' : '已收车' }}</span>
          </div>
          <strong>{{ online ? '正在接收附近订单' : '当前不接收新订单' }}</strong>
          <p>{{ online ? '保持在线，系统会根据车辆位置推送可用行程。' : '点击右上角开始接单，即可进入订单大厅。' }}</p>
          <div class="duty-metrics">
            <div><span>可用订单</span><strong>{{ orderCount }}</strong></div>
            <div><span>车辆状态</span><strong>{{ binding ? '已绑定' : '待绑定' }}</strong></div>
            <div><span>司机编号</span><strong>{{ driverId || '--' }}</strong></div>
          </div>
        </section>

        <section class="driver-side-panel">
          <header><div><span class="section-kicker">VEHICLE SNAPSHOT</span><h2>当前车辆</h2></div><CarFront :size="20" /></header>
          <div class="vehicle-snapshot">
            <span class="plate-box">{{ plateNumber }}</span>
            <strong>{{ vehicle?.brand || '车辆品牌待同步' }} {{ vehicle?.model || '' }}</strong>
            <p>{{ vehicle?.vehicleColor || '颜色待同步' }} · {{ vehicle?.seats || '--' }} 座</p>
          </div>
          <button class="quiet-action" type="button" @click="active = 'vehicle'">{{ binding ? '查看车辆与资质' : '输入车辆信息并锁定' }}</button>
        </section>
      </div>

      <section class="orders-panel">
        <header class="orders-heading">
          <div><span class="section-kicker">DISPATCH QUEUE</span><h2>附近订单</h2></div>
          <button class="icon-action" type="button" title="刷新订单" :disabled="orderLoading" @click="loadOrders"><LoaderCircle v-if="orderLoading" class="spin" :size="16" /><RefreshCw v-else :size="16" /></button>
        </header>
        <div v-if="orderLoading" class="orders-loading"><LoaderCircle class="spin" :size="20" />正在刷新订单</div>
        <div v-else-if="availableOrders.length" class="order-list">
          <article v-for="order in availableOrders.slice(0, 4)" :key="orderId(order)" class="order-row">
            <span class="order-route-icon"><Route :size="18" /></span>
            <div class="order-route"><strong>{{ orderRoute(order) }}</strong><span><MapPin :size="12" />订单 {{ orderId(order) }}</span></div>
            <strong class="order-price">{{ orderPrice(order) }}</strong>
            <button class="accept-button" type="button" :disabled="Boolean(acceptingId)" @click="acceptOrder(order)">{{ acceptingId === String(orderId(order)) ? '接单中' : '接单' }}</button>
          </article>
        </div>
        <div v-else class="orders-empty"><Navigation :size="27" /><strong>{{ online ? '附近暂时没有新订单' : '开始接单后查看订单' }}</strong><p>{{ online ? '稍后刷新，新的行程会出现在这里。' : '切换为营运状态，系统会同步附近可用行程。' }}</p></div>
      </section>
    </template>

    <template v-else-if="active === 'current'">
      <section v-if="currentOrder" class="trip-detail-page" :aria-busy="detailLoading">
        <header class="trip-detail-head">
          <div>
            <span class="section-kicker">ACCEPTED TRIP</span>
            <h1>已接订单</h1>
            <p>订单 {{ orderId(currentOrder) }} · {{ currentStatusLabel }}</p>
          </div>
          <div class="trip-head-actions">
            <span class="trip-status-pill">{{ currentStatusLabel }}</span>
            <button class="icon-action" type="button" title="刷新订单详情" :disabled="detailLoading" @click="loadOrderDetail(orderId(currentOrder))"><LoaderCircle v-if="detailLoading" class="spin" :size="16" /><RefreshCw v-else :size="16" /></button>
          </div>
        </header>

        <div v-if="! [8, 9].includes(normalizedStatus(currentOrder))" class="journey-progress" aria-label="订单进度">
          <div v-for="(step, index) in journeySteps" :key="step.label" class="journey-step" :class="{ done: index < currentStepIndex, active: index === currentStepIndex }">
            <span>{{ index < currentStepIndex ? '✓' : index + 1 }}</span>
            <strong>{{ step.label }}</strong>
          </div>
        </div>

        <div class="trip-detail-grid">
          <article class="trip-route-panel">
            <header>
              <div><span class="section-kicker">ROUTE PLAN</span><h2>行程路线</h2></div>
              <Route :size="21" />
            </header>
            <div class="trip-stop">
              <span class="stop-mark departure"><MapPin :size="17" /></span>
              <div><small>乘客上车点</small><strong>{{ currentOrder.departure || '待同步' }}</strong><span><Clock3 :size="12" />{{ formatDateTime(currentOrder.departTime || currentOrder.orderTime) }}</span></div>
            </div>
            <div class="trip-route-line"></div>
            <div class="trip-stop">
              <span class="stop-mark destination"><Flag :size="17" /></span>
              <div><small>目的地</small><strong>{{ currentOrder.destination || '待同步' }}</strong><span><MapPin :size="12" />{{ currentOrder.destLongitude && currentOrder.destLatitude ? '已获取目的地坐标' : '目的地坐标待同步' }}</span></div>
            </div>
            <dl class="trip-fares">
              <div><dt>订单金额</dt><dd>{{ orderPrice(currentOrder) }}</dd></div>
              <div><dt>行驶里程</dt><dd>{{ formatDistance(currentOrder) }}</dd></div>
              <div><dt>行程时长</dt><dd>{{ formatDuration(currentOrder) }}</dd></div>
            </dl>
          </article>

          <aside class="trip-contact-panel">
            <div class="trip-contact-head">
              <span class="contact-avatar"><UserRound :size="20" /></span>
              <div><span>乘客联系方式</span><strong>{{ currentOrder.passengerPhone || '待同步' }}</strong></div>
            </div>
            <a v-if="currentOrder.passengerPhone" class="call-passenger" :href="`tel:${currentOrder.passengerPhone}`"><Phone :size="16" />联系乘客</a>
            <dl class="trip-facts">
              <div><dt>接单时间</dt><dd>{{ formatDateTime(currentOrder.receiveOrderTime || currentOrder.gmtModified) }}</dd></div>
              <div><dt>服务车辆</dt><dd>{{ currentOrder.vehicleNo || plateNumber }}</dd></div>
              <div><dt>车辆类型</dt><dd>{{ vehicleTypeLabel(currentOrder.vehicleType) }}</dd></div>
              <div><dt>支付状态</dt><dd>{{ paymentLabel(currentOrder) }}</dd></div>
            </dl>
          </aside>
        </div>

        <section class="trip-action-panel">
          <div>
            <span class="section-kicker">TRIP CONTROL</span>
            <h2>{{ currentAction ? currentAction.label : '订单状态' }}</h2>
            <p>{{ currentAction?.hint || actionHint(currentOrder) }}</p>
          </div>
          <div class="trip-action-buttons">
            <button v-if="currentAction" class="primary-action" type="button" :disabled="orderActionLoading" @click="advanceCurrentOrder"><LoaderCircle v-if="orderActionLoading" class="spin" :size="16" /><ArrowRight v-else :size="16" />{{ currentAction.label }}</button>
            <button v-if="canCancelOrder" class="danger-button" type="button" :disabled="orderActionLoading" @click="cancelCurrentOrder"><X :size="16" />取消订单</button>
          </div>
        </section>
      </section>
      <section v-else class="orders-panel full-panel empty-trip-panel" :aria-busy="detailLoading">
        <div class="orders-empty large"><Navigation :size="34" /><strong>当前没有进行中的订单</strong><p>在订单大厅接单后，乘客联系方式、路线和行程操作会显示在这里。</p><button class="quiet-action inline-action" type="button" @click="active = 'orders'">前往订单大厅 <ArrowRight :size="15" /></button></div>
      </section>
    </template>

    <template v-else-if="active === 'orders'">
      <section class="orders-panel full-panel">
        <header class="orders-heading">
          <div><span class="section-kicker">ORDER HALL</span><h2>订单大厅</h2><p>可接订单会实时同步到这里。</p></div>
          <button class="refresh-action" type="button" :disabled="orderLoading" @click="loadOrders"><LoaderCircle v-if="orderLoading" class="spin" :size="16" /><RefreshCw v-else :size="16" />刷新订单</button>
        </header>
        <div v-if="availableOrders.length" class="order-list large">
          <article v-for="order in availableOrders" :key="orderId(order)" class="order-row">
            <span class="order-route-icon"><Route :size="18" /></span>
            <div class="order-route"><strong>{{ orderRoute(order) }}</strong><span><MapPin :size="12" />订单 {{ orderId(order) }}</span></div>
            <strong class="order-price">{{ orderPrice(order) }}</strong>
            <button class="accept-button" type="button" :disabled="Boolean(acceptingId)" @click="acceptOrder(order)">{{ acceptingId === String(orderId(order)) ? '接单中' : '接单' }}</button>
          </article>
        </div>
        <div v-else class="orders-empty large"><ClipboardList :size="32" /><strong>暂无可用订单</strong><p>{{ online ? '当前区域暂时没有待派订单。' : '请先切换为营运状态。' }}</p></div>
      </section>
    </template>

    <template v-else-if="active === 'vehicle'">
      <section class="vehicle-page-stack">
        <DriverVehicle
          :authenticated="true"
          @activity="handleVehicleActivity"
          @registered="refreshWorkspace"
          @expired="emit('logout')"
          @login="emit('logout')"
        />
        <article class="qualification-panel">
          <span class="section-kicker">COMPLIANCE</span>
          <h2>资质与绑定</h2>
          <div class="qualification-row"><span class="check-icon"><CheckCircle2 :size="17" /></span><div><strong>司机身份</strong><p>手机号 {{ identity }} 已登录</p></div><span class="qualified">正常</span></div>
          <div class="qualification-row"><span class="check-icon"><ShieldCheck :size="17" /></span><div><strong>车辆锁定</strong><p>{{ binding ? `司机编号 ${binding.driverId} 已锁定车辆 ${binding.carId}` : '当前账号尚未锁定车辆，可在上方输入车牌完成锁定' }}</p></div><span :class="binding ? 'qualified' : 'unqualified'">{{ binding ? '正常' : '待处理' }}</span></div>
          <div class="qualification-row"><span class="check-icon"><WalletCards :size="17" /></span><div><strong>结算账户</strong><p>订单结算状态由平台统一管理</p></div><span class="qualified">可用</span></div>
        </article>
      </section>
    </template>

    <template v-else>
      <section class="driver-account-grid">
        <article class="driver-account-card">
          <span class="driver-avatar">{{ identity.slice(-4) }}</span>
          <div><span class="section-kicker">DRIVER ACCOUNT</span><h2>{{ identity }}</h2><p>认证司机 · {{ online ? '营运中' : '已收车' }}</p></div>
          <span class="driver-verified"><ShieldCheck :size="15" /> 已认证</span>
        </article>
        <article class="driver-account-details">
          <div><span>司机编号</span><strong>{{ driverId || '待分配' }}</strong></div>
          <div><span>绑定车辆</span><strong>{{ plateNumber }}</strong></div>
          <div><span>营运状态</span><strong :class="{ onlineText: online }">{{ online ? '在线接单' : '暂停接单' }}</strong></div>
        </article>
      </section>
    </template>
  </WorkspaceShell>
</template>

<style scoped>
.driver-notice { position: fixed; top: 82px; right: 28px; z-index: 80; max-width: 360px; padding: 12px 15px; color: #fff; background: #23684e; border-radius: 5px; box-shadow: 0 12px 30px rgba(20, 35, 28, .18); font-size: 11px; }
.driver-notice.error { background: #a63d34; }
.driver-intro, .duty-toggle, .intro-kicker, .duty-panel-head, .duty-metrics, .driver-side-panel header, .quiet-action, .orders-heading, .icon-action, .orders-loading, .order-row, .order-route span, .orders-empty, .refresh-action, .vehicle-profile-head, .qualification-row, .check-icon, .driver-account-card, .driver-verified { display: flex; align-items: center; }
.driver-intro { justify-content: space-between; gap: 24px; margin-bottom: 29px; }
.intro-kicker { gap: 7px; margin: 0 0 11px; color: #8a755a; font-size: 9px; font-weight: 700; letter-spacing: .14em; }
.driver-intro h1 { margin: 0; font-size: clamp(29px, 4vw, 44px); line-height: 1.13; font-weight: 650; letter-spacing: 0; }
.driver-intro p { margin: 11px 0 0; color: #7e8781; font-size: 12px; }
.duty-toggle { min-height: 46px; justify-content: center; gap: 9px; flex: none; padding: 0 17px; border: 1px solid #d5a452; border-radius: 5px; color: #7a4e11; background: #f5e6ca; font-size: 11px; font-weight: 700; }
.duty-toggle.online { color: #fff; border-color: #247055; background: #247055; }
.duty-dot { width: 8px; height: 8px; background: currentColor; border-radius: 50%; }
.driver-grid { display: grid; grid-template-columns: minmax(0, 1.45fr) minmax(280px, .65fr); gap: 20px; }
.duty-panel, .driver-side-panel, .orders-panel, .vehicle-profile, .qualification-panel, .driver-account-card, .driver-account-details { background: #fffdf8; border: 1px solid #ddd8cc; border-radius: 7px; }
.duty-panel { padding: clamp(23px, 3vw, 34px); color: #eef3ef; background: #242c28; border-color: #303b35; }
.duty-panel.online { background: #1d3b31; border-color: #35604f; }
.duty-panel-head { justify-content: space-between; }
.duty-icon { width: 48px; height: 48px; display: grid; place-items: center; color: #f1b53c; background: #303934; border-radius: 6px; }
.duty-status { padding: 6px 9px; color: #d9cdb7; background: #343d38; border: 1px solid #47534c; border-radius: 4px; font-size: 9px; }
.duty-panel.online .duty-status { color: #bde4ce; background: #285342; border-color: #3b6d56; }
.duty-panel > strong { display: block; margin-top: 31px; font-size: clamp(21px, 3vw, 30px); font-weight: 650; }
.duty-panel > p { max-width: 550px; margin: 11px 0 0; color: #aab6af; font-size: 11px; line-height: 1.75; }
.duty-metrics { display: grid; grid-template-columns: repeat(3, 1fr); margin-top: 37px; border-top: 1px solid #414c46; }
.duty-metrics div { padding: 19px 18px 0 0; }
.duty-metrics div + div { padding-left: 18px; border-left: 1px solid #414c46; }
.duty-metrics span, .duty-metrics strong { display: block; }
.duty-metrics span { color: #8d9c94; font-size: 9px; }
.duty-metrics strong { margin-top: 7px; color: #f5f8f6; font-size: 17px; font-weight: 650; }
.driver-side-panel { padding: 22px; }
.driver-side-panel header { justify-content: space-between; gap: 14px; }
.section-kicker { display: block; margin-bottom: 6px; color: #969b96; font-size: 8px; font-weight: 700; letter-spacing: .14em; }
.driver-side-panel h2, .orders-heading h2, .qualification-panel h2 { margin: 0; font-size: 18px; font-weight: 650; }
.driver-side-panel header svg { color: #d98213; }
.vehicle-snapshot { margin-top: 25px; padding: 20px 0; border-top: 1px solid #e4dfd4; border-bottom: 1px solid #e4dfd4; }
.vehicle-snapshot strong { display: block; margin-top: 15px; font-size: 13px; }
.vehicle-snapshot p { margin: 5px 0 0; color: #89908b; font-size: 10px; }
.plate-box { display: inline-flex; padding: 7px 10px; color: #173f34; background: #dceee7; border: 1px solid #b8d8ca; border-radius: 4px; font-size: 12px; font-weight: 700; letter-spacing: .05em; }
.quiet-action { width: 100%; min-height: 39px; justify-content: center; gap: 8px; margin-top: 19px; border: 1px solid #d7d2c6; border-radius: 4px; color: #59635d; background: #f6f3ec; font-size: 10px; }
.orders-panel { margin-top: 20px; padding: 22px; }
.orders-panel.full-panel { margin-top: 0; padding: clamp(22px, 3vw, 32px); }
.orders-heading { justify-content: space-between; gap: 18px; }
.orders-heading p { margin: 7px 0 0; color: #8b928d; font-size: 10px; }
.icon-action { width: 34px; height: 34px; justify-content: center; border: 1px solid #d8d3c8; border-radius: 4px; color: #68716c; background: #f5f2eb; }
.order-list { margin-top: 18px; border-top: 1px solid #e4dfd5; }
.order-row { gap: 13px; min-height: 71px; padding: 12px 0; border-bottom: 1px solid #e4dfd5; }
.order-route-icon { width: 34px; height: 34px; display: grid; place-items: center; flex: none; color: #a96b13; background: #f6e8d1; border-radius: 50%; }
.order-route { min-width: 0; flex: 1; }
.order-route strong { display: block; font-size: 11px; font-weight: 650; overflow-wrap: anywhere; }
.order-route span { gap: 5px; margin-top: 5px; color: #8c938e; font-size: 9px; }
.order-price { flex: none; color: #37433d; font-size: 11px; font-weight: 650; }
.accept-button { min-height: 34px; flex: none; padding: 0 13px; border: 1px solid #1d6a51; border-radius: 4px; color: #fff; background: #247055; font-size: 10px; font-weight: 650; }
.orders-loading, .orders-empty { justify-content: center; min-height: 185px; gap: 9px; color: #8a928d; font-size: 10px; }
.orders-empty { flex-direction: column; text-align: center; }
.orders-empty svg { color: #c19a5b; }
.orders-empty strong { margin-top: 3px; color: #3b4540; font-size: 12px; }
.orders-empty p { margin: 0; color: #8b928d; font-size: 10px; }
.orders-empty.large { min-height: 420px; }
.refresh-action { min-height: 38px; gap: 7px; padding: 0 12px; border: 1px solid #d8d3c8; border-radius: 4px; color: #525c56; background: #f6f3ec; font-size: 10px; }
.active-trip-banner { display: flex; align-items: center; gap: 14px; margin-bottom: 20px; padding: 16px 18px; color: #eef3ef; background: #1d3b31; border: 1px solid #35604f; border-radius: 7px; }
.active-trip-icon { width: 38px; height: 38px; display: grid; place-items: center; flex: none; color: #f1b53c; background: #285342; border-radius: 6px; }
.active-trip-banner > div { min-width: 0; flex: 1; }
.active-trip-banner .section-kicker { margin-bottom: 4px; color: #9fc6b1; }
.active-trip-banner strong { display: block; font-size: 12px; overflow-wrap: anywhere; }
.active-trip-banner p { margin: 5px 0 0; color: #a9c4b5; font-size: 9px; }
.trip-banner-action { min-height: 36px; display: flex; align-items: center; justify-content: center; gap: 7px; flex: none; padding: 0 13px; border: 1px solid #5a8772; border-radius: 4px; color: #eef7f1; background: #285342; font-size: 10px; font-weight: 650; }
.trip-detail-page { display: grid; gap: 20px; }
.trip-detail-head { display: flex; align-items: flex-end; justify-content: space-between; gap: 20px; }
.trip-detail-head h1 { margin: 0; font-size: clamp(28px, 4vw, 40px); font-weight: 650; letter-spacing: 0; }
.trip-detail-head p { margin: 8px 0 0; color: #7f8882; font-size: 10px; }
.trip-head-actions { display: flex; align-items: center; gap: 9px; flex: none; }
.trip-status-pill { padding: 7px 10px; color: #7a4e11; background: #f5e6ca; border: 1px solid #e2c58f; border-radius: 4px; font-size: 9px; font-weight: 650; }
.journey-progress { display: grid; grid-template-columns: repeat(6, 1fr); padding: 16px 18px; background: #fffdf8; border: 1px solid #ddd8cc; border-radius: 7px; }
.journey-step { position: relative; display: flex; flex-direction: column; align-items: center; gap: 6px; color: #a1a7a3; text-align: center; font-size: 9px; }
.journey-step::before { content: ''; position: absolute; top: 11px; left: -50%; width: 100%; height: 1px; background: #e1ddd3; }
.journey-step:first-child::before { display: none; }
.journey-step span { width: 23px; height: 23px; display: grid; place-items: center; position: relative; z-index: 1; color: #8b928d; background: #f1eee7; border: 1px solid #ddd8cc; border-radius: 50%; font-size: 9px; }
.journey-step.done, .journey-step.active { color: #2c5744; }
.journey-step.done::before { background: #9dc1ad; }
.journey-step.done span { color: #fff; background: #477c62; border-color: #477c62; }
.journey-step.active span { color: #6d4710; background: #f5e6ca; border-color: #d9b46f; }
.trip-detail-grid { display: grid; grid-template-columns: minmax(0, 1.35fr) minmax(280px, .65fr); gap: 20px; }
.trip-route-panel, .trip-contact-panel, .trip-action-panel { padding: clamp(22px, 3vw, 30px); background: #fffdf8; border: 1px solid #ddd8cc; border-radius: 7px; }
.trip-route-panel header { display: flex; align-items: flex-start; justify-content: space-between; gap: 14px; }
.trip-route-panel header svg { color: #d98213; }
.trip-route-panel h2, .trip-action-panel h2 { margin: 0; font-size: 18px; font-weight: 650; }
.trip-stop { display: flex; gap: 13px; padding-top: 20px; }
.stop-mark { width: 34px; height: 34px; display: grid; place-items: center; flex: none; color: #8a6a36; background: #f6e8d1; border-radius: 50%; }
.stop-mark.destination { color: #287057; background: #e8f1eb; }
.trip-stop > div { min-width: 0; }
.trip-stop > div > small, .trip-stop > div > strong, .trip-stop > div > span { display: block; }
.trip-stop small { color: #929994; font-size: 8px; }
.trip-stop strong { margin-top: 5px; font-size: 12px; line-height: 1.5; overflow-wrap: anywhere; }
.trip-stop > div > span { display: flex; align-items: center; gap: 5px; margin-top: 6px; color: #8b928d; font-size: 9px; }
.trip-route-line { height: 20px; margin-left: 16px; border-left: 1px dashed #c9c2b4; }
.trip-fares { display: grid; grid-template-columns: repeat(3, 1fr); margin: 24px 0 0; padding: 16px 0 0; border-top: 1px solid #e4dfd5; }
.trip-fares div + div { padding-left: 14px; border-left: 1px solid #e4dfd5; }
.trip-fares dt { color: #8b928d; font-size: 8px; }
.trip-fares dd { margin: 6px 0 0; color: #2f3934; font-size: 12px; font-weight: 650; }
.trip-contact-head { display: flex; align-items: center; gap: 12px; }
.contact-avatar { width: 42px; height: 42px; display: grid; place-items: center; flex: none; color: #fff; background: #26312c; border-radius: 50%; }
.trip-contact-head div { min-width: 0; }
.trip-contact-head > div > span, .trip-contact-head > div > strong { display: block; }
.trip-contact-head > div > span { color: #8b928d; font-size: 8px; }
.trip-contact-head > div > strong { margin-top: 6px; font-size: 14px; letter-spacing: .02em; }
.call-passenger { min-height: 39px; display: flex; align-items: center; justify-content: center; gap: 7px; margin-top: 17px; color: #fff; background: #247055; border-radius: 4px; text-decoration: none; font-size: 10px; font-weight: 650; }
.trip-facts { margin: 19px 0 0; border-top: 1px solid #e4dfd5; }
.trip-facts div { display: flex; justify-content: space-between; gap: 15px; padding: 12px 0; border-bottom: 1px solid #eee9df; font-size: 9px; }
.trip-facts dt { color: #8b928d; }
.trip-facts dd { margin: 0; color: #343e39; text-align: right; overflow-wrap: anywhere; }
.trip-action-panel { display: flex; align-items: center; justify-content: space-between; gap: 24px; }
.trip-action-panel > div:first-child { min-width: 0; }
.trip-action-panel p { margin: 8px 0 0; color: #8b928d; font-size: 10px; }
.trip-action-buttons { display: flex; align-items: center; gap: 9px; flex: none; }
.primary-action, .danger-button { min-height: 40px; display: flex; align-items: center; justify-content: center; gap: 7px; padding: 0 16px; border-radius: 4px; font-size: 10px; font-weight: 650; }
.primary-action { color: #fff; background: #247055; border: 1px solid #1d6a51; }
.danger-button { color: #a63d34; background: #fbf0ed; border: 1px solid #e5c1ba; }
.empty-trip-panel { margin-top: 0; }
.orders-empty .inline-action { width: auto; min-height: 36px; display: inline-flex; margin-top: 14px; padding: 0 14px; }
.vehicle-page-stack { display: grid; gap: 20px; }
.vehicle-profile { padding: 28px; }
.vehicle-profile-head { justify-content: space-between; }
.vehicle-large-icon { width: 62px; height: 62px; display: grid; place-items: center; color: #d98213; background: #f6e8d1; border-radius: 7px; }
.vehicle-state { padding: 6px 9px; color: #7a5016; background: #f6e9d3; border-radius: 4px; font-size: 9px; }
.vehicle-profile h2 { margin: 25px 0 0; font-size: 25px; letter-spacing: .03em; }
.vehicle-profile > p { margin: 7px 0 0; color: #858d88; font-size: 11px; }
.vehicle-specs { display: grid; grid-template-columns: repeat(3, 1fr); margin-top: 26px; border-top: 1px solid #e4dfd5; border-bottom: 1px solid #e4dfd5; }
.vehicle-specs div { padding: 15px 10px 15px 0; }
.vehicle-specs div + div { padding-left: 10px; border-left: 1px solid #e4dfd5; }
.vehicle-specs span, .vehicle-specs strong { display: block; }
.vehicle-specs span { color: #8b928d; font-size: 8px; }
.vehicle-specs strong { margin-top: 5px; font-size: 11px; }
.qualification-panel { padding: clamp(22px, 3vw, 32px); }
.qualification-panel h2 { margin-bottom: 22px; }
.qualification-row { gap: 12px; padding: 17px 0; border-top: 1px solid #e4dfd5; }
.check-icon { width: 31px; height: 31px; justify-content: center; flex: none; color: #347054; background: #e8f1eb; border-radius: 50%; }
.qualification-row div { min-width: 0; flex: 1; }
.qualification-row strong { display: block; font-size: 11px; }
.qualification-row p { margin: 4px 0 0; color: #8b928d; font-size: 9px; line-height: 1.5; }
.qualified, .unqualified { flex: none; padding: 5px 8px; border-radius: 4px; font-size: 8px; }
.qualified { color: #347054; background: #e8f1eb; }
.unqualified { color: #9b4b3e; background: #f6e7e3; }
.driver-account-grid { display: grid; grid-template-columns: minmax(0, 1.2fr) minmax(280px, .8fr); gap: 20px; }
.driver-account-card { gap: 18px; padding: 28px; }
.driver-avatar { width: 65px; height: 65px; display: grid; place-items: center; flex: none; color: #fff; background: #26312c; border-radius: 7px; font-size: 16px; font-weight: 700; }
.driver-account-card div { min-width: 0; flex: 1; }
.driver-account-card h2 { margin: 0; font-size: 20px; }
.driver-account-card p { margin: 6px 0 0; color: #858d88; font-size: 10px; }
.driver-verified { gap: 5px; padding: 7px 9px; color: #347054; background: #e8f1eb; border-radius: 4px; font-size: 9px; }
.driver-account-details { padding: 18px 26px; }
.driver-account-details div { display: flex; justify-content: space-between; gap: 15px; padding: 15px 0; border-top: 1px solid #e4dfd5; font-size: 10px; }
.driver-account-details div:first-child { border-top: 0; }
.driver-account-details span { color: #858d88; }
.driver-account-details strong { color: #343e39; }
.onlineText { color: #247055 !important; }
.spin { animation: spin .8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

@media (max-width: 980px) {
  .driver-grid, .driver-account-grid, .trip-detail-grid { grid-template-columns: 1fr; }
  .journey-progress { grid-template-columns: repeat(3, 1fr); row-gap: 14px; }
  .journey-step:nth-child(4)::before { display: none; }
}

@media (max-width: 680px) {
  .driver-intro { align-items: flex-start; flex-direction: column; }
  .active-trip-banner, .trip-detail-head, .trip-action-panel { align-items: flex-start; flex-direction: column; }
  .trip-banner-action, .trip-action-buttons { width: 100%; }
  .trip-head-actions { width: 100%; justify-content: space-between; }
  .journey-progress { grid-template-columns: repeat(2, 1fr); }
  .journey-step:nth-child(odd)::before { display: none; }
  .trip-fares { grid-template-columns: 1fr; }
  .trip-fares div + div { padding: 12px 0 0; border-top: 1px solid #e4dfd5; border-left: 0; }
  .trip-action-buttons { flex-direction: column; }
  .primary-action, .danger-button { width: 100%; }
  .duty-toggle { width: 100%; }
  .duty-metrics { grid-template-columns: 1fr; }
  .duty-metrics div, .duty-metrics div + div { padding: 14px 0; border-left: 0; border-top: 1px solid #414c46; }
  .order-row { flex-wrap: wrap; }
  .order-route { flex-basis: calc(100% - 50px); }
  .order-price { margin-left: 47px; }
  .accept-button { margin-left: auto; }
  .vehicle-specs { grid-template-columns: 1fr; }
  .vehicle-specs div, .vehicle-specs div + div { padding: 13px 0; border-left: 0; border-top: 1px solid #e4dfd5; }
  .driver-account-card { align-items: flex-start; flex-wrap: wrap; }
  .driver-verified { margin-left: auto; }
  .driver-notice { top: 72px; right: 12px; left: 12px; max-width: none; }
}

@media (max-width: 480px) {
  .duty-panel, .driver-side-panel, .orders-panel, .vehicle-profile, .qualification-panel, .driver-account-card, .trip-route-panel, .trip-contact-panel, .trip-action-panel { padding: 18px 15px; }
}
</style>
