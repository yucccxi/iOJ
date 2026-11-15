<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { login } from '../api/auth'
import { errorMessage } from '../api/http'
import CaptchaBox from '../components/CaptchaBox.vue'
import { applyLogin } from '../stores/session'

const router = useRouter()
const route = useRoute()

const form = reactive({
  account: '',
  password: '',
  captchaId: '',
  captchaCode: '',
})
const captchaRef = ref<InstanceType<typeof CaptchaBox> | null>(null)
const submitting = ref(false)
const error = ref('')

async function submit() {
  error.value = ''
  if (!form.account.trim() || !form.password) {
    error.value = '请输入账号和密码'
    return
  }

  submitting.value = true
  try {
    const result = await login({
      account: form.account,
      password: form.password,
      captchaId: form.captchaId || undefined,
      captchaCode: form.captchaCode || undefined,
    })
    applyLogin(result)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/catalog'
    router.push(redirect)
  } catch (e) {
    error.value = errorMessage(e)
    captchaRef.value?.refresh()
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="card" style="max-width: 460px; margin: 0 auto">
    <h1>登录</h1>
    <div v-if="route.query.registered" class="notice">注册成功，请使用新账号登录。</div>
    <div v-if="route.query.passwordChanged" class="notice">
      密码已修改，所有旧 token 已失效，请使用新密码重新登录。
    </div>
    <form @submit.prevent="submit">
      <div class="form-row">
        <label for="account">用户名或邮箱</label>
        <input id="account" v-model="form.account" type="text" maxlength="128" autocomplete="username" />
      </div>
      <div class="form-row">
        <label for="password">密码</label>
        <input
          id="password"
          v-model="form.password"
          type="password"
          maxlength="64"
          autocomplete="current-password"
        />
      </div>

      <CaptchaBox
        ref="captchaRef"
        v-model:captcha-id="form.captchaId"
        v-model:code="form.captchaCode"
        purpose="LOGIN"
      />

      <p v-if="error" class="error">{{ error }}</p>

      <div class="actions">
        <button type="submit" :disabled="submitting">
          {{ submitting ? '登录中…' : '登录' }}
        </button>
        <RouterLink to="/register">注册账号</RouterLink>
        <RouterLink to="/password-reset">忘记密码？</RouterLink>
      </div>
    </form>
  </div>
</template>
