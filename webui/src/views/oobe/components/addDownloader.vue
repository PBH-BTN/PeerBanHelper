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
        <a-select
          v-model="config.downloaderConfig.config.type"
          style="width: 10em"
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
        <template v-if="config.downloaderConfig.config.type === ClientTypeEnum.BiglyBT" #extra>
          <i18n-t keypath="page.dashboard.editModal.biglybt">
            <template #url>
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
      <component
        :is="formMap[config.downloaderConfig.config.type] as any"
        v-model="config.downloaderConfig.config"
      />
      <a-form-item v-if="config.downloaderConfig.config.type">
        <a-button :loading="testing" @click="handleTest">{{
          t('page.oobe.addDownloader.test')
        }}</a-button>
      </a-form-item>
    </a-form>
  </a-space>
</template>
<script lang="ts" setup>
import { ClientTypeEnum } from '@/api/model/downloader'
import type { InitConfig } from '@/api/model/oobe'
import { TestDownloaderConfig } from '@/service/init'
import { Message } from '@arco-design/web-vue'
import { defineAsyncComponent, ref } from 'vue'
import { useI18n } from 'vue-i18n'

const qbittorrentForm = defineAsyncComponent(() => import('@/components/forms/qbittorrent.vue'))
const qbittorrentEEForm = defineAsyncComponent(() => import('@/components/forms/qbittorrentee.vue'))
const transmissionForm = defineAsyncComponent(() => import('@/components/forms/transmission.vue'))
const biglybtForm = defineAsyncComponent(() => import('@/components/forms/biglybt.vue'))
const delugeForm = defineAsyncComponent(() => import('@/components/forms/deluge.vue'))
const bitCometForm = defineAsyncComponent(() => import('@/components/forms/bitcomet.vue'))

const formMap = {
  [ClientTypeEnum.qBittorrent]: qbittorrentForm,
  [ClientTypeEnum.qBittorrentEE]: qbittorrentEEForm,
  [ClientTypeEnum.Transmission]: transmissionForm,
  [ClientTypeEnum.BiglyBT]: biglybtForm,
  [ClientTypeEnum.Deluge]: delugeForm,
  [ClientTypeEnum.BitComet]: bitCometForm
}
const { t } = useI18n()
const config = defineModel<InitConfig>({ required: true })

const testing = ref(false)
const handleTest = async () => {
  testing.value = true
  config.value.downloaderConfig.config.endpoint.replace(/\/$/, '')
  try {
    const testResult = await TestDownloaderConfig({
      name: config.value.downloaderConfig.name,
      config: config.value.downloaderConfig.config
    })
    if (!testResult.success) throw new Error(testResult.message)
  } catch (e: unknown) {
    if (e instanceof Error) Message.error({ content: e.message, resetOnHover: true })
    return false
  } finally {
    testing.value = false
  }
  Message.success({ content: t('page.oobe.addDownloader.test.success'), resetOnHover: true })
  config.value.valid = true
}
</script>
