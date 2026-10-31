import type { Extension } from '@codemirror/state'
import type { Language } from '@/types/api'

export async function loadEditorLanguage(language: Language | ''): Promise<Extension> {
  if (language === 'JAVA') return (await import('@codemirror/lang-java')).java()
  if (language === 'CPP' || language === 'C') return (await import('@codemirror/lang-cpp')).cpp()
  if (language === 'PYTHON3') return (await import('@codemirror/lang-python')).python()
  return []
}
