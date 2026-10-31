<script setup lang="ts">
import { computed } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
const props = defineProps<{ text: string | null | undefined }>()
const html = computed(() =>
  DOMPurify.sanitize(marked.parse(props.text || '', { async: false }), {
    USE_PROFILES: { html: true },
    FORBID_TAGS: ['style', 'iframe', 'form'],
    FORBID_ATTR: ['style'],
  }),
)
</script>
<template>
  <div v-if="DOMPurify.isSupported" class="markdown" v-html="html"></div>
  <div v-else class="markdown">{{ text }}</div>
</template>
