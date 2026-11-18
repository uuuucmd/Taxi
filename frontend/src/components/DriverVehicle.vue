<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { CarFront, LockKeyhole, MapPin, Plus, RefreshCw } from 'lucide-vue-next'
import { driverApi } from '../services/api'

const props = defineProps({ authenticated: Boolean })
const emit = defineEmits(['activity', 'registered', 'login', 'expired'])
const vehicle = ref(null)
const loaded = ref(false)
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const form = reactive({ vehicleNo: '', brand: '', model: '', address: '', seats: 4, ownerName: '', vehicleType: '1', plateColor: '1', vehicleColor: '1' })
const lockForm = reactive({ vehicleNo: '' })
const busy = computed(() => loading.value || saving.value)
const localSimulation = computed(() => vehicle.value?.locationMode === 'local')
const types = { '1': '快车 / 普通型', '2': '舒适型', '3': '商务型' }
let disposed = false

function failed(cause, label) {
  error.value = cause.message || `${label}失败，请稍后重试`
  emit('activity', { label, status: 'error', detail: error.value })
  if (cause.status === 401) emit('expired')
}

async function loadVehicle() {
  if (!props.authenticated || busy.value) return
  loading.value = true
  error.value = ''
  try {
    const result = await driverApi.vehicle()
    if (disposed) return
    vehicle.value = result.data || null
    loaded.value = true
  } catch (cause) {
    if (!disposed) failed(cause, '读取车辆')
  } finally {
    if (!disposed) loading.value = false
  }
}

async function perform(label, action) {
  if (!props.authenticated || busy.value) return
  saving.value = true
  error.value = ''
  try {
    const result = await action()
    if (disposed) return
    emit('activity', { label, status: 'success', detail: result?.message || '请求成功' })
  } catch (cause) {
    if (!disposed) failed(cause, label)
  } finally {
    if (!disposed) saving.value = false
  }
}

async function registerVehicle() {
  await perform('登记并绑定车辆', async () => {
    const result = await driverApi.registerVehicle({ ...form })
    if (!result.data?.id) throw new Error('未获取到车辆登记结果，请刷新确认，勿重复提交')
    if (!disposed) {
      vehicle.value = result.data
      loaded.value = true
      emit('registered')
    }
    return result
  })
}

async function lockVehicle() {
  await perform('锁定已有车辆', async () => {
    const vehicleNo = lockForm.vehicleNo.trim()
    if (!vehicleNo) throw new Error('请输入要锁定的车牌号')
    const result = await driverApi.lockVehicle(vehicleNo)
    if (!result.data?.id) throw new Error('未获取到车辆锁定结果，请刷新确认')
    if (!disposed) {
      vehicle.value = result.data
      loaded.value = true
      emit('registered')
    }
    return result
  })
}

async function initializeMap() {
  await perform('初始化车辆定位', async () => {
    const result = await driverApi.initializeVehicleMap()
    if (!result.data?.traceReady) throw new Error('车辆定位尚未初始化完成，请稍后重试')
    if (!disposed) vehicle.value = result.data
    return result
  })
}

onMounted(loadVehicle)
onBeforeUnmount(() => { disposed = true })
</script>

