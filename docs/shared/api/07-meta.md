# 7. 参考数据

> 返回目录：[iOJ 前端接口文档（v1）](README.md)

这两个接口都是公开的，前端启动时拉取一次即可。

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
    { "id": 1, "name": "入门" },
    { "id": 3, "name": "数学" },
    { "id": 7, "name": "图论" }
  ]
}
```

| 字段 | 说明 |
| --- | --- |
| `id` | 标签 id，用于 `GET /problems?tagId=` 筛选 |
| `name` | 标签名；上传题目时直接传名称，新建的标签会自动出现在这里 |
