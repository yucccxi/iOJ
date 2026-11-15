<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { errorMessage } from '../api/http'
import { getTags } from '../api/meta'
import {
  addTestcase,
  createProblem,
  deleteProblem,
  deleteTestcase,
  getChecker,
  getProblem,
  getTestcase,
  listTestcases,
  replaceTestcases,
  saveChecker,
  updateProblem,
} from '../api/problems'
import {
  DIFFICULTIES,
  type Difficulty,
  type ProblemSavePayload,
  type TagMetaVO,
  type TestcaseDetailVO,
  type TestcasePayload,
  type TestcaseSummaryVO,
} from '../api/types'
import { session } from '../stores/session'
import { fmtBytes, fmtDate, parseInteger } from '../utils/format'

const route = useRoute()
const router = useRouter()

const problemId = computed(() => (route.params.id ? Number(route.params.id) : null))
const editing = computed(() => problemId.value !== null)
const localDifficulties = DIFFICULTIES.filter((item) => item.value !== 'UNKNOWN')

const tags = ref<TagMetaVO[]>([])
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const notice = ref('')
const canManage = ref(true)

const form = reactive({
  title: '',
  difficulty: 'EASY' as Difficulty,
  // input[type=number] 的 v-model 会把值转成 number，这里两种都允许。
  rating: '' as string | number,
  description: '',
  inputFormat: '',
  outputFormat: '',
  hint: '',
  timeLimitMs: '1000' as string | number,
  memoryLimitMb: '256' as string | number,
  tagIds: [] as number[],
  samples: [] as Array<{ input: string; output: string; explanation: string }>,
})

const checker = ref('')
const checkerLoading = ref(false)
const checkerSaving = ref(false)
const checkerError = ref('')
const checkerNotice = ref('')

const testcases = ref<TestcaseSummaryVO[]>([])
const testcaseLoading = ref(false)
const testcaseError = ref('')
const testcaseNotice = ref('')
const testcaseDetail = ref<TestcaseDetailVO | null>(null)
const addForm = reactive({ input: '', output: '' })
const replaceJson = ref('[]')

function resetForm() {
  form.title = ''
  form.difficulty = 'EASY'
  form.rating = ''
  form.description = ''
  form.inputFormat = ''
  form.outputFormat = ''
  form.hint = ''
  form.timeLimitMs = '1000'
  form.memoryLimitMb = '256'
  form.tagIds = []
  form.samples = []
  checker.value = ''
  testcases.value = []
  testcaseDetail.value = null
  addForm.input = ''
  addForm.output = ''
  replaceJson.value = '[]'
  canManage.value = true
}

async function loadProblem() {
  if (problemId.value === null) {
    resetForm()
    return
  }

  loading.value = true
  error.value = ''
  notice.value = ''
  checkerError.value = ''
  checkerNotice.value = ''
  testcaseError.value = ''
  testcaseNotice.value = ''
  testcaseDetail.value = null

  try {
    const problem = await getProblem(problemId.value)
    form.title = problem.title
    form.difficulty = problem.difficulty
    form.rating = problem.rating >= 0 ? String(problem.rating) : ''
    form.description = problem.description
    form.inputFormat = problem.inputFormat ?? ''
    form.outputFormat = problem.outputFormat ?? ''
    form.hint = problem.hint ?? ''
    form.timeLimitMs = String(problem.timeLimitMs)
    form.memoryLimitMb = String(problem.memoryLimitMb)
    form.tagIds = problem.tags.map((tag) => tag.id)
    form.samples = problem.samples.map((sample) => ({
      input: sample.input,
      output: sample.output,
      explanation: sample.explanation ?? '',
    }))
    canManage.value =
      session.user?.role === 'ADMIN' || session.user?.id === problem.authorId

    if (canManage.value) {
      await Promise.allSettled([loadChecker(), loadTestcases()])
    }
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    loading.value = false
  }
}

