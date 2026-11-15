<script setup lang="ts">
import { onUnmounted, reactive, ref } from 'vue'
import { register, sendEmailCode } from '../api/auth'
import { errorMessage } from '../api/http'
import CaptchaBox from '../components/CaptchaBox.vue'

const form = reactive({
  username: '',
  email: '',
  password: '',
  nickname: '',
  emailCode: '',
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
    const result = await sendEmailCode({
      email: form.email,
      captchaId: form.captchaId || undefined,
      captchaCode: form.captchaCode || undefined,
    })
    success.value = `验证码已发送，${result.expiresInSeconds} 秒内有效，${result.resendAfterSeconds} 秒后可重发。`
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
    const user = await register({
      username: form.username,
      email: form.email,
      password: form.password,
      nickname: form.nickname.trim() ? form.nickname.trim() : undefined,
      emailCode: form.emailCode.trim() ? form.emailCode.trim() : undefined,
    })
    success.value = `注册成功：${user.username}（id=${user.id}），请前往登录。`
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="card" style="max-width: 560px; margin: 0 auto">
    <h1>注册</h1>
    <p class="hint">
      邮箱验证开启时必须先获取邮箱验证码；关闭时可直接留空注册。
    </p>

    <form @submit.prevent="submit">
      <div class="form-grid">
        <div class="form-row">
          <label for="username">用户名</label>
          <input
            id="username"
            v-model="form.username"
            type="text"
            maxlength="32"
            placeholder="3-32 位字母、数字或下划线"
            autocomplete="username"
          />
        </div>
        <div class="form-row">
          <label for="nickname">昵称（可选）</label>
          <input id="nickname" v-model="form.nickname" type="text" maxlength="32" />
        </div>
      </div>

      <div class="form-row">
        <label for="email">邮箱</label>
        <input id="email" v-model="form.email" type="email" maxlength="128" autocomplete="email" />
      </div>

      <div class="form-row">
        <label for="password">密码</label>
        <input
          id="password"
          v-model="form.password"
          type="password"
          minlength="8"
          maxlength="64"
          autocomplete="new-password"
        />
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

      <p v-if="error" class="error">{{ error }}</p>
      <p v-if="success" class="success">{{ success }}</p>

      <div class="actions" style="margin-top: 12px">
        <button type="submit" :disabled="submitting">
          {{ submitting ? '注册中…' : '注册' }}
        </button>
        <RouterLink to="/login">已有账号，去登录</RouterLink>
      </div>
    </form>
  </div>
</template>
