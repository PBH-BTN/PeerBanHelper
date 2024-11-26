<template>
  <a-table
    v-if="data?.data.results || tableLoading"
    :stripe="true"
    :columns="columns"
    :data="data?.data.results"
    :loading="tableLoading"
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
    :bordered="false"
    class="banlog-table"
    @page-change="changeCurrent"
    @page-size-change="changePageSize"
  >
    <template #downloader="{ record }">
      <a-tag :color="getColor(record.downloader)">{{ record.downloader }}</a-tag>
    </template>
    <template #peerId="{ record }">
      <p>
        {{ record.peerId ? record.peerId : t('page.banlist.banlist.listItem.empty') }}
        <a-tooltip
          :content="
            record.clientName ? record.clientName : t('page.banlist.banlist.listItem.empty')
          "
        >
          <icon-info-circle />
        </a-tooltip>
      </p>
    </template>
    <template #traffic="{ record }">
      <a-space fill direction="vertical">
        <a-typography-text
          ><icon-arrow-up class="green" /> {{ formatFileSize(record.uploaded) }}</a-typography-text
        >
        <a-typography-text
          ><icon-arrow-down class="red" />
          {{ formatFileSize(record.downloaded) }}</a-typography-text
        >
      </a-space>
    </template>
    <template #offset="{ record }">
      <a-space fill direction="vertical">
        <a-typography-text
          ><icon-arrow-up class="green" />
          {{ formatFileSize(record.uploadedOffset) }}</a-typography-text
        >
        <a-typography-text
          ><icon-arrow-down class="red" />
          {{ formatFileSize(record.downloadedOffset) }}</a-typography-text
        >
      </a-space>
    </template>
    <template #offsetTitle>
      <a-space size="mini">
        {{ t('page.torrentList.accessHistory.column.offset') }}
        <a-popover :content="t('page.torrentList.accessHistory.column.offsetDescription')">
          <icon-info-circle />
        </a-popover>
      </a-space>
    </template>
    <template #flags="{ record }">
      <p>
        {{ record.lastFlags }}
        <a-tooltip v-if="record.lastFlags">
          <template #content>
            <p v-for="description in parseFlags(record.lastFlags)" :key="description">
              {{ description }}
            </p>
          </template>
          <icon-info-circle />
        </a-tooltip>
      </p>
    </template>
    <template #time="{ record }">
      <a-space fill direction="vertical">
        <a-typography-text>
          {{ t('page.torrentList.accessHistory.column.timeseen.first') }}:
          {{ d(record.firstTimeSeen, 'long') }}</a-typography-text
        >
        <a-typography-text>
          {{ t('page.torrentList.accessHistory.column.timeseen.last') }}:
          {{ d(record.lastTimeSeen, 'long') }}</a-typography-text
        >
      </a-space>
    </template>
  </a-table>
  <a-empty
    v-else
    style="
      height: 20vh;
      align-items: center;
      display: flex;
      justify-content: center;
      flex-direction: column;
    "
  >
    {{ t('page.torrentList.accessHistory.empty') }}
  </a-empty>
</template>
<script lang="ts" setup>
import { GetIPAccessHistoryList } from '@/service/data'
import { useEndpointStore } from '@/stores/endpoint'
import { getColor } from '@/utils/color'
import { formatFileSize } from '@/utils/file'
import { IconInfoCircle } from '@arco-design/web-vue/es/icon'
import { useI18n } from 'vue-i18n'
import { usePagination } from 'vue-request'
const { t, d } = useI18n()
const endpointState = useEndpointStore()

const { ip } = defineProps<{
  ip: string
}>()
const {
  data,
  total,
  current,
  loading: tableLoading,
  pageSize,
  changeCurrent,
  changePageSize
} = usePagination(GetIPAccessHistoryList, {
  defaultParams: [
    {
      ip: ip,
      page: 1,
      pageSize: 5
    }
  ],
  pagination: {
    currentKey: 'page',
    pageSizeKey: 'pageSize',
    totalKey: 'data.total'
  },
  cacheKey: (params) =>
    `${endpointState.endpoint}-ipAccessHistory-${params?.[0].ip}-${params?.[0].page || 1}-${params?.[0].pageSize || 10}`
})

const columns = [
  {
    title: () => t('page.torrentList.accessHistory.column.downloader'),
    slotName: 'downloader'
  },
  {
    title: 'Peer ID',
    slotName: 'peerId'
  },
  {
    title: () => t('page.torrentList.accessHistory.column.traffic'),
    slotName: 'traffic',
    width: 120
  },
  {
    titleSlotName: 'offsetTitle',
    slotName: 'offset',
    width: 120
  },
  {
    title: () => t('page.dashboard.peerList.column.flag'),
    slotName: 'flags',
    width: 120
  },
  {
    title: () => t('page.torrentList.accessHistory.column.timeseen'),
    slotName: 'time',
    width: 260
  },
  {
    title: () => t('page.ipList.accessHistory.column.torrent'),
    dataIndex: 'torrent.name',
    ellipsis: true,
    tooltip: true
  }
]
const parseFlags = (flags: string) =>
  flags
    .split(' ')
    .map((flag) => flag + ' - ' + t('page.dashboard.peerList.column.flags.' + flag.trim()))
</script>
<style scoped>
.red {
  color: rgb(var(--red-5));
}
.green {
  color: rgb(var(--green-5));
}
</style>
