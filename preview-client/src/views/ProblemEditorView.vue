<script setup lang="ts">
import { useRequestScope } from '@/composables/useRequestScope'
const signal = useRequestScope()
import { computed, reactive, ref } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { api } from '@/api/resources'
import { useAuthStore } from '@/stores/auth'
import { useResource } from '@/composables/useResource'
import { errorText, problemInput } from '@/lib/contracts'
import type { ProblemInput } from '@/types/api'
import PageState from '@/components/PageState.vue'
import MarkdownView from '@/components/MarkdownView.vue'
import ConfirmButton from '@/components/ConfirmButton.vue'
const route = useRoute(),
  router = useRouter(),
  auth = useAuthStore()
const id = route.params.id ? String(route.params.id) : undefined
const form = reactive<ProblemInput>({
  title: '',
  difficulty: 'EASY',
  description: '',
  inputFormat: '',
  outputFormat: '',
  hint: '',
  source: '',
  timeLimitMs: 1000,
  memoryLimitMb: 256,
  tags: [],
  samples: [],
})
const tagText = ref(''),
  busy = ref(false),
  error = ref(''),
  preview = ref(false)
const baseline = ref(JSON.stringify({ form, tags: '' }))
const saved = ref(false)
const {
  data,
  loading,
  error: loadError,
  reload,
} = useResource(async (signal) => {
  if (!id) return null
  const problem = await api.problem(id, signal)
  if (signal.aborted) return null
  Object.assign(form, problemInput({ ...problem, tags: problem.tags.map((t) => t.name) }))
  tagText.value = form.tags.join(', ')
  baseline.value = JSON.stringify({ form, tags: tagText.value })
  return problem
})
const allowed = computed(
  () => !id || (!!data.value && (auth.isAdmin || data.value.authorId === auth.user?.id)),
)
const dirty = computed(() => JSON.stringify({ form, tags: tagText.value }) !== baseline.value)
onBeforeRouteLeave(
  () => saved.value || !dirty.value || window.confirm('尚有未保存的修改，确认离开？'),
)
async function save() {
  if (busy.value || !allowed.value) return
  const tags = [...new Set(tagText.value.split(/[,，\s]+/).filter(Boolean))]
  if (tags.length > 10 || tags.some((tag) => !/^[\p{L}\p{N}_+#.-]{1,32}$/u.test(tag))) {
    error.value = '标签最多 10 个，每个 1–32 位字母、数字或 _+#.-'
    return
  }
  busy.value = true
  error.value = ''
  try {
    const result = await api.saveProblem(id, problemInput({ ...form, tags }), signal)
    saved.value = true
    await router.push('/problems/' + result.id + (id ? '' : '/testcases'))
  } catch (cause) {
    error.value = errorText(cause)
  } finally {
    busy.value = false
  }
}
async function remove() {
  if (!id || busy.value) return
  busy.value = true
  error.value = ''
  try {
    await api.deleteProblem(Number(id), signal)
    saved.value = true
    await router.push('/problems/mine')
  } catch (cause) {
    error.value = errorText(cause)
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <div class="page-heading">
    <h1>{{ id ? '编辑题目 #' + id : '新建题目' }}</h1>
    <div class="actions">
      <RouterLink v-if="id" :to="'/problems/' + id + '/testcases'" class="button secondary"
        >测试数据</RouterLink
      ><button class="button secondary" @click="preview = !preview">
        {{ preview ? '继续编辑' : '预览题面' }}
      </button>
    </div>
  </div>
  <PageState :loading="loading" :error="loadError" @retry="reload" />
  <p v-if="!loading && !loadError && !allowed" class="error" role="alert">
    只有作者或管理员可以编辑该题目
  </p>
  <form v-if="!loading && !loadError && allowed" class="stack editor-form" @submit.prevent="save">
    <section class="panel content-panel stack">
      <div class="form-row">
        <label class="grow"
          >题目标题<input v-model="form.title" required maxlength="128" :disabled="busy" /></label
        ><label
          >难度<select v-model="form.difficulty" :disabled="busy">
            <option value="EASY">简单</option>
            <option value="MEDIUM">中等</option>
            <option value="HARD">困难</option>
          </select></label
        >
      </div>
      <div class="form-row">
        <label
          >时间限制（ms）<input
            v-model.number="form.timeLimitMs"
            type="number"
            required
            min="100"
            max="30000"
            :disabled="busy" /></label
        ><label
          >内存限制（MB）<input
            v-model.number="form.memoryLimitMb"
            type="number"
            required
            min="16"
            max="1024"
            :disabled="busy" /></label
        ><label class="grow"
          >标签<input v-model="tagText" :disabled="busy" placeholder="以逗号分隔" /><span
            class="field-hint"
            >最多 10 个</span
          ></label
        >
      </div>
      <label
        >来源<span class="optional">选填</span
        ><input v-model="form.source" maxlength="128" :disabled="busy"
      /></label>
    </section>
    <section class="panel content-panel stack">
      <h2>题面</h2>
      <template v-if="preview"
        ><h3>题目描述</h3>
        <MarkdownView :text="form.description" />
        <h3 v-if="form.inputFormat">输入格式</h3>
        <MarkdownView :text="form.inputFormat" />
        <h3 v-if="form.outputFormat">输出格式</h3>
        <MarkdownView :text="form.outputFormat" />
        <h3 v-if="form.hint">提示</h3>
        <MarkdownView :text="form.hint"
      /></template>
      <template v-else
        ><label
          >题目描述<span class="optional">Markdown</span
          ><textarea
            v-model="form.description"
            required
            maxlength="200000"
            rows="12"
            :disabled="busy"
          />
        </label>
        <div class="form-row align-start">
          <label class="grow"
            >输入格式<textarea
              v-model="form.inputFormat"
              maxlength="20000"
              rows="4"
              :disabled="busy"
            /></label
          ><label class="grow"
            >输出格式<textarea
              v-model="form.outputFormat"
              maxlength="20000"
              rows="4"
              :disabled="busy"
            />
          </label>
        </div>
        <label
          >提示<span class="optional">选填</span
          ><textarea v-model="form.hint" maxlength="20000" rows="3" :disabled="busy" /></label
      ></template>
    </section>
    <section class="panel content-panel">
      <div class="section-title">
        <h2>展示样例</h2>
        <button
          type="button"
          class="button secondary small"
          :disabled="busy || form.samples.length >= 10"
          @click="form.samples.push({ input: '', output: '', explanation: '' })"
        >
          添加样例
        </button>
      </div>
      <p v-if="!form.samples.length" class="subtle">暂无样例</p>
      <div v-for="(sample, index) in form.samples" :key="index" class="sample-edit">
        <div class="section-title">
          <h3>样例 {{ index + 1 }}</h3>
          <button
            type="button"
            class="text-button danger"
            :disabled="busy"
            @click="form.samples.splice(index, 1)"
          >
            移除
          </button>
        </div>
        <div class="form-row align-start">
          <label class="grow"
            >输入<textarea
              v-model="sample.input"
              class="mono"
              maxlength="10240"
              rows="4"
              :disabled="busy"
            /></label
          ><label class="grow"
            >输出<textarea
              v-model="sample.output"
              class="mono"
              maxlength="10240"
              rows="4"
              :disabled="busy"
            />
          </label>
        </div>
        <label
          >解释<span class="optional">选填</span
          ><textarea v-model="sample.explanation" rows="2" :disabled="busy" />
        </label>
      </div>
    </section>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <div class="actions spread">
      <ConfirmButton
        v-if="id"
        title="删除题目"
        message="将永久删除该题目、隐藏测试数据和全部提交记录。确认删除？"
        danger
        :disabled="busy"
        @confirm="remove"
        >删除题目</ConfirmButton
      ><button
        type="submit"
        class="button"
        :disabled="busy || !form.title.trim() || !form.description.trim()"
      >
        {{ busy ? '保存中…' : id ? '保存修改' : '创建并添加测试数据' }}
      </button>
    </div>
  </form>
</template>
