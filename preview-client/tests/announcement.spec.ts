import { mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, expect, test, vi } from 'vitest'
import AnnouncementNotice from '@/components/AnnouncementNotice.vue'

const storageKey = 'ioj:announcement:3588dc9a:dismissed'
const wrappers: VueWrapper[] = []
function notice() {
  const wrapper = mount(AnnouncementNotice)
  wrappers.push(wrapper)
  return wrapper
}

afterEach(() => {
  wrappers.splice(0).forEach((wrapper) => wrapper.unmount())
  vi.restoreAllMocks()
  window.sessionStorage.removeItem(storageKey)
})

test('公告链接正确，关闭后重新挂载仍保持隐藏且不改动认证存储', async () => {
  const wrapper = notice()
  expect(wrapper.text()).toContain('如果你第一次来这个网站，请看')
  expect(wrapper.get('a').attributes()).toMatchObject({
    href: 'https://blog.iynotuo.me/posts/3588dc9a',
    target: '_blank',
    rel: 'noopener noreferrer',
  })
  const write = vi.spyOn(Storage.prototype, 'setItem')
  await wrapper.get('button[aria-label="关闭公告提示"]').trigger('click')
  expect(wrapper.find('aside').exists()).toBe(false)
  expect(write.mock.calls).toEqual([[storageKey, '1']])
  expect(notice().find('aside').exists()).toBe(false)
})

test.each(['getItem', 'setItem'] as const)('存储 %s 被拒绝时仍可显示和关闭', async (method) => {
  vi.spyOn(Storage.prototype, method).mockImplementation(() => {
    throw new DOMException('Storage is disabled', 'SecurityError')
  })
  const wrapper = notice()
  expect(wrapper.find('aside').exists()).toBe(true)
  await wrapper.get('button').trigger('click')
  expect(wrapper.find('aside').exists()).toBe(false)
})
