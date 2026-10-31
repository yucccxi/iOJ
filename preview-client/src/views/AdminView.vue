<script setup lang="ts">
import { useRequestScope } from '@/composables/useRequestScope'
const signal = useRequestScope()
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '@/api/resources'
import { useResource } from '@/composables/useResource'
import { useAuthStore } from '@/stores/auth'
import { dateText, errorText } from '@/lib/contracts'
import PageState from '@/components/PageState.vue'
import Pagination from '@/components/Pagination.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import ConfirmButton from '@/components/ConfirmButton.vue'
const route = useRoute(),
  router = useRouter(),
  auth = useAuthStore()
const usersMode = route.path === '/admin/users'
const keyword = ref(String(route.query.keyword || '')),
  status = ref(String(route.query.status || ''))
const page = computed(() => Math.max(1, Number(route.query.page) || 1))
const users = useResource((signal) =>
  usersMode
    ? api.users({ keyword: String(route.query.keyword || ''), page: page.value, size: 20 }, signal)
    : Promise.resolve(null),
)
const problems = useResource((signal) =>
  !usersMode
    ? api.adminProblems(
        {
          keyword: String(route.query.keyword || ''),
          status: String(route.query.status || ''),
          page: page.value,
          size: 20,
        },
        signal,
      )
    : Promise.resolve(null),
)
const busy = ref(false),
  error = ref(''),
  notice = ref('')
async function run(action: () => Promise<unknown>) {
  if (busy.value) return
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    await action()
    notice.value = '操作成功'
    await (usersMode ? users.reload() : problems.reload())
  } catch (cause) {
    error.value = errorText(cause)
  } finally {
    busy.value = false
  }
}
function navigate(next = 1) {
  void router.push({
    query: {
      keyword: keyword.value || undefined,
      status: usersMode ? undefined : status.value || undefined,
      page: next > 1 ? next : undefined,
    },
  })
}
</script>
<template>
  <div class="page-heading"><h1>管理</h1></div>
  <nav class="tabs" aria-label="管理模块">
    <RouterLink to="/admin/problems" :class="{ selected: !usersMode }">题目审核</RouterLink
    ><RouterLink to="/admin/users" :class="{ selected: usersMode }">用户管理</RouterLink>
  </nav>
  <form class="filter-bar panel" @submit.prevent="navigate()">
    <label class="search-field"
      ><span class="sr-only">搜索</span
      ><input
        v-model="keyword"
        type="search"
        :placeholder="usersMode ? '搜索用户名、昵称或邮箱' : '搜索题目标题'"
        maxlength="128" /></label
    ><label v-if="!usersMode"
      ><span class="sr-only">题目状态</span
      ><select v-model="status">
        <option value="">全部状态</option>
        <option value="PENDING_REVIEW">待审核</option>
        <option value="PUBLISHED">已发布</option>
        <option value="HIDDEN">已下架</option>
        <option value="DRAFT">草稿</option>
      </select></label
    ><button class="button secondary">查询</button>
  </form>
  <p v-if="error" class="error" role="alert">{{ error }}</p>
  <p v-if="notice" class="notice" role="status">{{ notice }}</p>
  <section class="panel table-panel" v-if="usersMode">
    <PageState
      :loading="users.loading.value"
      :error="users.error.value"
      :empty="!users.data.value?.items.length"
      @retry="users.reload"
    />
    <div
      v-if="users.data.value?.items.length && !users.loading.value && !users.error.value"
      class="table-scroll"
    >
      <table>
        <thead>
          <tr>
            <th>ID</th>
            <th>用户</th>
            <th>角色</th>
            <th>注册时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in users.data.value.items" :key="row.id">
            <td class="mono">#{{ row.id }}</td>
            <td>
              <RouterLink :to="'/users/' + row.id">{{ row.nickname }}</RouterLink>
              <div class="subtle mono">{{ row.username }}</div>
            </td>
            <td>{{ row.role === 'ADMIN' ? '管理员' : '成员' }}</td>
            <td class="mono nowrap">{{ dateText(row.createdAt) }}</td>
            <td>
              <div class="actions nowrap">
                <ConfirmButton
                  title="修改角色"
                  :message="
                    '确认将 ' +
                    row.username +
                    ' 设为' +
                    (row.role === 'ADMIN' ? '成员' : '管理员') +
                    '？'
                  "
                  :disabled="busy || row.id === auth.user?.id"
                  @confirm="
                    run(() => api.userRole(row.id, row.role === 'ADMIN' ? 'USER' : 'ADMIN', signal))
                  "
                  >{{ row.role === 'ADMIN' ? '设为成员' : '设为管理员' }}</ConfirmButton
                >
                <ConfirmButton
                  title="封禁用户"
                  :message="'确认封禁 ' + row.username + '？其已有登录状态将失效。'"
                  danger
                  :disabled="busy || row.id === auth.user?.id"
                  @confirm="run(() => api.userStatus(row.id, 'BANNED', signal))"
                  >封禁</ConfirmButton
                >
                <ConfirmButton
                  title="解封用户"
                  :message="'确认解封 ' + row.username + '？'"
                  :disabled="busy || row.id === auth.user?.id"
                  @confirm="run(() => api.userStatus(row.id, 'ACTIVE', signal))"
                  >解封</ConfirmButton
                >
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <Pagination
      v-if="users.data.value && !users.loading.value && !users.error.value"
      :page="users.data.value.page"
      :size="users.data.value.size"
      :total="users.data.value.total"
      @change="navigate"
    />
  </section>
  <section v-else class="panel table-panel">
    <PageState
      :loading="problems.loading.value"
      :error="problems.error.value"
      :empty="!problems.data.value?.items.length"
      @retry="problems.reload"
    />
    <div
      v-if="problems.data.value?.items.length && !problems.loading.value && !problems.error.value"
      class="table-scroll"
    >
      <table>
        <thead>
          <tr>
            <th>题号</th>
            <th class="wide">题目</th>
            <th>作者</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in problems.data.value.items" :key="row.id">
            <td class="mono">#{{ row.id }}</td>
            <td>
              <RouterLink :to="'/problems/' + row.id">{{ row.title }}</RouterLink>
            </td>
            <td>
              <RouterLink :to="'/users/' + row.authorId">{{ row.authorNickname }}</RouterLink>
            </td>
            <td><StatusBadge :value="row.status" /></td>
            <td>
              <div class="actions nowrap">
                <RouterLink :to="'/problems/' + row.id + '/edit'" class="button small secondary"
                  >编辑</RouterLink
                ><ConfirmButton
                  title="上架题目"
                  :message="'确认上架题目 #' + row.id + '？'"
                  :disabled="busy || row.status === 'PUBLISHED'"
                  @confirm="run(() => api.problemStatus(row.id, 'PUBLISHED', signal))"
                  >上架</ConfirmButton
                ><ConfirmButton
                  title="下架题目"
                  :message="'确认下架题目 #' + row.id + '？'"
                  danger
                  :disabled="busy || row.status === 'HIDDEN'"
                  @confirm="run(() => api.problemStatus(row.id, 'HIDDEN', signal))"
                  >下架</ConfirmButton
                >
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <Pagination
      v-if="problems.data.value && !problems.loading.value && !problems.error.value"
      :page="problems.data.value.page"
      :size="problems.data.value.size"
      :total="problems.data.value.total"
      @change="navigate"
    />
  </section>
</template>
