<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { listAdminUsers, updateUserRole, updateUserStatus } from '../../api/admin'
import { errorMessage } from '../../api/http'
import type { PageResult, UserStatus, UserVO } from '../../api/types'
import Pagination from '../../components/Pagination.vue'
import { session } from '../../stores/session'
import { fmtDate, statusClass } from '../../utils/format'

const filters = reactive({ keyword: '', page: 1, size: 20 })
const result = ref<PageResult<UserVO> | null>(null)
const loading = ref(false)
const error = ref('')
const notice = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    result.value = await listAdminUsers({
      keyword: filters.keyword.trim() || undefined,
      page: filters.page,
      size: filters.size,
    })
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    loading.value = false
  }
}

async function toggleRole(user: UserVO) {
  const role = user.role === 'ADMIN' ? 'USER' : 'ADMIN'
  if (!window.confirm(`确认把用户 #${user.id}（${user.username}）的角色改为 ${role}？`)) return
  error.value = ''
  notice.value = ''
  try {
    await updateUserRole(user.id, { role })
    notice.value = `用户 #${user.id} 角色已改为 ${role}，对方下一次请求生效。`
    await load()
  } catch (e) {
    error.value = errorMessage(e)
  }
}

async function setStatus(user: UserVO, status: UserStatus) {
  const action = status === 'BANNED' ? '封禁' : '解封'
  if (!window.confirm(`确认${action}用户 #${user.id}（${user.username}）？`)) return
  error.value = ''
  notice.value = ''
  try {
    await updateUserStatus(user.id, { status })
    notice.value = `已${action}用户 #${user.id}。用户列表不返回封禁状态，请勿根据角色推断。`
    await load()
  } catch (e) {
    error.value = errorMessage(e)
  }
}

function search() {
  filters.page = 1
  load()
}

function changePage(page: number) {
  filters.page = page
  load()
}

function changeSize(size: number) {
  filters.size = size
  filters.page = 1
}

onMounted(load)
</script>

<template>
  <div class="page-title">
    <h1>用户管理</h1>
  </div>

  <div class="card">
    <div class="inline-form">
      <div class="form-row" style="flex: 1; min-width: 240px">
        <label for="keyword">关键词（用户名 / 昵称 / 邮箱）</label>
        <input id="keyword" v-model="filters.keyword" type="text" @keyup.enter="search" />
      </div>
      <button type="button" :disabled="loading" @click="search">
        {{ loading ? '查询中…' : '查询' }}
      </button>
      <button
        type="button"
        class="secondary"
        @click="
          () => {
            filters.keyword = ''
            filters.page = 1
            load()
          }
        "
      >
        重置
      </button>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="notice" class="success">{{ notice }}</p>
    <div class="notice warn">
      用户列表不返回邮箱和封禁状态；不要用 role 或昵称推断谁被封禁。管理员不能修改自己的角色或封禁自己。
    </div>
  </div>

  <div class="card">
    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>ID</th>
            <th>用户名</th>
            <th>昵称</th>
            <th>角色</th>
            <th>注册时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in result?.items ?? []" :key="item.id">
            <td>{{ item.id }}</td>
            <td><RouterLink :to="`/users/${item.id}`">{{ item.username }}</RouterLink></td>
            <td>{{ item.nickname }}</td>
            <td><span :class="statusClass(item.role)">{{ item.role }}</span></td>
            <td>{{ fmtDate(item.createdAt) }}</td>
            <td class="actions">
              <button
                type="button"
                class="secondary"
                :disabled="item.id === session.user?.id"
                @click="toggleRole(item)"
              >
                {{ item.role === 'ADMIN' ? '降为普通用户' : '设为管理员' }}
              </button>
              <button
                type="button"
                class="danger"
                :disabled="item.id === session.user?.id"
                @click="setStatus(item, 'BANNED')"
              >
                封禁
              </button>
              <button type="button" class="secondary" @click="setStatus(item, 'ACTIVE')">
                解封
              </button>
            </td>
          </tr>
          <tr v-if="!(result?.items.length)">
            <td colspan="6" class="hint">没有匹配的用户</td>
          </tr>
        </tbody>
      </table>
    </div>

    <Pagination
      :page="filters.page"
      :size="filters.size"
      :total="result?.total ?? 0"
      @update:page="changePage"
      @update:size="changeSize"
    />
  </div>
</template>
