<template>
  <a-space direction="vertical" style="width: 100%">
    <a-list
      :virtual-list-props="{
        height: 300
      }"
      :data="downloaders"
    >
      <template #item="{ item, index }">
        <a-list-item :key="index">
          <a-list-item-meta :title="`${item.host}:${item.port}`">
            <template #avatar>
              <a-avatar shape="square">
                {{ item.type[0].toUpperCase() }}
              </a-avatar>
            </template>
            <template #description>
              <a-descriptions>
                <a-descriptions-item :label="t('page.oobe.addDownloader.scan.type')"
                  ><a-tag :color="getColor(item.type)">{{ item.type }}</a-tag></a-descriptions-item
                >
                <a-descriptions-item label="PID">{{ item.pid }}</a-descriptions-item>
              </a-descriptions>
            </template>
          </a-list-item-meta>
          <template #actions>
            <a-button type="primary" @click="$emit('select', item)">{{
              t('page.oobe.addDownloader.scan.select')
            }}</a-button>
          </template>
        </a-list-item>
      </template>
    </a-list>
  </a-space>
</template>
<script setup lang="ts">
import type { ScanDownloaderInfo } from '@/api/model/downloader'
import { getColor } from '@/utils/color'
import { useI18n } from 'vue-i18n'
const { t } = useI18n()
defineEmits<{
  (e: 'select', item: ScanDownloaderInfo): void
}>()
const { downloaders } = defineProps<{
  downloaders: ScanDownloaderInfo[]
}>()
</script>
