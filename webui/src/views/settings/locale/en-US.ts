import ConfigLocale from '../components/config/locale/en-US'
import InfoLocale from '../components/info/locale/en-US'
import ProfileLocale from '../components/profile/locale/en-US'
export default {
  'page.settings.tab.config': 'Settings',
  'page.settings.tab.profile': 'Profile',
  'page.settings.tab.script': 'Custom Script',
  'page.settings.tab.script.disable': 'Temporarily disabled for security reasons temporarily',
  ...ProfileLocale,
  ...ConfigLocale,
  ...InfoLocale
}