<template>
  <section class="driver-vehicle">
    <div class="vehicle-toolbar">
      <h2>我的车辆</h2>
      <button class="secondary-button" :disabled="busy || !authenticated" @click="loadVehicle"><RefreshCw :size="16" />刷新车辆</button>
    </div>
    <p v-if="error" class="vehicle-error" role="alert">{{ error }}</p>
    <div v-if="!authenticated" class="empty-state"><CarFront :size="28" /><span>请先登录司机账号</span><button class="primary-button" @click="emit('login')">登录</button></div>
    <div v-else-if="!loaded" class="empty-state"><CarFront :size="28" /><span>{{ loading ? '正在读取车辆' : '尚未读取到车辆信息' }}</span></div>
    <div v-else-if="!vehicle" class="vehicle-registration">
      <form class="vehicle-lock-card" @submit.prevent="lockVehicle">
        <div class="surface-heading"><h3>锁定已有车辆</h3><LockKeyhole :size="20" /></div>
        <p class="vehicle-help">管理员登记车辆后，输入车牌号即可锁定到当前司机账号。同一辆车只能由一个司机使用，如需换车请联系管理员。</p>
        <fieldset :disabled="busy" class="vehicle-fields">
          <label class="field"><span>车牌号</span><input v-model="lockForm.vehicleNo" required maxlength="32" placeholder="京A12345" autocomplete="off" /></label>
          <div class="form-actions"><button class="primary-button" type="submit"><LockKeyhole :size="16" />{{ saving ? '正在锁定' : '锁定车辆' }}</button></div>
        </fieldset>
      </form>
      <div class="vehicle-divider"><span>没有可锁定车辆？登记一辆新车</span></div>
      <form class="vehicle-new-form" @submit.prevent="registerVehicle">
        <div class="surface-heading"><h3>登记新车辆</h3><CarFront :size="20" /></div>
        <fieldset :disabled="busy" class="vehicle-fields">
          <div class="field-grid three">
            <label class="field"><span>车牌号</span><input v-model="form.vehicleNo" required maxlength="32" placeholder="京A12345" /></label>
            <label class="field"><span>品牌</span><input v-model="form.brand" required maxlength="64" placeholder="比亚迪" /></label>
            <label class="field"><span>车型</span><input v-model="form.model" required maxlength="64" placeholder="秦 PLUS" /></label>
            <label class="field"><span>所在城市</span><input v-model="form.address" required maxlength="32" placeholder="北京市" /></label>
            <label class="field"><span>座位数</span><input v-model.number="form.seats" required type="number" min="1" max="9" step="1" /></label>
            <label class="field"><span>车主姓名</span><input v-model="form.ownerName" required maxlength="64" placeholder="车主姓名" /></label>
            <label class="field"><span>车辆类型</span><select v-model="form.vehicleType"><option v-for="(label, value) in types" :key="value" :value="value">{{ label }}</option></select></label>
            <label class="field"><span>车牌颜色</span><select v-model="form.plateColor"><option value="1">蓝色</option><option value="2">黄色</option><option value="3">黑色</option><option value="4">白色</option><option value="5">绿色</option><option value="9">其他</option></select></label>
            <label class="field"><span>车辆颜色</span><select v-model="form.vehicleColor"><option value="1">白色</option><option value="2">黑色</option></select></label>
          </div>
          <div class="form-actions"><button class="primary-button" type="submit"><Plus :size="16" />{{ saving ? '正在登记' : '登记并锁定车辆' }}</button></div>
        </fieldset>
      </form>
    </div>
    <div v-else class="vehicle-bound-layout">
      <div class="vehicle-details">
        <div class="surface-heading"><h3>{{ vehicle.vehicleNo }}</h3><CarFront :size="20" /></div>
        <dl class="vehicle-summary">
          <div><dt>品牌 / 车型</dt><dd>{{ vehicle.brand }} {{ vehicle.model }}</dd></div>
          <div><dt>车辆类型</dt><dd>{{ types[vehicle.vehicleType] || '-' }}</dd></div>
          <div><dt>座位数</dt><dd>{{ vehicle.seats }}</dd></div>
          <div><dt>城市码</dt><dd>{{ vehicle.address }}</dd></div>
          <div><dt>车辆状态</dt><dd>{{ vehicle.state === 0 ? '启用' : '停用' }}</dd></div>
          <div><dt>锁定状态</dt><dd>已锁定当前账号</dd></div>
          <div><dt>定位模式</dt><dd>{{ localSimulation ? '本地模拟' : '高德轨迹' }}</dd></div>
          <div v-if="!localSimulation"><dt>定位状态</dt><dd>{{ vehicle.traceReady ? '已初始化' : '未初始化' }}</dd></div>
        </dl>
        <button v-if="!vehicle.traceReady" class="secondary-button full-button" :disabled="busy || vehicle.state !== 0" @click="initializeMap"><MapPin :size="16" />{{ saving ? '正在处理' : '初始化车辆定位' }}</button>
        <p class="vehicle-location-note"><MapPin :size="15" />车辆定位请到司机工作台上报，附近订单筛选使用那里最近一次上报的位置。</p>
      </div>
    </div>
  </section>
</template>

