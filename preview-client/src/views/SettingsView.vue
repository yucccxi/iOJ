<script setup lang="ts">
import { useRequestScope } from '@/composables/useRequestScope'
const signal = useRequestScope()
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '@/api/resources'
import { useAuthStore } from '@/stores/auth'
import { errorText } from '@/lib/contracts'
const auth = useAuthStore(),
  router = useRouter()
const nickname = ref(auth.user?.nickname || ''),
  oldPassword = ref(''),
  newPassword = ref(''),
  repeat = ref('')
const saving = ref(false),
  changing = ref(false),
  error = ref(''),
  passwordError = ref(''),
  notice = ref('')
async function saveProfile() {
  if (saving.value) return
  saving.value = true
  error.value = ''
  notice.value = ''
  try {
    const user = await api.updateProfile(nickname.value.trim(), signal)
    auth.updateUser(user)
    notice.value = '昵称已更新'
  } catch (cause) {
    error.value = errorText(cause)
  } finally {
    saving.value = false
  }
}
async function changePassword() {
  passwordError.value = ''
  if (newPassword.value !== repeat.value) {
    passwordError.value = '两次新密码不一致'
    return
  }
  if (changing.value) return
  changing.value = true
  try {
    await api.password({ oldPassword: oldPassword.value, newPassword: newPassword.value }, signal)
    oldPassword.value = newPassword.value = repeat.value = ''
    auth.signOut()
    await router.replace({ path: '/login', query: { changed: '1' } })
  } catch (cause) {
    passwordError.value = errorText(cause)
  } finally {
    changing.value = false
  }
}
</script>
<template>
  <div class="page-heading"><h1>账号设置</h1></div>
  <div class="settings-grid">
    <section class="panel content-panel">
      <h2>个人资料</h2>
      <form class="stack" @submit.prevent="saveProfile">
        <label>用户名<input :value="auth.user?.username" disabled /></label
        ><label>昵称<input v-model="nickname" required maxlength="32" :disabled="saving" /></label>
        <p v-if="error" class="error" role="alert">{{ error }}</p>
        <p v-if="notice" class="notice" role="status">{{ notice }}</p>
        <button class="button fit" :disabled="saving || !nickname.trim()">
          {{ saving ? '保存中…' : '保存昵称' }}
        </button>
      </form>
    </section>
    <section class="panel content-panel">
      <h2>修改密码</h2>
      <form class="stack" @submit.prevent="changePassword">
        <label
          >原密码<input
            v-model="oldPassword"
            type="password"
            autocomplete="current-password"
            required
            maxlength="64"
            :disabled="changing" /></label
        ><label
          >新密码<input
            v-model="newPassword"
            type="password"
            autocomplete="new-password"
            required
            minlength="8"
            maxlength="64"
            :disabled="changing"
          /><span class="field-hint">8–64 位</span></label
        ><label
          >确认新密码<input
            v-model="repeat"
            type="password"
            autocomplete="new-password"
            required
            minlength="8"
            maxlength="64"
            :disabled="changing"
        /></label>
        <p v-if="passwordError" class="error" role="alert">{{ passwordError }}</p>
        <button class="button fit" :disabled="changing">
          {{ changing ? '处理中…' : '修改密码' }}
        </button>
      </form>
    </section>
  </div>
</template>
