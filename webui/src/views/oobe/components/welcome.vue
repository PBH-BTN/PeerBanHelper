<template>
  <a-typography style="text-align: left">
    <a-typography-title>
      {{ t('page.oobe.welcome.title') }}
    </a-typography-title>
    <a-typography-paragraph>
      {{ t('page.oobe.welcome.description') }}
    </a-typography-paragraph>
  </a-typography>
</template>
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
const { t } = useI18n()
</script>
