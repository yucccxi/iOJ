<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { getCatalog } from '../api/catalog'
import { errorMessage } from '../api/http'
import { getLanguages, getScopes, getTags } from '../api/meta'
import {
  CATALOG_SORTS,
  DIFFICULTIES,
  type CatalogItemVO,
  type CatalogQuery,
  type CatalogSort,
  type Difficulty,
  type LanguageVO,
  type PageResult,
  type ScopeMetaVO,
  type SortOrder,
  type TagMatchMode,
  type TagMetaVO,
} from '../api/types'
import Pagination from '../components/Pagination.vue'
import { fmtDate, fmtRating, parseInteger, statusLabel } from '../utils/format'

const languages = ref<LanguageVO[]>([])
const tags = ref<TagMetaVO[]>([])
const scopes = ref<ScopeMetaVO[]>([])
const result = ref<PageResult<CatalogItemVO> | null>(null)
const loading = ref(false)
const error = ref('')

const filters = reactive({
  keyword: '',
  difficulties: [] as Difficulty[],
  tagIds: [] as number[],
  tagMatch: 'ANY' as TagMatchMode,
  scopeIds: [] as number[],
  rating: '',
  minRating: '',
  maxRating: '',
  sort: 'DEFAULT' as CatalogSort,
  order: 'DESC' as SortOrder,
  page: 1,
  size: 20,
})

function buildQuery(): CatalogQuery {
  return {
    keyword: filters.keyword.trim() || undefined,
    difficulty: filters.difficulties.length ? [...filters.difficulties] : undefined,
    tagId: filters.tagIds.length ? [...filters.tagIds] : undefined,
    tagMatch: filters.tagIds.length ? filters.tagMatch : undefined,
    scopeId: filters.scopeIds.length ? [...filters.scopeIds] : undefined,
    rating: parseInteger(filters.rating),
    minRating: parseInteger(filters.minRating),
    maxRating: parseInteger(filters.maxRating),
    sort: filters.sort,
    order: filters.order,
    page: filters.page,
    size: filters.size,
  }
}

async function load() {
  error.value = ''
  if (filters.sort === 'RATING' && filters.scopeIds.length !== 1) {
    error.value = '按难度分排序时必须且只能选择一个 scope（不同 scope 的 rating 不可比）'
    return
  }

  loading.value = true
  try {
    result.value = await getCatalog(buildQuery())
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    loading.value = false
  }
}

function search() {
  filters.page = 1
  load()
}

function reset() {
  filters.keyword = ''
  filters.difficulties = []
  filters.tagIds = []
  filters.tagMatch = 'ANY'
  filters.scopeIds = []
  filters.rating = ''
  filters.minRating = ''
  filters.maxRating = ''
  filters.sort = 'DEFAULT'
  filters.order = 'DESC'
  filters.page = 1
  filters.size = 20
  load()
}

function changePage(page: number) {
  filters.page = page
  load()
}

function changeSize(size: number) {
  filters.size = size
  filters.page = 1
}

onMounted(() => {
  void load()
  void Promise.allSettled([
    getLanguages().then((data) => (languages.value = data)),
    getTags().then((data) => (tags.value = data)),
    getScopes().then((data) => (scopes.value = data)),
  ])
})
</script>

