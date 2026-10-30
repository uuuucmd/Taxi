<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ChevronLeft, ChevronRight, Pencil, Plus, RefreshCw, RotateCcw, Save, Search, Users, X } from 'lucide-vue-next'
import { bossApi } from '../services/api'

const emit = defineEmits(['add', 'activity'])
const keyword = ref('')
const state = ref('')
const appliedKeyword = ref('')
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const records = ref([])
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const editError = ref('')
const editor = ref(null)
const edit = reactive({ id: '', driverName: '', driverPhone: '', address: '', driverGender: '', state: '' })
const pageCount = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))
let requestVersion = 0
let disposed = false

async function loadDrivers() {
  const version = ++requestVersion
  loading.value = true
  error.value = ''
  try {
    const result = await bossApi.drivers({ page: page.value, pageSize: pageSize.value, keyword: appliedKeyword.value, state: state.value })
    if (disposed || version !== requestVersion) return
    const data = result?.data
    if (!Array.isArray(data?.records) || !Number.isSafeInteger(data.total) || data.total < 0) {
      throw new Error('未获取到有效的司机列表，请刷新后重试')
    }
    total.value = data.total
    if (page.value > pageCount.value) {
      page.value = pageCount.value
      await loadDrivers()
      return
    }
    records.value = data.records
  } catch (cause) {
    if (disposed || version !== requestVersion) return
    records.value = []
    total.value = 0
    error.value = cause.message || '读取司机列表失败，请稍后重试'
  } finally {
    if (version === requestVersion) loading.value = false
  }
}

function search() {
  page.value = 1
  appliedKeyword.value = keyword.value.trim()
  loadDrivers()
}

function reset() {
  keyword.value = ''
  state.value = ''
  search()
}

function changePage(value) {
  if (loading.value || value < 1 || value > pageCount.value) return
  page.value = value
  loadDrivers()
}

function createdTime(value) {
  if (!value) return '-'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '-' : date.toLocaleDateString('zh-CN')
}

async function openEditor(driver) {
  Object.assign(edit, {
    id: driver.id,
    driverName: driver.driverName || '',
    driverPhone: driver.driverPhone || '',
    address: driver.address || '',
    driverGender: driver.driverGender ?? '',
    state: driver.state ?? '',
  })
  editError.value = ''
  await nextTick()
  editor.value.showModal()
}

function closeEditor() {
  if (!saving.value) editor.value.close()
}

async function saveDriver() {
  if (saving.value) return
  saving.value = true
  editError.value = ''
  try {
    const body = {
      id: edit.id,
      driverName: edit.driverName.trim(),
      driverPhone: edit.driverPhone.trim(),
      address: edit.address.trim(),
      state: Number(edit.state),
    }
    if (edit.driverGender !== '') body.driverGender = Number(edit.driverGender)
    await bossApi.updateDriver(body)
    if (disposed) return
    editor.value.close()
    emit('activity', { label: '修改司机', status: 'success', detail: '司机档案已更新' })
    await loadDrivers()
  } catch (cause) {
    if (disposed) return
    editError.value = cause.message || '保存失败，请稍后重试'
  } finally {
    saving.value = false
  }
}

onMounted(loadDrivers)
onBeforeUnmount(() => { disposed = true; requestVersion += 1 })
</script>

