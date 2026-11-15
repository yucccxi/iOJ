<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  page: number
  size: number
  total: number
}>()

const emit = defineEmits<{
  (event: 'update:page', value: number): void
  (event: 'update:size', value: number): void
}>()

const totalPages = computed(() => Math.max(1, Math.ceil(props.total / props.size)))

function go(page: number) {
  if (page < 1 || page > totalPages.value || page === props.page) return
  emit('update:page', page)
}

function changeSize(event: Event) {
  const value = Number((event.target as HTMLSelectElement).value)
  emit('update:size', value)
  emit('update:page', 1)
}
</script>

<template>
  <div class="pagination">
    <button type="button" class="secondary" :disabled="page <= 1" @click="go(page - 1)">上一页</button>
    <span>第 {{ page }} / {{ totalPages }} 页，共 {{ total }} 条</span>
    <button
      type="button"
      class="secondary"
      :disabled="page >= totalPages"
      @click="go(page + 1)"
    >
      下一页
    </button>
    <select :value="size" @change="changeSize">
      <option :value="10">10 / 页</option>
      <option :value="20">20 / 页</option>
      <option :value="50">50 / 页</option>
      <option :value="100">100 / 页</option>
    </select>
  </div>
</template>
