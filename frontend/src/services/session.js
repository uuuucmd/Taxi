import { reactive, readonly } from 'vue'
import { clearTokens, getToken } from './api'

const sessionKey = 'flytaxi.web.session'
const roleHomes = {
  passenger: '/passenger',
  driver: '/driver',
  boss: '/admin',
}

function readSession() {
  try {
    const value = JSON.parse(localStorage.getItem(sessionKey) || 'null')
    if (value?.role && roleHomes[value.role] && getToken(value.role)) return value
    if (value?.role) clearTokens(value.role)
  } catch {
    // Malformed storage should fail closed at the login route.
  }
  localStorage.removeItem(sessionKey)
  return null
}

const state = reactive({ session: readSession() })

export const sessionState = readonly(state)

export function refreshSession() {
  state.session = readSession()
  return state.session
}

export function setSession(nextSession) {
  if (!nextSession?.role || !roleHomes[nextSession.role]) {
    throw new Error('Unknown session role')
  }
  state.session = nextSession
  localStorage.setItem(sessionKey, JSON.stringify(nextSession))
}

export function clearSession() {
  if (state.session?.role) clearTokens(state.session.role)
  state.session = null
  localStorage.removeItem(sessionKey)
}

export function homeForRole(role) {
  return roleHomes[role] || '/login'
}
