<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { isApiError, errorMessage } from '../api/http'
import { getLanguages } from '../api/meta'
import { deleteProblem, getProblem } from '../api/problems'
import { getSubmission, listSubmissions, submit as createSubmission } from '../api/submissions'
import {
  DIFFICULTIES,
  PROBLEM_STATUSES,
  type LanguageVO,
  type ProblemDetailVO,
  type SubmissionDetailVO,
  type SubmissionSummaryVO,
} from '../api/types'
import { session } from '../stores/session'
import {
  fmt,
  fmtDate,
  fmtRating,
  statusClass,
  statusLabel,
} from '../utils/format'

const route = useRoute()
const router = useRouter()

const problemId = computed(() => Number(route.params.id))
const problem = ref<ProblemDetailVO | null>(null)
const languages = ref<LanguageVO[]>([])
const recentSubmissions = ref<SubmissionSummaryVO[]>([])
const loading = ref(false)
const error = ref('')
const errorCode = ref(0)

const submitLanguage = ref('')
const submitCode = ref('')
const submitting = ref(false)
const submitError = ref('')
const submitNotice = ref('')
const currentSubmission = ref<SubmissionDetailVO | null>(null)
let pollTimer: number | undefined
let pollStartedAt = 0

const canManage = computed(() => {
  if (!problem.value || !session.user) return false
  return session.user.role === 'ADMIN' || session.user.id === problem.value.authorId
})

const polling = ref(false)

function stopPolling() {
  if (pollTimer !== undefined) {
    window.clearTimeout(pollTimer)
    pollTimer = undefined
  }
  polling.value = false
}

async function pollSubmission(submissionId: number) {
  try {
    const data = await getSubmission(submissionId)
    currentSubmission.value = data
    if (
      (data.status === 'PENDING' || data.status === 'JUDGING') &&
      Date.now() - pollStartedAt < 60_000
    ) {
      polling.value = true
      pollTimer = window.setTimeout(() => pollSubmission(submissionId), 800)
    } else if (data.status === 'PENDING' || data.status === 'JUDGING') {
      polling.value = false
      submitNotice.value = '评测时间较长，请稍后到提交详情或“提交”列表查看。'
    } else {
      polling.value = false
      submitNotice.value = `评测完成：${data.statusText}`
    }
  } catch (e) {
    polling.value = false
    submitError.value = errorMessage(e)
  }
}

async function load() {
  loading.value = true
  error.value = ''
  errorCode.value = 0
  stopPolling()
  currentSubmission.value = null
  try {
    problem.value = await getProblem(problemId.value)
  } catch (e) {
    problem.value = null
    error.value = errorMessage(e)
    if (isApiError(e)) errorCode.value = e.code
  } finally {
    loading.value = false
  }

  listSubmissions({ problemId: problemId.value, page: 1, size: 10 })
    .then((data) => (recentSubmissions.value = data.items))
    .catch(() => {
      recentSubmissions.value = []
    })
}

async function doSubmit() {
  submitError.value = ''
  submitNotice.value = ''
  if (!session.user) {
    submitError.value = '请先登录后再提交'
    return
  }
  if (!submitLanguage.value) {
    submitError.value = '请选择语言'
    return
  }
  if (!submitCode.value.trim()) {
    submitError.value = '代码不能为空'
    return
  }

  submitting.value = true
  try {
    const created = await createSubmission({
      problemId: problemId.value,
      language: submitLanguage.value,
      code: submitCode.value,
    })
    currentSubmission.value = created
    submitNotice.value = `提交成功（id=${created.id}），正在轮询评测结果…`
    pollStartedAt = Date.now()
    stopPolling()
    polling.value = true
    pollTimer = window.setTimeout(() => pollSubmission(created.id), 800)
    listSubmissions({ problemId: problemId.value, page: 1, size: 10 })
      .then((data) => (recentSubmissions.value = data.items))
      .catch(() => undefined)
  } catch (e) {
    submitError.value = errorMessage(e)
  } finally {
    submitting.value = false
  }
}

