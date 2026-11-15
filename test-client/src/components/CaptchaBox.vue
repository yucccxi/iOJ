<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { createCaptcha } from '../api/auth'
import { errorMessage } from '../api/http'
import type { CaptchaPurpose } from '../api/types'

const props = defineProps<{ purpose: CaptchaPurpose }>()

const captchaId = defineModel<string>('captchaId', { default: '' })
const code = defineModel<string>('code', { default: '' })

const image = ref('')
const loading = ref(false)
const disabled = ref(false)
const error = ref('')

async function refresh() {
  loading.value = true
  error.value = ''
  disabled.value = false
  captchaId.value = ''
  code.value = ''
  try {
    const result = await createCaptcha(props.purpose)
    captchaId.value = result.captchaId
    image.value = result.image
  } catch (e) {
    image.value = ''
    const message = errorMessage(e)
    if (message.includes('未启用')) {
      disabled.value = true
    } else {
      error.value = message
    }
  } finally {
    loading.value = false
  }
}

onMounted(refresh)

defineExpose({ refresh })
</script>

<template>
  <div class="captcha-box">
    <div class="form-row">
      <label>图形验证码</label>
      <input
        v-model="code"
        type="text"
        maxlength="16"
        placeholder="输入图片中的字符"
        :disabled="disabled"
      />
    </div>
    <div class="captcha-line">
      <img v-if="image" :src="image" alt="图形验证码" class="captcha-image" @click="refresh" />
      <button type="button" class="secondary" :disabled="loading" @click="refresh">
        {{ loading ? '加载中…' : '刷新验证码' }}
      </button>
      <span v-if="disabled" class="hint">图形验证码未启用，可留空</span>
      <span v-if="error" class="error">{{ error }}</span>
    </div>
  </div>
</template>
