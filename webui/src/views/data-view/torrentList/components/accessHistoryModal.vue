<template>
  <a-modal v-model:visible="visible" hide-cancel closable unmount-on-close width="auto">
    <template #title>
      <i18n-t keypath="page.torrentList.accessHistory.title">
        <template #name>
          {{ name }}
        </template>
      </i18n-t>
    </template>
    <a-space direction="vertical" fill>
      <a-typography-text style="font-size: 1.2em">
        {{ t('page.torrentList.accessHistory.description') }}
      </a-typography-text>
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
        class="banlog-table"
        @page-change="changeCurrent"
        @sorter-change="sorterChange"
        @page-size-change="changePageSize"
      >
        <template #address="{ record }">
          <a-typography-text code copyable style="white-space: nowrap">
            <queryIpLink :ip="record.address" style="color: var(--color-text-2)">
              {{ record.address }}
            </queryIpLink>
          </a-typography-text>
        </template>
        <template #downloader="{ record }">
          <a-tag :color="getColor(record.downloader.id)">{{ record.downloader.name }}</a-tag>
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
              ><icon-arrow-up class="green" />
              {{ formatFileSize(record.uploaded) }}</a-typography-text
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
    </a-space>
  </a-modal>
</template>
<script lang="ts" setup>
import queryIpLink from '@/components/queryIpLink.vue'
import { useSorter } from '@/composables/useSorter'
import { GetTorrentAccessHistoryList } from '@/service/data'
import { useEndpointStore } from '@/stores/endpoint'
import { getColor } from '@/utils/color'
import { formatFileSize } from '@/utils/file'
import { Popover, Space } from '@arco-design/web-vue'
import { IconInfoCircle } from '@arco-design/web-vue/es/icon'
import { h, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { usePagination } from 'vue-request'

const { t, d } = useI18n()
const endpointState = useEndpointStore()

// 使用可复用的排序功能
const { sorterParam, handleSorterChange } = useSorter({ multiSort: true, maxSortColumns: 3 })

const visible = ref(false)
const currentInfoHash = ref('')
const name = ref('')
defineExpose({
  showModal: (infoHash: string, torrentName: string) => {
    name.value = torrentName
    currentInfoHash.value = infoHash
    runAsync({ page: 1, pageSize: 10, infoHash })
    visible.value = true
  }
})
const {
  data,
  total,
  current,
  loading: tableLoading,
  pageSize,
  changeCurrent,
  changePageSize,
  runAsync
} = usePagination(GetTorrentAccessHistoryList, {
  pagination: {
    currentKey: 'page',
    pageSizeKey: 'pageSize',
    totalKey: 'data.total'
  },
  manual: true,
  cacheKey: (params) =>
    `${endpointState.endpoint}-torrentAccessHistory-${params?.[0].infoHash}-${params?.[0].page || 1}-${params?.[0].pageSize || 10}-${params?.[0].sorter || 'default'}`
})

const columns = [
  {
    title: () => t('page.torrentList.accessHistory.column.downloader'),
    slotName: 'downloader'
  },
  {
    title: () => t('page.torrentList.accessHistory.column.address'),
    slotName: 'address',
    dataIndex: 'address',
    sortable: {
      sortDirections: ['ascend', 'descend'] as ('ascend' | 'descend')[],
      sorter: true
    }
  },
  {
    title: () => t('page.banlog.banlogTable.column.peerPort'),
    dataIndex: 'port',
    sortable: {
      sortDirections: ['ascend', 'descend'] as ('ascend' | 'descend')[],
      sorter: true
    },
    width: 80
  },
  {
    title: 'Peer ID',
    slotName: 'peerId',
    dataIndex: 'peerId',
    sortable: {
      sortDirections: ['ascend', 'descend'] as ('ascend' | 'descend')[],
      sorter: true
    }
  },
  {
    title: () => t('page.torrentList.accessHistory.column.traffic'),
    slotName: 'traffic',
    dataIndex: 'uploaded',
    sortable: {
      sortDirections: ['ascend', 'descend'] as ('ascend' | 'descend')[],
      sorter: true
    }
  },
  {
    title: () =>
      h(Space, [
        t('page.torrentList.accessHistory.column.offset'),
        h(
          Popover,
          {
            content: t('page.torrentList.accessHistory.column.offsetDescription')
          },
          () => h(IconInfoCircle)
        )
      ]),
    slotName: 'offset',
    dataIndex: 'uploadedOffset',
    sortable: {
      sortDirections: ['ascend', 'descend'] as ('ascend' | 'descend')[],
      sorter: true
    }
  },
  {
    title: 'Flags',
    slotName: 'flags',
    dataIndex: 'lastFlags',
    sortable: {
      sortDirections: ['ascend', 'descend'] as ('ascend' | 'descend')[],
      sorter: true
    }
  },
  {
    title: () => t('page.torrentList.accessHistory.column.timeseen'),
    slotName: 'time',
    dataIndex: 'lastTimeSeen',
    sortable: {
      sortDirections: ['ascend', 'descend'] as ('ascend' | 'descend')[],
      sorter: true
    }
  }
]
const parseFlags = (flags: string) =>
  flags
    .split(' ')
    .map((flag) => flag + ' - ' + t('page.dashboard.peerList.column.flags.' + flag.trim()))

const sorterChange = (dataIndex: string, direction: string) => {
  handleSorterChange(dataIndex, direction as 'ascend' | 'descend' | '')
  runAsync({
    page: current.value,
    pageSize: pageSize.value,
    infoHash: currentInfoHash.value,
    sorter: sorterParam.value
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
</style>
