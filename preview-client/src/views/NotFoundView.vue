<script setup lang="ts">
import { useRoute } from 'vue-router'
const route = useRoute()
</script>
<template>
  <section class="state">
    <h1>{{ route.path === '/forbidden' ? '无权访问' : '页面不存在' }}</h1>
    <RouterLink to="/problems" class="button secondary">返回题库</RouterLink>
  </section>
</template>
