<template>
  <a-space direction="vertical" fill>
    <a-typography-text style="font-size: 1.2em">
      {{ t('page.rule_management.ruleSubscribe.description') }}
    </a-typography-text>
    <subscribeList />
  </a-space>
</template>
<script lang="ts" setup>
import subscribeList from './ruleList.vue'
import { useI18n } from 'vue-i18n'
const { t } = useI18n()
</script>
