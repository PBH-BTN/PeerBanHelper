<template>
  <a-space direction="vertical" style="width: 70%">
    <a-typography style="text-align: left">
      <a-typography-title>
        {{ t('page.oobe.addDownloader.title') }}
      </a-typography-title>
      <a-typography-paragraph>
        {{ t('page.oobe.addDownloader.description') }}
      </a-typography-paragraph>
    </a-typography>
    <a-form :model="config.downloaderConfig" auto-label-width>
      <a-form-item field="config.type" :label="t('page.dashboard.editModal.label.type')" required>
        <a-radio-group v-model="config.downloaderConfig.config.type" type="button">
          <a-radio :value="ClientTypeEnum.qBittorrent">qBittorrent</a-radio>
          <a-radio :value="ClientTypeEnum.Transmission">Transmission</a-radio>
          <a-radio :value="ClientTypeEnum.BiglyBT">BiglyBT</a-radio>
          <a-radio :value="ClientTypeEnum.Deluge">Deluge</a-radio>
        </a-radio-group>
        <template #extra v-if="config.downloaderConfig.config.type === ClientTypeEnum.BiglyBT">
          <i18n-t keypath="page.dashboard.editModal.biglybt">
            <template v-slot:url>
              <a href="https://github.com/PBH-BTN/PBH-Adapter-BiglyBT">{{
                t('page.dashboard.editModal.biglybt.url')
              }}</a>
            </template>
          </i18n-t>
        </template>
      </a-form-item>
      <a-form-item field="name" :label="t('page.dashboard.editModal.label.name')" required>
        <a-input v-model="config.downloaderConfig.name" allow-clear />
      </a-form-item>
      <component :is="formMap[config.downloaderConfig.config.type] as any" v-model="config.downloaderConfig.config" />
      <a-form-item v-if="config.downloaderConfig.config.type">
        <a-button :loading="testing" @click="handleTest">{{
          t('page.oobe.addDownloader.test')
        }}</a-button>
      </a-form-item>
    </a-form>
  </a-space>
</template>
<script lang="ts" setup>
import type { InitConfig } from '@/api/model/oobe'
import { ClientTypeEnum } from '@/api/model/downloader'
import { useI18n } from 'vue-i18n'
import { defineAsyncComponent, ref } from 'vue'
import { TestDownloaderConfig } from '@/service/init'
import { Message } from '@arco-design/web-vue'
const qbittorrentForm = defineAsyncComponent(() => import('@/components/forms/qbittorrent.vue'))
const transmissionForm = defineAsyncComponent(() => import('@/components/forms/transmission.vue'))
const biglybtForm = defineAsyncComponent(() => import('@/components/forms/biglybt.vue'))
const delugeForm = defineAsyncComponent(() => import('@/components/forms/deluge.vue'))

const formMap = {
  [ClientTypeEnum.qBittorrent]: qbittorrentForm,
  [ClientTypeEnum.Transmission]: transmissionForm,
  [ClientTypeEnum.BiglyBT]: biglybtForm,
  [ClientTypeEnum.Deluge]: delugeForm
}
const { t } = useI18n()
const config = defineModel<InitConfig>({ required: true })

const testing = ref(false)
const handleTest = async () => {
  testing.value = true
  try {
    const testResult = await TestDownloaderConfig({
      name: config.value.downloaderConfig.name,
      config: config.value.downloaderConfig.config
    })
    if (!testResult.success) throw new Error(testResult.message)
  } catch (e: any) {
    Message.error(e.message)
    return false
  } finally {
    testing.value = false
  }
  Message.success(t('page.oobe.addDownloader.test.success'))
  config.value.valid = true
}
</script>
