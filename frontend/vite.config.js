import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

const proxy = (target) => ({
  target,
  changeOrigin: true,
  rewrite: (path) => path.replace(/^\/api\/(passenger|driver|boss)/, ''),
})

export default defineConfig({
  plugins: [vue()],
  server: {
    host: '0.0.0.0',
    port: 5173,
    proxy: {
      '/api/passenger': proxy('http://localhost:8081'),
      '/api/driver': proxy('http://localhost:8088'),
      '/api/boss': proxy('http://localhost:8087'),
    },
  },
})
