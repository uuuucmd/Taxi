<script setup>
import { Activity, Check, X } from 'lucide-vue-next'

defineProps({
  logs: { type: Array, default: () => [] },
})
</script>

<template>
  <article class="surface activity-surface">
    <div class="surface-heading">
      <div><span class="section-kicker">REQUEST LOG</span><h2>最近请求</h2></div>
      <Activity :size="20" />
    </div>
    <div v-if="logs.length" class="activity-list">
      <div v-for="(log, index) in logs" :key="index" class="activity-item">
        <span class="activity-status" :class="log.status"><Check v-if="log.status === 'success'" :size="13" /><X v-else :size="13" /></span>
        <div><b>{{ log.label }}</b><small>{{ log.detail }}</small></div>
        <time>{{ log.time }}</time>
      </div>
    </div>
    <div v-else class="empty-state"><Activity :size="28" /><span>还没有请求记录</span><small>操作后会在这里显示结果</small></div>
  </article>
</template>
