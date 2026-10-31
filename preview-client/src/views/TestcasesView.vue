<script setup lang="ts">
import { useRequestScope } from '@/composables/useRequestScope'
const signal = useRequestScope()
import { onScopeDispose, ref } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '@/api/resources'
import { useResource } from '@/composables/useResource'
import { errorText, validateTestcases, parseTestcases } from '@/lib/contracts'
import type { Testcase } from '@/types/api'
import PageState from '@/components/PageState.vue'
import ConfirmButton from '@/components/ConfirmButton.vue'
const route = useRoute()
const id = String(route.params.id)
const {
  data,
  loading,
  error: loadError,
  reload,
} = useResource((signal) => api.testcases(id, signal))
const input = ref(''),
  output = ref(''),
  batch = ref(''),
  error = ref(''),
  notice = ref(''),
  busy = ref(false),
  detail = ref<Testcase | null>(null),
  detailLoading = ref(false)
let detailController: AbortController | undefined
onScopeDispose(() => detailController?.abort())
async function run(action: () => Promise<unknown>, message: string) {
  if (busy.value) return
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    await action()
    detailController?.abort()
    detail.value = null
    detailLoading.value = false
    notice.value = message
    await reload()
  } catch (cause) {
    error.value = errorText(cause)
  } finally {
    busy.value = false
  }
}
async function add() {
  await run(async () => {
    const [row] = validateTestcases([{ input: input.value, output: output.value }])
    await api.addTestcase(id, row!, signal)
    input.value = output.value = ''
  }, '测试点已添加')
}
async function replace() {
  await run(async () => {
    const value = parseTestcases(batch.value)
    await api.replaceTestcases(id, value, signal)
    batch.value = ''
  }, '测试数据已替换')
}
async function show(caseId: number) {
  detailController?.abort()
  detailController = new AbortController()
  const signal = detailController.signal
  detail.value = null
  detailLoading.value = true
  error.value = ''
  try {
    const value = await api.testcase(id, caseId, signal)
    if (!signal.aborted) detail.value = value
  } catch (cause) {
    if (!signal.aborted) error.value = errorText(cause)
  } finally {
    if (!signal.aborted) detailLoading.value = false
  }
}
</script>
<template>
  <div class="page-heading">
    <div>
      <div class="breadcrumb">
        <RouterLink :to="'/problems/' + id">题目 #{{ id }}</RouterLink
        ><span>/</span>
      </div>
      <h1>测试数据</h1>
    </div>
    <RouterLink :to="'/problems/' + id + '/edit'" class="button secondary">编辑题面</RouterLink>
  </div>
  <PageState :loading="loading" :error="loadError" @retry="reload" />
  <section v-if="!loading && !loadError && data" class="stack">
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <p v-if="notice" class="notice" role="status">{{ notice }}</p>
    <div class="panel table-panel">
      <div class="table-heading">
        <h2>
          隐藏测试点 <span class="subtle mono">{{ data.length }} / 100</span>
        </h2>
      </div>
      <div v-if="!data.length" class="state">暂无测试数据</div>
      <div v-else class="table-scroll">
        <table>
          <thead>
            <tr>
              <th>编号</th>
              <th>输入</th>
              <th>输出</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in data" :key="row.id">
              <td class="mono">#{{ row.ordinal }}</td>
              <td class="mono">{{ row.inputBytes }} B</td>
              <td class="mono">{{ row.outputBytes }} B</td>
              <td>
                <div class="actions">
                  <button class="button secondary small" :disabled="busy" @click="show(row.id)">
                    查看</button
                  ><ConfirmButton
                    title="删除测试点"
                    :message="'确认删除测试点 #' + row.ordinal + '？'"
                    danger
                    :disabled="busy"
                    @confirm="run(() => api.deleteTestcase(id, row.id, signal), '测试点已删除')"
                    >删除</ConfirmButton
                  >
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
    <div v-if="detailLoading" class="state" role="status">读取测试点…</div>
    <section v-if="detail" class="panel content-panel">
      <div class="section-title">
        <h2>测试点 #{{ detail.ordinal }}</h2>
        <button class="text-button" @click="detail = null">收起</button>
      </div>
      <div class="sample-grid">
        <div>
          <h3>输入</h3>
          <pre>{{ detail.input }}</pre>
        </div>
        <div>
          <h3>输出</h3>
          <pre>{{ detail.output }}</pre>
        </div>
      </div>
    </section>
    <div class="settings-grid">
      <form class="panel content-panel stack" @submit.prevent="add">
        <h2>追加测试点</h2>
        <p class="field-hint">输入、输出各最多 1 MiB；允许空文本</p>
        <label>输入<textarea v-model="input" class="mono" rows="6" :disabled="busy" /></label
        ><label>期望输出<textarea v-model="output" class="mono" rows="6" :disabled="busy" /></label
        ><button class="button fit" :disabled="busy || data.length >= 100">
          {{ busy ? '处理中…' : '添加测试点' }}
        </button>
      </form>
      <section class="panel content-panel stack">
        <h2>批量替换</h2>
        <label
          >JSON 数组<textarea
            v-model="batch"
            class="mono"
            rows="14"
            :disabled="busy"
            spellcheck="false"
            placeholder='[{"input":"1 2\n","output":"3\n"}]'
          /><span class="field-hint">最多 100 条；[] 清空全部测试数据</span></label
        ><ConfirmButton
          title="覆盖测试数据"
          message="将覆盖全部隐藏测试点，编号重新排列；空数组将清空数据。确认继续？"
          :disabled="busy || !batch.trim()"
          @confirm="replace"
          >替换全部</ConfirmButton
        ><ConfirmButton
          v-if="data.length"
          title="清空测试数据"
          message="清空后该题目无法提交评测。确认清空全部测试点？"
          danger
          :disabled="busy"
          @confirm="run(() => api.replaceTestcases(id, [], signal), '测试数据已清空')"
          >清空全部</ConfirmButton
        >
      </section>
    </div>
  </section>
</template>
