<template>
  <a-space direction="vertical" size="large">
    <a-alert> {{ t('page.topban.usage') }}</a-alert>
    <a-typography-text style="font-size: 1.2em">
      {{ t('page.topban.description') }}
    </a-typography-text>
    <rankTable />
  </a-space>
</template>

<script setup lang="ts">
import rankTable from './components/rankTable.vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
</script>
