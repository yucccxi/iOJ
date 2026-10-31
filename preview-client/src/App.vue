<script setup lang="ts">
import { watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
watch(
  () => auth.token,
  (token, previous) => {
    if (!token && previous && route.meta.requiresAuth)
      void router.replace({ path: '/login', query: { redirect: route.fullPath } })
  },
)
async function logout() {
  auth.signOut()
  if (route.meta.requiresAuth) await router.push('/problems')
}
</script>

<template>
  <a class="skip-link" href="#main">跳到内容</a>
  <header class="site-header">
    <div class="header-inner">
      <RouterLink to="/problems" class="brand" aria-label="iOJ 题库"
        ><span class="brand-mark">iOJ</span><span class="brand-caption">算法练习</span></RouterLink
      >
      <nav class="primary-nav" aria-label="主导航">
        <RouterLink
          to="/problems"
          :class="{
            active:
              route.path.startsWith('/problems') &&
              !route.meta.mine &&
              route.path !== '/problems/new',
          }"
          >题库</RouterLink
        >
        <RouterLink to="/submissions" :class="{ active: route.path.startsWith('/submissions') }"
          >提交记录</RouterLink
        >
        <RouterLink v-if="auth.user" to="/problems/mine" :class="{ active: route.meta.mine }"
          >我的题目</RouterLink
        >
        <RouterLink
          v-if="auth.isAdmin"
          to="/admin/problems"
          :class="{ active: route.path.startsWith('/admin') }"
          >管理</RouterLink
        >
      </nav>
      <div class="account-nav" v-if="auth.ready">
        <template v-if="auth.user"
          ><RouterLink to="/problems/new" class="button small">出题</RouterLink
          ><RouterLink :to="'/users/' + auth.user.id" class="account-name">{{
            auth.user.nickname
          }}</RouterLink
          ><RouterLink to="/settings" class="quiet-link">设置</RouterLink
          ><button class="text-button" @click="logout">退出</button></template
        >
        <template v-else
          ><RouterLink to="/login" class="quiet-link">登录</RouterLink
          ><RouterLink to="/register" class="button small secondary">注册</RouterLink></template
        >
      </div>
    </div>
  </header>
  <main id="main" class="main-shell" tabindex="-1">
    <RouterView :key="route.fullPath + ':' + auth.revision" />
  </main>
  <footer class="site-footer"><span class="mono">iOJ</span><span>算法练习</span></footer>
</template>
