/** User-visible copy for the default zh-CN locale. */
export const zh = {
  nav: {
    home: "首页",
    problems: "题库",
    discuss: "讨论",
    submissions: "提交记录",
    about: "关于",
    switchSection: "切换栏目",
    contribute: "贡献题目",
    login: "登录",
  },
  tagFilter: {
    label: "按标签筛选",
    title: "标签",
    trigger: (count: number) => (count > 0 ? `标签 · ${count}` : "标签"),
    searchPlaceholder: "搜索标签...",
    searchLabel: "搜索标签",
    empty: "没有匹配的标签",
    matchMode: "匹配方式",
    any: "满足任一",
    all: "满足全部",
    selected: (count: number) => `已选 ${count} 个标签`,
    reset: "重置",
    apply: "应用",
  },
  page: {
    problemDetail: "题目详情",
  },
  errors: {
    serviceUnavailable: (status: number) => `服务暂时不可用（HTTP ${status}）`,
  },
} as const;
