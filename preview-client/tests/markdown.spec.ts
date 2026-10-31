import { afterEach, describe, expect, test } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import MarkdownView from '@/components/MarkdownView.vue'

const mounted: VueWrapper[] = []
function render(text: string) {
  const wrapper = mount(MarkdownView, { props: { text } })
  mounted.push(wrapper)
  return wrapper
}
afterEach(() => {
  for (const wrapper of mounted.splice(0)) wrapper.unmount()
})

describe('Markdown 公式渲染契约', () => {
  test('题目中的空格行内公式、异或、上下标和同一行独立公式', () => {
    const wrapper = render(
      '数字 $ a $，关系 $ x \\oplus y = a $，范围 $ 0 \\le a \\lt 2^{31} $。\n\n$$ 2 \\oplus 1=3, \\qquad 2+1+4=7. $$',
    )
    expect(wrapper.findAll('.katex')).toHaveLength(4)
    expect(wrapper.findAll('.katex-display')).toHaveLength(1)
    expect(wrapper.find('math').exists()).toBe(true)
    expect(wrapper.text()).toContain('⊕')
    expect(wrapper.find('annotation').text()).toBe('a')
  })
  test('多行公式保留布局样式、根号 SVG 和可访问 MathML', () => {
    const wrapper = render(
      '$$\n\\begin{aligned}\nf(x)&=\\frac{x_1}{2}\\\\\ng(x)&=\\sqrt{x}+1\n\\end{aligned}\n$$',
    )
    expect(wrapper.find('.katex-display').exists()).toBe(true)
    expect(wrapper.find('.katex [style]').exists()).toBe(true)
    expect(wrapper.find('.katex svg').exists()).toBe(true)
    expect(wrapper.get('annotation').text()).toContain('\\frac{x_1}{2}')
  })
  test('公式与粗体、列表、引用及表格混排', () => {
    const wrapper = render(
      '**数学**与$a_i+b_j$。\n\n- 结果 $ -1 $\n\n> $ x^2 $\n\n| 变量 |\n| --- |\n| $ n $ |',
    )
    expect(wrapper.get('strong').text()).toBe('数学')
    expect(wrapper.findAll('.katex')).toHaveLength(4)
    expect(wrapper.find('li .katex').exists()).toBe(true)
    expect(wrapper.find('blockquote .katex').exists()).toBe(true)
    expect(wrapper.find('td .katex').exists()).toBe(true)
  })
  test('代码、转义美元符号和未闭合公式保持原文', () => {
    const tick = String.fromCharCode(96)
    const source = [
      tick + '$x$' + tick,
      '',
      tick.repeat(3) + 'txt',
      '$$ x^2 $$',
      tick.repeat(3),
      '',
      '<code>$y$</code>',
      '',
      '\\$5，单个 $ 尚未闭合',
    ].join('\n')
    const wrapper = render(source)
    expect(wrapper.find('.katex').exists()).toBe(false)
    expect(wrapper.get('pre code').text()).toContain('$$ x^2 $$')
    expect(wrapper.text()).toContain('$5，单个 $ 尚未闭合')
  })
  test('原始 HTML 清洗不放宽，可信公式输出单独保留', () => {
    const wrapper = render(
      '<span class="raw" style="position:fixed">正文</span><script>alert(1)</script><img src="x" onerror="alert(1)">\n\n<math><mtext>raw</mtext></math>\n\n$ x^2 $',
    )
    expect(wrapper.find('script').exists()).toBe(false)
    expect(wrapper.find('[onerror]').exists()).toBe(false)
    expect(wrapper.get('.raw').attributes('style')).toBeUndefined()
    expect(wrapper.findAll('math')).toHaveLength(1)
    expect(wrapper.find('.katex [style]').exists()).toBe(true)
  })
  test('公式中的危险链接、资源和 HTML 扩展不能执行', () => {
    const wrapper = render(
      '$\\href{javascript:alert(1)}{x}$\n\n$\\includegraphics{https://example.invalid/image}$\n\n$\\htmlStyle{position:fixed}{x}$',
    )
    expect(wrapper.find('a[href^="javascript"]').exists()).toBe(false)
    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.find('[style*="position:fixed"]').exists()).toBe(false)
    expect(wrapper.find('script').exists()).toBe(false)
  })
  test('错误公式回退显示，后续正常公式继续渲染', () => {
    const wrapper = render('错误 $\\frac{$，正常 $ x+1 $。')
    expect(wrapper.find('.katex-error').exists()).toBe(true)
    expect(wrapper.findAll('.katex')).toHaveLength(1)
    expect(wrapper.text()).toContain('\\frac{')
  })
  test('循环宏和超大尺寸有界，不影响页面后续公式', () => {
    const wrapper = render('$\\def\\cycle{\\cycle}\\cycle$\n\n$$\\rule{999em}{999em}$$\n\n$ x+1 $')
    expect(wrapper.find('.katex-error').exists()).toBe(true)
    expect(wrapper.findAll('.katex')).toHaveLength(2)
    const styles = wrapper
      .findAll('.katex-html [style]')
      .map((node) => node.attributes('style'))
      .join(' ')
    expect(styles).not.toContain('999em')
  })
  test('宏和旧公式不跨内容更新或其他实例保留', async () => {
    const wrapper = render('$\\gdef\\iojprivate{42}\\iojprivate$')
    expect(wrapper.find('.katex-error').exists()).toBe(false)
    await wrapper.setProps({ text: '$\\iojprivate$' })
    expect(wrapper.get('.katex-html').text()).toContain('\\iojprivate')
    expect(wrapper.get('.katex-html').text()).not.toContain('42')
    expect(render('$\\iojprivate$').get('.katex-html').text()).toContain('\\iojprivate')
    await wrapper.setProps({ text: '无公式' })
    expect(wrapper.find('.katex').exists()).toBe(false)
    expect(wrapper.text()).toBe('无公式')
  })
})
