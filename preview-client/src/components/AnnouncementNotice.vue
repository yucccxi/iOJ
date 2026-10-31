<script setup lang="ts">
import { ref } from 'vue'

const storageKey = 'ioj:announcement:3588dc9a:dismissed'
const visible = ref(true)

try {
  visible.value = window.sessionStorage.getItem(storageKey) !== '1'
} catch {
  // Storage may be disabled; the notice still works for this page visit.
}

function dismiss() {
  visible.value = false
  try {
    window.sessionStorage.setItem(storageKey, '1')
  } catch {
    // Keep dismissal in memory when storage is unavailable.
  }
}
</script>

<template>
  <aside v-if="visible" class="announcement-notice" role="status" aria-label="公告提示">
    <p>
      如果你第一次来这个网站，请看
      <a href="https://blog.iynotuo.me/posts/3588dc9a" target="_blank" rel="noopener noreferrer"
        >公告</a
      >
    </p>
    <button type="button" class="announcement-close" aria-label="关闭公告提示" @click="dismiss">
      <span aria-hidden="true">×</span>
    </button>
  </aside>
</template>

<style scoped>
.announcement-notice {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 8px;
  padding: 4px max(32px, env(safe-area-inset-right)) 4px max(32px, env(safe-area-inset-left));
  border-bottom: 1px solid var(--line);
  background: var(--positive-bg);
  color: var(--ink);
}

.announcement-notice p {
  flex: 1;
  min-width: 0;
  line-height: 1.7;
}

.announcement-notice a {
  display: inline-flex;
  align-items: center;
  min-height: 44px;
  font-weight: 600;
  text-decoration: underline;
}

.announcement-close {
  display: grid;
  flex: 0 0 44px;
  place-items: center;
  width: 44px;
  height: 44px;
  padding: 0;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: var(--muted);
  font-size: 24px;
  line-height: 1;
}

.announcement-close:hover {
  background: var(--soft);
  color: var(--ink);
}

@media (max-width: 480px) {
  .announcement-notice {
    padding-right: max(16px, env(safe-area-inset-right));
    padding-left: max(16px, env(safe-area-inset-left));
  }
}
</style>
