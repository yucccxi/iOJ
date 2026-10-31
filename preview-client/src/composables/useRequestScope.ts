import { onScopeDispose } from 'vue'
export function useRequestScope(): AbortSignal {
  const controller = new AbortController()
  onScopeDispose(() => controller.abort())
  return controller.signal
}
