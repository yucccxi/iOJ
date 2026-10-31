import { vi } from 'vitest'
let handler: typeof fetch = async () => {
  throw new Error('Test HTTP handler is not configured')
}
export function setHandler(next: typeof fetch) {
  handler = next
}
vi.stubGlobal('fetch', (...args: Parameters<typeof fetch>) => handler(...args))
// jsdom has no layout or native modal implementation; these stubs test commands only.
window.scrollTo = () => {}
if (!HTMLDialogElement.prototype.showModal)
  HTMLDialogElement.prototype.showModal = function () {
    this.setAttribute('open', '')
  }
if (!HTMLDialogElement.prototype.close)
  HTMLDialogElement.prototype.close = function () {
    this.removeAttribute('open')
  }
