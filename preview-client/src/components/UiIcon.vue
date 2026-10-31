<script setup lang="ts">
defineProps<{ name: 'search' | 'plus' | 'chevron' | 'code' | 'grip' }>()
const paths = {
  search: 'M21 21l-4.4-4.4M19 11a8 8 0 1 1-16 0 8 8 0 0 1 16 0',
  plus: 'M12 5v14M5 12h14',
  chevron: 'm6 9 6 6 6-6',
  code: 'm8 7-5 5 5 5m8-10 5 5-5 5m-3-14-2 18',
  grip: 'M10 5v14M14 5v14',
}
</script>
<template>
  <svg
    class="ui-icon"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.7"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    focusable="false"
  >
    <path :d="paths[name]" />
  </svg>
</template>
