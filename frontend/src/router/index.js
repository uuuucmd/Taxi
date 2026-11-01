import { createRouter, createWebHistory } from 'vue-router'
import LoginView from '../components/LoginView.vue'
import PassengerWorkspace from '../components/PassengerWorkspace.vue'
import DriverWorkspace from '../components/DriverWorkspace.vue'
import AdminWorkspace from '../components/AdminWorkspace.vue'
import { homeForRole, refreshSession } from '../services/session'

const routes = [
  { path: '/', redirect: '/login' },
  { path: '/login', name: 'login', component: LoginView },
  {
    path: '/passenger',
    name: 'passenger',
    component: PassengerWorkspace,
    meta: { requiresAuth: true, role: 'passenger' },
  },
  {
    path: '/driver',
    name: 'driver',
    component: DriverWorkspace,
    meta: { requiresAuth: true, role: 'driver' },
  },
  {
    path: '/admin',
    name: 'admin',
    component: AdminWorkspace,
    meta: { requiresAuth: true, role: 'boss' },
  },
  { path: '/:pathMatch(.*)*', redirect: '/login' },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach((to) => {
  const session = refreshSession()

  if (to.meta.requiresAuth) {
    if (!session) return { name: 'login' }
    if (to.meta.role !== session.role) return homeForRole(session.role)
  }

  if (to.name === 'login' && session) return homeForRole(session.role)
  return true
})

export default router
