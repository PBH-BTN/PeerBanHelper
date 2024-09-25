<template>
  <a
    style="text-decoration: none; cursor: pointer;"
    :style=" style "
    :href="`ipHistory?ip=${ip}`"
    :title="t('queryLink.title')"
  >
  <slot></slot>
  </a>
</template>
<script lang="ts" setup>
import type { StyleValue } from 'vue';
import { useI18n } from 'vue-i18n';
const {t} = useI18n()
const { ip } = defineProps<{
  ip: string
  style?: StyleValue
}>()
</script>
