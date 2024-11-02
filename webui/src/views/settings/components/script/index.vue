<template>
  <div v-if="loading" class="container">
    <a-spin :loading="loading"></a-spin>
  </div>
  <div v-else>
    <a-result v-if="!data?.data" status="404" :subtitle="t('page.settings.tab.script.disable')" />
    <scriptList v-else />
  </div>
</template>
<script setup lang="ts">
import { CheckModuleEnable } from '@/service/settings'
import { useI18n } from 'vue-i18n'
import { useRequest } from 'vue-request'
import scriptList from './components/scriptList.vue'
const { t } = useI18n()
const { data, loading } = useRequest(CheckModuleEnable, {
  defaultParams: ['expression-engine']
})
</script>
<style scoped>
.container {
  height: 100%;
  width: 100%;
  min-height: 30rem;
  display: flex;
  justify-content: center;
  align-items: center;
}
</style>