function buildPayload(): ProblemSavePayload {
  const payload: ProblemSavePayload = {
    title: form.title.trim(),
    difficulty: form.difficulty,
    rating: parseInteger(form.rating) ?? null,
    description: form.description,
    inputFormat: form.inputFormat.trim() === '' ? null : form.inputFormat,
    outputFormat: form.outputFormat.trim() === '' ? null : form.outputFormat,
    hint: form.hint.trim() === '' ? null : form.hint,
    timeLimitMs: Number(form.timeLimitMs),
    memoryLimitMb: Number(form.memoryLimitMb),
    tagIds: [...form.tagIds],
    samples: form.samples.map((sample) => ({
      input: sample.input,
      output: sample.output,
      explanation: sample.explanation.trim() === '' ? null : sample.explanation,
    })),
  }
  return payload
}

async function saveProblem() {
  error.value = ''
  notice.value = ''

  if (!form.title.trim()) {
    error.value = '标题不能为空'
    return
  }
  if (!form.description.trim()) {
    error.value = '题面不能为空'
    return
  }
  if (form.tagIds.length > 10) {
    error.value = '标签最多 10 个'
    return
  }
  if (form.samples.length > 10) {
    error.value = '样例最多 10 个'
    return
  }

  saving.value = true
  try {
    const payload = buildPayload()
    if (editing.value) {
      const result = await updateProblem(problemId.value!, payload)
      await loadProblem()
      notice.value = `已保存（PUT 整体替换）。当前状态：${result.status}，更新时间：${result.updatedAt}`
    } else {
      const result = await createProblem(payload)
      notice.value = `创建成功，状态：${result.status}`
      router.push({ name: 'problem-detail', params: { id: result.id } })
    }
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    saving.value = false
  }
}

async function removeProblem() {
  if (!problemId.value) return
  const confirmed = window.confirm(`确认删除题目 #${problemId.value}？会级联删除测试数据与提交记录，不可恢复。`)
  if (!confirmed) return
  try {
    await deleteProblem(problemId.value)
    router.push({ name: 'my-problems' })
  } catch (e) {
    error.value = errorMessage(e)
  }
}

async function loadChecker() {
  if (!problemId.value) return
  checkerLoading.value = true
  checkerError.value = ''
  try {
    const result = await getChecker(problemId.value)
    checker.value = result.code
  } catch (e) {
    checkerError.value = errorMessage(e)
  } finally {
    checkerLoading.value = false
  }
}

async function saveCheckerCode() {
  if (!problemId.value) return
  checkerSaving.value = true
  checkerError.value = ''
  checkerNotice.value = ''
  try {
    await saveChecker(problemId.value, checker.value)
    checkerNotice.value =
      checker.value.trim() === ''
        ? '已清空判题器，恢复内置逐行比对。'
        : '判题器已保存；只影响之后的评测，不会改变已完成的提交。'
  } catch (e) {
    checkerError.value = errorMessage(e)
  } finally {
    checkerSaving.value = false
  }
}

async function loadTestcases() {
  if (!problemId.value) return
  testcaseLoading.value = true
  testcaseError.value = ''
  try {
    testcases.value = await listTestcases(problemId.value)
  } catch (e) {
    testcaseError.value = errorMessage(e)
  } finally {
    testcaseLoading.value = false
  }
}

async function viewTestcase(testcaseId: number) {
  if (!problemId.value) return
  testcaseError.value = ''
  try {
    testcaseDetail.value = await getTestcase(problemId.value, testcaseId)
  } catch (e) {
    testcaseError.value = errorMessage(e)
  }
}

async function addOneTestcase() {
  if (!problemId.value) return
  testcaseError.value = ''
  testcaseNotice.value = ''
  try {
    const created = await addTestcase(problemId.value, {
      input: addForm.input,
      output: addForm.output,
    })
    testcaseNotice.value = `已追加测试点 #${created.ordinal}（id=${created.id}）`
    addForm.input = ''
    addForm.output = ''
    await loadTestcases()
  } catch (e) {
    testcaseError.value = errorMessage(e)
  }
}

