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
  page: {
    problemDetail: "题目详情",
  },
  errors: {
    serviceUnavailable: (status: number) => `服务暂时不可用（HTTP ${status}）`,
  },
} as const;
