<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import AMapLoader from '@amap/amap-jsapi-loader'
import { MapPinned } from 'lucide-vue-next'

const props = defineProps({
  longitude: { type: [Number, String], default: null },
  latitude: { type: [Number, String], default: null },
  resolveAddress: { type: Boolean, default: false },
})
const emit = defineEmits(['pick', 'error'])

const container = ref(null)
const loading = ref(false)
const resolving = ref(false)
const message = ref('')
let map = null
let marker = null
let geocoder = null
let resolveRequestId = 0

const key = import.meta.env.VITE_AMAP_JS_KEY
const securityCode = import.meta.env.VITE_AMAP_SECURITY_CODE

function toNumber(value) {
  if (value === null || value === undefined || String(value).trim() === '') return null
  const number = Number(value)
  return Number.isFinite(number) ? number : null
}

function currentPosition() {
  const longitude = toNumber(props.longitude)
  const latitude = toNumber(props.latitude)
  if (longitude !== null && latitude !== null
      && Math.abs(longitude) <= 180 && Math.abs(latitude) <= 85.051128) {
    return [longitude, latitude]
  }
  return [116.397428, 39.90923]
}

function pickFromLngLat(lngLat) {
  const longitude = Number(lngLat.getLng().toFixed(6))
  const latitude = Number(lngLat.getLat().toFixed(6))
  if (marker) marker.setPosition([longitude, latitude])
  message.value = ''

  if (!props.resolveAddress || !geocoder) {
    emit('pick', { longitude, latitude })
    return
  }

  const requestId = ++resolveRequestId
  resolving.value = true
  geocoder.getAddress([longitude, latitude], (status, result) => {
    if (requestId !== resolveRequestId) return
    resolving.value = false
    const regeocode = result?.regeocode
    const districtCode = String(regeocode?.addressComponent?.adcode || '')
    if (status !== 'complete' || !regeocode || !/^\d{6}$/.test(districtCode)) {
      message.value = '地图点位已选中，但地址解析失败，请重新选点或手动输入地址。'
      emit('error', message.value)
      return
    }
    const cityCode = /^(11|12|31|50)/.test(districtCode)
      ? `${districtCode.slice(0, 2)}0000`
      : `${districtCode.slice(0, 4)}00`
    emit('pick', {
      longitude,
      latitude,
      address: regeocode.formattedAddress || '',
      districtCode,
      cityCode,
    })
  })
}

async function initMap() {
  if (!key) {
    message.value = '未配置 VITE_AMAP_JS_KEY，地图点选不可用；请在仓库根目录 .env 中配置后重启前端。'
    return
  }
  loading.value = true
  try {
    if (securityCode) window._AMapSecurityConfig = { securityJsCode: securityCode }
    const AMap = await AMapLoader.load({
      key,
      version: '2.0',
      plugins: props.resolveAddress ? ['AMap.Geocoder'] : [],
    })
    if (props.resolveAddress) {
      geocoder = new AMap.Geocoder({ city: '全国' })
    }
    const position = currentPosition()
    map = new AMap.Map(container.value, { zoom: 13, center: position, viewMode: '2D' })
    marker = new AMap.Marker({ position, draggable: true, cursor: 'move' })
    map.add(marker)
    map.on('click', (event) => pickFromLngLat(event.lnglat))
    marker.on('dragend', () => pickFromLngLat(marker.getPosition()))
  } catch (error) {
    message.value = error?.message || '地图加载失败，请检查 Key、安全密钥和域名白名单。'
    emit('error', message.value)
  } finally {
    loading.value = false
  }
}

watch(() => [props.longitude, props.latitude], () => {
  if (!map || !marker) return
  const position = currentPosition()
  marker.setPosition(position)
  map.setCenter(position)
})

onMounted(initMap)
onBeforeUnmount(() => {
  resolveRequestId += 1
  if (map) map.destroy()
  map = null
  marker = null
  geocoder = null
})
</script>

<template>
  <div class="amap-picker">
    <div v-if="key" ref="container" class="amap-picker-canvas"></div>
    <div v-else class="amap-picker-placeholder">
      <MapPinned :size="26" />
      <strong>地图点选需要高德 JS API Key</strong>
      <p>在仓库根目录 .env 中配置 VITE_AMAP_JS_KEY 和 VITE_AMAP_SECURITY_CODE 后重启前端。</p>
    </div>
    <div v-if="loading || resolving" class="amap-picker-overlay">{{ resolving ? '正在解析地址…' : '地图加载中…' }}</div>
    <p v-if="message" class="amap-picker-message">{{ message }}</p>
    <p v-else class="amap-picker-hint">{{ resolveAddress ? '点击地图选点，或拖动标记微调；地址会自动回填。' : '点击地图选点，或拖动标记微调；坐标会同步到上方输入框。' }}</p>
  </div>
</template>

<style scoped>
.amap-picker { position: relative; margin-top: 14px; background: #fbf9f3; border: 1px solid #d8d3c8; border-radius: 6px; overflow: hidden; }
.amap-picker-canvas { width: 100%; height: 260px; }
.amap-picker-placeholder { min-height: 160px; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8px; padding: 20px; color: #8b928d; text-align: center; }
.amap-picker-placeholder strong { color: #59635d; font-size: 12px; }
.amap-picker-placeholder p { margin: 0; font-size: 10px; line-height: 1.6; }
.amap-picker-overlay { position: absolute; top: 0; left: 0; right: 0; height: 260px; display: grid; place-items: center; color: #59635d; background: rgba(251, 249, 243, .72); font-size: 11px; }
.amap-picker-message, .amap-picker-hint { margin: 0; padding: 9px 12px; border-top: 1px solid #e4dfd4; font-size: 9px; line-height: 1.6; }
.amap-picker-message { color: #a63d34; }
.amap-picker-hint { color: #8b928d; }
</style>
