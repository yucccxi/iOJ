import { api } from './http'
import type {
  CaptchaPurpose,
  CaptchaVO,
  LoginRequest,
  LoginResponse,
  PasswordResetRequest,
  RegisterRequest,
  SendEmailCodeRequest,
  SendEmailCodeVO,
  UserVO,
} from './types'

export function createCaptcha(purpose: CaptchaPurpose): Promise<CaptchaVO> {
  return api<CaptchaVO>('/captcha', { method: 'POST', body: { purpose } })
}

export function sendEmailCode(payload: SendEmailCodeRequest): Promise<SendEmailCodeVO> {
  return api<SendEmailCodeVO>('/auth/email-codes', { method: 'POST', body: payload })
}

export function sendPasswordResetCode(payload: SendEmailCodeRequest): Promise<SendEmailCodeVO> {
  return api<SendEmailCodeVO>('/auth/password-reset-codes', { method: 'POST', body: payload })
}

export function resetPassword(payload: PasswordResetRequest): Promise<null> {
  return api<null>('/auth/password-resets', { method: 'POST', body: payload })
}

export function register(payload: RegisterRequest): Promise<UserVO> {
  return api<UserVO>('/auth/register', { method: 'POST', body: payload })
}

export function login(payload: LoginRequest): Promise<LoginResponse> {
  return api<LoginResponse>('/auth/login', { method: 'POST', body: payload })
}

export function getMe(): Promise<UserVO> {
  return api<UserVO>('/auth/me')
}

export function logoutAll(): Promise<LoginResponse> {
  return api<LoginResponse>('/auth/logout-all', { method: 'POST' })
}
