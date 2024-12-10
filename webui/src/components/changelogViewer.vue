<template>
  <a-modal
    :visible="showModal"
    width="auto"
    mask-closable
    hide-cancel
    :title="t('changeLogModel.title', [endpointStore.latestVersion?.tagName])"
    @cancel="showModal = false"
  >
    <a-space direction="vertical" size="mini" style="padding-left: 20px; padding-right: 20px">
      <a-typography-title :heading="4">{{ t('changeLogModel.changelog') }} </a-typography-title>
      <iframe class="changelog" :srcdoc="md.render(endpointStore.latestVersion?.changeLog ?? '')" />
    </a-space>
    <template #footer>
      <a-space size="large">
        <a-button @click="showModal = false">{{ t('changeLogModel.notNow') }}</a-button>
        <a-button :href="endpointStore.latestVersion?.url" type="primary" target="_blank">{{
          t('changeLogModel.updateNow')
        }}</a-button>
      </a-space>
    </template>
  </a-modal>
</template>
<script setup lang="ts">
import { useEndpointStore } from '@/stores/endpoint'
import md from '@/utils/markdown'
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
const endpointStore = useEndpointStore()

const showModal = ref(false)

const { t } = useI18n()

defineExpose({
  showModal: () => {
    showModal.value = true
  }
})
</script>
<style lang="css" scoped>
.changelog {
  border: none;
  width: 80vh;
  height: 50vh;
}
</style>
