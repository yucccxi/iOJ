<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import UiIcon from './UiIcon.vue'
withDefaults(defineProps<{ activePane?: 'left' | 'right' }>(), { activePane: 'left' })
const root = ref<HTMLElement>()
const divider = ref<HTMLElement>()
const ratio = ref(45)
const dragging = ref(false)
let pointer: number | null = null
function move(event: PointerEvent) {
  if (pointer !== event.pointerId || !root.value) return
  const rect = root.value.getBoundingClientRect()
  if (rect.width)
    ratio.value = Math.max(
      28,
      Math.min(72, Math.round(((event.clientX - rect.left) / rect.width) * 100)),
    )
}
function start(event: PointerEvent) {
  if (event.button !== 0) return
  event.preventDefault()
  pointer = event.pointerId
  dragging.value = true
  divider.value?.setPointerCapture?.(event.pointerId)
  move(event)
}
function stop() {
  const previous = pointer
  pointer = null
  dragging.value = false
  if (previous !== null && divider.value?.hasPointerCapture?.(previous))
    divider.value.releasePointerCapture(previous)
}
function keyboard(event: KeyboardEvent) {
  const amount = event.shiftKey ? 10 : 2
  const next = {
    ArrowLeft: ratio.value - amount,
    ArrowRight: ratio.value + amount,
    Home: 28,
    End: 72,
  }[event.key]
  if (next === undefined) return
  event.preventDefault()
  ratio.value = Math.max(28, Math.min(72, next))
}
onBeforeUnmount(stop)
</script>
<template>
  <div
    ref="root"
    class="pane-layout"
    :class="{ 'is-resizing': dragging }"
    :data-active="activePane"
    :style="{ '--pane-ratio': ratio + '%' }"
  >
    <div class="pane-side pane-side--left"><slot name="left" /></div>
    <div
      ref="divider"
      class="pane-divider"
      role="separator"
      tabindex="0"
      aria-label="调整题面与代码宽度"
      aria-orientation="vertical"
      :aria-valuenow="ratio"
      :aria-valuemin="28"
      :aria-valuemax="72"
      :aria-valuetext="ratio + '% / ' + (100 - ratio) + '%'"
      title="拖动调整宽度，方向键微调"
      @pointerdown="start"
      @pointermove="move"
      @pointerup="stop"
      @pointercancel="stop"
      @lostpointercapture="stop"
      @keydown="keyboard"
    >
      <UiIcon name="grip" />
    </div>
    <div class="pane-side pane-side--right"><slot name="right" /></div>
  </div>
</template>