<template>
  <div class="page-title">
    <h1>统一题库</h1>
    <span class="hint">本地题与外部 OJ 题混合检索；外部题点击标题打开原题链接。</span>
  </div>

  <div class="card">
    <div class="form-grid">
      <div class="form-row">
        <label for="keyword">标题关键词</label>
        <input id="keyword" v-model="filters.keyword" type="text" @keyup.enter="search" />
      </div>
      <div class="form-row">
        <label>难度（多选，OR）</label>
        <div class="checkbox-list">
          <label v-for="item in DIFFICULTIES" :key="item.value">
            <input v-model="filters.difficulties" type="checkbox" :value="item.value" />
            {{ item.label }}
          </label>
        </div>
      </div>
      <div class="form-row">
        <label>标签（多选）</label>
        <div class="checkbox-list">
          <label v-for="tag in tags" :key="tag.id">
            <input v-model="filters.tagIds" type="checkbox" :value="tag.id" />
            {{ tag.name }}（{{ tag.problemCount }}）
          </label>
          <span v-if="!tags.length" class="hint">暂无标签</span>
        </div>
      </div>
      <div class="form-row">
        <label for="tagMatch">标签匹配方式</label>
        <select id="tagMatch" v-model="filters.tagMatch">
          <option value="ANY">满足任一</option>
          <option value="ALL">满足全部</option>
        </select>
      </div>
      <div class="form-row">
        <label>Scope（多选，OR）</label>
        <div class="checkbox-list">
          <label v-for="scope in scopes" :key="scope.id">
            <input v-model="filters.scopeIds" type="checkbox" :value="scope.id" />
            {{ scope.name }}（{{ scope.problemCount }}）
          </label>
          <span v-if="!scopes.length" class="hint">暂无 scope</span>
        </div>
      </div>
      <div class="form-row">
        <label>难度分</label>
        <div class="inline-form">
          <input v-model="filters.rating" type="number" placeholder="精确" style="width: 90px" />
          <input v-model="filters.minRating" type="number" placeholder="最小" style="width: 90px" />
          <input v-model="filters.maxRating" type="number" placeholder="最大" style="width: 90px" />
        </div>
      </div>
      <div class="form-row">
        <label for="sort">排序</label>
        <select id="sort" v-model="filters.sort">
          <option v-for="item in CATALOG_SORTS" :key="item.value" :value="item.value">
            {{ item.label }}
          </option>
        </select>
      </div>
      <div class="form-row">
        <label for="order">方向</label>
        <select id="order" v-model="filters.order">
          <option value="DESC">降序</option>
          <option value="ASC">升序</option>
        </select>
      </div>
    </div>

    <div class="actions" style="margin-top: 12px">
      <button type="button" :disabled="loading" @click="search">
        {{ loading ? '查询中…' : '查询' }}
      </button>
      <button type="button" class="secondary" :disabled="loading" @click="reset">重置</button>
      <span class="hint">sort=RATING 时必须只选一个 scope</span>
    </div>

    <p v-if="error" class="error" style="margin-top: 12px">{{ error }}</p>
  </div>

  <div class="card">
    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>ID</th>
            <th>标题</th>
            <th>Scope</th>
            <th>难度</th>
            <th>Rating</th>
            <th>标签</th>
            <th>提交 / 通过</th>
            <th>作者</th>
            <th>时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in result?.items ?? []" :key="item.id">
            <td>{{ item.id }}</td>
            <td>
              <a v-if="item.url" :href="item.url" target="_blank" rel="noreferrer">
                {{ item.title }}
              </a>
              <RouterLink v-else :to="`/problems/${item.id}`">{{ item.title }}</RouterLink>
              <div v-if="item.code" class="hint">原题号：{{ item.code }}</div>
              <div v-if="item.time" class="hint">比赛时间：{{ fmtDate(item.time) }}</div>
            </td>
            <td>{{ item.scopeName }}</td>
            <td>{{ statusLabel(item.difficulty, DIFFICULTIES) }}</td>
            <td>{{ fmtRating(item.rating) }}</td>
            <td>
              <span v-for="tag in item.tags" :key="tag.id" class="badge" style="margin-right: 4px">
                {{ tag.name }}
              </span>
              <span v-if="!item.tags.length" class="hint">-</span>
            </td>
            <td>{{ item.submitCount }} / {{ item.acceptedCount }}</td>
            <td>
              <RouterLink v-if="item.authorId" :to="`/users/${item.authorId}`">
                {{ item.authorNickname }}
              </RouterLink>
              <span v-else>-</span>
            </td>
            <td>{{ fmtDate(item.createdAt) }}</td>
          </tr>
          <tr v-if="!(result?.items.length)">
            <td colspan="9" class="hint">没有匹配的题目</td>
          </tr>
        </tbody>
      </table>
    </div>

    <Pagination
      :page="filters.page"
      :size="filters.size"
      :total="result?.total ?? 0"
      @update:page="changePage"
      @update:size="changeSize"
    />
  </div>

  <details class="card">
    <summary>参考数据（GET /meta/languages、/meta/tags、/meta/scopes）</summary>
    <h3>语言（{{ languages.length }}）</h3>
    <p>
      <span v-for="language in languages" :key="language.code" class="badge" style="margin-right: 6px">
        {{ language.code }} - {{ language.displayName }}
      </span>
      <span v-if="!languages.length" class="hint">服务端未配置语言</span>
    </p>
    <h3>标签（{{ tags.length }}）</h3>
    <p>
      <span v-for="tag in tags" :key="tag.id" class="badge" style="margin-right: 6px">
        {{ tag.id }}:{{ tag.name }}（{{ tag.problemCount }}）
      </span>
    </p>
    <h3>Scope（{{ scopes.length }}）</h3>
    <p>
      <span v-for="scope in scopes" :key="scope.id" class="badge" style="margin-right: 6px">
        {{ scope.id }}:{{ scope.name }}（{{ scope.problemCount }}）
      </span>
    </p>
  </details>
</template>
