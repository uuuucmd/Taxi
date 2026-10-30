<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ArrowRight, ClipboardList, LogIn, MapPin, RefreshCw, X } from 'lucide-vue-next'
import { driverApi, getToken } from '../services/api'

const props = defineProps({ authenticated: Boolean })
const emit = defineEmits(['login', 'activity', 'auth-expired'])
const orders = ref([])
const selectedId = ref(null)
const filter = ref('active')
const loading = ref(false)
const submitting = ref(false)
const error = ref('')
let requestVersion = 0
let disposed = false
let refreshTimer

const statuses = {
  0: '已失效', 1: '待派单', 2: '待接驾', 3: '接驾中', 4: '已到达', 5: '行程中',
  6: '待支付', 7: '收款中', 8: '已完成', 9: '已取消', 10: '费用计算中', 11: '结算异常',
}
const actions = [
  { label: '接单', path: 'accept', status: 1 },
  { label: '前往接驾', path: 'to-pick-up-passenger', status: 2 },
  { label: '已到达出发点', path: 'arrived-departure', status: 3 },
  { label: '乘客已上车', path: 'pick-up-passenger', status: 4 },
  { label: '乘客已下车', path: 'passenger-getoff', status: 5 },
]
const finished = order => [0, 8, 9].includes(order.orderStatus)
const activeOrders = computed(() => orders.value.filter(order => !finished(order)))
const finishedOrders = computed(() => orders.value.filter(finished))
const visibleOrders = computed(() => filter.value === 'active' ? activeOrders.value : finishedOrders.value)
const selectedOrder = computed(() => visibleOrders.value.find(order => order.id === selectedId.value) || null)
const locked = computed(() => loading.value || submitting.value)
const canCancel = computed(() => [2, 3, 4].includes(selectedOrder.value?.orderStatus))

watch(visibleOrders, list => {
  if (!list.some(order => order.id === selectedId.value)) selectedId.value = null
})

function statusLabel(order) {
  return statuses[order.orderStatus] || '状态未知'
}

function orderTime(order) {
  const value = order.departTime || order.orderTime || order.gmtCreate
  if (!value) return '时间未记录'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '时间未记录'
  return date.toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false })
}

function orderPrice(order) {
  return typeof order.price === 'number' && Number.isFinite(order.price) ? `¥ ${order.price.toFixed(2)}` : '待结算'
}

function reportError(cause) {
  error.value = cause.message || '订单请求失败，请稍后重试'
  if (cause.status === 401) emit('auth-expired')
}

async function loadOrders() {
  const version = ++requestVersion
  const token = getToken('driver')
  error.value = ''
  if (!props.authenticated || !token) {
    orders.value = []
    selectedId.value = null
    loading.value = false
    return
  }
  loading.value = true
  try {
    const result = await driverApi.orders()
    if (disposed || version !== requestVersion || token !== getToken('driver')) return
    if (!Array.isArray(result?.data)) throw new Error('未获取到有效的订单列表，请刷新后重试')
    orders.value = result.data
  } catch (cause) {
    if (disposed || version !== requestVersion || token !== getToken('driver')) return
    orders.value = []
    selectedId.value = null
    reportError(cause)
  } finally {
    if (version === requestVersion) loading.value = false
  }
}

async function advance(action) {
  const order = selectedOrder.value
  if (locked.value || !order || order.orderStatus !== action.status) return
  const request = action.path === 'accept'
    ? () => driverApi.acceptOrder(order.id)
    : () => driverApi.advanceOrder(action.path, { orderId: order.id })
  await submit(action.label, request)
}

async function cancelOrder() {
  const order = selectedOrder.value
  if (locked.value || !order || !canCancel.value) return
  if (!window.confirm(`确认取消从“${order.departure || '出发地'}”到“${order.destination || '目的地'}”的订单？`)) return
  await submit('取消订单', () => driverApi.cancelOrder(order.id))
}

async function submit(label, action) {
  submitting.value = true
  error.value = ''
  try {
    await action()
    emit('activity', { label, status: 'success', detail: '请求成功' })
    await loadOrders()
  } catch (cause) {
    reportError(cause)
    emit('activity', { label, status: 'error', detail: error.value })
    if (cause.status !== 401) {
      const actionError = error.value
      await loadOrders()
      error.value = actionError
    }
  } finally {
    submitting.value = false
  }
}

