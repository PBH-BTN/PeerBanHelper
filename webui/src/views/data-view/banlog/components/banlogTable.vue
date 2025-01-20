<template>
  <a-table
    :stripe="true"
    :columns="columns"
    :data="list"
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
    <template #peerAddress="{ record }">
      <a-typography-text copyable code>
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
import queryIpLink from '@/components/queryIpLink.vue'
import { getBanlogs } from '@/service/banLogs'
import { useAutoUpdatePlugin } from '@/stores/autoUpdate'
import { useEndpointStore } from '@/stores/endpoint'
import { formatFileSize } from '@/utils/file'
import { computed, ref, watch } from 'vue'
import { formatIPAddressPort } from '@/utils/string'
import { useI18n } from 'vue-i18n'
import { usePagination } from 'vue-request'
const forceLoading = ref(true)
const endpointState = useEndpointStore()
const { t, d } = useI18n()
const { data, total, current, loading, pageSize, changeCurrent, changePageSize, refresh } =
  usePagination(
    getBanlogs,
    {
      defaultParams: [
        {
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
        `${endpointState.endpoint}-banlogs-${params?.[0].page || 1}-${params?.[0].pageSize || 10}`,
      onAfter: () => {
        forceLoading.value = false
      }
    },
    [useAutoUpdatePlugin]
  )

watch([pageSize, current], () => {
  forceLoading.value = true
})

watch(() => endpointState.endpoint, refresh)

const tableLoading = computed(() => {
  return forceLoading.value || loading.value || !list.value
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
    width: 230
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
