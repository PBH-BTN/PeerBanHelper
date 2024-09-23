<template>
  <a-modal v-model:visible="visible" hide-cancel closable unmount-on-close width="auto">
    <template #title>
      <i18n-t keypath="page.torrentList.accessHisotry.title">
        <template #name>
          {{ name }}
        </template>
      </i18n-t>
    </template>
    <a-space direction="vertical" fill>
      <a-typography-text style="font-size: 1.2em">
        {{ t('page.torrentList.accessHisotry.description') }}
      </a-typography-text>
      <a-table
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
        @page-size-change="changePageSize"
      >
        <template #address="{ record }">
          <a-typography-text code copyable style="white-space: nowrap">{{
            record.address
          }}</a-typography-text>
        </template>
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
              {{ t('page.torrentList.accessHisotry.column.timeseen.first') }}:
              {{ d(record.firstTimeSeen, 'long') }}</a-typography-text
            >
            <a-typography-text>
              {{ t('page.torrentList.accessHisotry.column.timeseen.last') }}:
              {{ d(record.lastTimeSeen, 'long') }}</a-typography-text
            >
          </a-space>
        </template>
      </a-table>
    </a-space>
  </a-modal>
</template>
<script lang="ts" setup>
import { GetAccessHistoryList } from '@/service/data'
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

const visible = ref(false)
const name = ref('')
defineExpose({
  showModal: (infoHash: string, torrentName: string) => {
    name.value = torrentName
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
} = usePagination(GetAccessHistoryList, {
  pagination: {
    currentKey: 'page',
    pageSizeKey: 'pageSize',
    totalKey: 'data.total'
  },
  manual: true,
  cacheKey: (params) =>
    `${endpointState.endpoint}-torrentAccessHistory-${params?.[0].infoHash}-${params?.[0].page || 1}-${params?.[0].pageSize || 10}`
})

const columns = [
  {
    title: 'ID',
    dataIndex: 'id'
  },
  {
    title: () => t('page.torrentList.accessHisotry.column.downloader'),
    slotName: 'downloader'
  },
  {
    title: () => t('page.torrentList.accessHisotry.column.address'),
    slotName: 'address'
  },
  {
    title: 'Peer ID',
    slotName: 'peerId'
  },
  {
    title: () => t('page.torrentList.accessHisotry.column.traffic'),
    slotName: 'traffic'
  },
  {
    title: () =>
      h(Space, [
        t('page.torrentList.accessHisotry.column.offset'),
        h(
          Popover,
          {
            content: t('page.torrentList.accessHisotry.column.offsetDescription')
          },
          () => h(IconInfoCircle)
        )
      ]),
    slotName: 'offset'
  },
  {
    title: 'Flags',
    slotName: 'flags'
  },
  { title: () => t('page.torrentList.accessHisotry.column.timeseen'), slotName: 'time' }
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
