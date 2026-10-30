<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { CheckCircle2, Clock3, CreditCard, LogIn, MapPin, RefreshCw, Route, WalletCards } from 'lucide-vue-next'
import { getToken, passengerApi } from '../services/api'

const props = defineProps({
  authenticated: Boolean,
  active: Boolean,
})

const emit = defineEmits(['login', 'activity', 'auth-expired'])

const order = ref(null)
const loading = ref(false)
const paying = ref(false)
const error = ref('')
const paidLocally = ref(false)
let refreshTimer

const statusLabels = {
  1: '等待派单',
  2: '司机已接单',
  3: '司机前往接驾',
  4: '司机已到达',
  5: '行程中',
  6: '待支付',
  7: '待支付',
  8: '已支付',
  9: '已取消',
  10: '费用计算中',
  11: '结算异常',
}

const paymentReady = computed(() => [6, 7].includes(order.value?.orderStatus))
const paid = computed(() => paidLocally.value || order.value?.orderStatus === 8)
const statusText = computed(() => paid.value ? '已支付' : statusLabels[order.value?.orderStatus] || '处理中')

function responseData(result) {
  return result?.data ?? null
}

function formatMoney(value) {
  return typeof value === 'number' && Number.isFinite(value) ? value.toFixed(2) : '--'
}

function formatTime(value) {
  if (!value) return '时间未记录'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '时间未记录'
  return date.toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false })
}

function reportError(cause) {
  error.value = cause.message || '订单信息读取失败，请稍后重试'
  if (cause.status === 401) emit('auth-expired')
}

async function loadOrder() {
  if (!props.authenticated || !getToken('passenger') || !props.active) return
  loading.value = true
  error.value = ''
  try {
    const result = await passengerApi.currentOrder()
    const data = responseData(result)
    if (data) {
      order.value = data
      paidLocally.value = data.orderStatus === 8
    } else if (!paidLocally.value) {
      order.value = null
    }
  } catch (cause) {
    reportError(cause)
  } finally {
    loading.value = false
  }
}

async function simulatePay() {
  if (paying.value || !order.value || !paymentReady.value) return
  paying.value = true
  error.value = ''
  try {
    await passengerApi.payOrder(order.value.id)
    paidLocally.value = true
    order.value = { ...order.value, orderStatus: 8 }
    emit('activity', { label: '模拟支付', status: 'success', detail: `已完成 ¥ ${formatMoney(order.value.price)} 的本地模拟支付` })
  } catch (cause) {
    reportError(cause)
    emit('activity', { label: '模拟支付', status: 'error', detail: error.value })
  } finally {
    paying.value = false
  }
}

watch(() => [props.authenticated, props.active], ([authenticated, active]) => {
  if (refreshTimer) {
    window.clearInterval(refreshTimer)
    refreshTimer = undefined
  }
  if (authenticated && active) {
    loadOrder()
    refreshTimer = window.setInterval(loadOrder, 5000)
  } else if (!authenticated) {
    order.value = null
    paidLocally.value = false
  }
}, { immediate: true })

onBeforeUnmount(() => {
  if (refreshTimer) window.clearInterval(refreshTimer)
})
</script>

<template>
  <div v-if="!authenticated" class="payment-unauthenticated empty-state">
    <CreditCard :size="30" />
    <span>请先登录乘客账号</span>
    <button class="secondary-button" @click="emit('login')"><LogIn :size="16" />乘客登录</button>
  </div>
  <div v-else class="payment-layout" :aria-busy="loading">
    <section class="surface payment-summary">
      <div class="surface-heading">
        <div><span class="section-kicker">TRIP BILL / 02</span><h2>订单与支付</h2></div>
        <button class="icon-button" title="刷新订单" aria-label="刷新订单" :disabled="loading || paying" @click="loadOrder"><RefreshCw :size="18" /></button>
      </div>
      <p v-if="error" class="payment-error" role="alert">{{ error }}</p>
      <div v-if="loading && !order" class="payment-loading" role="status"><RefreshCw :size="18" />正在读取订单...</div>
      <div v-else-if="order" class="payment-order">
        <div class="payment-status-line"><span class="payment-status" :class="{ paid }"><CheckCircle2 v-if="paid" :size="15" /><Clock3 v-else :size="15" />{{ statusText }}</span><time>{{ formatTime(order.gmtCreate || order.orderTime) }}</time></div>
        <div class="payment-route"><div><MapPin :size="17" /><span><small>出发地</small><strong>{{ order.departure || '未记录' }}</strong></span></div><div><Route :size="17" /><span><small>目的地</small><strong>{{ order.destination || '未记录' }}</strong></span></div></div>
        <div class="payment-total"><span>应付金额</span><strong><small>¥</small>{{ formatMoney(order.price) }}</strong></div>
        <div class="payment-meta"><span>订单号</span><b>{{ order.id }}</b></div>
        <div v-if="paymentReady" class="payment-callout"><WalletCards :size="19" /><div><strong>行程已结束，可以模拟支付</strong><span>不会连接真实支付平台，只会更新本地订单状态。</span></div></div>
        <div v-else-if="paid" class="payment-callout success"><CheckCircle2 :size="19" /><div><strong>模拟支付已完成</strong><span>本次支付仅用于演示订单流程。</span></div></div>
        <p v-else class="payment-hint">当前订单还未进入支付阶段，行程结束后会自动显示支付入口。</p>
      </div>
      <div v-else-if="!error" class="empty-state"><CreditCard :size="28" /><span>暂无待处理订单</span><small>下单后可在这里查看行程和支付状态</small></div>
    </section>

    <aside class="surface payment-action">
      <div class="surface-heading"><div><span class="section-kicker">LOCAL CHECKOUT</span><h3>模拟支付</h3></div><CreditCard :size="21" /></div>
      <div class="payment-method"><span class="payment-method-icon"><WalletCards :size="19" /></span><div><strong>本地演示账户</strong><small>无需绑定银行卡</small></div><CheckCircle2 :size="18" /></div>
      <button class="primary-button full-button" :disabled="!paymentReady || paying" @click="simulatePay"><CreditCard :size="16" />{{ paying ? '处理中...' : `确认支付 ¥ ${formatMoney(order?.price)}` }}</button>
      <p class="muted-note">这是模拟支付页面，不会产生真实扣款。点击确认后，订单会变为“已支付”。</p>
    </aside>
  </div>
</template>
