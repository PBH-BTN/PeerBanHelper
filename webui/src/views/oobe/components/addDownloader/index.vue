<template>
  <a-space direction="vertical" style="width: 70%">
    <a-typography style="text-align: left">
      <a-typography-title style="margin-top: 0">
        {{ t('page.oobe.addDownloader.title') }}
      </a-typography-title>
      <a-typography-paragraph>
        {{ t('page.oobe.addDownloader.description') }}
      </a-typography-paragraph>
    </a-typography>
    <a-form :model="config.downloaderConfig" auto-label-width>
      <a-row :gutter="16" style="display: flex; justify-content: space-between">
        <a-col :span="8">
          <a-form-item
            field="config.type"
            :label="t('page.dashboard.editModal.label.type')"
            required
          >
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
              <a-option :value="ClientTypeEnum.Transmission">Transmission</a-option>
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
        </a-col>
        <a-col :span="8">
          <a-popconfirm
            :content="t('page.oobe.addDownloader.scan.tooltip')"
            @before-ok="handleScanDownloader"
          >
            <a-button type="outline">
              <template #icon>
                <icon-search />
              </template>
              {{ t('page.oobe.addDownloader.scan') }}
            </a-button>
          </a-popconfirm>
        </a-col>
      </a-row>
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
import { SacnDownloader, TestDownloaderConfig } from '@/service/init'
import { Message, Modal } from '@arco-design/web-vue'
import { v1 as uuid } from 'uuid'
import { defineAsyncComponent, h, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import scanDownloaderModal from './scanDownloaderModal.vue'

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
      id: uuid(),
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
const handleScanDownloader = async () => {
  try {
    const downloader = await SacnDownloader()
    if (!downloader.data || downloader.data.length === 0) {
      Message.warning({
        content: t('page.oobe.addDownloader.scan.noDownloader'),
        resetOnHover: true
      })
      return true
    }
    if (downloader.data.length === 1) {
      const item = downloader.data[0]!
      config.value.downloaderConfig.config.type = item.type
      config.value.downloaderConfig.config.endpoint = `http://${item.host}:${item.port}`
      Message.success({ content: t('page.oobe.addDownloader.scan.one'), resetOnHover: true })
      return true
    }
    const { close } = Modal.info({
      title: t('page.oobe.addDownloader.scan.multi'),
      width: '60vh',
      content: () =>
        h(scanDownloaderModal, {
          downloaders: downloader.data,
          onSelect: (item) => {
            config.value.downloaderConfig.config.type = item.type
            config.value.downloaderConfig.config.endpoint = `http://${item.host}:${item.port}`
            close()
          }
        }),
      okText: t('page.oobe.addDownloader.scan.cancel')
    })
  } catch (e: unknown) {
    if (e instanceof Error) Message.error({ content: e.message, resetOnHover: true })
  }
  return true
}
</script>
