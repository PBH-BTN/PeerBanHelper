<template>
  <a-space direction="vertical" fill>
    <a-typography-text style="font-size: 1.2em">
      {{ t('page.rule_management.enhancedRuleSubscribe.description') }}
    </a-typography-text>
    <enhancedSubscribeList />
  </a-space>
</template>
<script lang="ts" setup>
import enhancedSubscribeList from './ruleList.vue'
import { useI18n } from 'vue-i18n'
const { t } = useI18n()
</script>