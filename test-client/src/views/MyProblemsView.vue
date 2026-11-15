<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { errorMessage } from '../api/http'
import { listMyProblems } from '../api/problems'
import {
  DIFFICULTIES,
  PROBLEM_STATUSES,
  type PageResult,
  type ProblemSummaryVO,
} from '../api/types'
import Pagination from '../components/Pagination.vue'
import { fmtDate, fmtRating, statusClass, statusLabel } from '../utils/format'

const result = ref<PageResult<ProblemSummaryVO> | null>(null)
const loading = ref(false)
const error = ref('')
const filters = reactive({ keyword: '', page: 1, size: 20 })

async function load() {
  loading.value = true
  error.value = ''
  try {
    result.value = await listMyProblems({
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
    <h1>我的题目</h1>
    <RouterLink to="/problems/new"><button type="button">上传新题</button></RouterLink>
  </div>

  <div class="card">
    <div class="inline-form">
      <div class="form-row" style="flex: 1; min-width: 220px">
        <label for="keyword">标题关键词</label>
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
  </div>

  <div class="card">
    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>ID</th>
            <th>标题</th>
            <th>难度</th>
            <th>Rating</th>
            <th>状态</th>
            <th>提交 / 通过</th>
            <th>标签</th>
            <th>创建时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in result?.items ?? []" :key="item.id">
            <td>{{ item.id }}</td>
            <td><RouterLink :to="`/problems/${item.id}`">{{ item.title }}</RouterLink></td>
            <td>{{ statusLabel(item.difficulty, DIFFICULTIES) }}</td>
            <td>{{ fmtRating(item.rating) }}</td>
            <td><span :class="statusClass(item.status)">{{ statusLabel(item.status, PROBLEM_STATUSES) }}</span></td>
            <td>{{ item.submitCount }} / {{ item.acceptedCount }}</td>
            <td>
              <span v-for="tag in item.tags" :key="tag.id" class="badge" style="margin-right: 4px">
                {{ tag.name }}
              </span>
              <span v-if="!item.tags.length" class="hint">-</span>
            </td>
            <td>{{ fmtDate(item.createdAt) }}</td>
            <td class="actions">
              <RouterLink :to="`/problems/${item.id}/edit`">编辑</RouterLink>
              <RouterLink :to="`/problems/${item.id}`">查看</RouterLink>
            </td>
          </tr>
          <tr v-if="!(result?.items.length)">
            <td colspan="9" class="hint">还没有上传过题目</td>
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
