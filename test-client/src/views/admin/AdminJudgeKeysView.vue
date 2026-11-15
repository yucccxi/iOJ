<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { createJudgeKey, deleteJudgeKey, listJudgeKeys, updateJudgeKey } from '../../api/admin'
import { errorMessage } from '../../api/http'
import type { JudgeKeyCreatedVO, JudgeKeyVO } from '../../api/types'
import { fmtDate, fromDatetimeLocal } from '../../utils/format'

const keys = ref<JudgeKeyVO[]>([])
const loading = ref(false)
const error = ref('')
const notice = ref('')
const created = ref<JudgeKeyCreatedVO | null>(null)
const form = reactive({ name: '', expiresAt: '' })

async function load() {
  loading.value = true
  error.value = ''
  try {
    keys.value = await listJudgeKeys()
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    loading.value = false
  }
}

async function create() {
  error.value = ''
  notice.value = ''
  created.value = null
  try {
    const result = await createJudgeKey({
      name: form.name,
      expiresAt: fromDatetimeLocal(form.expiresAt),
    })
    created.value = result
    notice.value = `已创建 key #${result.id}；明文 key 只显示这一次。`
    form.name = ''
    form.expiresAt = ''
    await load()
  } catch (e) {
    error.value = errorMessage(e)
  }
}

async function toggle(key: JudgeKeyVO) {
  error.value = ''
  notice.value = ''
  try {
    await updateJudgeKey(key.id, { enabled: !key.enabled })
    notice.value = `key #${key.id} 已${key.enabled ? '禁用' : '启用'}`
    await load()
  } catch (e) {
    error.value = errorMessage(e)
  }
}

async function remove(key: JudgeKeyVO) {
  if (!window.confirm(`确认删除 key #${key.id}「${key.name}」？对应判题节点记录也会删除，历史提交仍保留。`)) {
    return
  }
  error.value = ''
  notice.value = ''
  try {
    await deleteJudgeKey(key.id)
    if (created.value?.id === key.id) created.value = null
    notice.value = `已删除 key #${key.id}`
    await load()
  } catch (e) {
    error.value = errorMessage(e)
  }
}

async function copyKey() {
  if (!created.value) return
  try {
    await navigator.clipboard.writeText(created.value.key)
    notice.value = '明文 key 已复制到剪贴板'
  } catch {
    error.value = '复制失败，请手动选中明文 key 复制'
  }
}

onMounted(load)
</script>

<template>
  <div class="page-title">
    <h1>判题 Key 管理</h1>
    <button type="button" class="secondary" :disabled="loading" @click="load">
      {{ loading ? '刷新中…' : '刷新' }}
    </button>
  </div>

  <div class="card">
    <h2>创建 Key</h2>
    <form class="inline-form" @submit.prevent="create">
      <div class="form-row" style="flex: 1; min-width: 220px">
        <label for="name">名称 / 节点名（≤128 字符）</label>
        <input id="name" v-model="form.name" type="text" maxlength="128" />
      </div>
      <div class="form-row">
        <label for="expiresAt">过期时间（可选，留空不过期）</label>
        <input id="expiresAt" v-model="form.expiresAt" type="datetime-local" step="1" />
      </div>
      <button type="submit">创建</button>
    </form>
    <p class="hint">
      明文 key 只在创建响应中出现一次；服务端只保存哈希和前缀。列表中的 prefix 用于识别 key。
      一个判题节点建议使用一个独立 key。
    </p>
    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="notice" class="success">{{ notice }}</p>
  </div>

  <div v-if="created" class="card">
    <h2>新 key 明文（仅此一次）</h2>
    <p class="mono" style="font-size: 15px"><strong>{{ created.key }}</strong></p>
    <div class="actions">
      <button type="button" @click="copyKey">复制 key</button>
      <span class="hint">
        id={{ created.id }}，prefix={{ created.prefix }}，创建于 {{ fmtDate(created.createdAt) }}
      </span>
    </div>
    <div class="notice warn" style="margin-top: 12px">
      把该值配置到判题机的 IOJ_JUDGE_KEY。丢失后不能找回，只能删除并重新创建。
    </div>
  </div>

  <div class="card">
    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>ID</th>
            <th>名称</th>
            <th>前缀</th>
            <th>启用</th>
            <th>过期时间</th>
            <th>最近领取</th>
            <th>创建时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="key in keys" :key="key.id">
            <td>{{ key.id }}</td>
            <td>{{ key.name }}</td>
            <td class="mono">{{ key.prefix }}</td>
            <td>{{ key.enabled ? '是' : '否' }}</td>
            <td>{{ fmtDate(key.expiresAt) }}</td>
            <td>{{ fmtDate(key.lastUsedAt) }}</td>
            <td>{{ fmtDate(key.createdAt) }}</td>
            <td class="actions">
              <button type="button" class="secondary" @click="toggle(key)">
                {{ key.enabled ? '禁用' : '启用' }}
              </button>
              <button type="button" class="danger" @click="remove(key)">删除</button>
            </td>
          </tr>
          <tr v-if="!keys.length">
            <td colspan="8" class="hint">还没有判题 key</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
