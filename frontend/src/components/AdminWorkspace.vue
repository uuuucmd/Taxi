<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import {
  Activity,
  CarFront,
  CheckCircle2,
  ClipboardList,
  Gauge,
  LoaderCircle,
  Plus,
  RefreshCw,
  Search,
  Settings2,
  ShieldCheck,
  UserRound,
  Users,
} from 'lucide-vue-next'
import WorkspaceShell from './WorkspaceShell.vue'
import { bossApi } from '../services/api'

const props = defineProps({ identity: { type: String, required: true } })
const emit = defineEmits(['logout'])

const active = ref('overview')
const loading = ref(false)
const submitting = ref(false)
const drivers = ref([])
const driverTotal = ref(0)
const search = ref('')
const notice = ref('')
const noticeType = ref('success')

const driverForm = reactive({
  driverName: '',
  driverPhone: '',
  address: '北京市',
  driverGender: 1,
  state: 1,
})

const vehicleForm = reactive({
  address: '北京市',
  vehicleNo: '',
  plateColor: '1',
  seats: 4,
  brand: '',
  model: '',
  vehicleType: '1',
  ownerName: '',
  vehicleColor: '1',
  state: 0,
})

const navItems = [
  { id: 'overview', label: '运营概览', icon: Gauge },
  { id: 'drivers', label: '司机管理', icon: Users },
  { id: 'vehicles', label: '车辆管理', icon: CarFront },
  { id: 'operations', label: '平台配置', icon: Settings2 },
]

const filteredDrivers = computed(() => {
  const keyword = search.value.trim().toLowerCase()
  if (!keyword) return drivers.value
  return drivers.value.filter((driver) =>
    String(driver.driverName || driver.name || '').toLowerCase().includes(keyword)
    || String(driver.driverPhone || driver.phone || '').includes(keyword),
  )
})

const activeDrivers = computed(() => drivers.value.filter((driver) => Number(driver.state ?? 1) === 1).length)
const inactiveDrivers = computed(() => Math.max(drivers.value.length - activeDrivers.value, 0))

function responseData(result) {
  return result?.data ?? result
}

function notify(text, type = 'success') {
  notice.value = text
  noticeType.value = type
  window.clearTimeout(notify.timer)
  notify.timer = window.setTimeout(() => (notice.value = ''), 4200)
}

function handleError(error, fallback) {
  if (error.status === 401) {
    emit('logout')
    return
  }
  notify(error.message || fallback, 'error')
}

function extractDrivers(data) {
  if (Array.isArray(data)) return data
  if (Array.isArray(data?.records)) return data.records
  if (Array.isArray(data?.list)) return data.list
  if (Array.isArray(data?.items)) return data.items
  return []
}

async function loadDrivers() {
  loading.value = true
  try {
    const data = responseData(await bossApi.drivers({ page: 1, pageSize: 100 }))
    drivers.value = extractDrivers(data)
    driverTotal.value = Number(data?.total ?? data?.totalCount ?? drivers.value.length)
  } catch (error) {
    handleError(error, '司机数据读取失败')
  } finally {
    loading.value = false
  }
}

async function addDriver() {
  if (!driverForm.driverName.trim() || !/^1\d{10}$/.test(driverForm.driverPhone.trim())) {
    notify('请填写司机姓名和有效手机号', 'error')
    return
  }
  submitting.value = true
  try {
    await bossApi.addDriver({ ...driverForm, driverPhone: driverForm.driverPhone.trim() })
    Object.assign(driverForm, { driverName: '', driverPhone: '', address: '北京市', driverGender: 1, state: 1 })
    notify('司机档案已创建')
    await loadDrivers()
  } catch (error) {
    handleError(error, '司机档案创建失败')
  } finally {
    submitting.value = false
  }
}

async function toggleDriver(driver) {
  const state = Number(driver.state ?? 1) === 1 ? 0 : 1
  try {
    await bossApi.updateDriver({ ...driver, state })
    notify(state === 1 ? '司机账号已启用' : '司机账号已停用')
    await loadDrivers()
  } catch (error) {
    handleError(error, '司机状态更新失败')
  }
}

