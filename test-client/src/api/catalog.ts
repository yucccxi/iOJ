import { api } from './http'
import type { CatalogItemVO, CatalogQuery, PageResult } from './types'

export function getCatalog(query: CatalogQuery): Promise<PageResult<CatalogItemVO>> {
  return api<PageResult<CatalogItemVO>>('/catalog', { query: { ...query } })
}
