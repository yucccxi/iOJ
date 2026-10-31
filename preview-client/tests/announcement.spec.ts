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

test('公告链接正确，关闭只影响当前页面，重新挂载后再次显示', async () => {
  const wrapper = notice()
  expect(wrapper.text()).toContain('如果你第一次来这个网站，请看')
  expect(wrapper.get('[role="status"]').attributes('aria-label')).toBe('公告提示')
  expect(wrapper.get('a').attributes()).toMatchObject({
    href: 'https://blog.iynotuo.me/posts/3588dc9a',
    target: '_blank',
    rel: 'noopener noreferrer',
  })
  const write = vi.spyOn(Storage.prototype, 'setItem')
  await wrapper.get('button[aria-label="关闭公告提示"]').trigger('click')
  expect(wrapper.find('aside').exists()).toBe(false)
  expect(write).not.toHaveBeenCalled()
  expect(notice().find('aside').exists()).toBe(true)
})

test('旧的关闭记录不阻止公告显示，组件不再读取或写入存储', async () => {
  window.sessionStorage.setItem(storageKey, '1')
  const read = vi.spyOn(Storage.prototype, 'getItem')
  const write = vi.spyOn(Storage.prototype, 'setItem')
  const wrapper = notice()
  expect(wrapper.find('aside').exists()).toBe(true)
  await wrapper.get('button').trigger('click')
  expect(wrapper.find('aside').exists()).toBe(false)
  expect(read).not.toHaveBeenCalled()
  expect(write).not.toHaveBeenCalled()
})
