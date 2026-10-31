<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '@/api/resources'
import { statuses } from '@/lib/contracts'
import { useResource } from '@/composables/useResource'
import PageState from '@/components/PageState.vue'
import Pagination from '@/components/Pagination.vue'
import SubmissionTable from '@/components/SubmissionTable.vue'
const route = useRoute(),
  router = useRouter()
const problemId = ref(String(route.query.problemId || '')),
  userId = ref(String(route.query.userId || '')),
  status = ref(String(route.query.status || ''))
const page = computed(() => Math.max(1, Number(route.query.page) || 1))
const { data, loading, error, reload } = useResource((signal) =>
  api.submissions(
    {
      problemId: String(route.query.problemId || ''),
      userId: String(route.query.userId || ''),
      status: String(route.query.status || ''),
      page: page.value,
      size: 20,
    },
    signal,
  ),
)
function navigate() {
  void router.push({
    query: {
      problemId: problemId.value || undefined,
      userId: userId.value || undefined,
      status: status.value || undefined,
    },
  })
}
function paginate(next: number) {
  void router.push({ query: { ...route.query, page: next > 1 ? next : undefined } })
}
</script>
<template>
  <section>
    <div class="page-heading"><h1>提交记录</h1></div>
    <form class="filter-bar panel" @submit.prevent="navigate()">
      <label
        >题号<input v-model="problemId" type="number" min="1" step="1" placeholder="全部"
      /></label>
      <label
        >用户 ID<input v-model="userId" type="number" min="1" step="1" placeholder="全部"
      /></label>
      <label
        >评测状态<select v-model="status">
          <option value="">全部状态</option>
          <option v-for="(label, key) in statuses" :key="key" :value="key">{{ label }}</option>
        </select></label
      >
      <button class="button secondary">查询</button>
    </form>
    <div class="panel table-panel">
      <PageState :loading="loading" :error="error" :empty="!data?.items.length" @retry="reload" />
      <SubmissionTable
        v-if="!loading && !error && data?.items.length"
        :items="data.items"
      /><Pagination
        v-if="data && !loading && !error"
        :page="data.page"
        :size="data.size"
        :total="data.total"
        @change="paginate"
      />
    </div>
  </section>
</template>
