<script setup lang="ts">
import { computed } from 'vue'
import DOMPurify from 'dompurify'
import { renderMarkdown } from '@/lib/markdown'
import 'katex/dist/katex.min.css'
const props = defineProps<{ text: string | null | undefined }>()
const html = computed(() => renderMarkdown(props.text || ''))
</script>
<template>
  <div v-if="DOMPurify.isSupported" class="markdown" v-html="html"></div>
  <div v-else class="markdown">{{ text }}</div>
</template>
