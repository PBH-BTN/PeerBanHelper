<!--copied from https://github.com/noeGnh/vue3-country-flag-icon/blob/master/src/components/CountryFlag/Index.vue-->
<template>
  <span
    :class="flagIconClass"
    :title="props.title || props.iso"
    class="flag-icon"
    v-if="props.iso !== ''"
  />
</template>

<script lang="ts" setup>
import '@dzangolab/flag-icon-css/less/flag-icon.less'
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
const { locale } = useI18n()

const props = defineProps<{
  iso: string
  title?: string
  mode?: 'rounded' | 'squared'
}>()

const flagIconClass = computed(() => {
  let cls = ''
  let iso = props.iso.toLowerCase()
  if (locale.value === 'zh-CN' && iso === 'tw') {
    iso = 'cn' // ROC flags are not allowed in mainland China
  }
  cls = 'flag-icon-' + iso

  if (props.mode) {
    cls += ' flag-icon-' + props.mode
  }

  return cls
})
</script>
