<template>
  <a-modal
    v-model:visible="showModal"
    :mask-closable="false"
    :title="
      newItem ? t('page.dashboard.editModal.title.new') : t('page.dashboard.editModal.title.edit')
    "
    unmount-on-close
    @cancel="() => resetFields()"
    @before-ok="handleBeforeOk"
  >
    <a-form ref="formRef" :model="form" auto-label-width>
      <a-form-item field="config.type" :label="t('page.dashboard.editModal.label.type')" required>
        <a-select
          v-model="form.config.type"
          style="width: 11em"
          :trigger-props="{ autoFitPopupMinWidth: true }"
        >
          <a-option :value="ClientTypeEnum.qBittorrent">qBittorrent</a-option>
          <a-option :value="ClientTypeEnum.qBittorrentEE">qBittorrentEE</a-option>
          <a-option :value="ClientTypeEnum.BiglyBT">BiglyBT</a-option>
          <a-option :value="ClientTypeEnum.Deluge">Deluge</a-option>
          <a-option :value="ClientTypeEnum.BitComet">BitComet</a-option>
          <!--          <a-tooltip :content="t('page.dashboard.editModal.transmission.discourage')">-->
          <a-option :value="ClientTypeEnum.Transmission">Transmission</a-option>
          <!--          </a-tooltip>-->
        </a-select>
        <template v-if="form.config.type === ClientTypeEnum.BiglyBT" #extra>
          <i18n-t keypath="page.dashboard.editModal.biglybt">
            <template #url>
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
      <a-form-item
        field="config.paused"
        :label="t('page.dashboard.editModal.label.paused')"
        required
      >
        <a-switch v-model="form.config.paused" checked-color="orange" />
      </a-form-item>
      <component :is="formMap[form.config.type]" v-model="form.config" />
    </a-form>
  </a-modal>
</template>
<script setup lang="ts">
import { ClientTypeEnum, type downloaderConfig } from '@/api/model/downloader'
import { CreateDownloader, TestDownloaderConfig, UpdateDownloader } from '@/service/downloaders'
import { type Form, Message } from '@arco-design/web-vue'
import { type Component, defineAsyncComponent, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'

const qbittorrentForm = defineAsyncComponent(() => import('@/components/forms/qbittorrent.vue'))
const qbittorrentEEForm = defineAsyncComponent(() => import('@/components/forms/qbittorrentee.vue'))
const transmissionForm = defineAsyncComponent(() => import('@/components/forms/transmission.vue'))
const biglybtForm = defineAsyncComponent(() => import('@/components/forms/biglybt.vue'))
const delugeForm = defineAsyncComponent(() => import('@/components/forms/deluge.vue'))
const bitCometForm = defineAsyncComponent(() => import('@/components/forms/bitcomet.vue'))

const { t } = useI18n()
const showModal = ref(false)
const newItem = ref(false)

const formMap: Record<ClientTypeEnum, Component> = {
  [ClientTypeEnum.qBittorrent]: qbittorrentForm,
  [ClientTypeEnum.qBittorrentEE]: qbittorrentEEForm,
  [ClientTypeEnum.Transmission]: transmissionForm,
  [ClientTypeEnum.BiglyBT]: biglybtForm,
  [ClientTypeEnum.Deluge]: delugeForm,
  [ClientTypeEnum.BitComet]: bitCometForm
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
        incrementBan: true,
        paused: false
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
      Message.success({ content: result.message, resetOnHover: true })
      emits('changed')
      return true
    } else {
      throw new Error(result.message)
    }
  } catch (e: unknown) {
    if (e instanceof Error) Message.error({ content: e.message, resetOnHover: true })
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
