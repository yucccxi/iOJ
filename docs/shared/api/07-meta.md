# 7. 参考数据

> 返回目录：[iOJ 前端接口文档（v1）](README.md)

这三个接口都是公开的，前端启动时拉取一次即可。

## 7.1 可选语言

`GET /meta/languages`

```json
{
  "code": 0, "message": "ok",
  "data": [
    { "code": "JAVA", "displayName": "Java 21" },
    { "code": "CPP", "displayName": "C++17" },
    { "code": "C", "displayName": "C11" },
    { "code": "PYTHON3", "displayName": "Python 3" }
  ]
}
```

| 字段 | 说明 |
| --- | --- |
| `code` | 提交时 `language` 必须使用这个值 |
| `displayName` | 下拉框展示名称 |

## 7.2 标签列表

`GET /meta/tags`

```json
{
  "code": 0, "message": "ok",
  "data": [
    { "id": 1, "name": "入门", "problemCount": 42 },
    { "id": 3, "name": "数学", "problemCount": 12 },
    { "id": 7, "name": "图论", "problemCount": 0 }
  ]
}
```

| 字段 | 说明 |
| --- | --- |
| `id` | 标签 id，用于 `GET /problems?tagId=` / `GET /catalog?tagId=` 筛选，以及出题时的 `tagIds` |
| `name` | 标签名 |
| `problemCount` | 使用该标签的题目数（本地题 + 外部 OJ 链接）；`0` 表示还没人用，可安全删除 |

> 列表公开可读（含使用量），但**新增/改名/删除只有管理员能做**，见 [8.6 标签管理](08-admin.md#86-标签管理)。出题时只能从这里返回的 `id` 里选，不能新建。

## 7.3 来源列表

`GET /meta/sources`

```json
{
  "code": 0, "message": "ok",
  "data": [
    { "id": 2, "name": "2026 新生赛", "createdAt": "2026-10-01T09:00:00", "problemCount": 8 },
    { "id": 7, "name": "Codeforces", "createdAt": "2026-11-01T09:00:00", "problemCount": 15 }
  ]
}
```

| 字段 | 说明 |
| --- | --- |
| `id` | 来源 id，用于出题的 `sourceId`，以及 `GET /catalog?sourceId=` 筛选 |
| `name` | 来源名（校内比赛、教材、OJ 平台都在这张表里） |
| `createdAt` | 创建时间 |
| `problemCount` | 引用该来源的题目数（本地题 + 外部 OJ 链接）；`0` 表示可安全删除 |

> 列表公开可读（含使用量），**新增/改名/删除只有管理员能做**，见 [8.7 来源管理](08-admin.md#87-来源管理)。

> 三个参考数据接口都是公开的，前端启动时拉一次即可；标签与来源的管理接口见 [8.6 标签管理](08-admin.md#86-标签管理) / [8.7 来源管理](08-admin.md#87-来源管理)。
