<script setup lang="ts">
import { useRoute } from 'vue-router'
import { api } from '@/api/resources'
import { useResource } from '@/composables/useResource'
import { useAuthStore } from '@/stores/auth'
import { dateText } from '@/lib/contracts'
import PageState from '@/components/PageState.vue'
import SubmissionTable from '@/components/SubmissionTable.vue'
const route = useRoute(),
  auth = useAuthStore()
const id = String(route.params.id)
const profile = useResource((signal) =>
  api.profile(auth.user?.id === Number(id) ? 'me' : id, signal),
)
const submissions = useResource((signal) =>
  api.submissions({ userId: id, page: 1, size: 10 }, signal),
)
</script>
<template>
  <PageState
    :loading="profile.loading.value"
    :error="profile.error.value"
    @retry="profile.reload"
  />
  <section v-if="profile.data.value && !profile.loading.value && !profile.error.value">
    <div class="page-heading">
      <div>
        <h1>{{ profile.data.value.nickname }}</h1>
        <p class="subtle mono">@{{ profile.data.value.username }} · #{{ profile.data.value.id }}</p>
      </div>
      <RouterLink
        v-if="auth.user?.id === profile.data.value.id"
        to="/settings"
        class="button secondary"
        >账号设置</RouterLink
      >
    </div>
    <div class="profile-strip panel">
      <div>
        <span class="metric mono">{{ profile.data.value.solvedCount }}</span
        ><span>已通过题目</span>
      </div>
      <dl>
        <div>
          <dt>角色</dt>
          <dd>{{ profile.data.value.role === 'ADMIN' ? '管理员' : '成员' }}</dd>
        </div>
        <div>
          <dt>注册时间</dt>
          <dd class="mono">{{ dateText(profile.data.value.createdAt) }}</dd>
        </div>
      </dl>
    </div>
    <div class="section-title section-block">
      <h2>最近提交</h2>
      <RouterLink :to="{ path: '/submissions', query: { userId: id } }">全部提交</RouterLink>
    </div>
    <div class="panel table-panel">
      <PageState
        :loading="submissions.loading.value"
        :error="submissions.error.value"
        :empty="!submissions.data.value?.items.length"
        @retry="submissions.reload"
      /><SubmissionTable
        v-if="
          submissions.data.value?.items.length &&
          !submissions.loading.value &&
          !submissions.error.value
        "
        :items="submissions.data.value.items"
      />
    </div>
  </section>
</template>
