<script setup>
import { computed, onBeforeUnmount, reactive, ref } from 'vue'
import {
  ArrowRight,
  CarFront,
  CheckCircle2,
  KeyRound,
  LoaderCircle,
  LockKeyhole,
  MapPin,
  Phone,
  ShieldCheck,
  Smartphone,
} from 'lucide-vue-next'
import { bossApi, clearTokens, driverApi, passengerApi, saveTokens } from '../services/api'

const emit = defineEmits(['authenticated'])

const roles = [
  {
    id: 'passenger',
    label: '客户端',
    title: '乘客登录',
    description: '叫车、查看行程与完成支付',
    icon: Smartphone,
    accent: '#0f8b7f',
  },
  {
    id: 'driver',
    label: '司机端',
    title: '司机登录',
    description: '接单、车辆状态与营运管理',
    icon: CarFront,
    accent: '#d98213',
  },
  {
    id: 'boss',
    label: '管理员',
    title: '管理登录',
    description: '司机、车辆与运营数据管理',
    icon: ShieldCheck,
    accent: '#c8463a',
  },
]

const role = ref('passenger')
const form = reactive({
  phone: '',
  code: '',
  username: 'admin',
  password: '',
})
const busy = ref(false)
const sendingCode = ref(false)
const countdown = ref(0)
const error = ref('')
const message = ref('')
let countdownTimer

const roleMeta = computed(() => roles.find((item) => item.id === role.value))
const isPhoneRole = computed(() => role.value === 'passenger' || role.value === 'driver')

function selectRole(nextRole) {
  if (busy.value) return
  role.value = nextRole
  error.value = ''
  message.value = ''
}

function responseData(result) {
  return result?.data ?? result
}

function startCountdown() {
  countdown.value = 60
  window.clearInterval(countdownTimer)
  countdownTimer = window.setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0) window.clearInterval(countdownTimer)
  }, 1000)
}

async function sendCode() {
  error.value = ''
  message.value = ''
  const phone = form.phone.trim()
  if (!/^1\d{10}$/.test(phone)) {
    error.value = '请输入 11 位手机号'
    return
  }

  sendingCode.value = true
  try {
    const result = role.value === 'passenger'
      ? await passengerApi.requestCode(phone)
      : await driverApi.requestCode(phone)
    const devCode = String(responseData(result) ?? '').trim()
    startCountdown()
    if (/^\d{6}$/.test(devCode)) {
      form.code = devCode
      message.value = `本地开发验证码：${devCode}`
    } else {
      message.value = '验证码已发送'
    }
  } catch (requestError) {
    error.value = requestError.message || '验证码发送失败'
  } finally {
    sendingCode.value = false
  }
}

async function submit() {
  error.value = ''
  message.value = ''
  busy.value = true
  try {
    let result
    let identity

    if (role.value === 'passenger') {
      const phone = form.phone.trim()
      if (!/^1\d{10}$/.test(phone) || !form.code.trim()) throw new Error('请输入手机号和验证码')
      result = await passengerApi.login(phone, form.code.trim())
      identity = phone
    } else if (role.value === 'driver') {
      const phone = form.phone.trim()
      if (!/^1\d{10}$/.test(phone) || !form.code.trim()) throw new Error('请输入手机号和验证码')
      result = await driverApi.login(phone, form.code.trim())
      identity = phone
    } else {
      const username = form.username.trim()
      if (!username || !form.password) throw new Error('请输入管理员账号和密码')
      result = await bossApi.login(username, form.password)
      identity = username
    }

    const tokenData = responseData(result)
    if (!tokenData?.accessToken) throw new Error('登录成功，但未收到访问令牌')

    clearTokens(role.value)
    saveTokens(role.value, tokenData)
    emit('authenticated', {
      role: role.value,
      identity,
      displayName: identity,
      loggedInAt: new Date().toISOString(),
    })
  } catch (submitError) {
    error.value = submitError.message || '登录失败，请稍后重试'
  } finally {
    busy.value = false
  }
}

onBeforeUnmount(() => window.clearInterval(countdownTimer))
</script>

