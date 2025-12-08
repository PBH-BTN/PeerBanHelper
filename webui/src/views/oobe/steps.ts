import type { OobeStepConfig } from '@/api/model/oobe'

export const oobeSteps: OobeStepConfig[] = [
  {
    titleKey: 'page.oobe.steps.welcome',
    canNext: (config) => config.acceptPrivacy,
    component: () => import('./components/welcome.vue')
  },
  {
    titleKey: 'page.oobe.steps.setToken.title',
    descriptionKey: 'page.oobe.steps.setToken.description',
    canNext: (config) => config.token.length > 0,
    component: () => import('./components/setToken.vue')
  },
  {
    titleKey: 'page.oobe.btnConfig.title',
    descriptionKey: 'page.oobe.btnConfig.briefDescription',
    component: () => import('./components/btn.vue')
  },
  {
    titleKey: 'page.oobe.steps.addDownloader.title',
    canNext: (config) => config.valid,
    component: () => import('./components/addDownloader.vue')
  },
  {
    titleKey: 'page.oobe.steps.success.title',
    descriptionKey: 'page.oobe.steps.success.description',
    canNext: () => false,
    component: () => import('./components/result.vue')
  }
]
