<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { errorMessage } from '../api/http'
import { listSubmissions } from '../api/submissions'
import { getMyProfile, getUser } from '../api/users'
import {
  SUBMISSION_STATUSES,
  type PageResult,
  type SubmissionSummaryVO,
  type UserProfileVO,
} from '../api/types'
import Pagination from '../components/Pagination.vue'
import { session } from '../stores/session'
import { fmt, fmtDate, statusClass, statusLabel } from '../utils/format'

const route = useRoute()
const isMe = computed(() => route.name === 'user-me')

const profile = ref<UserProfileVO | null>(null)
const submissions = ref<PageResult<SubmissionSummaryVO> | null>(null)
const loading = ref(false)
const error = ref('')
const submissionError = ref('')
const page = ref(1)
const size = ref(10)

const isOwnProfile = computed(
  () => !!profile.value && session.user?.id === profile.value.id,
)

async function loadSubmissions() {
  if (!profile.value) return
  submissionError.value = ''
  try {
    submissions.value = await listSubmissions({
      userId: profile.value.id,
      page: page.value,
      size: size.value,
    })
  } catch (e) {
    submissionError.value = errorMessage(e)
    submissions.value = null
  }
}

async function loadProfile() {
  loading.value = true
  error.value = ''
  submissionError.value = ''
  profile.value = null
  submissions.value = null
  page.value = 1
  try {
    profile.value = isMe.value
      ? await getMyProfile()
      : await getUser(Number(route.params.id))
    await loadSubmissions()
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    loading.value = false
  }
}

function changePage(value: number) {
  page.value = value
  loadSubmissions()
}

function changeSize(value: number) {
  size.value = value
  page.value = 1
}

onMounted(loadProfile)
watch(() => route.fullPath, loadProfile)
</script>

<template>
  <div v-if="loading" class="card">加载中…</div>
  <div v-else-if="error" class="card">
    <p class="error">{{ error }}</p>
    <RouterLink to="/catalog">返回题库</RouterLink>
  </div>

  <template v-else-if="profile">
    <div class="page-title">
      <h1>{{ profile.nickname }}（{{ profile.username }}）</h1>
      <div class="actions">
        <RouterLink v-if="isOwnProfile" to="/settings">账号设置</RouterLink>
        <RouterLink :to="{ path: '/submissions', query: { userId: profile.id } }">
          提交列表
        </RouterLink>
      </div>
    </div>

    <div class="card">
      <div class="detail-grid">
        <div><strong>用户 ID</strong>{{ profile.id }}</div>
        <div><strong>用户名</strong>{{ profile.username }}</div>
        <div><strong>昵称</strong>{{ profile.nickname }}</div>
        <div><strong>角色</strong>{{ profile.role }}</div>
        <div><strong>注册时间</strong>{{ fmtDate(profile.createdAt) }}</div>
        <div><strong>通过题目数</strong>{{ profile.solvedCount }}</div>
        <div><strong>贡献已发布题目</strong>{{ profile.contributedCount }}</div>
      </div>
      <p v-if="isOwnProfile" class="hint">这是你自己的主页。</p>
    </div>

    <div class="card">
      <h2>提交记录</h2>
      <p v-if="submissionError" class="error">{{ submissionError }}</p>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>ID</th>
              <th>题目</th>
              <th>语言</th>
              <th>状态</th>
              <th>耗时</th>
              <th>内存</th>
              <th>时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in submissions?.items ?? []" :key="item.id">
              <td><RouterLink :to="`/submissions/${item.id}`">{{ item.id }}</RouterLink></td>
              <td><RouterLink :to="`/problems/${item.problemId}`">{{ item.problemTitle }}</RouterLink></td>
              <td>{{ item.language }}</td>
              <td><span :class="statusClass(item.status)">{{ statusLabel(item.status, SUBMISSION_STATUSES) }}</span></td>
              <td>{{ fmt(item.timeUsedMs) }}</td>
              <td>{{ fmt(item.memoryUsedKb) }}</td>
              <td>{{ fmtDate(item.createdAt) }}</td>
            </tr>
            <tr v-if="!(submissions?.items.length)">
              <td colspan="7" class="hint">暂无提交</td>
            </tr>
          </tbody>
        </table>
      </div>

      <Pagination
        :page="page"
        :size="size"
        :total="submissions?.total ?? 0"
        @update:page="changePage"
        @update:size="changeSize"
      />
    </div>
  </template>
</template>
