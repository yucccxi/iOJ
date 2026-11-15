<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { listJudgeNodes } from '../../api/admin'
import { errorMessage } from '../../api/http'
import type { JudgeNodeVO } from '../../api/types'
import { fmt, fmtDate, statusClass } from '../../utils/format'

const nodes = ref<JudgeNodeVO[]>([])
const loading = ref(false)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    nodes.value = await listJudgeNodes()
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page-title">
    <h1>判题节点</h1>
    <button type="button" class="secondary" :disabled="loading" @click="load">
      {{ loading ? '刷新中…' : '刷新' }}
    </button>
  </div>

  <div class="card">
    <p class="hint">
      节点由 Agent 首次 claim 或心跳自动注册；一个 key 只能绑定一个 nodeId。
      OFFLINE 表示超过 node-offline-seconds 没有心跳，任务等租约过期后回收。节点列表只读。
    </p>
    <p v-if="error" class="error">{{ error }}</p>
  </div>

  <div class="card">
    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>节点 ID</th>
            <th>主机名</th>
            <th>版本</th>
            <th>语言</th>
            <th>最大并发</th>
            <th>运行中</th>
            <th>Isolate 版本</th>
            <th>状态</th>
            <th>最后心跳</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="node in nodes" :key="node.nodeId">
            <td class="mono">{{ node.nodeId }}</td>
            <td>{{ fmt(node.hostname) }}</td>
            <td>{{ fmt(node.version) }}</td>
            <td>
              <span v-for="language in node.languages" :key="language" class="badge" style="margin-right: 4px">
                {{ language }}
              </span>
              <span v-if="!node.languages.length" class="hint">-</span>
            </td>
            <td>{{ node.maxConcurrency }}</td>
            <td>{{ node.runningTasks }}</td>
            <td>{{ fmt(node.isolateVersion) }}</td>
            <td><span :class="statusClass(node.status)">{{ node.status }}</span></td>
            <td>{{ fmtDate(node.lastHeartbeatAt) }}</td>
          </tr>
          <tr v-if="!nodes.length">
            <td colspan="9" class="hint">还没有判题节点上线</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
