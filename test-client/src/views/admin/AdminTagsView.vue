<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { createTag, deleteTag, renameTag } from '../../api/admin'
import { errorMessage } from '../../api/http'
import { getTags } from '../../api/meta'
import type { TagMetaVO } from '../../api/types'

const tags = ref<TagMetaVO[]>([])
const loading = ref(false)
const error = ref('')
const notice = ref('')
const newName = ref('')
const forceDelete = ref(false)

async function load() {
  loading.value = true
  error.value = ''
  try {
    tags.value = await getTags()
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
    const tag = await createTag({ name: newName.value })
    notice.value = `已创建标签 #${tag.id} ${tag.name}`
    newName.value = ''
    await load()
  } catch (e) {
    error.value = errorMessage(e)
  }
}

async function rename(tag: TagMetaVO) {
  const name = window.prompt(`重命名标签 #${tag.id}`, tag.name)
  if (name === null || name.trim() === '') return
  error.value = ''
  notice.value = ''
  try {
    await renameTag(tag.id, { name })
    notice.value = `标签 #${tag.id} 已重命名`
    await load()
  } catch (e) {
    error.value = errorMessage(e)
  }
}

async function remove(tag: TagMetaVO) {
  const suffix = forceDelete.value ? '并先解除所有题目关联' : ''
  if (!window.confirm(`确认删除标签 #${tag.id}「${tag.name}」${suffix}？`)) return
  error.value = ''
  notice.value = ''
  try {
    await deleteTag(tag.id, forceDelete.value)
    notice.value = `已删除标签 #${tag.id}`
    await load()
  } catch (e) {
    error.value = errorMessage(e)
  }
}

onMounted(load)
</script>

<template>
  <div class="page-title">
    <h1>标签管理</h1>
    <button type="button" class="secondary" :disabled="loading" @click="load">
      {{ loading ? '刷新中…' : '刷新' }}
    </button>
  </div>

  <div class="card">
    <h2>新建标签</h2>
    <form class="inline-form" @submit.prevent="create">
      <div class="form-row" style="flex: 1; min-width: 220px">
        <label for="newName">名称（1-32 位字母、数字、中文或 _+#.-）</label>
        <input id="newName" v-model="newName" type="text" maxlength="32" />
      </div>
      <button type="submit">创建</button>
    </form>
    <p class="hint">
      列表来自公开接口 GET /meta/tags；problemCount 只统计已发布本地题 + 外部题。
      删除仍被使用的标签需要勾选 force，或后端返回 1005。
    </p>
    <div class="checkbox-list">
      <label><input v-model="forceDelete" type="checkbox" /> 删除时强制解除所有关联（force=true）</label>
    </div>
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
            <th>使用量（已发布）</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="tag in tags" :key="tag.id">
            <td>{{ tag.id }}</td>
            <td>{{ tag.name }}</td>
            <td>{{ tag.problemCount }}</td>
            <td class="actions">
              <button type="button" class="secondary" @click="rename(tag)">重命名</button>
              <button type="button" class="danger" @click="remove(tag)">删除</button>
            </td>
          </tr>
          <tr v-if="!tags.length">
            <td colspan="4" class="hint">暂无标签</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
