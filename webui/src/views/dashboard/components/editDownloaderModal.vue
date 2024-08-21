<template>
  <a-modal
    v-model:visible="showModal"
    :mask-closable="false"
    :title="
      newItem ? t('page.dashboard.editModal.title.new') : t('page.dashboard.editModal.title.edit')
    "
    unmountOnClose
    @cancel="() => resetFields()"
    @before-ok="handleBeforeOk"
  >
    <a-form ref="formRef" :model="form" auto-label-width>
      <a-form-item field="config.type" :label="t('page.dashboard.editModal.label.type')" required>
        <a-radio-group v-model="form.config.type" type="button">
          <a-radio :value="ClientTypeEnum.qBittorrent">qBittorrent</a-radio>
          <a-radio :value="ClientTypeEnum.Transmission">Transmission</a-radio>
          <a-radio :value="ClientTypeEnum.BiglyBT">BiglyBT</a-radio>
          <a-radio :value="ClientTypeEnum.Deluge">Deluge</a-radio>
        </a-radio-group>
        <template #extra v-if="form.config.type === ClientTypeEnum.BiglyBT">
          <i18n-t keypath="page.dashboard.editModal.biglybt">
            <template v-slot:url>
              <a href="https://github.com/PBH-BTN/PBH-Adapter-BiglyBT">
                {{ t('page.dashboard.editModal.biglybt.url') }}
              </a>
            </template>
          </i18n-t>
        </template>
      </a-form-item>
      <a-form-item
        field="name"
        :label="t('page.dashboard.editModal.label.name')"
        required
        :rules="[{ match: /^[^.\t\n/]+$/ }]"
      >
        <a-input v-model="form.name" allow-clear />
      </a-form-item>
      <component :is="formMap[form.config.type] as any" v-model="form.config" />
    </a-form>
  </a-modal>
</template>
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { defineAsyncComponent, reactive, ref } from 'vue'
import { Message, type Form } from '@arco-design/web-vue'
import { ClientTypeEnum, type downloaderConfig } from '@/api/model/downloader'
import { CreateDownloader, TestDownloaderConfig, UpdateDownloader } from '@/service/downloaders'
const qbittorrentForm = defineAsyncComponent(() => import('@/components/forms/qbittorrent.vue'))
const transmissionForm = defineAsyncComponent(() => import('@/components/forms/transmission.vue'))
const biglybtForm = defineAsyncComponent(() => import('@/components/forms/biglybt.vue'))
const delugeForm = defineAsyncComponent(() => import('@/components/forms/deluge.vue'))

const { t } = useI18n()
const showModal = ref(false)
const newItem = ref(false)

const formMap = {
  [ClientTypeEnum.qBittorrent]: qbittorrentForm,
  [ClientTypeEnum.Transmission]: transmissionForm,
  [ClientTypeEnum.BiglyBT]: biglybtForm,
  [ClientTypeEnum.Deluge]: delugeForm
}

const form = reactive({
  name: '',
  config: {
    basicAuth: {},
    verifySsl: true,
    httpVersion: 'HTTP_1_1',
    incrementBan: true
  } as downloaderConfig
})
const oldName = ref('')
defineExpose({
  showModal: (isNewItem: boolean, currentConfig?: { name: string; config: downloaderConfig }) => {
    newItem.value = isNewItem
    if (!isNewItem && currentConfig) {
      form.name = currentConfig.name
      oldName.value = currentConfig.name
      form.config = currentConfig.config
    } else {
      form.name = ''
      form.config = {
        basicAuth: {},
        verifySsl: true,
        httpVersion: 'HTTP_1_1',
        incrementBan: true
      } as downloaderConfig
    }
    showModal.value = true
  }
})

const emits = defineEmits<{
  (e: 'changed'): void
}>()

const formRef = ref<InstanceType<typeof Form>>()
const handleBeforeOk = async () => {
  const validateError = await formRef.value?.validate()
  if (validateError) {
    return false
  }
  try {
    const testResult = await TestDownloaderConfig(form)
    if (!testResult.success) throw new Error(testResult.message)
    const result = newItem.value
      ? await CreateDownloader(form)
      : await UpdateDownloader(oldName.value, form)
    if (result.success) {
      Message.success(result.message)
      emits('changed')
      return true
    } else {
      throw new Error(result.message)
    }
  } catch (e: any) {
    Message.error(e.message)
    return false
  }
}
const resetFields = () => {
  formRef.value?.resetFields()
  form.config = { basicAuth: {}, verifySsl: true, httpVersion: 'HTTP_1_1' } as downloaderConfig
}
</script>
<style scoped>
a {
  text-decoration: none;
}
</style>
