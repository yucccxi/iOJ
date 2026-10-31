<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, useId, watch } from 'vue'
import { basicSetup } from 'codemirror'
import { Compartment, EditorState } from '@codemirror/state'
import { EditorView, keymap, placeholder } from '@codemirror/view'
import { indentWithTab } from '@codemirror/commands'
import { HighlightStyle, syntaxHighlighting } from '@codemirror/language'
import { loadEditorLanguage } from '@/lib/editorLanguages'
import { tags } from '@lezer/highlight'
import type { Language } from '@/types/api'

const props = withDefaults(
  defineProps<{ modelValue: string; language: Language | ''; disabled?: boolean }>(),
  { disabled: false },
)
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const host = ref<HTMLElement>()
const hintId = useId()
const languageConfig = new Compartment()
const editableConfig = new Compartment()
const themeConfig = new Compartment()
let view: EditorView | undefined
let media: MediaQueryList | undefined
let languageVersion = 0
const languageLoading = ref(false)
const languageError = ref(false)

async function updateLanguage() {
  const version = ++languageVersion
  languageLoading.value = true
  languageError.value = false
  view?.dispatch({ effects: languageConfig.reconfigure([]) })
  try {
    const extension = await loadEditorLanguage(props.language)
    if (view && version === languageVersion)
      view.dispatch({ effects: languageConfig.reconfigure(extension) })
  } catch {
    if (view && version === languageVersion) languageError.value = true
  } finally {
    if (version === languageVersion) languageLoading.value = false
  }
}
function editableExtension() {
  return [EditorState.readOnly.of(props.disabled), EditorView.editable.of(!props.disabled)]
}
function theme(dark: boolean) {
  return EditorView.theme(
    {
      '&': {
        height: '100%',
        color: 'var(--ink)',
        backgroundColor: 'var(--surface)',
        fontSize: '14px',
      },
      '&.cm-focused': { outline: 'none' },
      '.cm-scroller': { overflow: 'auto', fontFamily: 'var(--mono)', lineHeight: '1.8' },
      '.cm-content': { padding: '18px 0', caretColor: 'var(--ink)' },
      '.cm-line': { padding: '0 18px' },
      '.cm-gutters': {
        color: 'var(--muted)',
        backgroundColor: 'var(--surface)',
        borderRight: '1px solid var(--line)',
        paddingRight: '6px',
      },
      '.cm-activeLine, .cm-activeLineGutter': { backgroundColor: 'var(--editor-active)' },
      '.cm-selectionBackground, &.cm-focused .cm-selectionBackground, ::selection': {
        backgroundColor: 'var(--editor-selection)',
      },
      '.cm-cursor': { borderLeftColor: 'var(--ink)' },
      '.cm-tooltip, .cm-panels': {
        color: 'var(--ink)',
        backgroundColor: 'var(--soft)',
        borderColor: 'var(--line)',
      },
    },
    { dark },
  )
}
function updateTheme() {
  view?.dispatch({ effects: themeConfig.reconfigure(theme(media?.matches || false)) })
}
onMounted(() => {
  media = window.matchMedia('(prefers-color-scheme: dark)')
  view = new EditorView({
    parent: host.value,
    doc: props.modelValue,
    extensions: [
      keymap.of([
        {
          key: 'Escape',
          run(editor) {
            editor.setTabFocusMode(2000)
            return false
          },
        },
        indentWithTab,
      ]),
      basicSetup,
      placeholder('在此编写代码'),
      languageConfig.of([]),
      editableConfig.of(editableExtension()),
      themeConfig.of(theme(media.matches)),
      syntaxHighlighting(
        HighlightStyle.define([
          { tag: tags.keyword, color: 'var(--code-keyword)' },
          { tag: [tags.string, tags.character], color: 'var(--code-string)' },
          { tag: [tags.number, tags.bool, tags.null], color: 'var(--code-number)' },
          { tag: tags.comment, color: 'var(--code-comment)', fontStyle: 'italic' },
          {
            tag: [tags.typeName, tags.className, tags.function(tags.variableName)],
            color: 'var(--code-function)',
          },
        ]),
      ),
      EditorState.transactionFilter.of((transaction) =>
        transaction.newDoc.length > 65536 ? [] : transaction,
      ),
      EditorView.contentAttributes.of({
        'aria-label': '源代码',
        'aria-describedby': hintId,
        spellcheck: 'false',
        autocapitalize: 'off',
        autocomplete: 'off',
      }),
      EditorView.updateListener.of((update) => {
        if (update.docChanged) {
          const value = update.state.doc.toString()
          if (value !== props.modelValue) emit('update:modelValue', value)
        }
      }),
    ],
  })
  media.addEventListener('change', updateTheme)
  void updateLanguage()
})
watch(
  () => props.modelValue,
  (value) => {
    if (view && value !== view.state.doc.toString())
      view.dispatch({ changes: { from: 0, to: view.state.doc.length, insert: value } })
  },
)
watch(
  () => props.language,
  () => void updateLanguage(),
)
watch(
  () => props.disabled,
  () => view?.dispatch({ effects: editableConfig.reconfigure(editableExtension()) }),
)
onBeforeUnmount(() => {
  languageVersion++
  media?.removeEventListener('change', updateTheme)
  view?.destroy()
  view = undefined
})
</script>
<template>
  <div class="code-editor" :aria-busy="languageLoading">
    <div ref="host" class="editor-mount"></div>
    <span :id="hintId" class="sr-only">按 Esc 再按 Tab 移出编辑器；代码最多 65536 字符。</span>
    <p v-if="languageError" class="editor-language-error error" role="alert">
      语法高亮加载失败
      <button type="button" class="text-button" @click="updateLanguage">重试</button>
    </p>
  </div>
</template>
