<template>
  <a-modal
    :visible="showModal"
    width="auto"
    mask-closable
    hide-cancel
    :title="t('changeLogModel.title', [version])"
    @ok="showModal = false"
    @cancel="showModal = false"
  >
    <a-space direction="vertical" size="mini" style="padding-left: 20px; padding-right: 20px">
      <a-typography-title :heading="4">{{ t('changeLogModel.changelog') }} </a-typography-title>
      <iframe class="changelog" :srcdoc="md.render(changeLogMd)" />
    </a-space>
  </a-modal>
</template>
<script setup lang="ts">
import md from '@/utils/markdown'
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'

const changeLogMd = ref('')
const showModal = ref(false)

const version = ref('')
const { t } = useI18n()

defineExpose({
  showModal: (changeLog: string, newVersion: string) => {
    showModal.value = true
    changeLogMd.value = changeLog
    version.value = newVersion
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
