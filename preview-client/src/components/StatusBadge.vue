<script setup lang="ts">
import { statuses, problemStatusText, difficultyText } from '@/lib/contracts'
import type { SubmissionStatus, ProblemStatus, Difficulty } from '@/types/api'
defineProps<{ value: string }>()
function label(value: string) {
  return (
    statuses[value as SubmissionStatus] ||
    problemStatusText[value as ProblemStatus] ||
    difficultyText[value as Difficulty] ||
    value
  )
}
function tone(value: string) {
  if (['ACCEPTED', 'PUBLISHED', 'EASY'].includes(value)) return 'positive'
  if (['PENDING', 'JUDGING', 'PENDING_REVIEW', 'MEDIUM'].includes(value)) return 'pending'
  if (['DRAFT', 'HIDDEN'].includes(value)) return 'muted'
  return 'negative'
}
</script>
<template>
  <span class="badge" :class="tone(value)">{{ label(value) }}</span>
</template>
