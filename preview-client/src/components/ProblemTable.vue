<script setup lang="ts">
import type { ProblemSummary } from '@/types/api'
import StatusBadge from './StatusBadge.vue'
defineProps<{ items: ProblemSummary[]; manage?: boolean }>()
</script>
<template>
  <div class="table-scroll">
    <table class="problem-table">
      <thead>
        <tr>
          <th>题号</th>
          <th class="wide">题目与标签</th>
          <th>难度</th>
          <th v-if="manage">状态</th>
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
            <div class="problem-tags">
              <span v-for="tag in item.tags.slice(0, 2)" :key="tag.id" class="tag">{{
                tag.name
              }}</span>
              <details v-if="item.tags.length > 2" class="more-tags">
                <summary :aria-label="'展开其余 ' + (item.tags.length - 2) + ' 个标签'">
                  +{{ item.tags.length - 2 }}
                </summary>
                <div class="tag-popover">
                  <span v-for="tag in item.tags.slice(2)" :key="tag.id" class="tag">{{
                    tag.name
                  }}</span>
                </div>
              </details>
            </div>
          </td>
          <td><StatusBadge :value="item.difficulty" /></td>
          <td v-if="manage"><StatusBadge :value="item.status" /></td>
          <td class="mono nowrap numeric">{{ item.acceptedCount }} / {{ item.submitCount }}</td>
          <td class="author-cell">
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
