import { afterEach, describe, expect, test, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { EditorView } from '@codemirror/view'
import { language } from '@codemirror/language'
import CodeEditor from '@/components/CodeEditor.vue'
import ResizablePanels from '@/components/ResizablePanels.vue'
import AccountMenu from '@/components/AccountMenu.vue'
import type { Language } from '@/types/api'
import * as languages from '@/lib/editorLanguages'
import type { Extension } from '@codemirror/state'

const mounted: VueWrapper[] = []
function editor(props: { modelValue: string; language: Language | ''; disabled?: boolean }) {
  const wrapper = mount(CodeEditor, { props, attachTo: document.body })
  mounted.push(wrapper)
  const view = EditorView.findFromDOM(wrapper.get('.cm-editor').element as HTMLElement)!
  return { wrapper, view }
}
afterEach(() => {
  for (const wrapper of mounted.splice(0)) wrapper.unmount()
  vi.restoreAllMocks()
  document.body.innerHTML = ''
})

describe('编辑器和布局契约（不验证几何与动画观感）', () => {
  test('编辑器发送文本，外部模型替换不产生重复更新', async () => {
    const { wrapper, view } = editor({ modelValue: 'before', language: 'JAVA' })
    expect(
      document.getElementById(view.contentDOM.getAttribute('aria-describedby')!)?.textContent,
    ).toContain('Esc')
    view.dispatch({ changes: { from: 0, to: view.state.doc.length, insert: 'after' } })
    expect(wrapper.emitted('update:modelValue')).toEqual([['after']])
    await wrapper.setProps({ modelValue: 'external' })
    expect(view.state.doc.toString()).toBe('external')
    expect(wrapper.emitted('update:modelValue')).toHaveLength(1)
  })
  test.each([
    ['JAVA', 'java'],
    ['CPP', 'cpp'],
    ['C', 'cpp'],
    ['PYTHON3', 'python'],
  ] as const)('%s 语言选择更新实际编辑器扩展', async (code, name) => {
    const { wrapper, view } = editor({ modelValue: '', language: '' })
    expect(view.state.facet(language)).toBeNull()
    await wrapper.setProps({ language: code })
    await vi.dynamicImportSettled()
    await flushPromises()
    expect(view.state.facet(language)?.name).toBe(name)
  })
  test('禁用状态与 65536 字符边界保留', async () => {
    const { wrapper, view } = editor({ modelValue: '', language: 'CPP' })
    view.dispatch({ changes: { from: 0, insert: 'x'.repeat(65536) } })
    expect(view.state.doc.length).toBe(65536)
    view.dispatch({ changes: { from: 0, insert: 'x' } })
    expect(view.state.doc.length).toBe(65536)
    await wrapper.setProps({ disabled: true })
    expect(view.state.readOnly).toBe(true)
    expect(view.contentDOM.getAttribute('contenteditable')).toBe('false')
    await wrapper.setProps({ disabled: false })
    expect(view.contentDOM.getAttribute('contenteditable')).toBe('true')
  })
  test('迟到的语言加载不覆盖新选择，卸载后不回写编辑器', async () => {
    const java = await languages.loadEditorLanguage('JAVA')
    const python = await languages.loadEditorLanguage('PYTHON3')
    let finishJava!: (extension: Extension) => void
    let finishCpp!: (extension: Extension) => void
    const pendingJava = new Promise<Extension>((resolve) => {
      finishJava = resolve
    })
    const pendingCpp = new Promise<Extension>((resolve) => {
      finishCpp = resolve
    })
    vi.spyOn(languages, 'loadEditorLanguage').mockImplementation((code) =>
      code === 'JAVA' ? pendingJava : code === 'CPP' ? pendingCpp : Promise.resolve(python),
    )
    const { wrapper, view } = editor({ modelValue: 'draft', language: 'JAVA' })
    await wrapper.setProps({ language: 'PYTHON3' })
    await flushPromises()
    expect(view.state.facet(language)?.name).toBe('python')
    finishJava(java)
    await flushPromises()
    expect(view.state.facet(language)?.name).toBe('python')
    await wrapper.setProps({ language: 'CPP' })
    const dispatch = vi.spyOn(view, 'dispatch')
    wrapper.unmount()
    mounted.splice(mounted.indexOf(wrapper), 1)
    dispatch.mockClear()
    finishCpp([])
    await flushPromises()
    expect(dispatch).not.toHaveBeenCalled()
  })
  test('语言加载失败可重试，代码仍能编辑且不会丢失', async () => {
    vi.spyOn(languages, 'loadEditorLanguage')
      .mockRejectedValueOnce(new Error('network'))
      .mockResolvedValueOnce([])
    const { wrapper, view } = editor({ modelValue: 'draft', language: 'JAVA' })
    await flushPromises()
    expect(wrapper.get('[role=alert]').text()).toContain('语法高亮加载失败')
    view.dispatch({ changes: { from: 5, insert: ' preserved' } })
    await wrapper.get('.editor-language-error button').trigger('click')
    await flushPromises()
    expect(wrapper.find('[role=alert]').exists()).toBe(false)
    expect(view.state.doc.toString()).toBe('draft preserved')
  })
  test('系统主题变化可生效，卸载销毁编辑器并解除主题监听', async () => {
    const { wrapper, view } = editor({ modelValue: '', language: 'PYTHON3' })
    const themeIndex = vi
      .mocked(window.matchMedia)
      .mock.calls.findLastIndex(([query]) => query === '(prefers-color-scheme: dark)')
    const media = vi.mocked(window.matchMedia).mock.results[themeIndex]!.value as MediaQueryList
    const remove = vi.spyOn(media, 'removeEventListener')
    const destroy = vi.spyOn(view, 'destroy')
    expect(view.state.facet(EditorView.darkTheme)).toBe(false)
    Object.defineProperty(media, 'matches', { value: true })
    media.dispatchEvent(new Event('change'))
    expect(view.state.facet(EditorView.darkTheme)).toBe(true)
    wrapper.unmount()
    mounted.splice(mounted.indexOf(wrapper), 1)
    expect(destroy).toHaveBeenCalledOnce()
    expect(remove).toHaveBeenCalledWith('change', expect.any(Function))
  })
  test('分栏支持键盘微调和边界', async () => {
    const wrapper = mount(ResizablePanels, { slots: { left: '题面', right: '代码' } })
    mounted.push(wrapper)
    const divider = wrapper.get('[role=separator]')
    await divider.trigger('keydown', { key: 'ArrowRight' })
    expect(divider.attributes('aria-valuenow')).toBe('47')
    await divider.trigger('keydown', { key: 'Home' })
    await divider.trigger('keydown', { key: 'ArrowLeft' })
    expect(divider.attributes('aria-valuenow')).toBe('28')
    await divider.trigger('keydown', { key: 'End' })
    await divider.trigger('keydown', { key: 'ArrowRight' })
    expect(divider.attributes('aria-valuenow')).toBe('72')
  })
  test('指针拖动更新分栏，松开后停止更新并释放捕获', async () => {
    const wrapper = mount(ResizablePanels)
    mounted.push(wrapper)
    vi.spyOn(wrapper.element, 'getBoundingClientRect').mockReturnValue(new DOMRect(0, 0, 1000, 500))
    const divider = wrapper.get('[role=separator]')
    const element = divider.element as HTMLElement
    element.setPointerCapture = vi.fn()
    element.hasPointerCapture = vi.fn(() => true)
    element.releasePointerCapture = vi.fn()
    async function pointer(type: string, clientX: number) {
      const event = new MouseEvent(type, { bubbles: true, cancelable: true, button: 0, clientX })
      Object.defineProperty(event, 'pointerId', { value: 5 })
      element.dispatchEvent(event)
      await flushPromises()
    }
    await pointer('pointerdown', 450)
    await pointer('pointermove', 700)
    expect(divider.attributes('aria-valuenow')).toBe('70')
    await pointer('pointerup', 700)
    expect(element.releasePointerCapture).toHaveBeenCalledWith(5)
    await pointer('pointermove', 400)
    expect(divider.attributes('aria-valuenow')).toBe('70')
  })
  test('账户菜单可用 Escape 和外部点击关闭，退出仍发出事件', async () => {
    const wrapper = mount(AccountMenu, {
      attachTo: document.body,
      props: { user: { id: 1, username: 'alice', nickname: 'Alice', role: 'USER', createdAt: '' } },
      global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } },
    })
    mounted.push(wrapper)
    const details = wrapper.get('details').element as HTMLDetailsElement
    details.open = true
    await wrapper.get('summary').trigger('keydown', { key: 'Escape' })
    expect(details.open).toBe(false)
    expect(document.activeElement).toBe(wrapper.get('summary').element)
    details.open = true
    document.body.dispatchEvent(new Event('pointerdown', { bubbles: true }))
    await flushPromises()
    expect(details.open).toBe(false)
    await wrapper.get('button').trigger('click')
    expect(wrapper.emitted('logout')).toHaveLength(1)
  })
})