<template>
  <div class="login-shell" :style="{ '--role-accent': roleMeta.accent }">
    <aside class="login-story">
      <div class="login-brand">
        <span class="brand-symbol"><MapPin :size="21" /></span>
        <div>
          <strong>FlyTaxi</strong>
          <span>CITY MOBILITY NETWORK</span>
        </div>
      </div>

      <div class="story-copy">
        <p class="story-kicker">ONE CITY · THREE WORKSPACES</p>
        <h1>每一次出发，<br>都有清晰的去向。</h1>
        <p class="story-description">统一身份入口连接乘客、司机与运营团队，让每段行程和每项调度都有专属工作台。</p>
      </div>

      <div class="route-map" aria-hidden="true">
        <span class="map-road road-a"></span>
        <span class="map-road road-b"></span>
        <span class="map-road road-c"></span>
        <span class="map-node node-start"><MapPin :size="16" /></span>
        <span class="map-node node-car"><CarFront :size="17" /></span>
        <span class="map-node node-end"><CheckCircle2 :size="16" /></span>
        <div class="map-caption">
          <span>城区运力在线</span>
          <strong>24 / 7</strong>
        </div>
      </div>

      <div class="story-footer">
        <span class="status-dot"></span>
        <span>服务网络运行正常</span>
      </div>
    </aside>

    <main class="login-main">
      <div class="login-topline">
        <span>统一身份认证</span>
        <span>{{ new Date().toLocaleDateString('zh-CN', { month: 'long', day: 'numeric' }) }}</span>
      </div>

      <section class="login-card">
        <header class="login-heading">
          <span class="heading-icon"><component :is="roleMeta.icon" :size="21" /></span>
          <div>
            <p>欢迎回来</p>
            <h2>{{ roleMeta.title }}</h2>
          </div>
        </header>

        <div class="role-picker" role="tablist" aria-label="登录角色">
          <button
            v-for="item in roles"
            :key="item.id"
            type="button"
            class="role-option"
            :class="{ active: role === item.id }"
            :aria-selected="role === item.id"
            role="tab"
            @click="selectRole(item.id)"
          >
            <component :is="item.icon" :size="18" />
            <span>{{ item.label }}</span>
          </button>
        </div>

        <p class="role-description">{{ roleMeta.description }}</p>

        <form class="login-form" @submit.prevent="submit">
          <template v-if="isPhoneRole">
            <label class="form-field">
              <span>手机号</span>
              <div class="input-wrap">
                <Phone :size="17" />
                <input
                  v-model="form.phone"
                  type="tel"
                  inputmode="numeric"
                  maxlength="11"
                  autocomplete="tel"
                  placeholder="请输入 11 位手机号"
                />
              </div>
            </label>

            <label class="form-field">
              <span>验证码</span>
              <div class="code-row">
                <div class="input-wrap">
                  <KeyRound :size="17" />
                  <input
                    v-model="form.code"
                    type="text"
                    inputmode="numeric"
                    maxlength="6"
                    autocomplete="one-time-code"
                    placeholder="输入 6 位验证码"
                  />
                </div>
                <button
                  type="button"
                  class="code-button"
                  :disabled="sendingCode || countdown > 0"
                  @click="sendCode"
                >
                  <LoaderCircle v-if="sendingCode" class="spin" :size="15" />
                  <span v-else>{{ countdown > 0 ? `${countdown}s` : '获取验证码' }}</span>
                </button>
              </div>
            </label>
          </template>

          <template v-else>
            <label class="form-field">
              <span>管理员账号</span>
              <div class="input-wrap">
                <ShieldCheck :size="17" />
                <input v-model="form.username" type="text" autocomplete="username" placeholder="请输入管理员账号" />
              </div>
            </label>

            <label class="form-field">
              <span>登录密码</span>
              <div class="input-wrap">
                <LockKeyhole :size="17" />
                <input v-model="form.password" type="password" autocomplete="current-password" placeholder="请输入登录密码" />
              </div>
            </label>

            <p class="demo-credential">本地默认账号：admin / admin123</p>
          </template>

          <p v-if="error" class="form-feedback error">{{ error }}</p>
          <p v-else-if="message" class="form-feedback success">{{ message }}</p>

          <button class="login-submit" type="submit" :disabled="busy">
            <LoaderCircle v-if="busy" class="spin" :size="18" />
            <span v-else>进入{{ roleMeta.label }}</span>
            <ArrowRight v-if="!busy" :size="18" />
          </button>
        </form>

        <footer class="login-note">
          <LockKeyhole :size="13" />
          <span>登录后将进入对应角色的独立工作台</span>
        </footer>
      </section>
    </main>
  </div>
