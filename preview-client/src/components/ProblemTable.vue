<script setup lang="ts">
import type { ProblemSummary } from '@/types/api'
import StatusBadge from './StatusBadge.vue'
defineProps<{ items: ProblemSummary[]; manage?: boolean }>()
</script>
<template>
  <div class="table-scroll">
    <table>
      <thead>
        <tr>
          <th>题号</th>
          <th class="wide">题目</th>
          <th>难度</th>
          <th v-if="manage">状态</th>
          <th>标签</th>
          <th class="numeric">通过 / 提交</th>
          <th>出题人</th>
          <th v-if="manage">操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="item in items" :key="item.id">
          <td class="mono subtle">#{{ item.id }}</td>
          <td class="title-cell">
            <RouterLink :to="'/problems/' + item.id">{{ item.title }}</RouterLink>
          </td>
          <td><StatusBadge :value="item.difficulty" /></td>
          <td v-if="manage"><StatusBadge :value="item.status" /></td>
          <td>
            <span class="tag" v-for="tag in item.tags" :key="tag.id">{{ tag.name }}</span
            ><span v-if="!item.tags.length" class="subtle">—</span>
          </td>
          <td class="mono nowrap numeric">{{ item.acceptedCount }} / {{ item.submitCount }}</td>
          <td>
            <RouterLink :to="'/users/' + item.authorId">{{ item.authorNickname }}</RouterLink>
          </td>
          <td v-if="manage">
            <RouterLink :to="'/problems/' + item.id + '/edit'">编辑</RouterLink>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
