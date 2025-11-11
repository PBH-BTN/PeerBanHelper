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
    @sorter-change="sorterChange"
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
import { useSorter } from '@/composables/useSorter'
import { GetIPBanHistoryList } from '@/service/data'
import { useEndpointStore } from '@/stores/endpoint'
import { formatFileSize } from '@/utils/file'
import { computed, toRef, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { usePagination } from 'vue-request'

const endpointState = useEndpointStore()
const { t, d } = useI18n()

// 使用可复用的排序功能
const { sorterParam, handleSorterChange } = useSorter({ multiSort: true, maxSortColumns: 3 })

const props = defineProps<{
  ip: string
}>()
const ipRef = toRef(props, 'ip')
const { data, total, current, loading, pageSize, changeCurrent, changePageSize, refresh, run } =
  usePagination(GetIPBanHistoryList, {
    defaultParams: [
      {
        ip: ipRef.value,
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
      `${endpointState.endpoint}-ipBanHistory-${params?.[0].ip}-${params?.[0].page || 1}-${params?.[0].pageSize || 10}-${params?.[0].sorter || 'default'}`
  })

watch(() => endpointState.endpoint, refresh)
watch(
  ipRef,
  (newIp) => {
    if (newIp) {
      run({ ip: newIp, page: 1, pageSize: 10 })
    }
  },
  { immediate: true }
)

const columns = [
  {
    title: () =>
      t('page.banlog.banlogTable.column.banTime') +
      '/' +
      t('page.banlog.banlogTable.column.unbanTime'),
    slotName: 'banAt',
    dataIndex: 'banAt',
    sortable: {
      sortDirections: ['ascend', 'descend'] as ('ascend' | 'descend')[],
      sorter: true
    },
    width: 220
  },
  {
    title: () => t('page.banlog.banlogTable.column.peerPort'),
    dataIndex: 'peerPort',
    sortable: {
      sortDirections: ['ascend', 'descend'] as ('ascend' | 'descend')[],
      sorter: true
    },
    width: 80
  },
  {
    title: () => t('page.banlog.banlogTable.column.peerId'),
    slotName: 'peerId',
    dataIndex: 'peerId',
    sortable: {
      sortDirections: ['ascend', 'descend'] as ('ascend' | 'descend')[],
      sorter: true
    },
    width: 120
  },
  {
    title: () => t('page.banlog.banlogTable.column.trafficSnapshot'),
    slotName: 'peerStatus',
    dataIndex: 'peerUploaded',
    sortable: {
      sortDirections: ['ascend', 'descend'] as ('ascend' | 'descend')[],
      sorter: true
    },
    width: 150
  },
  {
    title: () => t('page.banlog.banlogTable.column.torrentName'),
    dataIndex: 'torrentName',
    sortable: {
      sortDirections: ['ascend', 'descend'] as ('ascend' | 'descend')[],
      sorter: true
    },
    ellipsis: true,
    tooltip: true
  },
  {
    title: () => t('page.banlog.banlogTable.column.torrentSize'),
    slotName: 'torrentSize',
    dataIndex: 'torrentSize',
    sortable: {
      sortDirections: ['ascend', 'descend'] as ('ascend' | 'descend')[],
      sorter: true
    },
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

const sorterChange = (dataIndex: string, direction: string) => {
  handleSorterChange(dataIndex, direction as 'ascend' | 'descend' | '')
  run({ ip: ipRef.value, page: current.value, pageSize: pageSize.value, sorter: sorterParam.value })
}
</script>

<style scoped>
.red {
  color: rgb(var(--red-5));
}
.green {
  color: rgb(var(--green-5));
}
</style>
