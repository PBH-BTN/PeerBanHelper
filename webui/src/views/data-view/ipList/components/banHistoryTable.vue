<template>
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
    :bordered="false"
    column-resizable
    size="medium"
    class="banlog-table"
    @page-change="changeCurrent"
    @page-size-change="changePageSize"
  >
    <template #banAt="{ record }">
      <a-space fill direction="vertical">
        <a-typography-text><icon-stop /> {{ d(record.banAt, 'long') }}</a-typography-text>
        <a-typography-text
          ><icon-clock-circle />
          {{
            record.unbanAt ? d(record.unbanAt, 'long') : t('page.banlog.banlogTable.notUnbanned')
          }}</a-typography-text
        >
      </a-space>
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
            record.peerClientName ? record.peerClientName : t('page.banlist.banlist.listItem.empty')
          "
        >
          <icon-info-circle />
        </a-tooltip>
      </p>
    </template>
    <template #torrentSize="{ record }">
      <a-tooltip :content="`Hash: ${record.torrentInfoHash}`">
        <p>{{ formatFileSize(record.torrentSize) }}</p>
      </a-tooltip>
    </template>
  </a-table>
</template>
<script setup lang="ts">
import { GetIPBanHistoryList } from '@/service/data'
import { useEndpointStore } from '@/stores/endpoint'
import { formatFileSize } from '@/utils/file'
import { computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { usePagination } from 'vue-request'

const endpointState = useEndpointStore()
const { t, d } = useI18n()

const { ip } = defineProps<{
  ip: string
}>()
const { data, total, current, loading, pageSize, changeCurrent, changePageSize, refresh } =
  usePagination(GetIPBanHistoryList, {
    defaultParams: [
      {
        ip: ip,
        page: 1,
        pageSize: 10
      }
    ],
    pagination: {
      currentKey: 'page',
      pageSizeKey: 'pageSize',
      totalKey: 'data.total'
    },
    cacheKey: (params) =>
      `${endpointState.endpoint}-banlogs-${params?.[0].page || 1}-${params?.[0].pageSize || 10}`
  })

watch(() => endpointState.endpoint, refresh)

const columns = [
  {
    title: () =>
      t('page.banlog.banlogTable.column.banTime') +
      '/' +
      t('page.banlog.banlogTable.column.unbanTime'),
    slotName: 'banAt',
    width: 220
  },
  {
    title: () => t('page.banlog.banlogTable.column.peerPort'),
    dataIndex: 'peerPort',
    width: 80
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
    title: () => t('page.banlog.banlogTable.column.torrentName'),
    dataIndex: 'torrentName',
    ellipsis: true,
    tooltip: true
  },
  {
    title: () => t('page.banlog.banlogTable.column.torrentSize'),
    slotName: 'torrentSize',
    width: 120
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
