import ConfigLocale from '../components/config/locale/zh-CN'
import InfoLocale from '../components/info/locale/zh-CN'
import ProfileLocale from '../components/profile/locale/zh-CN'
import ScriptLocal from '../components/script/locale/zh-CN'
export default {
  'page.settings.tab.config': '基础设置',
  'page.settings.tab.profile': '首选项',
  'page.settings.tab.script': '自定义脚本',
  ...ProfileLocale,
  ...ConfigLocale,
  ...InfoLocale,
  ...ScriptLocal
}
