import { shallowRef, ref, watchEffect, onScopeDispose } from 'vue'
import { errorText } from '@/lib/contracts'
export function useResource<T>(loader: (signal: AbortSignal) => Promise<T>) {
  const data = shallowRef<T | null>(null)
  const loading = ref(true)
  const error = ref('')
  let controller: AbortController | undefined
  let sequence = 0
  let disposed = false
  async function reload() {
    if (disposed) return
    const run = ++sequence
    controller?.abort()
    controller = new AbortController()
    const signal = controller.signal
    loading.value = true
    error.value = ''
    try {
      const value = await loader(signal)
      if (run === sequence && !disposed) data.value = value
    } catch (cause) {
      if (!signal.aborted && run === sequence && !disposed) {
        data.value = null
        error.value = errorText(cause)
      }
    } finally {
      if (run === sequence && !disposed) loading.value = false
    }
  }
  watchEffect(() => {
    void reload()
  })
  onScopeDispose(() => {
    disposed = true
    sequence++
    controller?.abort()
  })
  return { data, loading, error, reload }
}
