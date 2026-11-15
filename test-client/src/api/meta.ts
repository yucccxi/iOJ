import { api } from './http'
import type { LanguageVO, ScopeMetaVO, TagMetaVO } from './types'

export function getLanguages(): Promise<LanguageVO[]> {
  return api<LanguageVO[]>('/meta/languages')
}

export function getTags(): Promise<TagMetaVO[]> {
  return api<TagMetaVO[]>('/meta/tags')
}

export function getScopes(): Promise<ScopeMetaVO[]> {
  return api<ScopeMetaVO[]>('/meta/scopes')
}