<style scoped>
.driver-vehicle { --line: #e4dfd5; --muted: #858d88; --red: #a63d34; min-width: 0; }
.vehicle-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding-bottom: 20px; border-bottom: 1px solid var(--line); margin-bottom: 24px; }
.vehicle-toolbar h2 { margin: 0; font-size: 20px; }
.vehicle-error { margin: 0 0 20px; padding: 12px 0; color: var(--red); font-size: 13px; line-height: 1.6; overflow-wrap: anywhere; }
.vehicle-registration { max-width: 980px; }
.vehicle-lock-card, .vehicle-new-form { padding: clamp(18px, 2.4vw, 26px); background: #fffdf8; border: 1px solid #ddd8cc; border-radius: 7px; }
.vehicle-lock-card { max-width: 620px; margin-bottom: 24px; }
.vehicle-help { margin: 0 0 20px; color: var(--muted); font-size: 13px; line-height: 1.7; }
.vehicle-divider { display: flex; align-items: center; gap: 16px; max-width: 620px; margin-bottom: 24px; color: var(--muted); font-size: 12px; }
.vehicle-divider::before, .vehicle-divider::after { content: ''; height: 1px; flex: 1; background: var(--line); }
.vehicle-fields { border: 0; padding: 0; margin: 0; min-width: 0; }
.field-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 18px 20px; }
.field { display: flex; flex-direction: column; gap: 8px; min-width: 0; }
.field > span { color: var(--muted); font-size: 11px; font-weight: 650; letter-spacing: .02em; }
.field input, .field select { width: 100%; min-height: 42px; padding: 0 12px; color: #1f2a26; background: #fff; border: 1px solid #d7d2c6; border-radius: 5px; font-size: 13px; }
.field input::placeholder { color: #a3aaa5; }
.field input:focus, .field select:focus { border-color: #d98213; outline: 2px solid rgba(217, 130, 19, .16); outline-offset: 1px; }
.vehicle-lock-card .field { max-width: 420px; }
.form-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 22px; }
.vehicle-lock-card .form-actions { max-width: 420px; }
.primary-button, .secondary-button { min-height: 40px; display: inline-flex; align-items: center; justify-content: center; gap: 8px; padding: 0 16px; border-radius: 5px; font-size: 12px; font-weight: 650; }
.primary-button { color: #fff; background: #247055; border: 1px solid #1d6a51; }
.secondary-button { color: #525c56; background: #f6f3ec; border: 1px solid #d8d3c8; }
.primary-button:hover:not(:disabled) { background: #1d624a; }
.secondary-button:hover:not(:disabled) { background: #eeeae1; }
.full-button { width: 100%; }
.empty-state { min-height: 220px; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 14px; padding: 24px; color: var(--muted); background: #fffdf8; border: 1px dashed #d8d3c8; border-radius: 7px; font-size: 13px; }
.vehicle-bound-layout { max-width: 760px; }
.vehicle-details { min-width: 0; }
.surface-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 20px; }
.surface-heading h3 { margin: 0; font-size: 18px; letter-spacing: 0; overflow-wrap: anywhere; }
.surface-heading svg { color: #c57a16; }
.vehicle-summary { margin: 0 0 24px; }
.vehicle-summary > div { display: flex; justify-content: space-between; align-items: baseline; gap: 16px; padding: 14px 0; border-bottom: 1px solid var(--line); font-size: 13px; }
.vehicle-summary dt { color: var(--muted); flex-shrink: 0; }
.vehicle-summary dd { margin: 0; text-align: right; overflow-wrap: anywhere; }
.vehicle-location-note { display: flex; align-items: flex-start; gap: 8px; margin: 18px 0 0; color: var(--muted); font-size: 12px; line-height: 1.7; }
.vehicle-location-note svg { flex: none; margin-top: 1px; color: #c57a16; }
@media (max-width: 980px) { .field-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 680px) {
  .field-grid { grid-template-columns: 1fr; }
  .vehicle-toolbar { align-items: flex-start; }
  .vehicle-lock-card, .vehicle-new-form { padding: 18px 15px; }
  .form-actions { justify-content: stretch; }
  .form-actions .primary-button { width: 100%; }
}
</style>
