<script setup>
import { computed, ref } from 'vue'
import { CarFront, ChevronRight, LogOut, MapPin, Menu, ShieldCheck, Smartphone, X } from 'lucide-vue-next'

const props = defineProps({
  role: { type: String, required: true },
  identity: { type: String, required: true },
  items: { type: Array, required: true },
  active: { type: String, required: true },
})

const emit = defineEmits(['update:active', 'logout'])
const mobileOpen = ref(false)

const roleMeta = computed(() => ({
  passenger: { label: '客户端', title: '乘客工作台', icon: Smartphone, accent: '#0f8b7f' },
  driver: { label: '司机端', title: '司机工作台', icon: CarFront, accent: '#d98213' },
  boss: { label: '管理员', title: '运营管理台', icon: ShieldCheck, accent: '#c8463a' },
}[props.role]))

const currentLabel = computed(() => props.items.find((item) => item.id === props.active)?.label || '')
const initial = computed(() => (props.identity || 'U').slice(0, 1).toUpperCase())

function selectItem(id) {
  emit('update:active', id)
  mobileOpen.value = false
}
</script>

<template>
  <div class="workspace-shell" :style="{ '--role-accent': roleMeta.accent }">
    <aside class="workspace-sidebar" :class="{ open: mobileOpen }">
      <div class="sidebar-brand">
        <span class="sidebar-brand-mark"><MapPin :size="20" /></span>
        <div>
          <strong>FlyTaxi</strong>
          <span>{{ roleMeta.label }}</span>
        </div>
        <button class="sidebar-close" type="button" title="关闭菜单" @click="mobileOpen = false">
          <X :size="18" />
        </button>
      </div>

      <div class="role-card">
        <component :is="roleMeta.icon" :size="20" />
        <div>
          <span>当前身份</span>
          <strong>{{ roleMeta.title }}</strong>
        </div>
      </div>

      <nav class="workspace-nav" aria-label="工作台导航">
        <button
          v-for="item in items"
          :key="item.id"
          type="button"
          :class="{ active: active === item.id }"
          @click="selectItem(item.id)"
        >
          <component :is="item.icon" :size="18" />
          <span>{{ item.label }}</span>
          <ChevronRight :size="14" />
        </button>
      </nav>

      <div class="sidebar-session">
        <span class="session-avatar">{{ initial }}</span>
        <div>
          <strong>{{ identity }}</strong>
          <span>已安全登录</span>
        </div>
      </div>

      <button class="logout-button" type="button" @click="emit('logout')">
        <LogOut :size="16" />
        <span>退出登录</span>
      </button>
    </aside>

    <div v-if="mobileOpen" class="sidebar-scrim" @click="mobileOpen = false"></div>

    <main class="workspace-main">
      <header class="workspace-topbar">
        <button class="menu-button" type="button" title="打开菜单" @click="mobileOpen = true">
          <Menu :size="20" />
        </button>
        <div class="workspace-breadcrumb">
          <span>{{ roleMeta.title }}</span>
          <ChevronRight :size="13" />
          <strong>{{ currentLabel }}</strong>
        </div>
        <div class="topbar-identity">
          <span class="online-mark"></span>
          <span>{{ identity }}</span>
          <span class="identity-avatar">{{ initial }}</span>
        </div>
      </header>

      <div class="workspace-content">
        <slot />
      </div>
    </main>
  </div>
</template>

