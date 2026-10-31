<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import type { User } from '@/types/api'
import UiIcon from './UiIcon.vue'
const props = defineProps<{ user: User }>()
const emit = defineEmits<{ logout: [] }>()
const menu = ref<HTMLDetailsElement>()
const initial = computed(() => Array.from(props.user.nickname || props.user.username)[0])
function close(focus = false) {
  if (!menu.value) return
  menu.value.open = false
  if (focus) menu.value.querySelector('summary')?.focus()
}
function outside(event: PointerEvent) {
  if (event.target instanceof Node && !menu.value?.contains(event.target)) close()
}
function logout() {
  close()
  emit('logout')
}
onMounted(() => document.addEventListener('pointerdown', outside))
onBeforeUnmount(() => document.removeEventListener('pointerdown', outside))
</script>
<template>
  <details ref="menu" class="account-menu" @keydown.esc.stop.prevent="close(true)">
    <summary class="account-trigger" aria-label="账户菜单">
      <span class="avatar" aria-hidden="true">{{ initial }}</span>
      <span class="account-name">{{ user.nickname }}</span
      ><UiIcon name="chevron" />
    </summary>
    <div class="account-popover">
      <span class="menu-identity mono">@{{ user.username }}</span>
      <RouterLink :to="'/users/' + user.id" @click="close()">个人主页</RouterLink>
      <RouterLink to="/settings" @click="close()">账号设置</RouterLink>
      <button type="button" class="menu-logout" @click="logout">退出</button>
    </div>
  </details>
</template>
