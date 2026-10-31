<script setup lang="ts">
import { ref } from 'vue'
defineProps<{ title: string; message: string; disabled?: boolean; danger?: boolean }>()
const emit = defineEmits<{ confirm: [] }>()
const dialog = ref<HTMLDialogElement>()
function confirm() {
  dialog.value?.close()
  emit('confirm')
}
</script>
<template>
  <span class="confirm-control">
    <button
      type="button"
      class="button secondary"
      :class="{ danger }"
      :disabled="disabled"
      @click="dialog?.showModal()"
    >
      <slot />
    </button>
    <dialog ref="dialog" aria-modal="true" :aria-label="title">
      <h2>{{ title }}</h2>
      <p>{{ message }}</p>
      <div class="actions">
        <button type="button" class="button secondary" autofocus @click="dialog?.close()">
          取消</button
        ><button
          type="button"
          class="button"
          :class="{ danger }"
          @click="confirm"
        >
          确认
        </button>
      </div>
    </dialog>
  </span>
</template>
