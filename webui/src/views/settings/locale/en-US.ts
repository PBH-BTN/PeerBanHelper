import ConfigLocale from '../components/config/locale/en-US'
import InfoLocale from '../components/info/locale/en-US'
import LabsLocale from '../components/labs/locale/en-US'
import ProfileLocale from '../components/profile/locale/en-US'
export default {
  'page.settings.tab.config': 'Settings',
  'page.settings.tab.profile': 'Profile',
  ...ProfileLocale,
  ...ConfigLocale,
  ...InfoLocale,
  ...LabsLocale
}
