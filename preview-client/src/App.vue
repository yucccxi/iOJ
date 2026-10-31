<script setup lang="ts">
import { computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import AccountMenu from '@/components/AccountMenu.vue'
import UiIcon from '@/components/UiIcon.vue'
const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const practice = computed(() => /^\/problems\/\d+$/.test(route.path))
function focusContent() {
  const main = document.getElementById('main')
  const target =
    main?.querySelector<HTMLElement>('[autofocus]') || main?.querySelector<HTMLElement>('h1')
  if (target) {
    if (target.tagName === 'H1') target.tabIndex = -1
    target.focus({ preventScroll: true })
  }
}
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
        ><span class="brand-mark">iOJ<span class="brand-dot">.</span></span></RouterLink
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
          ><RouterLink to="/problems/new" class="button small secondary"
            ><UiIcon name="plus" />出题</RouterLink
          ><AccountMenu :user="auth.user" @logout="logout"
        /></template>
        <template v-else
          ><RouterLink to="/login" class="quiet-link">登录</RouterLink
          ><RouterLink to="/register" class="button small secondary">注册</RouterLink></template
        >
      </div>
    </div>
  </header>
  <main id="main" class="main-shell" :class="{ 'main-shell--practice': practice }" tabindex="-1">
    <div :key="auth.revision" class="route-host">
      <RouterView v-slot="{ Component, route: viewRoute }">
        <Transition name="page" mode="out-in" @after-enter="focusContent">
          <div :key="viewRoute.fullPath" class="route-page"><component :is="Component" /></div>
        </Transition>
      </RouterView>
    </div>
  </main>
  <footer v-if="!practice" class="site-footer">
    <span class="mono">iOJ</span><span>算法练习</span>
  </footer>
</template>
