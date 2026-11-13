# 统一题库与参考数据

本文说明统一题库搜索和语言、标签、scope 参考数据接口，读者为前端与外部调用方。题目详情、出题和测试数据接口见 [题目与测试数据](03-problems.md)。

> 返回目录：[前端 API 索引](README.md)

## 1. 统一题库

`GET /api/v1/catalog` ｜ 公开

把本地题目和外部 OJ 题目（只存链接元数据）放进同一个列表筛选与分页。本地题只返回 `PUBLISHED`；外部题没有发布状态，全部参与。

### 1.1 查询参数

所有参数可选；同时传表示“且”关系，重复参数表示 OR。

| 参数 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `keyword` | string | — | 按标题模糊搜索，本地题和外部题都匹配；空字符串等于不筛选 |
| `difficulty` | string | — | `EASY` / `MEDIUM` / `HARD` / `UNKNOWN`；可重复传多个，多个值 OR；`UNKNOWN` 只出现在外部题 |
| `tagId` | integer | — | 标签 id，可重复；本地题和外部题都按各自的标签关联筛选 |
| `tagMatch` | string | `ANY` | 多标签匹配：`ANY` 满足任一，`ALL` 满足全部 |
| `rating` | integer | — | 难度分精确匹配 |
| `minRating` / `maxRating` | integer | — | 难度分闭区间，可只传一边 |
| `scopeId` | integer | — | scope id，可重复；不传表示全部；未知 id 返回 `400` / `1000` |
| `sort` | string | `DEFAULT` | `DEFAULT` / `RATING` / `ACCEPTED` / `SUBMISSIONS` / `ACCEPTANCE_RATE` |
| `order` | string | `DESC` | `ASC` / `DESC` |
| `page` / `size` | integer | `1` / `20` | 分页；`size` 最大 100，越界会被纠正 |

`sort=RATING` 必须且只能指定一个 `scopeId`：不同 scope 的 rating 刻度不可比。

多值示例：

```text
?difficulty=EASY&difficulty=HARD
?scopeId=16&scopeId=7
?tagId=3&tagId=5&tagMatch=ALL
?scopeId=7&sort=RATING&order=ASC
```

### 1.2 成功响应

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "items": [
      {
        "id": 12,
        "title": "A+B Problem",
        "code": null,
        "difficulty": "EASY",
        "time": null,
        "rating": 800,
        "scopeId": 16,
        "scopeName": "LOCAL",
        "url": null,
        "submitCount": 30,
        "acceptedCount": 18,
        "authorId": 1,
        "authorNickname": "Alice",
        "tags": [{ "id": 3, "name": "数学" }],
        "createdAt": "2026-10-30T11:18:05"
      },
      {
        "id": 5,
        "title": "Two Sum",
        "code": "CF_1A",
        "difficulty": "EASY",
        "time": "2026-10-31T22:35:00",
        "rating": 1200,
        "scopeId": 7,
        "scopeName": "Codeforces",
        "url": "https://codeforces.com/problemset/problem/1/A",
        "submitCount": 4200,
        "acceptedCount": 3100,
        "authorId": null,
        "authorNickname": null,
        "tags": [{ "id": 3, "name": "数学" }],
        "createdAt": "2026-11-01T09:30:00"
      }
    ],
    "total": 2,
    "page": 1,
    "size": 20
  }
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `data.items[].id` | integer | 本地题或外部题的 id：本地题用于 `GET /problems/{id}`；外部题用 `url` 跳转 |
| `data.items[].title` | string | 标题 |
| `data.items[].code` | string / null | 外部题原站题号（如 `CF_1A`、`AT_ABC123_D`）；本地题恒为 `null` |
| `data.items[].difficulty` | string | `EASY` / `MEDIUM` / `HARD`；外部题无 rating 时为 `UNKNOWN` |
| `data.items[].time` | string / null | 外部题比赛时间，UTC+8，格式 `yyyy-MM-ddTHH:mm:ss`（可能带小数秒）；本地题与缺失时均为 `null` |
| `data.items[].rating` | integer | 数值难度分；未评分返回 `-1` |
| `data.items[].scopeId` / `scopeName` | integer / string | scope id / 名称；本地题固定为 `LOCAL` |
| `data.items[].url` | string / null | 外部题原题链接；本地题恒为 `null` |
| `data.items[].submitCount` / `acceptedCount` | integer | 本地题由本站累加；外部题是导入的原站统计 |
| `data.items[].authorId` / `authorNickname` | integer / string / null | 只有本地题有值；外部题恒为 `null` |
| `data.items[].tags` | array | 标签 `{ id, name }`；可能为 `[]` |
| `data.items[].createdAt` | string | 本地题创建时间或外部题录入时间 |

区分两类题：

- `url != null`：外部 OJ 题，点击后打开原题链接；没有本站题目详情接口。
- `url == null`：本地题，使用 `id` 访问 `GET /problems/{id}`。
- 也可以用 `authorId != null` 或 `scopeName == "LOCAL"` 辅助判断。
- 响应不包含 `kind` 字段；不返回本地题的 `status`，因为列表中的本地题恒为已发布。