async function removeProblem() {
  if (!problem.value) return
  const confirmed = window.confirm(
    `确认删除题目 #${problem.value.id}「${problem.value.title}」？会一并删除测试数据、样例、标签关联和提交记录，不可恢复。`,
  )
  if (!confirmed) return
  try {
    await deleteProblem(problem.value.id)
    router.push({ name: 'my-problems' })
  } catch (e) {
    error.value = errorMessage(e)
  }
}

onMounted(async () => {
  await load()
  try {
    languages.value = await getLanguages()
    if (languages.value.length) submitLanguage.value = languages.value[0]!.code
  } catch {
    languages.value = []
  }
})

watch(problemId, () => {
  submitCode.value = ''
  submitError.value = ''
  submitNotice.value = ''
  load()
})

onUnmounted(stopPolling)
</script>

<template>
  <div v-if="loading" class="card">加载中…</div>

  <div v-else-if="error && !problem" class="card">
    <h1>题目不可见</h1>
    <div v-if="errorCode === 3002" class="notice warn">
      题目未发布或已下架；这不是账号权限问题。
    </div>
    <p class="error">{{ error }}</p>
    <RouterLink to="/catalog">返回题库</RouterLink>
  </div>

  <template v-else-if="problem">
    <div class="page-title">
      <h1>#{{ problem.id }} {{ problem.title }}</h1>
      <div class="actions">
        <span :class="statusClass(problem.status)">
          {{ statusLabel(problem.status, PROBLEM_STATUSES) }}
        </span>
        <template v-if="canManage">
          <RouterLink :to="`/problems/${problem.id}/edit`">
            <button type="button" class="secondary">编辑 / 判题器 / 测试数据</button>
          </RouterLink>
          <button type="button" class="danger" @click="removeProblem">删除题目</button>
        </template>
      </div>
    </div>

    <div class="card">
      <div class="detail-grid">
        <div><strong>难度</strong>{{ statusLabel(problem.difficulty, DIFFICULTIES) }}</div>
        <div><strong>Rating</strong>{{ fmtRating(problem.rating) }}</div>
        <div><strong>Scope</strong>{{ problem.scopeName }}（{{ problem.scopeId }}）</div>
        <div><strong>时间限制</strong>{{ problem.timeLimitMs }} ms</div>
        <div><strong>内存限制</strong>{{ problem.memoryLimitMb }} MB</div>
        <div><strong>判题器</strong>{{ problem.hasChecker ? '自定义判题器' : '内置逐行比对' }}</div>
        <div>
          <strong>作者</strong>
          <RouterLink :to="`/users/${problem.authorId}`">{{ problem.authorNickname }}</RouterLink>
        </div>
        <div><strong>提交 / 通过</strong>{{ problem.submitCount }} / {{ problem.acceptedCount }}</div>
        <div><strong>当前用户已通过</strong>{{ problem.solved ? '是' : '否' }}</div>
        <div><strong>创建时间</strong>{{ fmtDate(problem.createdAt) }}</div>
        <div><strong>更新时间</strong>{{ fmtDate(problem.updatedAt) }}</div>
      </div>

      <p>
        <span v-for="tag in problem.tags" :key="tag.id" class="badge" style="margin-right: 6px">
          {{ tag.name }}
        </span>
        <span v-if="!problem.tags.length" class="hint">无标签</span>
      </p>

      <div class="section">
        <h2>题面</h2>
        <pre>{{ problem.description }}</pre>
      </div>

      <div class="split section">
        <div>
          <h3>输入格式</h3>
          <pre>{{ fmt(problem.inputFormat) }}</pre>
        </div>
        <div>
          <h3>输出格式</h3>
          <pre>{{ fmt(problem.outputFormat) }}</pre>
        </div>
      </div>

      <div class="section">
        <h3>提示</h3>
        <pre>{{ fmt(problem.hint) }}</pre>
      </div>

      <div class="section">
        <h2>样例（{{ problem.samples.length }}）</h2>
        <div v-for="sample in problem.samples" :key="sample.ordinal" class="card">
          <h3>样例 {{ sample.ordinal }}</h3>
          <div class="split">
            <div>
              <strong>输入</strong>
              <pre>{{ sample.input || '（空）' }}</pre>
            </div>
            <div>
              <strong>输出</strong>
              <pre>{{ sample.output || '（空）' }}</pre>
            </div>
          </div>
          <p v-if="sample.explanation" class="hint">解释：{{ sample.explanation }}</p>
        </div>
        <p v-if="!problem.samples.length" class="hint">没有样例</p>
      </div>
    </div>

    <div class="card">
      <h2>提交代码</h2>
      <div v-if="!session.user" class="notice">
        请先 <RouterLink :to="{ name: 'login', query: { redirect: route.fullPath } }">登录</RouterLink>
        后再提交。
      </div>
      <form v-else @submit.prevent="doSubmit">
        <div class="form-row">
          <label for="language">语言</label>
          <select id="language" v-model="submitLanguage">
            <option v-for="language in languages" :key="language.code" :value="language.code">
              {{ language.displayName }}（{{ language.code }}）
            </option>
          </select>
          <span v-if="!languages.length" class="hint">服务端未配置可用语言</span>
        </div>
        <div class="form-row">
          <label for="code">源代码</label>
          <textarea id="code" v-model="submitCode" rows="14" placeholder="在此粘贴源代码"></textarea>
        </div>
        <div class="actions">
          <button type="submit" :disabled="submitting">
            {{ submitting ? '提交中…' : '提交' }}
          </button>
          <span class="hint">重复点击会创建多条提交，按钮已做防重复。</span>
        </div>
      </form>

      <p v-if="submitError" class="error">{{ submitError }}</p>
      <p v-if="submitNotice" class="success">{{ submitNotice }}</p>

      <div v-if="currentSubmission" class="section">
        <h3>
          本次提交
          <span :class="statusClass(currentSubmission.status)">{{ currentSubmission.statusText }}</span>
          <RouterLink :to="`/submissions/${currentSubmission.id}`" style="margin-left: 8px">
            查看完整详情 #{{ currentSubmission.id }}
          </RouterLink>
        </h3>
        <p v-if="polling" class="hint">正在自动轮询评测结果（间隔 800ms，最多 60 秒）…</p>
        <div class="detail-grid">
          <div><strong>耗时</strong>{{ fmt(currentSubmission.timeUsedMs) }} ms</div>
          <div><strong>内存</strong>{{ fmt(currentSubmission.memoryUsedKb) }} KB</div>
          <div><strong>提交时间</strong>{{ fmtDate(currentSubmission.createdAt) }}</div>
          <div><strong>评测完成</strong>{{ fmtDate(currentSubmission.judgedAt) }}</div>
        </div>
        <pre v-if="currentSubmission.compileMessage">{{ currentSubmission.compileMessage }}</pre>
        <pre v-if="currentSubmission.judgeMessage">{{ currentSubmission.judgeMessage }}</pre>
        <div v-if="currentSubmission.cases.length" class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>测试点</th>
                <th>状态</th>
                <th>耗时</th>
                <th>内存</th>
                <th>信息</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in currentSubmission.cases" :key="item.ordinal">
                <td>{{ item.ordinal }}</td>
                <td><span :class="statusClass(item.status)">{{ item.statusText }}</span></td>
                <td>{{ fmt(item.timeUsedMs) }}</td>
                <td>{{ fmt(item.memoryUsedKb) }}</td>
                <td>{{ fmt(item.message) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <div class="card">
      <h2>最近提交</h2>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>ID</th>
              <th>提交者</th>
              <th>语言</th>
              <th>状态</th>
              <th>耗时</th>
              <th>内存</th>
              <th>时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in recentSubmissions" :key="item.id">
              <td><RouterLink :to="`/submissions/${item.id}`">{{ item.id }}</RouterLink></td>
              <td>
                <RouterLink :to="`/users/${item.userId}`">{{ item.userNickname }}</RouterLink>
              </td>
              <td>{{ item.language }}</td>
              <td><span :class="statusClass(item.status)">{{ item.statusText }}</span></td>
              <td>{{ fmt(item.timeUsedMs) }}</td>
              <td>{{ fmt(item.memoryUsedKb) }}</td>
              <td>{{ fmtDate(item.createdAt) }}</td>
            </tr>
            <tr v-if="!recentSubmissions.length">
              <td colspan="7" class="hint">暂无提交</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </template>
</template>