<style scoped>
.workspace-shell { min-height: 100vh; display: flex; color: #1b2522; background: #f3f1ea; }
.workspace-sidebar { width: 244px; height: 100vh; padding: 25px 15px 18px; display: flex; flex-direction: column; position: sticky; top: 0; z-index: 30; color: #eef3ef; background: #17201d; border-right: 1px solid #2c3530; }
.sidebar-brand, .sidebar-brand > div, .role-card, .workspace-nav button, .sidebar-session, .logout-button, .workspace-topbar, .workspace-breadcrumb, .topbar-identity { display: flex; align-items: center; }
.sidebar-brand { gap: 11px; padding: 0 8px 27px; }
.sidebar-brand-mark { width: 37px; height: 37px; display: grid; place-items: center; color: #17201d; background: #f1b53c; border-radius: 6px; }
.sidebar-brand strong { display: block; font-size: 14px; letter-spacing: .13em; }
.sidebar-brand span:not(.sidebar-brand-mark) { display: block; margin-top: 3px; color: #8e9b94; font-size: 9px; }
.sidebar-close { display: none; margin-left: auto; border: 0; color: #bec8c2; background: transparent; }
.role-card { gap: 11px; margin: 0 2px 24px; padding: 13px 12px; color: var(--role-accent); background: #1e2823; border: 1px solid #333f38; border-radius: 6px; }
.role-card div { min-width: 0; }
.role-card span, .role-card strong { display: block; }
.role-card span { color: #84918a; font-size: 9px; }
.role-card strong { margin-top: 4px; color: #f2f6f3; font-size: 11px; font-weight: 600; }
.workspace-nav { display: grid; gap: 4px; }
.workspace-nav button { width: 100%; min-height: 44px; gap: 11px; padding: 0 12px; border: 1px solid transparent; border-radius: 5px; color: #8f9b95; background: transparent; text-align: left; transition: .18s ease; }
.workspace-nav button span { flex: 1; font-size: 11px; }
.workspace-nav button svg:last-child { opacity: 0; transition: opacity .18s ease; }
.workspace-nav button:hover { color: #f5f8f6; background: #202a25; }
.workspace-nav button.active { color: #fff; background: #26332c; border-color: color-mix(in srgb, var(--role-accent) 42%, transparent); }
.workspace-nav button.active svg:first-child { color: var(--role-accent); }
.workspace-nav button.active svg:last-child { opacity: 1; }
.sidebar-session { gap: 10px; margin-top: auto; padding: 16px 8px; border-top: 1px solid #2a342e; }
.session-avatar, .identity-avatar { display: grid; place-items: center; flex: none; font-weight: 700; }
.session-avatar { width: 31px; height: 31px; color: #17201d; background: var(--role-accent); border-radius: 5px; }
.sidebar-session div { min-width: 0; }
.sidebar-session strong, .sidebar-session span { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.sidebar-session strong { max-width: 150px; color: #eaf0ec; font-size: 10px; font-weight: 600; }
.sidebar-session span { margin-top: 3px; color: #7f8b85; font-size: 9px; }
.logout-button { width: 100%; min-height: 39px; justify-content: center; gap: 8px; border: 1px solid #36423b; border-radius: 5px; color: #aab5af; background: transparent; font-size: 10px; }
.logout-button:hover { color: #fff; border-color: #66736b; background: #222d27; }
.workspace-main { min-width: 0; flex: 1; }
.workspace-topbar { height: 70px; justify-content: space-between; gap: 20px; padding: 0 clamp(20px, 4vw, 54px); background: #f9f7f1; border-bottom: 1px solid #ded9cd; }
.menu-button { display: none; width: 36px; height: 36px; place-items: center; border: 1px solid #d5d0c4; border-radius: 5px; color: #36413c; background: transparent; }
.workspace-breadcrumb { gap: 7px; color: #8a908b; font-size: 10px; }
.workspace-breadcrumb strong { color: #323b37; font-weight: 650; }
.topbar-identity { gap: 9px; color: #5e6762; font-size: 10px; }
.online-mark { width: 7px; height: 7px; background: #4c9c69; border-radius: 50%; }
.identity-avatar { width: 29px; height: 29px; color: #fff; background: #28342f; border-radius: 4px; font-size: 10px; }
.workspace-content { max-width: 1460px; margin: 0 auto; padding: clamp(26px, 4vw, 48px) clamp(20px, 4vw, 54px) 36px; }
.sidebar-scrim { display: none; }

@media (max-width: 860px) {
  .workspace-sidebar { width: 250px; position: fixed; left: -270px; transition: left .22s ease; box-shadow: 14px 0 35px rgba(15, 21, 18, .18); }
  .workspace-sidebar.open { left: 0; }
  .sidebar-close, .menu-button { display: grid; }
  .sidebar-scrim { display: block; position: fixed; inset: 0; z-index: 20; background: rgba(15, 21, 18, .42); }
  .workspace-topbar { height: 64px; justify-content: flex-start; padding: 0 16px; }
  .topbar-identity { margin-left: auto; }
}

@media (max-width: 520px) {
  .workspace-breadcrumb span, .workspace-breadcrumb svg, .topbar-identity > span:not(.identity-avatar) { display: none; }
  .workspace-content { padding: 24px 14px 28px; }
}
</style>
