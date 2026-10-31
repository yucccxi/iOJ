<script setup lang="ts">
import { computed } from 'vue'
const props = defineProps<{ page: number; size: number; total: number }>()
defineEmits<{ change: [page: number] }>()
const pages = computed(() => Math.max(1, Math.ceil(props.total / props.size)))
</script>
<template>
  <nav class="pagination" aria-label="分页">
    <span>共 {{ total }} 条</span>
    <div>
      <button class="button secondary" :disabled="page <= 1" @click="$emit('change', page - 1)">
        上一页
      </button>
      <span aria-live="polite">{{ page }} / {{ pages }}</span>
      <button class="button secondary" :disabled="page >= pages" @click="$emit('change', page + 1)">
        下一页
      </button>
    </div>
  </nav>
</template>
