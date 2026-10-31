<script setup lang="ts">
import { useRequestScope } from '@/composables/useRequestScope'
const signal = useRequestScope()
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '@/api/resources'
import { useAuthStore } from '@/stores/auth'
import { useCodeDraftsStore } from '@/stores/codeDrafts'
import { useResource } from '@/composables/useResource'
import { errorText } from '@/lib/contracts'
import type { Language } from '@/types/api'
import PageState from '@/components/PageState.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import MarkdownView from '@/components/MarkdownView.vue'
import SubmissionTable from '@/components/SubmissionTable.vue'
const route = useRoute(),
  router = useRouter(),
  auth = useAuthStore()
const id = String(route.params.id)
const { data: problem, loading, error, reload } = useResource((signal) => api.problem(id, signal))
const languages = useResource((signal) => api.languages(signal))
const recent = useResource((signal) => api.submissions({ problemId: id, page: 1, size: 5 }, signal))
const drafts = useCodeDraftsStore()
const draft = drafts.read(id)
const language = ref<Language | ''>(draft.language),
  code = ref(draft.code),
  busy = ref(false),
  submitError = ref('')
watch([code, language], ([code, language]) => drafts.save(id, { code, language }), {
  flush: 'sync',
})
const manageable = computed(
  () => problem.value && (auth.isAdmin || auth.user?.id === problem.value.authorId),
)
async function submit() {
  if (!auth.user) {
    await router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  if (busy.value || !language.value || !code.value.trim()) return
  if (code.value.length > 65536) {
    submitError.value = '代码不能超过 65536 字符'
    return
  }
  busy.value = true
  submitError.value = ''
  try {
    const result = await api.submit(
      { problemId: Number(id), language: language.value, code: code.value },
      signal,
    )
    await router.push('/submissions/' + result.id)
  } catch (cause) {
    submitError.value = errorText(cause)
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <PageState :loading="loading" :error="error" @retry="reload" />
  <section v-if="problem && !loading && !error">
    <div class="page-heading">
      <div>
        <div class="breadcrumb">
          <RouterLink to="/problems">题库</RouterLink><span class="mono">/ #{{ problem.id }}</span>
        </div>
        <h1>{{ problem.title }}</h1>
      </div>
      <div class="actions" v-if="manageable">
        <RouterLink :to="'/problems/' + id + '/edit'" class="button secondary">编辑</RouterLink
        ><RouterLink :to="'/problems/' + id + '/testcases'" class="button secondary"
          >测试数据</RouterLink
        >
      </div>
    </div>
    <div class="problem-meta">
      <StatusBadge :value="problem.difficulty" /><StatusBadge
        v-if="problem.status !== 'PUBLISHED'"
        :value="problem.status"
      /><span v-if="auth.user && problem.solved" class="solved">已通过</span
      ><span class="mono">{{ problem.timeLimitMs }} ms</span
      ><span class="mono">{{ problem.memoryLimitMb }} MB</span
      ><span>通过 {{ problem.acceptedCount }} / {{ problem.submitCount }}</span
      ><RouterLink :to="'/users/' + problem.authorId">{{ problem.authorNickname }}</RouterLink>
    </div>
    <div class="workspace">
      <article class="panel statement">
        <div class="section-title">
          <h2>题目描述</h2>
          <div>
            <span class="tag" v-for="tag in problem.tags" :key="tag.id">{{ tag.name }}</span>
          </div>
        </div>
        <MarkdownView :text="problem.description" />
        <template v-if="problem.inputFormat"
          ><h2>输入格式</h2>
          <MarkdownView :text="problem.inputFormat"
        /></template>
        <template v-if="problem.outputFormat"
          ><h2>输出格式</h2>
          <MarkdownView :text="problem.outputFormat"
        /></template>
        <section v-for="sample in problem.samples" :key="sample.ordinal" class="sample">
          <h2>样例 {{ sample.ordinal }}</h2>
          <div class="sample-grid">
            <div>
              <h3>输入</h3>
              <pre>{{ sample.input }}</pre>
            </div>
            <div>
              <h3>输出</h3>
              <pre>{{ sample.output }}</pre>
            </div>
          </div>
          <MarkdownView v-if="sample.explanation" :text="sample.explanation" />
        </section>
        <template v-if="problem.hint"
          ><h2>提示</h2>
          <MarkdownView :text="problem.hint"
        /></template>
        <p v-if="problem.source" class="subtle">来源：{{ problem.source }}</p>
      </article>
      <aside class="panel code-panel">
        <form @submit.prevent="submit">
          <div class="editor-toolbar">
            <h2>提交代码</h2>
            <label
              ><span class="sr-only">语言</span
              ><select v-model="language" required :disabled="busy || languages.loading.value">
                <option value="" disabled>选择语言</option>
                <option
                  v-for="item in languages.data.value || []"
                  :key="item.code"
                  :value="item.code"
                >
                  {{ item.displayName }}
                </option>
              </select></label
            >
          </div>
          <div v-if="languages.error.value" class="inline-alert error" role="alert">
            {{ languages.error.value }}
            <button type="button" class="text-button" @click="languages.reload">重试</button>
          </div>
          <label class="code-label"
            ><span class="sr-only">源代码</span
            ><textarea
              v-model="code"
              class="code-editor"
              required
              maxlength="65536"
              spellcheck="false"
              autocapitalize="off"
              autocomplete="off"
              :disabled="busy"
              placeholder="在此编写代码"
            />
          </label>
          <div class="editor-footer">
            <span class="mono subtle">{{ code.length }} / 65536</span
            ><button v-if="auth.user" class="button" :disabled="busy || !language || !code.trim()">
              {{ busy ? '提交中…' : '提交评测' }}</button
            ><RouterLink
              v-else
              :to="{ path: '/login', query: { redirect: route.fullPath } }"
              class="button"
              >登录后提交</RouterLink
            >
          </div>
          <p v-if="language === 'JAVA'" class="field-hint code-hint">
            Java 请使用 public class Main
          </p>
          <p v-if="submitError" class="error inline-alert" role="alert">{{ submitError }}</p>
        </form>
      </aside>
    </div>
    <section class="section-block">
      <div class="section-title">
        <h2>最近提交</h2>
        <RouterLink :to="{ path: '/submissions', query: { problemId: id } }">全部提交</RouterLink>
      </div>
      <div class="panel table-panel">
        <PageState
          :loading="recent.loading.value"
          :error="recent.error.value"
          :empty="!recent.data.value?.items.length"
          @retry="recent.reload"
        /><SubmissionTable
          v-if="recent.data.value?.items.length && !recent.loading.value && !recent.error.value"
          :items="recent.data.value.items"
        />
      </div>
    </section>
  </section>
</template>
