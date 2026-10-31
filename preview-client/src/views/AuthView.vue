<script setup lang="ts">
import { useRequestScope } from '@/composables/useRequestScope'
const signal = useRequestScope()
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '@/api/resources'
import { useAuthStore } from '@/stores/auth'
import { errorText, safeReturn } from '@/lib/contracts'
const route = useRoute(),
  router = useRouter(),
  auth = useAuthStore()
const register = computed(() => route.path === '/register')
const account = ref(''),
  email = ref(''),
  nickname = ref(''),
  password = ref('')
const busy = ref(false),
  error = ref('')
async function submit() {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    if (register.value) {
      await api.register(
        {
          username: account.value,
          email: email.value,
          password: password.value,
          nickname: nickname.value || undefined,
        },
        signal,
      )
      password.value = ''
      await router.replace({
        path: '/login',
        query: { registered: '1', redirect: safeReturn(route.query.redirect) },
      })
      return
    }
    await auth.signIn(account.value, password.value, signal)
    password.value = ''
    await router.replace(safeReturn(route.query.redirect))
  } catch (cause) {
    error.value = errorText(cause)
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <section class="auth-panel panel">
    <h1>{{ register ? '注册' : '登录' }}</h1>
    <p v-if="route.query.registered" class="notice" role="status">注册成功，请登录</p>
    <p v-if="route.query.changed" class="notice" role="status">密码已修改，请重新登录</p>
    <form @submit.prevent="submit" class="stack">
      <label
        >{{ register ? '用户名' : '用户名或邮箱'
        }}<input
          v-model="account"
          required
          :minlength="register ? 3 : undefined"
          :maxlength="register ? 32 : 128"
          :pattern="register ? '[A-Za-z0-9_]{3,32}' : undefined"
          autocomplete="username"
          autofocus
          :disabled="busy"
        /><span v-if="register" class="field-hint">3–32 位字母、数字或下划线</span></label
      >
      <label v-if="register"
        >邮箱<input
          v-model="email"
          type="email"
          required
          maxlength="128"
          autocomplete="email"
          :disabled="busy"
      /></label>
      <label v-if="register"
        >昵称<span class="optional">选填</span
        ><input v-model="nickname" maxlength="32" autocomplete="nickname" :disabled="busy"
      /></label>
      <label
        >密码<input
          v-model="password"
          type="password"
          required
          :minlength="register ? 8 : 1"
          maxlength="64"
          :autocomplete="register ? 'new-password' : 'current-password'"
          :disabled="busy"
        /><span v-if="register" class="field-hint">8–64 位</span></label
      >
      <p v-if="error" class="error" role="alert">{{ error }}</p>
      <button class="button" :disabled="busy">
        {{ busy ? '处理中…' : register ? '注册' : '登录' }}
      </button>
    </form>
    <div class="auth-bottom">
      <RouterLink
        :to="{
          path: register ? '/login' : '/register',
          query: { redirect: safeReturn(route.query.redirect) },
        }"
        >{{ register ? '已有账号，登录' : '创建账号' }}</RouterLink
      >
    </div>
  </section>
</template>