</template>

<style scoped>
.login-shell {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(420px, 0.9fr) minmax(520px, 1.1fr);
  min-width: 0;
  overflow-x: hidden;
  background: #f4f1e9;
  color: #17201e;
}

.login-story {
  min-height: 100vh;
  padding: 42px clamp(32px, 5vw, 76px) 34px;
  display: flex;
  flex-direction: column;
  position: relative;
  overflow: hidden;
  color: #f8faf6;
  background: #151b18;
  border-right: 1px solid #2a312d;
}

.login-brand,
.login-brand > div,
.login-topline,
.login-heading,
.role-option,
.code-row,
.login-submit,
.login-note,
.story-footer {
  display: flex;
  align-items: center;
}

.login-brand { gap: 13px; }
.login-brand > div { flex-direction: column; align-items: flex-start; }
.brand-symbol { width: 42px; height: 42px; display: grid; place-items: center; color: #151b18; background: #f1b53c; border-radius: 7px; }
.login-brand strong { display: block; font-size: 17px; letter-spacing: .12em; }
.login-brand span { display: block; margin-top: 4px; color: #99a69f; font-size: 9px; letter-spacing: .15em; }
.story-copy { position: relative; z-index: 2; margin-top: clamp(70px, 12vh, 132px); max-width: 520px; }
.story-kicker { margin: 0 0 20px; color: #f1b53c; font-size: 10px; font-weight: 700; letter-spacing: .16em; }
.story-copy h1 { margin: 0; font-size: clamp(40px, 5vw, 68px); line-height: 1.12; font-weight: 650; letter-spacing: 0; }
.story-description { max-width: 450px; margin: 25px 0 0; color: #b4c0ba; font-size: 14px; line-height: 1.85; }

.route-map { min-height: 230px; margin-top: 46px; position: relative; border: 1px solid #343c37; background: #19211d; overflow: hidden; }
.route-map::before, .route-map::after { content: ''; position: absolute; background: #242d28; }
.route-map::before { width: 1px; height: 100%; left: 34%; }
.route-map::after { width: 100%; height: 1px; top: 58%; }
.map-road { position: absolute; display: block; height: 8px; border-top: 2px solid #48534c; border-bottom: 2px solid #48534c; }
.road-a { width: 44%; left: 6%; top: 28%; transform: rotate(18deg); }
.road-b { width: 50%; right: -2%; bottom: 23%; transform: rotate(-22deg); }
.road-c { width: 34%; left: 27%; top: 56%; transform: rotate(62deg); }
.map-node { width: 34px; height: 34px; display: grid; place-items: center; position: absolute; z-index: 2; color: #151b18; background: #f1b53c; border: 4px solid #19211d; border-radius: 50%; }
.node-start { left: 13%; bottom: 18%; }
.node-car { left: 47%; top: 44%; color: #f8faf6; background: #0f8b7f; }
.node-end { right: 11%; top: 16%; background: #f8faf6; }
.map-caption { display: flex; justify-content: space-between; align-items: flex-end; position: absolute; left: 20px; right: 20px; bottom: 17px; z-index: 2; }
.map-caption span { color: #8d9993; font-size: 10px; }
.map-caption strong { color: #f8faf6; font-size: 24px; font-weight: 600; }
.story-footer { gap: 9px; margin-top: auto; padding-top: 34px; color: #8f9b95; font-size: 11px; }
.status-dot { width: 7px; height: 7px; background: #75c77d; border-radius: 50%; box-shadow: 0 0 0 4px rgba(117, 199, 125, .1); }

.login-main { min-width: 0; min-height: 100vh; padding: 30px clamp(28px, 7vw, 110px); display: flex; flex-direction: column; }
.login-topline { justify-content: space-between; color: #7a817c; font-size: 11px; letter-spacing: .04em; }
.login-card { width: 100%; max-width: 530px; min-width: 0; margin: auto; padding: 34px 0 42px; }
.login-heading { gap: 14px; margin-bottom: 28px; }
.heading-icon { width: 46px; height: 46px; display: grid; place-items: center; color: var(--role-accent); background: color-mix(in srgb, var(--role-accent) 11%, transparent); border: 1px solid color-mix(in srgb, var(--role-accent) 25%, transparent); border-radius: 7px; }
.login-heading p { margin: 0 0 4px; color: #7e857f; font-size: 11px; }
.login-heading h2 { margin: 0; font-size: 30px; line-height: 1.15; font-weight: 650; letter-spacing: 0; }
.role-picker { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; padding: 5px; background: #e8e4da; border-radius: 8px; }
.role-option { min-height: 50px; justify-content: center; gap: 7px; border: 1px solid transparent; border-radius: 6px; color: #696f6b; background: transparent; transition: color .18s ease, background .18s ease, border-color .18s ease; }
.role-option:hover { color: #17201e; }
.role-option.active { color: #17201e; background: #fffdf8; border-color: #d8d3c7; box-shadow: 0 2px 7px rgba(20, 27, 24, .06); }
.role-option span { font-size: 12px; font-weight: 600; }
.role-description { margin: 16px 0 28px; color: #7a817c; font-size: 12px; }
.login-form { display: grid; gap: 19px; }
.form-field { display: grid; gap: 8px; color: #343c38; font-size: 11px; font-weight: 600; }
.input-wrap { min-height: 49px; display: flex; align-items: center; gap: 10px; padding: 0 14px; color: #89908b; background: #fffdf8; border: 1px solid #d8d4ca; border-radius: 6px; transition: border-color .18s ease, box-shadow .18s ease; }
.input-wrap:focus-within { border-color: var(--role-accent); box-shadow: 0 0 0 3px color-mix(in srgb, var(--role-accent) 10%, transparent); }
.input-wrap input { width: 100%; min-width: 0; height: 47px; padding: 0; border: 0; outline: 0; color: #17201e; background: transparent; font-size: 13px; font-weight: 500; }
.input-wrap input::placeholder { color: #a3aaa5; font-weight: 400; }
.code-row { display: grid; grid-template-columns: minmax(0, 1fr) 118px; gap: 9px; }
.code-button { min-height: 49px; border: 1px solid #cbc6ba; border-radius: 6px; color: #35403a; background: #ebe7dd; font-size: 11px; font-weight: 650; }
.code-button:hover:not(:disabled) { border-color: #a9a297; background: #e2ddd2; }
.demo-credential { margin: -5px 0 0; color: #8a8177; font-size: 10px; }
.form-feedback { margin: 0; padding: 10px 12px; border-radius: 5px; font-size: 11px; line-height: 1.5; }
.form-feedback.error { color: #982f28; background: #f8e8e4; border: 1px solid #ecc8c1; }
.form-feedback.success { color: #246d4d; background: #e7f4ed; border: 1px solid #c6e4d4; }
.login-submit { width: 100%; min-height: 51px; justify-content: center; gap: 9px; border: 1px solid #151b18; border-radius: 6px; color: #fff; background: #17201e; font-size: 12px; font-weight: 700; transition: transform .18s ease, background .18s ease; }
.login-submit:hover:not(:disabled) { background: #29332e; transform: translateY(-1px); }
.login-note { justify-content: center; gap: 7px; margin-top: 24px; color: #8d938e; font-size: 10px; }
.spin { animation: spin .8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

@media (max-width: 980px) {
  .login-shell { grid-template-columns: minmax(0, 1fr); }
  .login-story { min-height: 420px; padding: 28px 28px 24px; }
  .story-copy { margin-top: 48px; }
  .story-copy h1 { font-size: 42px; }
  .route-map { display: none; }
  .login-main { min-height: 620px; padding: 24px 24px 34px; }
}

@media (max-width: 560px) {
  .login-story { min-height: 330px; }
  .story-copy { margin-top: 38px; }
  .story-copy h1 { font-size: 34px; }
  .story-description { margin-top: 16px; font-size: 12px; }
  .story-footer { padding-top: 22px; }
  .login-main { width: 100%; padding: 20px 16px 28px; }
  .login-card { padding: 20px 0; }
  .role-picker { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .role-option { min-width: 0; min-height: 48px; flex-direction: column; gap: 3px; padding: 5px; }
  .role-option span { font-size: 10px; }
  .code-row { grid-template-columns: minmax(0, 1fr) 96px; }
  .input-wrap { min-width: 0; }
}
</style>
