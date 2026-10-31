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
  <aside v-if="visible" class="announcement-notice" aria-label="公告提示">
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
  position: fixed;
  z-index: 12;
  right: max(16px, env(safe-area-inset-right));
  bottom: max(16px, env(safe-area-inset-bottom));
  display: flex;
  align-items: center;
  gap: 8px;
  width: 344px;
  max-width: calc(100% - 32px);
  padding: 8px 8px 8px 16px;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: var(--surface);
  color: var(--ink);
  box-shadow: 0 4px 20px #00000014;
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
</style>
