<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '@/api/resources'
import { useResource } from '@/composables/useResource'
import PageState from '@/components/PageState.vue'
import ProblemTable from '@/components/ProblemTable.vue'
import Pagination from '@/components/Pagination.vue'
const route = useRoute(),
  router = useRouter()
const mine = computed(() => !!route.meta.mine)
const keyword = ref(String(route.query.keyword || ''))
const difficulty = ref(String(route.query.difficulty || ''))
const tagId = ref(String(route.query.tagId || ''))
const page = computed(() => Math.max(1, Number(route.query.page) || 1))
const tags = useResource((signal) => (mine.value ? Promise.resolve([]) : api.tags(signal)))
const { data, loading, error, reload } = useResource((signal) =>
  (mine.value ? api.mine : api.problems)(
    {
      keyword: String(route.query.keyword || ''),
      difficulty: mine.value ? undefined : String(route.query.difficulty || ''),
      tagId: mine.value ? undefined : String(route.query.tagId || ''),
      page: page.value,
      size: 20,
    },
    signal,
  ),
)
function navigate(next = 1) {
  void router.push({
    query: {
      keyword: keyword.value || undefined,
      difficulty: mine.value ? undefined : difficulty.value || undefined,
      tagId: mine.value ? undefined : tagId.value || undefined,
      page: next > 1 ? next : undefined,
    },
  })
}
</script>
<template>
  <section>
    <div class="page-heading">
      <h1>{{ mine ? '我的题目' : '题库' }}</h1>
      <RouterLink v-if="mine" to="/problems/new" class="button">新建题目</RouterLink>
    </div>
    <form class="filter-bar panel" @submit.prevent="navigate()">
      <label class="search-field"
        ><span class="sr-only">搜索题目标题</span
        ><input v-model="keyword" type="search" placeholder="搜索题目标题" maxlength="128"
      /></label>
      <template v-if="!mine"
        ><label
          ><span class="sr-only">难度</span
          ><select v-model="difficulty">
            <option value="">全部难度</option>
            <option value="EASY">简单</option>
            <option value="MEDIUM">中等</option>
            <option value="HARD">困难</option>
          </select></label
        >
        <label
          ><span class="sr-only">标签</span
          ><select v-model="tagId">
            <option value="">全部标签</option>
            <option v-for="tag in tags.data.value || []" :key="tag.id" :value="String(tag.id)">
              {{ tag.name }}
            </option>
          </select></label
        ></template
      >
      <button class="button secondary">查询</button>
    </form>
    <p v-if="tags.error.value" class="error" role="alert">
      {{ tags.error.value }} <button class="text-button" @click="tags.reload">重试标签</button>
    </p>
    <div class="panel table-panel">
      <PageState
        :loading="loading"
        :error="error"
        :empty="!data?.items.length"
        :empty-text="mine ? '暂无上传的题目' : '暂无题目'"
        @retry="reload"
      />
      <ProblemTable
        v-if="!loading && !error && data?.items.length"
        :items="data.items"
        :manage="mine"
      />
      <Pagination
        v-if="data && !error && !loading"
        :page="data.page"
        :size="data.size"
        :total="data.total"
        @change="navigate"
      />
    </div>
  </section>
</template>
