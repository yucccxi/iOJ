<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { errorMessage } from '../api/http'
import { getSubmission } from '../api/submissions'
import { SUBMISSION_STATUSES, type SubmissionDetailVO } from '../api/types'
import { fmt, fmtDate, statusClass, statusLabel } from '../utils/format'

const route = useRoute()
const submissionId = computed(() => Number(route.params.id))

const submission = ref<SubmissionDetailVO | null>(null)
const loading = ref(false)
const error = ref('')
const pollNotice = ref('')
const polling = ref(false)

let pollTimer: number | undefined
let pollStartedAt = 0

function isPending(data: SubmissionDetailVO | null): boolean {
  return data?.status === 'PENDING' || data?.status === 'JUDGING'
}

function stopPolling() {
  if (pollTimer !== undefined) {
    window.clearTimeout(pollTimer)
    pollTimer = undefined
  }
  polling.value = false
}

async function load(auto: boolean) {
  if (!auto) {
    stopPolling()
    pollNotice.value = ''
    loading.value = true
    error.value = ''
  }

  try {
    const data = await getSubmission(submissionId.value)
    submission.value = data
    if (auto) {
      if (isPending(data)) {
        if (Date.now() - pollStartedAt < 60_000) {
          polling.value = true
          pollTimer = window.setTimeout(() => load(true), 800)
        } else {
          polling.value = false
          pollNotice.value = '评测时间较长，已停止自动刷新，请稍后手动刷新。'
        }
      } else {
        polling.value = false
        pollNotice.value = `评测完成：${data.statusText}`
      }
    }
  } catch (e) {
    error.value = errorMessage(e)
    polling.value = false
  } finally {
    if (!auto) loading.value = false
  }
}

async function start() {
  await load(false)
  if (isPending(submission.value)) {
    pollStartedAt = Date.now()
    polling.value = true
    pollTimer = window.setTimeout(() => load(true), 800)
  }
}

async function manualRefresh() {
  await start()
}

onMounted(start)

watch(submissionId, () => {
  submission.value = null
  start()
})

onUnmounted(stopPolling)
</script>

<template>
  <div class="page-title">
    <h1>提交 #{{ submissionId }}</h1>
    <div class="actions">
      <button type="button" class="secondary" :disabled="loading" @click="manualRefresh">
        {{ loading ? '刷新中…' : '刷新' }}
      </button>
      <RouterLink to="/submissions">返回列表</RouterLink>
    </div>
  </div>

  <div v-if="loading && !submission" class="card">加载中…</div>
  <div v-else-if="error && !submission" class="card">
    <p class="error">{{ error }}</p>
    <RouterLink to="/submissions">返回提交列表</RouterLink>
  </div>

  <template v-else-if="submission">
    <div v-if="error" class="notice danger">{{ error }}</div>

    <div class="card">
      <div class="detail-grid">
        <div>
          <strong>题目</strong>
          <RouterLink :to="`/problems/${submission.problemId}`">{{ submission.problemTitle }}</RouterLink>
        </div>
        <div>
          <strong>提交者</strong>
          <RouterLink :to="`/users/${submission.userId}`">{{ submission.userNickname }}</RouterLink>
        </div>
        <div><strong>语言</strong>{{ submission.language }}</div>
        <div>
          <strong>状态</strong>
          <span :class="statusClass(submission.status)">
            {{ statusLabel(submission.status, SUBMISSION_STATUSES) }}
          </span>
        </div>
        <div><strong>耗时</strong>{{ fmt(submission.timeUsedMs) }} ms</div>
        <div><strong>内存</strong>{{ fmt(submission.memoryUsedKb) }} KB</div>
        <div><strong>提交时间</strong>{{ fmtDate(submission.createdAt) }}</div>
        <div><strong>评测完成</strong>{{ fmtDate(submission.judgedAt) }}</div>
      </div>

      <p v-if="polling" class="hint">评测中，正在自动轮询（间隔 800ms，最多 60 秒）…</p>
      <p v-if="pollNotice" class="success">{{ pollNotice }}</p>
      <div v-if="submission.status === 'CHECKER_ERROR' || submission.status === 'SYSTEM_ERROR'" class="notice warn">
        这是评测侧问题，不是代码错误。请稍后重试或联系出题者。
      </div>
    </div>

    <div class="card">
      <h2>源代码</h2>
      <div v-if="submission.code === null" class="notice">
        无权限查看源代码。只有提交者本人、题目作者和管理员可以看到代码、编译信息和逐点结果。
      </div>
      <pre v-else>{{ submission.code }}</pre>
    </div>

    <div v-if="submission.compileMessage" class="card">
      <h2>编译信息</h2>
      <pre>{{ submission.compileMessage }}</pre>
    </div>

    <div v-if="submission.judgeMessage" class="card">
      <h2>判题信息</h2>
      <pre>{{ submission.judgeMessage }}</pre>
    </div>

    <div class="card">
      <h2>逐测试点结果（{{ submission.cases.length }}）</h2>
      <p v-if="!submission.cases.length" class="hint">
        没有逐点结果：可能无查看权限、编译错误、系统错误或评测尚未产生结果。
      </p>
      <div v-else class="table-wrap">
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
            <tr v-for="item in submission.cases" :key="item.ordinal">
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
  </template>
</template>
