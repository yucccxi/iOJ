import { api } from './http'
import type { ChangePasswordRequest, UpdateProfileRequest, UserProfileVO, UserVO } from './types'

export function getUser(id: number): Promise<UserProfileVO> {
  return api<UserProfileVO>(`/users/${id}`)
}

export function getMyProfile(): Promise<UserProfileVO> {
  return api<UserProfileVO>('/users/me')
}

export function updateProfile(payload: UpdateProfileRequest): Promise<UserVO> {
  return api<UserVO>('/users/me', { method: 'PATCH', body: payload })
}

export function changePassword(payload: ChangePasswordRequest): Promise<null> {
  return api<null>('/users/me/password', { method: 'PUT', body: payload })
}
