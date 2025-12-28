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
    :bordered="{ cell: true }"
    style="width: 80vw"
    @sorter-change="sorterChange"
    @page-change="changeCurrent"
    @page-size-change="changePageSize"
  >
    <template #banAt="{ record }">
      <div style="display: flex; align-items: center">
        <icon-stop />
        <a-typography-text
          class="expandable-ellipsis-text"
          :ellipsis="{ showTooltip: true }"
          style="margin-left: 4px"
        >
          {{ d(record.banAt, 'long') }}
        </a-typography-text>
      </div>
      <div style="display: flex; align-items: center">
        <icon-clock-circle />
        <a-typography-text
          class="expandable-ellipsis-text"
          :ellipsis="{ showTooltip: true }"
          style="margin-left: 4px"
        >
          {{
            record.unbanAt ? d(record.unbanAt, 'long') : t('page.banlog.banlogTable.notUnbanned')
          }}
        </a-typography-text>
      </div>
    </template>
    <template #peerAddress="{ record }">
      <queryIpLink :ip="record.peerIp">
        <a-typography-text
          copyable
          :ellipsis="{ showTooltip: true }"
          style="margin-bottom: 0; color: var(--color-text-2); font-style: italic"
        >
          {{ formatIPAddressPort(record.peerIp, record.peerPort) }}
        </a-typography-text>
      </queryIpLink>
    </template>
    <template #peerId="{ record }">
      <div style="display: flex; align-items: center">
        <a-typography-text
          :ellipsis="{
            showTooltip: true
          }"
          class="expandable-ellipsis-text"
        >
          {{ record.peerId ? record.peerId : t('page.banlist.banlist.listItem.empty') }}
        </a-typography-text>
        <a-tooltip
          :content="
            record.peerClientName ? record.peerClientName : t('page.banlist.banlist.listItem.empty')
          "
        >
          <icon-info-circle style="flex-shrink: 0; margin-left: 4px" />
        </a-tooltip>
      </div>
    </template>
    <template #peerStatus="{ record }">
      <div style="display: flex; align-items: center">
        <div style="flex: 1">
          <div style="display: flex; align-items: center">
            <icon-arrow-up class="green" />
            <a-typography-text
              class="expandable-ellipsis-text"
              :ellipsis="true"
              style="margin-left: 4px"
            >
              {{ formatFileSize(record.peerUploaded) }}
            </a-typography-text>
          </div>
          <div style="display: flex; align-items: center; margin-top: 4px">
            <icon-arrow-down class="red" />
            <a-typography-text
              class="expandable-ellipsis-text"
              :ellipsis="true"
              style="margin-left: 4px"
            >
              {{ formatFileSize(record.peerDownloaded) }}
            </a-typography-text>
          </div>
        </div>
        <a-tooltip :content="(record.peerProgress * 100).toFixed(2) + '%'">
          <a-progress :percent="record.peerProgress" size="mini" />
        </a-tooltip>
      </div>
    </template>
    <template #torrentName="{ record }">
      <a-tooltip
        :content="
          t('page.banlog.banlogTable.column.torrentName.tooltip', {
            downloader: record.downloader?.name,
            hash: record.torrentInfoHash
          })
        "
      >
        <p style="text-overflow: ellipsis; overflow: hidden">{{ record.torrentName }}</p>
      </a-tooltip>
    </template>
    <template #torrentSize="{ record }">
      <a-tooltip :content="`Hash: ${record.torrentInfoHash}`">
        <p style="text-overflow: ellipsis; overflow: hidden; white-space: nowrap">
          {{ formatFileSize(record.torrentSize) }}
        </p>
      </a-tooltip>
    </template>
  </a-table>
</template>
<script setup lang="ts">
import queryIpLink from '@/components/queryIpLink.vue'
import { getBanlogs } from '@/service/banLogs'
import { useFirstPageOnlyAutoUpdatePlugin } from '@/stores/autoUpdate'
import { useEndpointStore } from '@/stores/endpoint'
import { formatFileSize } from '@/utils/file'
import { formatIPAddressPort } from '@/utils/string'
import type { TableSortable } from '@arco-design/web-vue'
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { usePagination } from 'vue-request'
const forceLoading = ref(true)
const endpointState = useEndpointStore()
const { t, d } = useI18n()
const { data, total, current, loading, pageSize, changeCurrent, changePageSize, refresh, run } =
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
    [useFirstPageOnlyAutoUpdatePlugin]
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
    dataIndex: 'banAt',
    sortable: {
      sortDirections: ['ascend', 'descend'] as TableSortable['sortDirections'],
      sorter: true
    },
    width: 210
  },
  {
    title: () => t('page.banlog.banlogTable.column.peerAddress'),
    slotName: 'peerAddress',
    dataIndex: 'peerIp',
    sortable: {
      sortDirections: ['ascend', 'descend'] as TableSortable['sortDirections'],
      sorter: true
    },
    width: 230
  },
  {
    title: () => t('page.banlog.banlogTable.column.peerId'),
    slotName: 'peerId',
    dataIndex: 'peerId',
    sortable: {
      sortDirections: ['ascend', 'descend'] as TableSortable['sortDirections'],
      sorter: true
    },
    width: 120
  },
  {
    title: () => t('page.banlog.banlogTable.column.trafficSnapshot'),
    slotName: 'peerStatus',
    dataIndex: 'peerUploaded',
    sortable: {
      sortDirections: ['ascend', 'descend'] as TableSortable['sortDirections'],
      sorter: true
    },
    width: 150
  },
  {
    title: () => t('page.banlog.banlogTable.column.torrentName'),
    dataIndex: 'torrentName',
    slotName: 'torrentName',
    sortable: {
      sortDirections: ['ascend', 'descend'] as TableSortable['sortDirections'],
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
      sortDirections: ['ascend', 'descend'] as TableSortable['sortDirections'],
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
  if (!direction)
    run({
      page: current.value,
      pageSize: pageSize.value
    })
  else
    run({
      page: current.value,
      pageSize: pageSize.value,
      sorter: `${dataIndex}|${direction}`
    })
}
</script>

<style scoped>
.red {
  color: rgb(var(--red-5));
}
.green {
  color: rgb(var(--green-5));
}

.expandable-ellipsis-text {
  flex: 1;
  min-width: 0;
  margin-bottom: 0;
}
</style>