async function removeTestcase(testcaseId: number) {
  if (!problemId.value) return
  if (!window.confirm(`确认删除测试数据 #${testcaseId}？其他编号不会自动重排。`)) return
  testcaseError.value = ''
  testcaseNotice.value = ''
  try {
    await deleteTestcase(problemId.value, testcaseId)
    if (testcaseDetail.value?.id === testcaseId) testcaseDetail.value = null
    testcaseNotice.value = '已删除'
    await loadTestcases()
  } catch (e) {
    testcaseError.value = errorMessage(e)
  }
}

async function fillReplaceJson() {
  if (!problemId.value) return
  testcaseError.value = ''
  try {
    const payload: TestcasePayload[] = []
    for (const item of testcases.value) {
      const detail = await getTestcase(problemId.value, item.id)
      payload.push({ input: detail.input, output: detail.output })
    }
    replaceJson.value = JSON.stringify(payload, null, 2)
    testcaseNotice.value = '已用当前测试数据填充 JSON，可直接编辑后覆盖。'
  } catch (e) {
    testcaseError.value = errorMessage(e)
  }
}

function parseTestcaseJson(text: string): TestcasePayload[] {
  const parsed: unknown = JSON.parse(text)
  if (!Array.isArray(parsed)) throw new Error('顶层必须是数组')
  return parsed.map((item, index) => {
    if (typeof item !== 'object' || item === null) {
      throw new Error(`第 ${index + 1} 项不是对象`)
    }
    const record = item as Record<string, unknown>
    if (typeof record.input !== 'string' || typeof record.output !== 'string') {
      throw new Error(`第 ${index + 1} 项必须包含字符串 input 和 output`)
    }
    return { input: record.input, output: record.output }
  })
}

async function replaceAllTestcases() {
  if (!problemId.value) return
  testcaseError.value = ''
  testcaseNotice.value = ''
  try {
    const payload = parseTestcaseJson(replaceJson.value)
    const result = await replaceTestcases(problemId.value, payload)
    testcaseNotice.value = `已覆盖全部测试数据，共 ${result.length} 条。`
    await loadTestcases()
  } catch (e) {
    testcaseError.value = errorMessage(e)
  }
}

onMounted(async () => {
  try {
    tags.value = await getTags()
  } catch {
    tags.value = []
  }
  await loadProblem()
})

watch(problemId, () => {
  loadProblem()
})
</script>