<template>
  <section class="boss-drivers" aria-label="司机列表" :aria-busy="loading">
    <div class="boss-drivers-heading">
      <div><span class="section-kicker">DRIVER DIRECTORY</span><h2>司机列表 <span>{{ total }} 位</span></h2></div>
      <div class="boss-drivers-commands">
        <button class="icon-button" title="刷新司机列表" aria-label="刷新司机列表" :disabled="loading" @click="loadDrivers"><RefreshCw :size="17" /></button>
        <button class="primary-button" @click="emit('add')"><Plus :size="16" />新增司机</button>
      </div>
    </div>
    <form class="boss-drivers-filters" @submit.prevent="search">
      <label class="field boss-driver-search"><span>姓名 / 手机号</span><input v-model="keyword" type="search" maxlength="100" placeholder="搜索司机" /></label>
      <label class="field"><span>账号状态</span><select v-model="state" :disabled="loading" @change="search"><option value="">全部状态</option><option value="1">启用</option><option value="0">停用</option></select></label>
      <button class="secondary-button" type="submit" :disabled="loading"><Search :size="16" />搜索</button>
      <button class="icon-button" type="button" title="重置筛选" aria-label="重置筛选" :disabled="loading" @click="reset"><RotateCcw :size="17" /></button>
    </form>
    <p v-if="error" class="boss-drivers-error" role="alert">{{ error }}</p>
    <p v-if="loading" class="boss-drivers-loading" role="status">正在读取司机列表...</p>
    <div v-if="records.length" class="boss-drivers-table-wrap" tabindex="0" aria-label="司机资料表格">
      <table class="boss-drivers-table">
        <thead><tr><th scope="col">姓名</th><th scope="col">手机号</th><th scope="col">所在城市</th><th scope="col">性别</th><th scope="col">账号状态</th><th scope="col">创建日期</th><th scope="col">操作</th></tr></thead>
        <tbody>
          <tr v-for="driver in records" :key="driver.id">
            <td class="boss-driver-name">{{ driver.driverName || '未填写' }}</td>
            <td class="boss-driver-phone">{{ driver.driverPhone || '-' }}</td>
            <td>{{ driver.address || '-' }}</td>
            <td>{{ driver.driverGender === 1 ? '男' : driver.driverGender === 2 ? '女' : '未填写' }}</td>
            <td><span class="boss-driver-state" :class="{ enabled: driver.state === 1 }">{{ driver.state === 1 ? '启用' : driver.state === 0 ? '停用' : '未知' }}</span></td>
            <td>{{ createdTime(driver.gmtCreate) }}</td>
            <td><button class="icon-button" :title="`编辑${driver.driverName || '司机'}`" :aria-label="`编辑${driver.driverName || driver.driverPhone || '司机'}`" :disabled="loading" @click="openEditor(driver)"><Pencil :size="15" /></button></td>
          </tr>
        </tbody>
      </table>
    </div>
    <div v-else-if="!loading && !error" class="empty-state"><Users :size="28" /><span>{{ appliedKeyword || state !== '' ? '没有符合条件的司机' : '暂无司机档案' }}</span></div>
    <div class="boss-drivers-pagination">
      <span>共 {{ total }} 位司机</span>
      <label class="boss-drivers-page-size">每页<select v-model.number="pageSize" aria-label="每页司机数量" :disabled="loading" @change="search"><option :value="10">10</option><option :value="20">20</option><option :value="50">50</option></select>位</label>
      <div class="boss-drivers-page-buttons">
        <button class="icon-button" title="上一页" aria-label="上一页" :disabled="loading || page <= 1" @click="changePage(page - 1)"><ChevronLeft :size="18" /></button>
        <span aria-live="polite">{{ page }} / {{ pageCount }}</span>
        <button class="icon-button" title="下一页" aria-label="下一页" :disabled="loading || page >= pageCount" @click="changePage(page + 1)"><ChevronRight :size="18" /></button>
      </div>
    </div>
    <dialog ref="editor" class="boss-driver-editor" aria-labelledby="driver-editor-title" @cancel="saving && $event.preventDefault()">
      <form @submit.prevent="saveDriver">
        <div class="boss-drivers-heading"><h2 id="driver-editor-title">编辑司机</h2><button class="icon-button" type="button" title="关闭编辑" aria-label="关闭编辑" :disabled="saving" @click="closeEditor"><X :size="18" /></button></div>
        <fieldset :disabled="saving" class="boss-driver-editor-fields">
          <label class="field"><span>司机姓名</span><input v-model="edit.driverName" required maxlength="64" /></label>
          <label class="field"><span>手机号</span><input v-model="edit.driverPhone" required type="tel" inputmode="tel" pattern="1[0-9]{10}" maxlength="11" /></label>
          <label class="field"><span>所在城市</span><input v-model="edit.address" maxlength="32" /></label>
          <label class="field"><span>性别</span><select v-model="edit.driverGender"><option value="">未填写</option><option :value="1">男</option><option :value="2">女</option></select></label>
          <label class="field"><span>账号状态</span><select v-model="edit.state" required><option value="" disabled>请选择</option><option :value="1">启用</option><option :value="0">停用</option></select></label>
        </fieldset>
        <p v-if="editError" class="boss-drivers-error" role="alert">{{ editError }}</p>
        <div class="form-actions"><button type="button" class="secondary-button" :disabled="saving" @click="closeEditor"><X :size="16" />取消</button><button type="submit" class="primary-button" :disabled="saving"><Save :size="16" />{{ saving ? '正在保存...' : '保存修改' }}</button></div>
      </form>
    </dialog>
  </section>
</template>