async function addVehicle() {
  if (!vehicleForm.vehicleNo.trim() || !vehicleForm.brand.trim() || !vehicleForm.model.trim()) {
    notify('请填写车牌、品牌和车型', 'error')
    return
  }
  submitting.value = true
  try {
    await bossApi.addCar({ ...vehicleForm, vehicleNo: vehicleForm.vehicleNo.trim() })
    Object.assign(vehicleForm, {
      address: '北京市', vehicleNo: '', plateColor: '1', seats: 4, brand: '', model: '', vehicleType: '1', ownerName: '', vehicleColor: '1', state: 0,
    })
    notify('车辆档案已创建')
  } catch (error) {
    handleError(error, '车辆档案创建失败')
  } finally {
    submitting.value = false
  }
}

onMounted(loadDrivers)
</script>

<template>
  <WorkspaceShell
    role="boss"
    :identity="identity"
    :items="navItems"
    :active="active"
    @update:active="active = $event"
    @logout="emit('logout')"
  >
    <div v-if="notice" class="admin-notice" :class="noticeType">{{ notice }}</div>

    <section class="admin-intro">
      <div>
        <p class="admin-kicker"><ShieldCheck :size="14" /> OPERATIONS CONTROL</p>
        <h1>运营全局，一目了然。</h1>
        <p>管理司机档案、车辆资料与平台基础配置。</p>
      </div>
      <div class="admin-session"><span></span>{{ identity }} · 管理员权限</div>
    </section>

    <template v-if="active === 'overview'">
      <section class="admin-stats">
        <article>
          <span class="stat-icon green"><Users :size="19" /></span>
          <div><span>司机总数</span><strong>{{ driverTotal }}</strong><p>已收录的司机档案</p></div>
        </article>
        <article>
          <span class="stat-icon amber"><Activity :size="19" /></span>
          <div><span>启用司机</span><strong>{{ activeDrivers }}</strong><p>可进入司机端作业</p></div>
        </article>
        <article>
          <span class="stat-icon red"><UserRound :size="19" /></span>
          <div><span>停用司机</span><strong>{{ inactiveDrivers }}</strong><p>当前暂停登录或接单</p></div>
        </article>
      </section>

      <div class="admin-dashboard">
        <section class="admin-directory">
          <header class="admin-panel-heading">
            <div><span class="admin-section-kicker">DRIVER DIRECTORY</span><h2>司机目录</h2></div>
            <button class="refresh-button" type="button" :disabled="loading" @click="loadDrivers"><LoaderCircle v-if="loading" class="spin" :size="15" /><RefreshCw v-else :size="15" />刷新</button>
          </header>
          <div v-if="loading" class="admin-loading"><LoaderCircle class="spin" :size="20" />正在读取司机数据</div>
          <div v-else-if="drivers.length" class="driver-table">
            <div class="driver-table-head"><span>司机</span><span>城市</span><span>状态</span></div>
            <div v-for="driver in drivers.slice(0, 6)" :key="driver.id || driver.driverPhone" class="driver-table-row">
              <span class="driver-cell"><i>{{ String(driver.driverName || driver.name || '司').slice(0, 1) }}</i><span><strong>{{ driver.driverName || driver.name || '未命名司机' }}</strong><small>{{ driver.driverPhone || driver.phone || '未填写手机号' }}</small></span></span>
              <span>{{ driver.address || driver.city || '未填写' }}</span>
              <span><b :class="Number(driver.state ?? 1) === 1 ? 'active-tag' : 'inactive-tag'">{{ Number(driver.state ?? 1) === 1 ? '启用' : '停用' }}</b></span>
            </div>
          </div>
          <div v-else class="admin-empty"><ClipboardList :size="30" /><strong>还没有司机档案</strong><p>新增司机后会显示在目录中。</p></div>
        </section>

        <section class="quick-actions-panel">
          <span class="admin-section-kicker">QUICK ACTIONS</span>
          <h2>运营快捷入口</h2>
          <button type="button" @click="active = 'drivers'"><span><Users :size="18" /><strong>新增司机</strong><small>建立司机基础档案</small></span><Plus :size="16" /></button>
          <button type="button" @click="active = 'vehicles'"><span><CarFront :size="18" /><strong>录入车辆</strong><small>维护车辆与归属信息</small></span><Plus :size="16" /></button>
          <button type="button" @click="active = 'operations'"><span><Settings2 :size="18" /><strong>平台配置</strong><small>查看服务与角色配置</small></span><Plus :size="16" /></button>
        </section>
      </div>
    </template>

    <template v-else-if="active === 'drivers'">
      <div class="management-layout">
        <section class="admin-directory full-directory">
          <header class="admin-panel-heading">
            <div><span class="admin-section-kicker">DRIVER MANAGEMENT</span><h2>司机管理</h2></div>
            <label class="table-search"><Search :size="15" /><input v-model="search" placeholder="搜索姓名或手机号" /></label>
          </header>
          <div v-if="loading" class="admin-loading"><LoaderCircle class="spin" :size="20" />正在读取司机数据</div>
          <div v-else-if="filteredDrivers.length" class="driver-table managed">
            <div class="driver-table-head"><span>司机</span><span>城市</span><span>状态</span><span>操作</span></div>
            <div v-for="driver in filteredDrivers" :key="driver.id || driver.driverPhone" class="driver-table-row">
              <span class="driver-cell"><i>{{ String(driver.driverName || driver.name || '司').slice(0, 1) }}</i><span><strong>{{ driver.driverName || driver.name || '未命名司机' }}</strong><small>{{ driver.driverPhone || driver.phone || '未填写手机号' }}</small></span></span>
              <span>{{ driver.address || driver.city || '未填写' }}</span>
              <span><b :class="Number(driver.state ?? 1) === 1 ? 'active-tag' : 'inactive-tag'">{{ Number(driver.state ?? 1) === 1 ? '启用' : '停用' }}</b></span>
              <span><button class="table-action" type="button" @click="toggleDriver(driver)">{{ Number(driver.state ?? 1) === 1 ? '停用' : '启用' }}</button></span>
            </div>
          </div>
          <div v-else class="admin-empty"><ClipboardList :size="30" /><strong>没有匹配的司机</strong><p>调整搜索条件或新增司机档案。</p></div>
        </section>

        <section class="editor-panel">
          <header><div><span class="admin-section-kicker">NEW DRIVER</span><h2>新增司机</h2></div><UserRound :size="20" /></header>
          <form @submit.prevent="addDriver">
            <label><span>司机姓名</span><input v-model="driverForm.driverName" placeholder="输入姓名" /></label>
            <label><span>手机号码</span><input v-model="driverForm.driverPhone" inputmode="numeric" maxlength="11" placeholder="11 位手机号" /></label>
            <label><span>所在城市</span><input v-model="driverForm.address" placeholder="例如：北京市" /></label>
            <div class="split-fields">
              <label><span>性别</span><select v-model.number="driverForm.driverGender"><option :value="1">男</option><option :value="2">女</option></select></label>
              <label><span>状态</span><select v-model.number="driverForm.state"><option :value="1">启用</option><option :value="0">停用</option></select></label>
            </div>
            <button class="admin-primary" type="submit" :disabled="submitting"><LoaderCircle v-if="submitting" class="spin" :size="16" /><Plus v-else :size="16" />创建司机档案</button>
          </form>
        </section>
      </div>
    </template>

    <template v-else-if="active === 'vehicles'">
      <div class="vehicle-management">
        <section class="vehicle-intro-panel">
          <span class="vehicle-visual"><CarFront :size="38" /></span>
          <div><span class="admin-section-kicker">VEHICLE DIRECTORY</span><h2>车辆档案管理</h2><p>登记车辆品牌、车型、车牌和归属信息，供司机端完成车辆绑定。</p></div>
        </section>
        <section class="editor-panel vehicle-editor">
          <header><div><span class="admin-section-kicker">NEW VEHICLE</span><h2>录入车辆</h2></div><CarFront :size="20" /></header>
          <form @submit.prevent="addVehicle">
            <div class="form-grid two">
              <label><span>车牌号码</span><input v-model="vehicleForm.vehicleNo" placeholder="例如：京A·12345" /></label>
              <label><span>车辆归属</span><input v-model="vehicleForm.ownerName" placeholder="车主姓名" /></label>
              <label><span>车辆品牌</span><input v-model="vehicleForm.brand" placeholder="例如：比亚迪" /></label>
              <label><span>车辆型号</span><input v-model="vehicleForm.model" placeholder="例如：秦 PLUS" /></label>
              <label><span>所在城市</span><input v-model="vehicleForm.address" placeholder="例如：北京市" /></label>
              <label><span>座位数量</span><input v-model.number="vehicleForm.seats" type="number" min="1" max="9" /></label>
              <label><span>车辆颜色</span><select v-model="vehicleForm.vehicleColor"><option value="1">白色</option><option value="2">黑色</option><option value="3">银色</option></select></label>
              <label><span>运营状态</span><select v-model.number="vehicleForm.state"><option :value="0">启用</option><option :value="1">停用</option></select></label>
            </div>
            <button class="admin-primary" type="submit" :disabled="submitting"><LoaderCircle v-if="submitting" class="spin" :size="16" /><Plus v-else :size="16" />创建车辆档案</button>
          </form>
        </section>
      </div>
    </template>

    <template v-else>
      <section class="operations-panel">
        <header><div><span class="admin-section-kicker">ACCESS MATRIX</span><h2>角色与功能权限</h2></div><ShieldCheck :size="23" /></header>
        <div class="permission-matrix">
          <div class="permission-head"><span>工作台</span><span>核心功能</span><span>登录方式</span><span>状态</span></div>
          <div><span><UserRound :size="16" />客户端</span><span>叫车、估价、行程与支付</span><span>手机验证码</span><b class="permission-on"><CheckCircle2 :size="13" />已启用</b></div>
          <div><span><CarFront :size="16" />司机端</span><span>接单、车辆状态与营运管理</span><span>手机验证码</span><b class="permission-on"><CheckCircle2 :size="13" />已启用</b></div>
          <div><span><ShieldCheck :size="16" />管理员</span><span>司机、车辆与平台配置</span><span>账号密码</span><b class="permission-on"><CheckCircle2 :size="13" />已启用</b></div>
        </div>
        <div class="platform-cards">
          <div><span>API 接入服务</span><strong>客户端 / 司机端 / 管理端</strong></div>
          <div><span>数据服务</span><strong>Nacos · Redis · MySQL</strong></div>
          <div><span>当前账号</span><strong>{{ props.identity }}</strong></div>
        </div>
      </section>
    </template>
  </WorkspaceShell>
