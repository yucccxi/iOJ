<script setup lang="ts">
import { onUnmounted, reactive, ref } from 'vue'
import { resetPassword, sendPasswordResetCode } from '../api/auth'
import { errorMessage } from '../api/http'
import CaptchaBox from '../components/CaptchaBox.vue'
import { logoutLocal } from '../stores/session'

const form = reactive({
  email: '',
  emailCode: '',
  newPassword: '',
  captchaId: '',
  captchaCode: '',
})

const captchaRef = ref<InstanceType<typeof CaptchaBox> | null>(null)
const sending = ref(false)
const submitting = ref(false)
const countdown = ref(0)
const error = ref('')
const success = ref('')

let timer: number | undefined

function stopTimer() {
  if (timer !== undefined) {
    window.clearInterval(timer)
    timer = undefined
  }
}

function startCountdown(seconds: number) {
  stopTimer()
  countdown.value = seconds
  timer = window.setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0) stopTimer()
  }, 1000)
}

onUnmounted(stopTimer)

async function sendCode() {
  error.value = ''
  success.value = ''
  if (!form.email.trim()) {
    error.value = '请先填写邮箱'
    return
  }

  sending.value = true
  try {
    const result = await sendPasswordResetCode({
      email: form.email,
      captchaId: form.captchaId || undefined,
      captchaCode: form.captchaCode || undefined,
    })
    success.value = `找回密码验证码已发送，${result.expiresInSeconds} 秒内有效。`
    startCountdown(result.resendAfterSeconds)
    captchaRef.value?.refresh()
  } catch (e) {
    error.value = errorMessage(e)
    captchaRef.value?.refresh()
  } finally {
    sending.value = false
  }
}

async function submit() {
  error.value = ''
  success.value = ''
  submitting.value = true
  try {
    await resetPassword({
      email: form.email,
      emailCode: form.emailCode,
      newPassword: form.newPassword,
    })
    logoutLocal()
    success.value = '密码已重置，所有旧 token 已失效，请使用新密码登录。'
    form.emailCode = ''
    form.newPassword = ''
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="card" style="max-width: 560px; margin: 0 auto">
    <h1>找回密码</h1>

    <form @submit.prevent="submit">
      <div class="form-row">
        <label for="email">注册邮箱</label>
        <input id="email" v-model="form.email" type="email" maxlength="128" autocomplete="email" />
      </div>

      <CaptchaBox
        ref="captchaRef"
        v-model:captcha-id="form.captchaId"
        v-model:code="form.captchaCode"
        purpose="EMAIL_SENDING"
      />

      <div class="inline-form">
        <div class="form-row" style="flex: 1; min-width: 200px">
          <label for="emailCode">邮箱验证码（6 位数字）</label>
          <input id="emailCode" v-model="form.emailCode" type="text" maxlength="6" />
        </div>
        <button
          type="button"
          class="secondary"
          :disabled="sending || countdown > 0"
          @click="sendCode"
        >
          {{ countdown > 0 ? `${countdown} 秒后重发` : sending ? '发送中…' : '获取验证码' }}
        </button>
      </div>

      <div class="form-row">
        <label for="newPassword">新密码</label>
        <input
          id="newPassword"
          v-model="form.newPassword"
          type="password"
          minlength="8"
          maxlength="64"
          autocomplete="new-password"
        />
      </div>

      <p v-if="error" class="error">{{ error }}</p>
      <p v-if="success" class="success">{{ success }}</p>

      <div class="actions" style="margin-top: 12px">
        <button type="submit" :disabled="submitting">
          {{ submitting ? '提交中…' : '重置密码' }}
        </button>
        <RouterLink to="/login">返回登录</RouterLink>
      </div>
    </form>
  </div>
</template>
