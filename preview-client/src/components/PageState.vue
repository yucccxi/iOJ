<script setup lang="ts">
defineProps<{ loading: boolean; error: string; empty?: boolean; emptyText?: string }>()
defineEmits<{ retry: [] }>()
</script>
<template>
  <div v-if="error" class="state error" role="alert">
    <p>{{ error }}</p>
    <button type="button" class="button secondary" @click="$emit('retry')">重试</button>
  </div>
  <div v-else-if="loading" class="state" role="status">加载中…</div>
  <div v-else-if="empty" class="state">{{ emptyText || '暂无记录' }}</div>
</template>
