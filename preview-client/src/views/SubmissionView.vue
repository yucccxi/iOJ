<script setup lang="ts">
import { onScopeDispose, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '@/api/resources'
import { useResource } from '@/composables/useResource'
import { dateText, finished } from '@/lib/contracts'
import PageState from '@/components/PageState.vue'
import StatusBadge from '@/components/StatusBadge.vue'
const route = useRoute()
const { data, loading, error, reload } = useResource((signal) =>
  api.submission(String(route.params.id), signal),
)
let timer: ReturnType<typeof setTimeout> | undefined
let count = 0
const slow = ref(false)
watch(data, (value) => {
  clearTimeout(timer)
  if (value && !finished(value.status)) {
    if (count++ < 60)
      timer = setTimeout(() => {
        void reload()
      }, 800)
    else slow.value = true
  } else slow.value = false
})
onScopeDispose(() => clearTimeout(timer))
function refresh() {
  count = 0
  slow.value = false
  void reload()
}
</script>
<template>
  <div class="page-heading">
    <div>
      <div class="breadcrumb">
        <RouterLink to="/submissions">提交记录</RouterLink><span>/</span>
      </div>
      <h1>提交 #{{ route.params.id }}</h1>
    </div>
    <button class="button secondary" :disabled="loading" @click="refresh">刷新</button>
  </div>
  <PageState :loading="loading && !data" :error="error" @retry="refresh" />
  <section v-if="data && !error" class="stack">
    <div class="panel submission-summary">
      <div aria-live="polite"><StatusBadge :value="data.status" /></div>
      <dl>
        <div>
          <dt>题目</dt>
          <dd>
            <RouterLink :to="'/problems/' + data.problemId">{{ data.problemTitle }}</RouterLink>
          </dd>
        </div>
        <div>
          <dt>提交者</dt>
          <dd>
            <RouterLink :to="'/users/' + data.userId">{{ data.userNickname }}</RouterLink>
          </dd>
        </div>
        <div>
          <dt>语言</dt>
          <dd class="mono">{{ data.language }}</dd>
        </div>
        <div>
          <dt>耗时</dt>
          <dd class="mono">{{ data.timeUsedMs == null ? '—' : data.timeUsedMs + ' ms' }}</dd>
        </div>
        <div>
          <dt>内存</dt>
          <dd class="mono">{{ data.memoryUsedKb == null ? '—' : data.memoryUsedKb + ' KB' }}</dd>
        </div>
        <div>
          <dt>提交时间</dt>
          <dd class="mono">{{ dateText(data.createdAt) }}</dd>
        </div>
        <div>
          <dt>完成时间</dt>
          <dd class="mono">{{ dateText(data.judgedAt) }}</dd>
        </div>
      </dl>
      <p v-if="slow" class="notice" role="status">评测时间较长，请稍后刷新</p>
    </div>
    <section v-if="data.compileMessage || data.judgeMessage" class="panel content-panel">
      <h2>{{ data.compileMessage ? '编译信息' : '评测信息' }}</h2>
      <pre class="diagnostic">{{ data.compileMessage || data.judgeMessage }}</pre>
    </section>
    <section v-if="data.code !== null" class="panel content-panel">
      <h2>源代码</h2>
      <pre class="source-code"><code>{{ data.code }}</code></pre>
    </section>
    <p v-else class="subtle">源码未公开</p>
    <section v-if="data.cases.length" class="panel table-panel">
      <div class="table-heading"><h2>测试点结果</h2></div>
      <div class="table-scroll">
        <table>
          <thead>
            <tr>
              <th>测试点</th>
              <th>结果</th>
              <th>耗时</th>
              <th>内存</th>
              <th>信息</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in data.cases" :key="item.ordinal">
              <td class="mono">#{{ item.ordinal }}</td>
              <td><StatusBadge :value="item.status" /></td>
              <td class="mono nowrap">
                {{ item.timeUsedMs == null ? '—' : item.timeUsedMs + ' ms' }}
              </td>
              <td class="mono nowrap">
                {{ item.memoryUsedKb == null ? '—' : item.memoryUsedKb + ' KB' }}
              </td>
              <td>
                <pre class="case-message">{{ item.message || '—' }}</pre>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </section>
</template>
