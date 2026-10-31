import { Marked } from 'marked'
import markedKatex from 'marked-katex-extension'
import katex, { type KatexOptions } from 'katex'
import DOMPurify from 'dompurify'

const escapes: Record<string, string> = {
  '&': '&amp;',
  '<': '&lt;',
  '>': '&gt;',
  '"': '&quot;',
  "'": '&#39;',
}
function escapeHtml(text: string) {
  return text.replace(/[&<>"']/g, (character) => escapes[character]!)
}

export function renderMarkdown(source: string): string {
  // Keep untrusted Markdown HTML separate from KaTeX's controlled layout markup.
  const nonce = Array.from(crypto.getRandomValues(new Uint32Array(4))).join('-')
  const fragments = new Map<string, { html: string; raw: string }>()
  const options: KatexOptions = {
    output: 'htmlAndMathml',
    trust: false,
    throwOnError: false,
    strict: 'ignore',
    maxExpand: 1000,
    maxSize: 10,
    errorColor: 'var(--danger)',
    macros: {},
  }
  const extension = markedKatex({ nonStandard: true })
  const parser = new Marked({
    extensions: (extension.extensions || []).map((entry) => ({
      ...entry,
      renderer(token) {
        const id = nonce + '-' + fragments.size
        const slot = '<span data-ioj-formula="' + id + '"></span>'
        const formula = typeof token.text === 'string' ? token.text : ''
        let rendered: string
        try {
          rendered = katex.renderToString(formula, {
            ...options,
            displayMode: token.displayMode === true,
          })
        } catch {
          rendered = '<span class="math-error">' + escapeHtml(token.raw) + '</span>'
        }
        fragments.set(id, { html: rendered, raw: token.raw })
        return slot + (token.type === 'blockKatex' ? '\n' : '')
      },
    })),
  })
  const clean = DOMPurify.sanitize(parser.parse(source, { async: false }), {
    RETURN_DOM_FRAGMENT: true,
    USE_PROFILES: { html: true },
    FORBID_TAGS: ['style', 'iframe', 'form'],
    FORBID_ATTR: ['style'],
  })
  // Only this render's unpredictable slots receive generated math; raw HTML code stays literal.
  const document = clean.ownerDocument
  for (const slot of clean.querySelectorAll('span[data-ioj-formula]')) {
    const formula = fragments.get(slot.getAttribute('data-ioj-formula') || '')
    if (!formula) continue
    if (slot.closest('code, pre, kbd, samp')) {
      slot.replaceWith(document.createTextNode(formula.raw))
    } else {
      const template = document.createElement('template')
      template.innerHTML = formula.html
      slot.replaceWith(template.content)
    }
  }
  const container = document.createElement('div')
  container.append(clean)
  return container.innerHTML
}
