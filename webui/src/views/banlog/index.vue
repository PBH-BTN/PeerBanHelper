<template>
  <a-space direction="vertical" fill>
    <a-typography-text style="font-size: 1.2em">
      {{ t('page.banlog.description') }}
    </a-typography-text>
    <banlogTable />
  </a-space>
</template>

<style scoped>
.align-right {
  display: flex;
  justify-content: flex-end;
}
</style>

<script setup lang="ts">
import banlogTable from './components/banlogTable.vue'
import { useI18n } from 'vue-i18n'
const { t } = useI18n()
</script>
