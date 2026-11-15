<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { listAdminProblems, updateProblemStatus } from '../../api/admin'
import { errorMessage } from '../../api/http'
import {
  DIFFICULTIES,
  PROBLEM_STATUSES,
  type PageResult,
  type ProblemStatus,
  type ProblemSummaryVO,
} from '../../api/types'
import Pagination from '../../components/Pagination.vue'
import { fmtDate, fmtRating, statusClass, statusLabel } from '../../utils/format'

const filters = reactive({
  keyword: '',
  status: '' as ProblemStatus | '',
  page: 1,
  size: 20,
})

const result = ref<PageResult<ProblemSummaryVO> | null>(null)
const loading = ref(false)
const error = ref('')
const notice = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    result.value = await listAdminProblems({
      keyword: filters.keyword.trim() || undefined,
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

async function setStatus(item: ProblemSummaryVO, status: 'PUBLISHED' | 'HIDDEN') {
  const action = status === 'PUBLISHED' ? '上架' : '下架'
  if (!window.confirm(`确认${action}题目 #${item.id}「${item.title}」？`)) return
  error.value = ''
  notice.value = ''
  try {
    await updateProblemStatus(item.id, { status })
    notice.value = `已${action}题目 #${item.id}`
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
    <h1>题目审核</h1>
    <RouterLink to="/catalog">前往题库</RouterLink>
  </div>

  <div class="card">
    <div class="form-grid">
      <div class="form-row">
        <label for="keyword">标题关键词</label>
        <input id="keyword" v-model="filters.keyword" type="text" @keyup.enter="search" />
      </div>
      <div class="form-row">
        <label for="status">状态</label>
        <select id="status" v-model="filters.status">
          <option value="">全部</option>
          <option v-for="item in PROBLEM_STATUSES" :key="item.value" :value="item.value">
            {{ item.label }}（{{ item.value }}）
          </option>
        </select>
      </div>
    </div>
    <div class="actions" style="margin-top: 12px">
      <button type="button" :disabled="loading" @click="search">
        {{ loading ? '查询中…' : '查询' }}
      </button>
      <button
        type="button"
        class="secondary"
        @click="
          () => {
            filters.keyword = ''
            filters.status = ''
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
    <p class="hint">
      上架要求题目已有测试数据，否则返回 3003；DRAFT / PENDING_REVIEW 不能通过本接口设置。
    </p>
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
            <th>作者</th>
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
              <RouterLink :to="`/users/${item.authorId}`">{{ item.authorNickname }}</RouterLink>
            </td>
            <td>{{ fmtDate(item.createdAt) }}</td>
            <td class="actions">
              <button
                v-if="item.status !== 'PUBLISHED'"
                type="button"
                @click="setStatus(item, 'PUBLISHED')"
              >
                上架
              </button>
              <button
                v-if="item.status === 'PUBLISHED'"
                type="button"
                class="danger"
                @click="setStatus(item, 'HIDDEN')"
              >
                下架
              </button>
              <RouterLink :to="`/problems/${item.id}/edit`">编辑</RouterLink>
            </td>
          </tr>
          <tr v-if="!(result?.items.length)">
            <td colspan="9" class="hint">没有匹配的题目</td>
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
