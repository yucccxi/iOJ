<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { errorMessage } from '../api/http'
import { listSubmissions } from '../api/submissions'
import {
  SUBMISSION_STATUSES,
  type PageResult,
  type SubmissionStatus,
  type SubmissionSummaryVO,
} from '../api/types'
import Pagination from '../components/Pagination.vue'
import { fmt, fmtDate, parseInteger, statusClass, statusLabel } from '../utils/format'

const route = useRoute()
const filters = reactive({
  problemId: typeof route.query.problemId === 'string' ? route.query.problemId : '',
  userId: typeof route.query.userId === 'string' ? route.query.userId : '',
  status: '' as SubmissionStatus | '',
  page: 1,
  size: 20,
})

const result = ref<PageResult<SubmissionSummaryVO> | null>(null)
const loading = ref(false)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    result.value = await listSubmissions({
      problemId: parseInteger(filters.problemId),
      userId: parseInteger(filters.userId),
      status: filters.status || undefined,
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

function reset() {
  filters.problemId = ''
  filters.userId = ''
  filters.status = ''
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
    <h1>提交列表</h1>
  </div>

  <div class="card">
    <div class="form-grid">
      <div class="form-row">
        <label for="problemId">题目 ID</label>
        <input id="problemId" v-model="filters.problemId" type="number" @keyup.enter="search" />
      </div>
      <div class="form-row">
        <label for="userId">用户 ID</label>
        <input id="userId" v-model="filters.userId" type="number" @keyup.enter="search" />
      </div>
      <div class="form-row">
        <label for="status">状态</label>
        <select id="status" v-model="filters.status">
          <option value="">全部</option>
          <option v-for="item in SUBMISSION_STATUSES" :key="item.value" :value="item.value">
            {{ item.label }}（{{ item.value }}）
          </option>
        </select>
      </div>
    </div>
    <div class="actions" style="margin-top: 12px">
      <button type="button" :disabled="loading" @click="search">
        {{ loading ? '查询中…' : '查询' }}
      </button>
      <button type="button" class="secondary" @click="reset">重置</button>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
  </div>

  <div class="card">
    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>ID</th>
            <th>题目</th>
            <th>提交者</th>
            <th>语言</th>
            <th>状态</th>
            <th>耗时</th>
            <th>内存</th>
            <th>提交时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in result?.items ?? []" :key="item.id">
            <td><RouterLink :to="`/submissions/${item.id}`">{{ item.id }}</RouterLink></td>
            <td><RouterLink :to="`/problems/${item.problemId}`">{{ item.problemTitle }}</RouterLink></td>
            <td><RouterLink :to="`/users/${item.userId}`">{{ item.userNickname }}</RouterLink></td>
            <td>{{ item.language }}</td>
            <td>
              <span :class="statusClass(item.status)">
                {{ statusLabel(item.status, SUBMISSION_STATUSES) }}
              </span>
            </td>
            <td>{{ fmt(item.timeUsedMs) }}</td>
            <td>{{ fmt(item.memoryUsedKb) }}</td>
            <td>{{ fmtDate(item.createdAt) }}</td>
          </tr>
          <tr v-if="!(result?.items.length)">
            <td colspan="8" class="hint">没有匹配的提交</td>
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