watch(() => props.authenticated, loadOrders, { immediate: true })
watch(() => props.authenticated, authenticated => {
  if (refreshTimer) {
    window.clearInterval(refreshTimer)
    refreshTimer = undefined
  }
  if (authenticated) refreshTimer = window.setInterval(loadOrders, 5000)
}, { immediate: true })
onBeforeUnmount(() => {
  disposed = true
  requestVersion += 1
  if (refreshTimer) window.clearInterval(refreshTimer)
})
</script>

<template>
  <div v-if="!authenticated" class="driver-orders-unauthenticated empty-state">
    <ClipboardList :size="30" />
    <span>请先登录司机账号</span>
    <button class="secondary-button" @click="emit('login')"><LogIn :size="16" />司机登录</button>
  </div>
  <div v-else class="driver-orders-layout">
    <section class="driver-order-list" aria-label="我的订单" :aria-busy="loading">
      <div class="driver-orders-heading">
        <div><span class="section-kicker">MY ORDERS</span><h2>我的订单</h2></div>
        <button class="icon-button" title="刷新订单" aria-label="刷新订单" :disabled="locked" @click="loadOrders"><RefreshCw :size="18" /></button>
      </div>
      <div class="driver-order-tabs" role="tablist" aria-label="订单分类">
        <button role="tab" :aria-selected="filter === 'active'" :class="{ active: filter === 'active' }" :disabled="submitting" @click="filter = 'active'">待处理 <span>{{ activeOrders.length }}</span></button>
        <button role="tab" :aria-selected="filter === 'finished'" :class="{ active: filter === 'finished' }" :disabled="submitting" @click="filter = 'finished'">已结束 <span>{{ finishedOrders.length }}</span></button>
      </div>
      <p v-if="error" class="driver-order-error" role="alert">{{ error }}</p>
      <p v-if="loading" class="driver-order-loading" role="status">正在读取订单...</p>
      <div v-if="visibleOrders.length" class="driver-order-rows" role="radiogroup" aria-label="选择订单">
        <label v-for="order in visibleOrders" :key="order.id" class="driver-order-row" :class="{ selected: selectedId === order.id }">
          <input v-model="selectedId" type="radio" name="driver-order" :value="order.id" :disabled="locked" :aria-label="`${order.departure || '出发地'}到${order.destination || '目的地'}，${statusLabel(order)}`" />
          <span class="driver-order-summary">
            <span class="driver-order-route"><span>{{ order.departure || '出发地未记录' }}</span><ArrowRight :size="14" /><span>{{ order.destination || '目的地未记录' }}</span></span>
            <span class="driver-order-secondary"><time>{{ orderTime(order) }}</time><span>{{ orderPrice(order) }}</span></span>
          </span>
          <span class="driver-order-status" :class="{ complete: finished(order), warning: order.orderStatus === 11 }">{{ statusLabel(order) }}</span>
        </label>
      </div>
      <div v-else-if="!loading && !error" class="empty-state"><ClipboardList :size="28" /><span>{{ filter === 'active' ? '暂无待处理订单' : '暂无已结束订单' }}</span></div>
    </section>
    <section class="surface driver-order-detail" aria-label="订单详情">
      <div class="surface-heading"><div><span class="section-kicker">CURRENT TRIP</span><h2>当前行程</h2></div><ClipboardList :size="22" /></div>
      <template v-if="selectedOrder">
        <div class="driver-selected-status"><span class="driver-order-status">{{ statusLabel(selectedOrder) }}</span><time>{{ orderTime(selectedOrder) }}</time></div>
        <div class="driver-selected-route">
          <div><MapPin :size="17" /><span><small>出发地</small><strong>{{ selectedOrder.departure || '未记录' }}</strong></span></div>
          <div><MapPin :size="17" /><span><small>目的地</small><strong>{{ selectedOrder.destination || '未记录' }}</strong></span></div>
        </div>
        <div class="driver-selected-price"><span>行程费用</span><strong>{{ orderPrice(selectedOrder) }}</strong></div>
        <div class="workflow">
          <button v-for="action in actions" :key="action.path" class="workflow-button" :disabled="locked || selectedOrder.orderStatus !== action.status" @click="advance(action)"><span>{{ action.label }}</span><ArrowRight :size="15" /></button>
        </div>
        <div v-if="canCancel" class="form-actions"><button class="danger-button" :disabled="locked" @click="cancelOrder"><X :size="16" />取消订单</button></div>
      </template>
      <div v-else class="empty-state"><MapPin :size="28" /><span>尚未选择订单</span></div>
    </section>
  </div>
</template>
