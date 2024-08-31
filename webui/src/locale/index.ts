import { createI18n } from 'vue-i18n'
import zhCN from '@arco-design/web-vue/es/locale/lang/zh-cn'
import enUS from '@arco-design/web-vue/es/locale/lang/en-us'
import en from './en-US'
import cn from './zh-CN'
import type { ArcoLang } from '@arco-design/web-vue/es/locale/interface'

export const LOCALE_OPTIONS = [
  { label: '中文', value: 'zh-CN' },
  { label: 'English', value: 'en-US' }
]

export function getNavigatorLanguage() {
  const lang = navigator.language
  if (lang.includes('zh')) {
    return 'zh-CN'
  }
  return 'en-US'
}

const datetimeFormat = {
  hour: {
    month: 'short',
    day: 'numeric',
    hour: 'numeric'
  },
  short: {
    year: 'numeric',
    month: 'short',
    day: 'numeric'
  },
  day: {
    year: 'numeric',
    month: 'short',
    day: 'numeric'
  },
  long: {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
    minute: 'numeric'
  },
  longlong: {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
    minute: 'numeric',
    second: 'numeric'
  },
  // 用于秒级倒计时的显示
  'short-second': {
    minute: 'numeric',
    second: 'numeric'
  }
} as const

const i18n = createI18n({
  locale: getNavigatorLanguage(),
  fallbackLocale: 'en-US',
  legacy: false,
  messages: {
    'en-US': en,
    'zh-CN': cn
  },
  datetimeFormats: {
    'en-US': datetimeFormat,
    'zh-CN': datetimeFormat
  }
})

export const ArcoI18nMessages = {
  'en-US': enUS,
  'zh-CN': zhCN
} as Record<string, ArcoLang>

export default i18n
