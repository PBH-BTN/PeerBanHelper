<template>
  <a-modal v-model:visible="visible" hide-cancel closable unmount-on-close width="auto">
    <template #title>
      <i18n-t keypath="page.torrentList.banHistory.title">
        <template #name>
          {{ name }}
        </template>
      </i18n-t>
    </template>
    <a-space direction="vertical" fill style="max-width: 1400px">
      <a-typography-text style="font-size: 1.2em">
        {{ t('page.torrentList.banHistory.description') }}
      </a-typography-text>
      <a-table
        :stripe="true"
        :columns="columns"
        :data="list"
        :loading="loading"
        :pagination="{
          total,
          current,
          pageSize,
          showPageSize: true,
          baseSize: 4,
          bufferSize: 1
        }"
        column-resizable
        size="medium"
        @page-change="changeCurrent"
        @page-size-change="changePageSize"
      >
        <template #banAt="{ record }">
          <a-space fill direction="vertical">
            <a-typography-text><icon-stop /> {{ d(record.banAt, 'long') }}</a-typography-text>
            <a-typography-text
              ><icon-clock-circle />
              {{
                record.unbanAt
                  ? d(record.unbanAt, 'long')
                  : t('page.banlog.banlogTable.notUnbanned')
              }}</a-typography-text
            >
          </a-space>
        </template>
        <template #peerAddress="{ record }">
          <a-typography-text code>
            <queryIpLink :ip="record.peerIp" style="color: var(--color-text-2)">
              {{ formatIPAddressPort(record.peerIp, record.peerPort) }}
            </queryIpLink>
          </a-typography-text>
        </template>
        <template #peerStatus="{ record }">
          <a-space fill style="justify-content: space-between">
            <a-space fill direction="vertical">
              <a-typography-text
                ><icon-arrow-up class="green" />
                {{ formatFileSize(record.peerUploaded) }}</a-typography-text
              >
              <a-typography-text
                ><icon-arrow-down class="red" />
                {{ formatFileSize(record.peerDownloaded) }}</a-typography-text
              >
            </a-space>
            <a-tooltip :content="(record.peerProgress * 100).toFixed(2) + '%'">
              <a-progress :percent="record.peerProgress" size="mini" />
            </a-tooltip>
          </a-space>
        </template>
        <template #peerId="{ record }">
          <p>
            {{ record.peerId ? record.peerId : t('page.banlist.banlist.listItem.empty') }}
            <a-tooltip
              :content="
                record.peerClientName
                  ? record.peerClientName
                  : t('page.banlist.banlist.listItem.empty')
              "
            >
              <icon-info-circle />
            </a-tooltip>
          </p>
        </template>
      </a-table>
    </a-space>
  </a-modal>
</template>
<script setup lang="ts">
import queryIpLink from '@/components/queryIpLink.vue'
import { GetTorrentBanHistoryList } from '@/service/data'
import { useEndpointStore } from '@/stores/endpoint'
import { formatFileSize } from '@/utils/file'
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { formatIPAddressPort } from '@/utils/string'

import { usePagination } from 'vue-request'

const endpointState = useEndpointStore()
const { t, d } = useI18n()
const visible = ref(false)
const name = ref('')
defineExpose({
  showModal: (infoHash: string, torrentName: string) => {
    name.value = torrentName
    runAsync({ page: 1, pageSize: 10, infoHash })
    visible.value = true
  }
})

const { data, total, current, loading, pageSize, changeCurrent, changePageSize, runAsync } =
  usePagination(GetTorrentBanHistoryList, {
    manual: true,
    pagination: {
      currentKey: 'page',
      pageSizeKey: 'pageSize',
      totalKey: 'data.total'
    },
    cacheKey: (params) =>
      `${endpointState.endpoint}-torrentBanHistory-${params?.[0].infoHash}-${params?.[0].page || 1}-${params?.[0].pageSize || 10}`
  })

const columns = [
  {
    title: () =>
      t('page.banlog.banlogTable.column.banTime') +
      '/' +
      t('page.banlog.banlogTable.column.unbanTime'),
    slotName: 'banAt',
    width: 210
  },
  {
    title: () => t('page.banlog.banlogTable.column.peerAddress'),
    slotName: 'peerAddress',
    width: 200
  },
  {
    title: () => t('page.banlog.banlogTable.column.peerId'),
    slotName: 'peerId',
    width: 120
  },
  {
    title: () => t('page.banlog.banlogTable.column.trafficSnapshot'),
    slotName: 'peerStatus',
    width: 150
  },
  {
    title: () => t('page.banlog.banlogTable.column.description'),
    dataIndex: 'description',
    ellipsis: true,
    tooltip: true
  }
]
const list = computed(() => data.value?.data.results)
</script>

<style scoped>
.red {
  color: rgb(var(--red-5));
}
.green {
  color: rgb(var(--green-5));
}
</style>
