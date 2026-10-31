<script setup lang="ts">
import type { SubmissionSummary } from '@/types/api'
import { dateText } from '@/lib/contracts'
import StatusBadge from './StatusBadge.vue'
defineProps<{ items: SubmissionSummary[] }>()
</script>
<template>
  <div class="table-scroll">
    <table>
      <thead>
        <tr>
          <th>提交</th>
          <th>题目</th>
          <th>状态</th>
          <th>语言</th>
          <th class="numeric">耗时</th>
          <th class="numeric">内存</th>
          <th>提交者</th>
          <th>提交时间</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="item in items" :key="item.id">
          <td class="mono">
            <RouterLink :to="'/submissions/' + item.id">#{{ item.id }}</RouterLink>
          </td>
          <td>
            <RouterLink :to="'/problems/' + item.problemId">{{ item.problemTitle }}</RouterLink>
          </td>
          <td><StatusBadge :value="item.status" /></td>
          <td class="mono">{{ item.language }}</td>
          <td class="mono nowrap numeric">
            {{ item.timeUsedMs == null ? '—' : item.timeUsedMs + ' ms' }}
          </td>
          <td class="mono nowrap numeric">
            {{ item.memoryUsedKb == null ? '—' : item.memoryUsedKb + ' KB' }}
          </td>
          <td>
            <RouterLink :to="'/users/' + item.userId">{{ item.userNickname }}</RouterLink>
          </td>
          <td class="mono nowrap subtle">{{ dateText(item.createdAt) }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
