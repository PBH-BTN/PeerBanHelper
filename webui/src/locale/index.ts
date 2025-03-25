import { createI18n } from 'vue-i18n'
import zhCN from '@arco-design/web-vue/es/locale/lang/zh-cn'
import zhTW from '@arco-design/web-vue/es/locale/lang/zh-tw'
import enUS from '@arco-design/web-vue/es/locale/lang/en-us'
import en from './en-US'
import cn from './zh-CN'
import tw from './zh-TW'
import type { ArcoLang } from '@arco-design/web-vue/es/locale/interface'
import dayjs from 'dayjs'

export const LOCALE_OPTIONS = [
  { label: '简体中文 (zh-CN)', value: 'zh-CN' },
  { label: '繁體中文 (zh-TW)', value: 'zh-TW' },
  { label: 'English (en-US)', value: 'en-US' }
]

export function getNavigatorLanguage() {
  const lang = navigator.language
  if (lang.includes('zh')) {
    if (lang.includes('zh-CN')) {
      return 'zh-CN'
    }
    if (lang.includes('zh-TW')) {
      return 'zh-TW'
    }
    if (lang.includes('zh-HK')) {
      // 繁体中文（香港回退）
      return 'zh-TW'
    }
    return 'zh-CN' // 简体中文（默认）
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
  },
  log: {
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
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
    'zh-CN': cn,
    'zh-TW': tw
  },
  datetimeFormats: {
    'en-US': datetimeFormat,
    'zh-CN': datetimeFormat,
    'zh-TW': datetimeFormat
  }
})
dayjs.locale(getNavigatorLanguage())

export const ArcoI18nMessages = {
  'en-US': enUS,
  'zh-CN': zhCN,
  'zh-TW': zhTW
} as Record<string, ArcoLang>

export default i18n
