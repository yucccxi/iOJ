# 11. 图形验证码

> 返回目录：[iOJ 前端接口文档（v1）](README.md)

登录、发送注册邮箱验证码、发送找回密码邮箱验证码时，需要先获取并提交图形验证码。

图形验证码接口是独立于 `/auth` 的顶层接口：

```http
POST /api/v1/captcha
```

## 11.1 获取验证码

`POST /captcha` ｜ 公开

**请求体**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `purpose` | string | 是 | `LOGIN` 或 `EMAIL_SENDING` |

```json
{ "purpose": "LOGIN" }
```

`purpose` 的含义：

| purpose | 使用场景 |
| --- | --- |
| `LOGIN` | `POST /auth/login` |
| `EMAIL_SENDING` | `POST /auth/email-codes`、`POST /auth/password-reset-codes` |

**响应 `data`**

```json
{
  "captchaId": "7d5c9a2e4b8f4c1d9e6a3b7f2c8d1e5a",
  "image": "data:image/png;base64,...",
  "expiresInSeconds": 120
}
```

| 字段 | 说明 |
| --- | --- |
| `captchaId` | 本次验证码的 id，提交业务请求时原样带回 |
| `image` | PNG 图片的 data URL，可直接放进 `<img src>` |
| `expiresInSeconds` | 有效期（秒），默认 120；过期后需要重新获取 |

> 验证码答案只存在于图片里，不会出现在响应中。响应带 `Cache-Control: no-store`，前端不要缓存图片或复用旧 `captchaId`。

**常见错误**：`1000`（图形验证码功能未启用或 `purpose` 不是合法枚举）、`1001`（`purpose` 缺失）、`1006`（同一 IP 获取过于频繁，返回 `429`）。

## 11.2 在业务请求中提交

获取验证码后，把 `captchaId` 和用户输入的 `captchaCode` 放进对应业务请求：

```json
{
  "account": "alice",
  "password": "password123",
  "captchaId": "7d5c9a2e4b8f4c1d9e6a3b7f2c8d1e5a",
  "captchaCode": "A7K2P"
}
```

业务请求里**不要传 `purpose`**：服务端按接口固定校验：

| 业务接口 | 服务端期望 purpose |
| --- | --- |
| `POST /auth/login` | `LOGIN` |
| `POST /auth/email-codes` | `EMAIL_SENDING` |
| `POST /auth/password-reset-codes` | `EMAIL_SENDING` |

## 11.3 验证码规则

- 一次性使用：验证成功后立即失效
- 与 purpose 绑定：登录验证码不能用于发邮件，反之亦然
- 短有效期：默认 120 秒
- 最多输错 3 次，超过后作废
- 登录密码错误、发码业务失败后，验证码也已经被消费，需要重新获取
- 同一 IP 的获取频率有限制；超过后返回 `429` / `1006`，请稍后重试

## 11.4 错误码

| code | HTTP | 含义 | 前端处理 |
| --- | --- | --- | --- |
| `2010` | 400 | 验证码缺失、错误、过期、已使用、次数超限或 purpose 不匹配 | 刷新验证码，按 `message` 提示 |
| `1006` | 429 | 获取验证码过于频繁 | 稍后重试；不要继续轮询 |
| `1000` | 400 | 图形验证码功能未启用或 `purpose` 不是合法枚举 | 按 `message` 提示 |
| `1001` | 400 | `purpose` 缺失 | 检查请求体 |

## 11.5 前端建议

1. 页面打开时获取对应 purpose 的验证码
2. 点击“换一张”重新获取
3. 业务请求带上 `captchaId + captchaCode`
4. 收到 `2010` 后自动刷新验证码
5. 登录密码错误后刷新验证码
6. 发码失败后刷新验证码
7. 收到 `1006` 时稍后重试，不要持续请求

> 如果部署关闭了图形验证码（`IOJ_CAPTCHA_ENABLED=false`），`POST /captcha` 返回 `1000`，业务接口不再要求这两个字段。