<template>
  <div class="page-title">
    <h1>{{ editing ? `编辑题目 #${problemId}` : '上传新题' }}</h1>
    <div class="actions">
      <RouterLink v-if="editing" :to="`/problems/${problemId}`">查看题目</RouterLink>
      <RouterLink to="/problems/mine">我的题目</RouterLink>
    </div>
  </div>

  <div v-if="loading" class="card">加载中…</div>

  <template v-else>
    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="notice" class="success">{{ notice }}</p>

    <div v-if="editing && !canManage" class="notice warn">
      你不是这道题的作者或管理员，提交修改会被后端拒绝（403/1003）。
    </div>

    <div class="card">
      <h2>基本信息</h2>
      <div class="form-grid">
        <div class="form-row" style="grid-column: 1 / -1">
          <label for="title">标题（必填）</label>
          <input id="title" v-model="form.title" type="text" maxlength="128" />
        </div>
        <div class="form-row">
          <label for="difficulty">难度（必填）</label>
          <select id="difficulty" v-model="form.difficulty">
            <option v-for="item in localDifficulties" :key="item.value" :value="item.value">
              {{ item.label }}（{{ item.value }}）
            </option>
          </select>
        </div>
        <div class="form-row">
          <label for="rating">Rating（可选，0-5000）</label>
          <input id="rating" v-model="form.rating" type="number" min="0" max="5000" />
        </div>
        <div class="form-row">
          <label for="timeLimit">时间限制（100-30000 ms）</label>
          <input id="timeLimit" v-model="form.timeLimitMs" type="number" min="100" max="30000" />
        </div>
        <div class="form-row">
          <label for="memoryLimit">内存限制（16-1024 MB）</label>
          <input id="memoryLimit" v-model="form.memoryLimitMb" type="number" min="16" max="1024" />
        </div>
      </div>

      <div class="form-row">
        <label for="description">题面（必填，支持 Markdown 文本）</label>
        <textarea id="description" v-model="form.description" rows="12"></textarea>
      </div>
      <div class="form-row">
        <label for="inputFormat">输入格式（可选）</label>
        <textarea id="inputFormat" v-model="form.inputFormat" rows="3"></textarea>
      </div>
      <div class="form-row">
        <label for="outputFormat">输出格式（可选）</label>
        <textarea id="outputFormat" v-model="form.outputFormat" rows="3"></textarea>
      </div>
      <div class="form-row">
        <label for="hint">提示（可选）</label>
        <textarea id="hint" v-model="form.hint" rows="3"></textarea>
      </div>

      <div class="form-row">
        <label>标签（最多 10 个，只能选已有标签）</label>
        <div class="checkbox-list">
          <label v-for="tag in tags" :key="tag.id">
            <input v-model="form.tagIds" type="checkbox" :value="tag.id" />
            {{ tag.name }}
          </label>
          <span v-if="!tags.length" class="hint">暂无标签，请联系管理员创建</span>
        </div>
      </div>

      <div class="section">
        <div class="page-title">
          <h3>样例（{{ form.samples.length }} / 10）</h3>
          <button
            type="button"
            class="secondary"
            :disabled="form.samples.length >= 10"
            @click="form.samples.push({ input: '', output: '', explanation: '' })"
          >
            添加样例
          </button>
        </div>
        <div v-for="(sample, index) in form.samples" :key="index" class="card">
          <div class="page-title">
            <h3>样例 {{ index + 1 }}</h3>
            <button type="button" class="danger" @click="form.samples.splice(index, 1)">删除</button>
          </div>
          <div class="split">
            <div class="form-row">
              <label>输入（可为空字符串）</label>
              <textarea v-model="sample.input" rows="4"></textarea>
            </div>
            <div class="form-row">
              <label>输出（可为空字符串）</label>
              <textarea v-model="sample.output" rows="4"></textarea>
            </div>
          </div>
          <div class="form-row">
            <label>解释（可选）</label>
            <textarea v-model="sample.explanation" rows="2"></textarea>
          </div>
        </div>
      </div>

      <div class="notice warn">
        PUT /problems/{id} 是整体替换：未提交的 rating、样例、标签会被清空，时限/内存恢复默认值。
        本表单会带上全部字段。
      </div>

      <div class="actions">
        <button type="button" :disabled="saving" @click="saveProblem">
          {{ saving ? '保存中…' : editing ? '保存（整体替换）' : '创建题目' }}
        </button>
        <button
          type="button"
          class="secondary"
          @click="router.push(editing ? `/problems/${problemId}` : '/problems/mine')"
        >
          取消
        </button>
        <button v-if="editing" type="button" class="danger" @click="removeProblem">
          删除题目
        </button>
      </div>
    </div>

    <template v-if="editing && canManage">
      <div class="card">
        <div class="page-title">
          <h2>自定义判题器（Special Judge）</h2>
          <button type="button" class="secondary" :disabled="checkerLoading" @click="loadChecker">
            {{ checkerLoading ? '加载中…' : '重新加载源码' }}
          </button>
        </div>
        <p class="hint">
          C++ 源码，≤65536 字符。留空或纯空白表示清空判题器，恢复内置逐行比对。
          保存判题器不会影响题面、标签、样例和发布状态。
        </p>
        <div class="form-row">
          <label for="checker">判题器源码</label>
          <textarea id="checker" v-model="checker" rows="14"></textarea>
        </div>
        <p v-if="checkerError" class="error">{{ checkerError }}</p>
        <p v-if="checkerNotice" class="success">{{ checkerNotice }}</p>
        <div class="actions">
          <button type="button" :disabled="checkerSaving" @click="saveCheckerCode">
            {{ checkerSaving ? '保存中…' : '保存判题器' }}
          </button>
          <button type="button" class="secondary" @click="checker = ''">清空编辑框</button>
        </div>
      </div>

      <div class="card">
        <div class="page-title">
          <h2>隐藏测试数据（{{ testcases.length }} 条）</h2>
          <div class="actions">
            <button type="button" class="secondary" :disabled="testcaseLoading" @click="loadTestcases">
              {{ testcaseLoading ? '加载中…' : '刷新列表' }}
            </button>
          </div>
        </div>
        <p class="hint">
          测试数据与题面样例是两套内容。单题默认最多 100 条；每条 input/output 的 UTF-8 字节数默认最多 4 MiB。
          删除中间数据后编号不会重排。
        </p>
        <p v-if="testcaseError" class="error">{{ testcaseError }}</p>
        <p v-if="testcaseNotice" class="success">{{ testcaseNotice }}</p>

        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>ID</th>
                <th>编号</th>
                <th>输入字节</th>
                <th>输出字节</th>
                <th>创建时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in testcases" :key="item.id">
                <td>{{ item.id }}</td>
                <td>{{ item.ordinal }}</td>
                <td>{{ fmtBytes(item.inputBytes) }}</td>
                <td>{{ fmtBytes(item.outputBytes) }}</td>
                <td>{{ fmtDate(item.createdAt) }}</td>
                <td class="actions">
                  <button type="button" class="secondary" @click="viewTestcase(item.id)">查看</button>
                  <button type="button" class="danger" @click="removeTestcase(item.id)">删除</button>
                </td>
              </tr>
              <tr v-if="!testcases.length">
                <td colspan="6" class="hint">还没有测试数据；没有测试数据的题目无法提交或上架。</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div v-if="testcaseDetail" class="card section">
          <div class="page-title">
            <h3>测试数据 #{{ testcaseDetail.id }}（编号 {{ testcaseDetail.ordinal }}）</h3>
            <button type="button" class="secondary" @click="testcaseDetail = null">关闭</button>
          </div>
          <div class="split">
            <div>
              <strong>输入</strong>
              <pre>{{ testcaseDetail.input }}</pre>
            </div>
            <div>
              <strong>输出</strong>
              <pre>{{ testcaseDetail.output }}</pre>
            </div>
          </div>
        </div>

        <div class="section">
          <h3>追加一条（POST）</h3>
          <div class="split">
            <div class="form-row">
              <label for="addInput">输入</label>
              <textarea id="addInput" v-model="addForm.input" rows="5"></textarea>
            </div>
            <div class="form-row">
              <label for="addOutput">输出</label>
              <textarea id="addOutput" v-model="addForm.output" rows="5"></textarea>
            </div>
          </div>
          <button type="button" @click="addOneTestcase">追加测试数据</button>
        </div>

        <div class="section">
          <div class="page-title">
            <h3>覆盖全部（PUT，数组顺序即编号顺序）</h3>
            <button type="button" class="secondary" @click="fillReplaceJson">
              用当前测试数据填充 JSON
            </button>
          </div>
          <div class="form-row">
            <label for="replaceJson">JSON 数组，例如 [{"input":"1 2\n","output":"3\n"}]</label>
            <textarea id="replaceJson" v-model="replaceJson" rows="12"></textarea>
          </div>
          <p class="hint">传 [] 表示清空；清空后题目无法提交或上架（3003）。</p>
          <button type="button" class="danger" @click="replaceAllTestcases">覆盖全部测试数据</button>
        </div>
      </div>
    </template>

    <div v-else-if="editing" class="card hint">
      判题器和测试数据操作仅对题目作者与管理员开放，因此未加载。
    </div>

    <div v-if="editing" class="card">
      <h2>只读信息</h2>
      <p class="hint">
        题目状态不会通过 PUT /problems/{id} 改变，上架/下架请使用管理后台的审核接口。
        判题器使用独立表单保存，不会被题目整体更新覆盖。
      </p>
    </div>
  </template>
</template>
