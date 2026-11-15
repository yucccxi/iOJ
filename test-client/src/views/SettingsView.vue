<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getMe, logoutAll } from '../api/auth'
import { errorMessage } from '../api/http'
import { changePassword, updateProfile } from '../api/users'
import type { UserVO } from '../api/types'
import { applyLogin, logoutLocal, updateSessionUser } from '../stores/session'
import { fmtDate } from '../utils/format'

const router = useRouter()
const me = ref<UserVO | null>(null)
const loading = ref(false)
const error = ref('')
const notice = ref('')

const nickname = ref('')
const passwordForm = reactive({ oldPassword: '', newPassword: '' })

async function loadMe() {
  loading.value = true
  error.value = ''
  try {
    me.value = await getMe()
    nickname.value = me.value.nickname
    updateSessionUser(me.value)
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    loading.value = false
  }
}

async function saveNickname() {
  error.value = ''
  notice.value = ''
  try {
    const user = await updateProfile({ nickname: nickname.value })
    me.value = user
    updateSessionUser(user)
    notice.value = '昵称已更新，题库与提交列表中的作者名会同步变化。'
  } catch (e) {
    error.value = errorMessage(e)
  }
}

async function submitPassword() {
  error.value = ''
  notice.value = ''
  try {
    await changePassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword,
    })
    logoutLocal()
    router.push({ name: 'login', query: { passwordChanged: '1' } })
  } catch (e) {
    error.value = errorMessage(e)
  }
}

async function logoutEverywhere() {
  error.value = ''
  notice.value = ''
  try {
    const result = await logoutAll()
    applyLogin(result)
    me.value = result.user
    notice.value = '已使该账号之前签发的所有 token 失效；当前设备已换用新 token。'
  } catch (e) {
    error.value = errorMessage(e)
  }
}

function logoutHere() {
  logoutLocal()
  router.push({ name: 'login' })
}

onMounted(loadMe)
</script>

<template>
  <div class="page-title">
    <h1>账号设置</h1>
    <button type="button" class="secondary" :disabled="loading" @click="loadMe">
      {{ loading ? '刷新中…' : '刷新当前账号（GET /auth/me）' }}
    </button>
  </div>

  <p v-if="error" class="error">{{ error }}</p>
  <p v-if="notice" class="success">{{ notice }}</p>

  <div class="card">
    <h2>当前账号</h2>
    <div v-if="me" class="detail-grid">
      <div><strong>ID</strong>{{ me.id }}</div>
      <div><strong>用户名</strong>{{ me.username }}</div>
      <div><strong>昵称</strong>{{ me.nickname }}</div>
      <div><strong>角色</strong>{{ me.role }}</div>
      <div><strong>注册时间</strong>{{ fmtDate(me.createdAt) }}</div>
    </div>
    <p v-else class="hint">未获取到账号信息</p>
  </div>

  <div class="card">
    <h2>修改昵称（PATCH /users/me）</h2>
    <form class="inline-form" @submit.prevent="saveNickname">
      <div class="form-row" style="flex: 1; min-width: 220px">
        <label for="nickname">昵称</label>
        <input id="nickname" v-model="nickname" type="text" maxlength="32" />
      </div>
      <button type="submit">保存昵称</button>
    </form>
  </div>

  <div class="card">
    <h2>修改密码（PUT /users/me/password）</h2>
    <div class="notice warn">
      修改成功后当前 token 和所有旧 token 会立即失效，需要重新登录。
    </div>
    <form @submit.prevent="submitPassword">
      <div class="form-grid">
        <div class="form-row">
          <label for="oldPassword">原密码</label>
          <input
            id="oldPassword"
            v-model="passwordForm.oldPassword"
            type="password"
            autocomplete="current-password"
          />
        </div>
        <div class="form-row">
          <label for="newPassword">新密码（8-64 位）</label>
          <input
            id="newPassword"
            v-model="passwordForm.newPassword"
            type="password"
            minlength="8"
            maxlength="64"
            autocomplete="new-password"
          />
        </div>
      </div>
      <button type="submit">修改密码</button>
    </form>
  </div>

  <div class="card">
    <h2>退出所有设备（POST /auth/logout-all）</h2>
    <p class="hint">
      让该账号之前签发的所有 JWT 立即失效，并为当前设备返回一个新 token。其他设备需要重新登录。
    </p>
    <div class="actions">
      <button type="button" class="secondary" @click="logoutEverywhere">退出所有设备</button>
      <button type="button" class="danger" @click="logoutHere">仅退出当前设备</button>
    </div>
  </div>
</template>
