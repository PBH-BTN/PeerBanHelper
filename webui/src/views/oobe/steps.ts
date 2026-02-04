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
    canNext: (config) => config.downloaderValid,
    component: () => import('./components/addDownloader/index.vue')
  },
  {
    titleKey: 'page.oobe.advance.title',
    canNext: (config) => {
      if (config.database.type === 'mysql' || config.database.type === 'postgresql') {
        return config.databaseValid
      }
      return true
    },
    hidden: true,
    component: () => import('./components/advance.vue')
  },
  {
    titleKey: 'page.oobe.steps.success.title',
    descriptionKey: 'page.oobe.steps.success.description',
    canNext: () => false,
    component: () => import('./components/result.vue')
  }
]
