import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { safeReturn } from '@/lib/contracts'
const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', redirect: '/problems' },
    { path: '/problems', component: () => import('@/views/ProblemListView.vue') },
    {
      path: '/problems/mine',
      component: () => import('@/views/ProblemListView.vue'),
      meta: { requiresAuth: true, mine: true },
    },
    {
      path: '/problems/new',
      component: () => import('@/views/ProblemEditorView.vue'),
      meta: { requiresAuth: true },
    },
    { path: '/problems/:id(\\d+)', component: () => import('@/views/ProblemView.vue') },
    {
      path: '/problems/:id(\\d+)/edit',
      component: () => import('@/views/ProblemEditorView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/problems/:id(\\d+)/testcases',
      component: () => import('@/views/TestcasesView.vue'),
      meta: { requiresAuth: true },
    },
    { path: '/submissions', component: () => import('@/views/SubmissionListView.vue') },
    { path: '/submissions/:id(\\d+)', component: () => import('@/views/SubmissionView.vue') },
    { path: '/users/:id(\\d+)', component: () => import('@/views/ProfileView.vue') },
    {
      path: '/settings',
      component: () => import('@/views/SettingsView.vue'),
      meta: { requiresAuth: true },
    },
    { path: '/login', component: () => import('@/views/AuthView.vue') },
    { path: '/register', component: () => import('@/views/AuthView.vue') },
    {
      path: '/admin/problems',
      component: () => import('@/views/AdminView.vue'),
      meta: { requiresAuth: true, admin: true },
    },
    {
      path: '/admin/users',
      component: () => import('@/views/AdminView.vue'),
      meta: { requiresAuth: true, admin: true },
    },
    { path: '/:pathMatch(.*)*', component: () => import('@/views/NotFoundView.vue') },
  ],
  scrollBehavior: () => ({ top: 0 }),
})
router.beforeEach(async (to) => {
  const auth = useAuthStore()
  await auth.restore()
  if (auth.token && (to.meta.requiresAuth || to.path === '/login')) {
    try {
      await auth.verify()
    } catch {
      /* API errors remain readable on the destination page. */
    }
  }
  if (to.meta.requiresAuth && !auth.user)
    return { path: '/login', query: { redirect: safeReturn(to.fullPath) } }
  if (to.meta.admin && !auth.isAdmin) return '/forbidden'
  if ((to.path === '/login' || to.path === '/register') && auth.user)
    return safeReturn(to.query.redirect)
  document.title = 'iOJ · 算法练习'
})
export default router
