<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { createScope, deleteScope, renameScope } from '../../api/admin'
import { errorMessage } from '../../api/http'
import { getScopes } from '../../api/meta'
import type { ScopeMetaVO } from '../../api/types'
import { fmtDate } from '../../utils/format'

const scopes = ref<ScopeMetaVO[]>([])
const loading = ref(false)
const error = ref('')
const notice = ref('')
const newName = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    scopes.value = await getScopes()
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    loading.value = false
  }
}

async function create() {
  error.value = ''
  notice.value = ''
  try {
    const scope = await createScope({ name: newName.value })
    notice.value = `已创建 scope #${scope.id} ${scope.name}`
    newName.value = ''
    await load()
  } catch (e) {
    error.value = errorMessage(e)
  }
}

async function rename(scope: ScopeMetaVO) {
  const name = window.prompt(`重命名 scope #${scope.id}`, scope.name)
  if (name === null || name.trim() === '') return
  error.value = ''
  notice.value = ''
  try {
    await renameScope(scope.id, { name })
    notice.value = `scope #${scope.id} 已重命名，题库中的 scopeName 会立即变化`
    await load()
  } catch (e) {
    error.value = errorMessage(e)
  }
}

async function remove(scope: ScopeMetaVO) {
  if (!window.confirm(`确认删除 scope #${scope.id}「${scope.name}」？仍被引用时返回 1005，且不支持 force。`)) {
    return
  }
  error.value = ''
  notice.value = ''
  try {
    await deleteScope(scope.id)
    notice.value = `已删除 scope #${scope.id}`
    await load()
  } catch (e) {
    error.value = errorMessage(e)
  }
}

onMounted(load)
</script>

<template>
  <div class="page-title">
    <h1>Scope 管理</h1>
    <button type="button" class="secondary" :disabled="loading" @click="load">
      {{ loading ? '刷新中…' : '刷新' }}
    </button>
  </div>

  <div class="card">
    <h2>新建 Scope</h2>
    <form class="inline-form" @submit.prevent="create">
      <div class="form-row" style="flex: 1; min-width: 220px">
        <label for="newName">名称（1-128 字符）</label>
        <input id="newName" v-model="newName" type="text" maxlength="128" />
      </div>
      <button type="submit">创建</button>
    </form>
    <p class="hint">
      列表来自公开接口 GET /meta/scopes。LOCAL 是系统保留 scope，不得改名或删除。
      删除仍被引用的 scope 会返回 1005，不支持 force。
    </p>
    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="notice" class="success">{{ notice }}</p>
  </div>

  <div class="card">
    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>ID</th>
            <th>名称</th>
            <th>创建时间</th>
            <th>使用量（已发布）</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="scope in scopes" :key="scope.id">
            <td>{{ scope.id }}</td>
            <td>{{ scope.name }}</td>
            <td>{{ fmtDate(scope.createdAt) }}</td>
            <td>{{ scope.problemCount }}</td>
            <td class="actions">
              <button
                type="button"
                class="secondary"
                :disabled="scope.name === 'LOCAL'"
                @click="rename(scope)"
              >
                重命名
              </button>
              <button
                type="button"
                class="danger"
                :disabled="scope.name === 'LOCAL'"
                @click="remove(scope)"
              >
                删除
              </button>
            </td>
          </tr>
          <tr v-if="!scopes.length">
            <td colspan="5" class="hint">暂无 scope</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