### 1.3 排序规则

| `sort` | 规则 |
| --- | --- |
| `DEFAULT` | 本地题按 `updatedAt`，外部题按 `time`；方向由 `order` 决定，默认 `DESC`；时间为空的外部题始终排最后 |
| `RATING` | 按 `rating`；未评分按 `-1` 处理 |
| `ACCEPTED` | 按 `acceptedCount` |
| `SUBMISSIONS` | 按 `submitCount` |
| `ACCEPTANCE_RATE` | 按 `acceptedCount / submitCount`；`submitCount=0` 的题始终排最后，不受方向影响 |

值相同时按本地/外部类型和 `id` 倒序做稳定 tie-break，保证翻页结果稳定。

### 1.4 失败情况

| HTTP | 错误码 | 触发条件 |
| --- | --- | --- |
| 400 | `1000` | `scopeId` / `rating` / `minRating` / `maxRating` 不是整数；scope 不存在；`tagMatch`、`sort` 或 `order` 不是合法枚举；`sort=RATING` 时没有指定单一 `scopeId`；请求方法不对 |

### 1.5 行为说明

- 只返回已发布本地题；草稿/待审核/已下架本地题请用 `GET /problems/mine` 或管理员审核列表。
- `rating` 精确筛选和 `minRating` / `maxRating` 不会命中 `NULL`（未评分）题；接口返回的 `-1` 只用于展示。
- 标签与 scope 对两类题都生效；`tagMatch=ALL` 要求题目标签包含全部请求的 id。
- 分页参数越界会被纠正，不返回错误；`total` 是满足条件的总条数。

## 2. 参考数据

三个接口都是公开的，前端启动时拉取一次即可。它们没有业务错误；服务端内部异常返回 `500` / `1500`，未知路径返回 `404` / `1004`。标签和 scope 的写操作只有管理员能做，见 [管理后台](05-admin.md)。

### 2.1 可选语言

`GET /api/v1/meta/languages` ｜ 公开

**成功响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": [
    { "code": "JAVA", "displayName": "Java 21" },
    { "code": "CPP", "displayName": "C++17" },
    { "code": "C", "displayName": "C11" },
    { "code": "PYTHON3", "displayName": "Python 3" }
  ]
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `data[].code` | string | 提交时 `language` 必须使用这个值 |
| `data[].displayName` | string | 下拉框展示名称 |

**行为说明**

- 列表来自服务端 `ioj.judge.languages` 配置；启用新语言后自动出现，前端不需要改代码。
- 空列表表示服务端没有配置任何语言；此时提交会返回 `4002`。

### 2.2 标签列表

`GET /api/v1/meta/tags` ｜ 公开

**成功响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": [
    { "id": 1, "name": "入门", "problemCount": 42 },
    { "id": 3, "name": "数学", "problemCount": 12 },
    { "id": 7, "name": "图论", "problemCount": 0 }
  ]
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `data[].id` | integer | 标签 id，用于 `GET /catalog?tagId=` 和出题时的 `tagIds` |
| `data[].name` | string | 标签名 |
| `data[].problemCount` | integer | 使用该标签的**已发布**本地题 + 外部题数量；草稿/待审核/已下架本地题不计入 |

**行为说明**

- 列表按名称排序，包含使用量为 0 的标签。
- 标签是管理员维护的共享字典，出题时只能选已有 id，不得通过题目接口创建。
- 删除标签时的占用检查会统计所有状态的本地题；因此 `problemCount=0` 不代表一定能删除。

### 2.3 Scope 列表

`GET /api/v1/meta/scopes` ｜ 公开

**成功响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": [
    { "id": 16, "name": "LOCAL", "createdAt": "2026-11-04T20:00:00", "problemCount": 42 },
    { "id": 2, "name": "AtCoder", "createdAt": "2026-10-01T09:00:00", "problemCount": 8 },
    { "id": 7, "name": "Codeforces", "createdAt": "2026-11-01T09:00:00", "problemCount": 15 }
  ]
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `data[].id` | integer | scope id，用于响应里的 `scopeId` 和 `GET /catalog?scopeId=` |
| `data[].name` | string | scope 名；`LOCAL` 是系统保留 scope，所有本地题都引用它 |
| `data[].createdAt` | string | 创建时间 |
| `data[].problemCount` | integer | 引用该 scope 的**已发布**本地题 + 外部题数量 |

**行为说明**

- `LOCAL` 由数据库初始化脚本创建，不得重命名或删除。
- 本地题上传/编辑时不得选择 scope，服务端固定为 `LOCAL`。
- 删除 scope 时的占用检查会统计所有状态的本地题和外部题；`problemCount=0` 不代表一定能删除。

## 相关文档

- [前端 API 索引](README.md)
- [题目与测试数据](03-problems.md)
- [管理后台](05-admin.md)（标签、scope 写操作）
- [外部题库同步](../../server/09-catalog-sync.md)
