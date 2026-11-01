<script setup>
import { computed } from 'vue'
import { RouterView, useRouter } from 'vue-router'
import { clearSession, homeForRole, sessionState, setSession } from './services/session'

const router = useRouter()
const session = computed(() => sessionState.session)

async function handleAuthenticated(nextSession) {
  setSession(nextSession)
  await router.replace(homeForRole(nextSession.role))
}

async function logout() {
  clearSession()
  await router.replace({ name: 'login' })
}
</script>

<template>
  <RouterView v-slot='{ Component, route }'>
    <component
      :is='Component'
      :key='route.name'
      :identity='session?.identity'
      @authenticated='handleAuthenticated'
      @logout='logout'
    />
  </RouterView>
</template>
