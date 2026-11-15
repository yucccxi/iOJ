<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { logoutLocal, session } from './stores/session'

const router = useRouter()
const user = computed(() => session.user)
const admin = computed(() => session.user?.role === 'ADMIN')

function logout() {
  logoutLocal()
  router.push({ name: 'login' })
}
</script>

<template>
  <header class="topbar">
    <RouterLink class="brand" to="/catalog">iOJ 测试前端</RouterLink>
    <nav class="main-nav">
      <RouterLink to="/catalog">题库</RouterLink>
      <RouterLink to="/submissions">提交</RouterLink>
      <RouterLink v-if="user" to="/problems/mine">我的题目</RouterLink>
      <RouterLink v-if="user" to="/problems/new">出题</RouterLink>
      <template v-if="admin">
        <span class="nav-divider">|</span>
        <RouterLink to="/admin/problems">审核</RouterLink>
        <RouterLink to="/admin/users">用户</RouterLink>
        <RouterLink to="/admin/tags">标签</RouterLink>
        <RouterLink to="/admin/scopes">Scope</RouterLink>
        <RouterLink to="/admin/judge-keys">判题 Key</RouterLink>
        <RouterLink to="/admin/judge-nodes">判题节点</RouterLink>
      </template>
    </nav>
    <div class="account">
      <template v-if="user">
        <RouterLink to="/users/me">{{ user.nickname }}</RouterLink>
        <RouterLink to="/settings">设置</RouterLink>
        <button class="link-button" type="button" @click="logout">退出</button>
      </template>
      <template v-else>
        <RouterLink to="/login">登录</RouterLink>
        <RouterLink to="/register">注册</RouterLink>
      </template>
    </div>
  </header>

  <main class="container">
    <RouterView />
  </main>
</template>
