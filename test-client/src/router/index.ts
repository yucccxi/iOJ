import { createRouter, createWebHistory } from 'vue-router'
import { isAdmin, session } from '../stores/session'

declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean
    requiresAdmin?: boolean
  }
}

const router = createRouter({
  history: createWebHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    { path: '/', redirect: '/catalog' },
    { path: '/login', name: 'login', component: () => import('../views/LoginView.vue') },
    { path: '/register', name: 'register', component: () => import('../views/RegisterView.vue') },
    {
      path: '/password-reset',
      name: 'password-reset',
      component: () => import('../views/PasswordResetView.vue'),
    },
    { path: '/catalog', name: 'catalog', component: () => import('../views/CatalogView.vue') },
    {
      path: '/problems/mine',
      name: 'my-problems',
      component: () => import('../views/MyProblemsView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/problems/new',
      name: 'problem-new',
      component: () => import('../views/ProblemEditView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/problems/:id/edit',
      name: 'problem-edit',
      component: () => import('../views/ProblemEditView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/problems/:id',
      name: 'problem-detail',
      component: () => import('../views/ProblemDetailView.vue'),
    },
    {
      path: '/submissions',
      name: 'submissions',
      component: () => import('../views/SubmissionListView.vue'),
    },
    {
      path: '/submissions/:id',
      name: 'submission-detail',
      component: () => import('../views/SubmissionDetailView.vue'),
    },
    {
      path: '/users/me',
      name: 'user-me',
      component: () => import('../views/UserProfileView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/users/:id',
      name: 'user-profile',
      component: () => import('../views/UserProfileView.vue'),
    },
    {
      path: '/settings',
      name: 'settings',
      component: () => import('../views/SettingsView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/admin/problems',
      name: 'admin-problems',
      component: () => import('../views/admin/AdminProblemsView.vue'),
      meta: { requiresAuth: true, requiresAdmin: true },
    },
    {
      path: '/admin/users',
      name: 'admin-users',
      component: () => import('../views/admin/AdminUsersView.vue'),
      meta: { requiresAuth: true, requiresAdmin: true },
    },
    {
      path: '/admin/tags',
      name: 'admin-tags',
      component: () => import('../views/admin/AdminTagsView.vue'),
      meta: { requiresAuth: true, requiresAdmin: true },
    },
    {
      path: '/admin/scopes',
      name: 'admin-scopes',
      component: () => import('../views/admin/AdminScopesView.vue'),
      meta: { requiresAuth: true, requiresAdmin: true },
    },
    {
      path: '/admin/judge-keys',
      name: 'admin-judge-keys',
      component: () => import('../views/admin/AdminJudgeKeysView.vue'),
      meta: { requiresAuth: true, requiresAdmin: true },
    },
    {
      path: '/admin/judge-nodes',
      name: 'admin-judge-nodes',
      component: () => import('../views/admin/AdminJudgeNodesView.vue'),
      meta: { requiresAuth: true, requiresAdmin: true },
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('../views/NotFoundView.vue'),
    },
  ],
})

router.beforeEach((to) => {
  if (to.meta.requiresAuth && !session.user) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.meta.requiresAdmin && !isAdmin()) {
    return { name: 'catalog' }
  }
  return true
})

export default router