</template>

<style scoped>
.admin-notice { position: fixed; top: 82px; right: 28px; z-index: 80; max-width: 360px; padding: 12px 15px; color: #fff; background: #23684e; border-radius: 5px; box-shadow: 0 12px 30px rgba(20, 35, 28, .18); font-size: 11px; }
.admin-notice.error { background: #a63d34; }
.admin-intro, .admin-kicker, .admin-session, .admin-panel-heading, .refresh-button, .driver-table-head, .driver-table-row, .driver-cell, .quick-actions-panel button, .admin-section-kicker, .table-search, .editor-panel header, .split-fields, .admin-primary, .vehicle-intro-panel, .permission-head, .permission-matrix > div, .permission-matrix b, .operations-panel header { display: flex; align-items: center; }
.admin-intro { justify-content: space-between; gap: 24px; margin-bottom: 29px; }
.admin-kicker { gap: 7px; margin: 0 0 11px; color: #9a5b51; font-size: 9px; font-weight: 700; letter-spacing: .14em; }
.admin-intro h1 { margin: 0; font-size: clamp(30px, 4vw, 45px); line-height: 1.13; font-weight: 650; letter-spacing: 0; }
.admin-intro p { margin: 11px 0 0; color: #7e8781; font-size: 12px; }
.admin-session { gap: 8px; flex: none; padding: 9px 12px; color: #77443c; background: #f5e8e4; border: 1px solid #e5cec7; border-radius: 4px; font-size: 9px; }
.admin-session span { width: 7px; height: 7px; background: #c8463a; border-radius: 50%; }
.admin-stats { display: grid; grid-template-columns: repeat(3, 1fr); gap: 15px; }
.admin-stats article { display: flex; gap: 13px; padding: 20px; background: #fffdf8; border: 1px solid #ddd8cc; border-radius: 6px; }
.stat-icon { width: 39px; height: 39px; display: grid; place-items: center; flex: none; border-radius: 5px; }
.stat-icon.green { color: #287156; background: #e6f0ea; }
.stat-icon.amber { color: #a66a17; background: #f5e9d5; }
.stat-icon.red { color: #a64b40; background: #f5e7e4; }
.admin-stats article div { min-width: 0; }
.admin-stats span, .admin-stats strong, .admin-stats p { display: block; }
.admin-stats div > span { color: #8a918c; font-size: 9px; }
.admin-stats strong { margin-top: 4px; font-size: 24px; font-weight: 650; }
.admin-stats p { margin: 4px 0 0; color: #9aa09c; font-size: 9px; }
.admin-dashboard { display: grid; grid-template-columns: minmax(0, 1.55fr) minmax(270px, .65fr); gap: 19px; margin-top: 19px; }
.admin-directory, .quick-actions-panel, .editor-panel, .vehicle-intro-panel, .operations-panel { padding: 23px; background: #fffdf8; border: 1px solid #ddd8cc; border-radius: 6px; }
.admin-panel-heading { justify-content: space-between; gap: 15px; }
.admin-section-kicker { display: block; margin-bottom: 6px; color: #999d99; font-size: 8px; font-weight: 700; letter-spacing: .14em; }
.admin-panel-heading h2, .quick-actions-panel h2, .editor-panel h2, .vehicle-intro-panel h2, .operations-panel h2 { margin: 0; font-size: 18px; font-weight: 650; }
.refresh-button { min-height: 34px; gap: 6px; padding: 0 10px; border: 1px solid #d8d3c8; border-radius: 4px; color: #606963; background: #f5f2eb; font-size: 9px; }
.driver-table { margin-top: 17px; }
.driver-table-head, .driver-table-row { display: grid; grid-template-columns: minmax(170px, 1.4fr) minmax(80px, .7fr) 70px; gap: 12px; align-items: center; }
.driver-table-head { min-height: 31px; color: #9a9f9b; font-size: 8px; border-bottom: 1px solid #e4dfd5; }
.driver-table-row { min-height: 64px; border-bottom: 1px solid #e8e3d9; color: #65706a; font-size: 9px; }
.driver-cell { gap: 9px; min-width: 0; }
.driver-cell i { width: 30px; height: 30px; display: grid; place-items: center; flex: none; color: #fff; background: #35423c; border-radius: 50%; font-size: 10px; font-style: normal; font-weight: 700; }
.driver-cell span { min-width: 0; }
.driver-cell strong, .driver-cell small { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.driver-cell strong { color: #303a35; font-size: 10px; font-weight: 650; }
.driver-cell small { margin-top: 3px; color: #949a96; font-size: 8px; }
.active-tag, .inactive-tag { display: inline-block; padding: 4px 7px; border-radius: 4px; font-size: 8px; font-weight: 500; }
.active-tag { color: #347054; background: #e8f1eb; }
.inactive-tag { color: #9d4b40; background: #f6e7e3; }
.admin-loading, .admin-empty { min-height: 230px; display: flex; flex-direction: column; align-items: center; justify-content: center; color: #8c938e; font-size: 10px; text-align: center; }
.admin-loading { flex-direction: row; gap: 8px; }
.admin-empty svg { color: #c9a467; }
.admin-empty strong { margin-top: 10px; color: #3c4641; font-size: 11px; }
.admin-empty p { margin: 5px 0 0; font-size: 9px; }
.quick-actions-panel h2 { margin-bottom: 18px; }
.quick-actions-panel button { width: 100%; min-height: 65px; justify-content: space-between; gap: 12px; padding: 10px 0; border: 0; border-top: 1px solid #e4dfd5; color: #5f6863; background: transparent; text-align: left; }
.quick-actions-panel button > span { display: flex; align-items: center; gap: 10px; flex: 1; }
.quick-actions-panel button svg:first-child { color: #a66257; }
.quick-actions-panel button span strong, .quick-actions-panel button span small { display: block; }
.quick-actions-panel button span strong { color: #333d38; font-size: 10px; font-weight: 650; }
.quick-actions-panel button span small { margin-top: 4px; color: #969c98; font-size: 8px; }
.quick-actions-panel button > svg { color: #a9aeaa; }
.management-layout { display: grid; grid-template-columns: minmax(0, 1.45fr) minmax(300px, .65fr); gap: 19px; align-items: start; }
.full-directory { min-width: 0; }
.table-search { min-height: 35px; gap: 7px; padding: 0 10px; border: 1px solid #d8d3c8; border-radius: 4px; color: #89908b; background: #f7f4ed; }
.table-search input { width: 170px; border: 0; outline: 0; color: #35403a; background: transparent; font-size: 9px; }
.driver-table.managed .driver-table-head, .driver-table.managed .driver-table-row { grid-template-columns: minmax(150px, 1.4fr) minmax(70px, .6fr) 60px 55px; }
.table-action { min-height: 28px; padding: 0 9px; border: 1px solid #d4cfc3; border-radius: 4px; color: #5e6762; background: #f6f3ec; font-size: 8px; }
.editor-panel { position: sticky; top: 90px; }
.editor-panel header { justify-content: space-between; gap: 14px; margin-bottom: 19px; }
.editor-panel header svg { color: #c8463a; }
.editor-panel form { display: grid; gap: 13px; }
.editor-panel label { display: grid; gap: 6px; color: #5d6661; font-size: 9px; font-weight: 600; }
.editor-panel input, .editor-panel select { width: 100%; height: 39px; padding: 0 10px; border: 1px solid #d9d4c9; border-radius: 4px; outline: 0; color: #303a35; background: #f9f7f1; font-size: 10px; }
.editor-panel input:focus, .editor-panel select:focus { border-color: #c8463a; box-shadow: 0 0 0 3px rgba(200, 70, 58, .07); }
.split-fields { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.admin-primary { min-height: 41px; justify-content: center; gap: 7px; margin-top: 4px; border: 1px solid #8f332b; border-radius: 4px; color: #fff; background: #a33f35; font-size: 10px; font-weight: 650; }
.vehicle-management { display: grid; grid-template-columns: minmax(280px, .7fr) minmax(0, 1.3fr); gap: 19px; align-items: start; }
.vehicle-intro-panel { min-height: 250px; align-items: flex-start; gap: 17px; }
.vehicle-visual { width: 70px; height: 70px; display: grid; place-items: center; flex: none; color: #aa5c51; background: #f3e4e0; border-radius: 7px; }
.vehicle-intro-panel p { margin: 13px 0 0; color: #858d88; font-size: 10px; line-height: 1.7; }
.vehicle-editor { position: static; }
.form-grid.two { display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px; }
.vehicle-editor .admin-primary { grid-column: 1 / -1; }
.vehicle-editor form { margin-top: 5px; }
.operations-panel { padding: clamp(23px, 3vw, 32px); }
.operations-panel header { justify-content: space-between; gap: 15px; margin-bottom: 24px; }
.operations-panel header svg { color: #c8463a; }
.permission-matrix { border: 1px solid #e1dcd2; border-radius: 5px; overflow: hidden; }
.permission-matrix > div { display: grid; grid-template-columns: .7fr 1.6fr .8fr .65fr; gap: 12px; min-height: 58px; padding: 0 16px; align-items: center; border-top: 1px solid #e7e2d8; color: #67716b; font-size: 9px; }
.permission-matrix > div:first-child { border-top: 0; }
.permission-matrix .permission-head { min-height: 37px; color: #9a9f9b; background: #f7f4ed; font-size: 8px; }
.permission-matrix > div > span:first-child { display: flex; align-items: center; gap: 7px; color: #36403b; font-weight: 650; }
.permission-matrix b { gap: 4px; color: #347054; font-weight: 500; }
.platform-cards { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin-top: 16px; }
.platform-cards div { padding: 17px; background: #f7f4ed; border: 1px solid #e1dcd2; border-radius: 5px; }
.platform-cards span, .platform-cards strong { display: block; }
.platform-cards span { color: #939995; font-size: 8px; }
.platform-cards strong { margin-top: 7px; color: #39433e; font-size: 10px; font-weight: 650; }
.spin { animation: spin .8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

@media (max-width: 1080px) {
  .admin-dashboard, .management-layout, .vehicle-management { grid-template-columns: 1fr; }
  .editor-panel { position: static; }
}

@media (max-width: 720px) {
  .admin-intro { align-items: flex-start; flex-direction: column; }
  .admin-session { align-self: flex-start; }
  .admin-stats { grid-template-columns: 1fr; }
  .driver-table-head { display: none; }
  .driver-table-row, .driver-table.managed .driver-table-row { grid-template-columns: 1fr auto; padding: 11px 0; gap: 8px; }
  .driver-table-row > span:nth-child(2) { display: none; }
  .driver-table.managed .driver-table-row > span:nth-child(4) { grid-column: 2; grid-row: 2; justify-self: end; }
  .permission-matrix { overflow-x: auto; }
  .permission-matrix > div { min-width: 650px; }
  .platform-cards { grid-template-columns: 1fr; }
  .form-grid.two { grid-template-columns: 1fr; }
  .admin-notice { top: 72px; right: 12px; left: 12px; max-width: none; }
}

@media (max-width: 480px) {
  .admin-directory, .quick-actions-panel, .editor-panel, .vehicle-intro-panel, .operations-panel { padding: 18px 15px; }
  .admin-panel-heading { align-items: flex-start; flex-direction: column; }
  .table-search { width: 100%; }
  .table-search input { width: 100%; }
  .split-fields { grid-template-columns: 1fr; }
  .vehicle-intro-panel { flex-direction: column; min-height: 0; }
}
</style>
