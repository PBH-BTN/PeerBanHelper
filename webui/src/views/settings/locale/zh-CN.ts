import ConfigLocale from '../components/config/locale/zh-CN'
import InfoLocale from '../components/info/locale/zh-CN'
import ProfileLocale from '../components/profile/locale/zh-CN'

export default {
  'page.settings.tab.config': '基础设置',
  'page.settings.tab.profile': '首选项',
  'page.settings.tab.script': '自定义脚本',
  'page.settings.tab.script.disable': '由于安全原因暂时禁用',
  ...ProfileLocale,
  ...ConfigLocale,
  ...InfoLocale
}
