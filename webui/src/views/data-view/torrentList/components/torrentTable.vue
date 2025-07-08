<template>
  <a-space direction="vertical" fill>
    <a-space class="align-right" fill>
      <a-input-search
        :style="{ width: '250px' }"
        :placeholder="t('page.torrentList.accessHistory.searchPlaceholder')"
        allow-clear
        search-button
        @search="handleSearch"
        @change="handleSearch"
        @clear="run({ page: current, pageSize: pageSize })"
      />
    </a-space>
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
      <template #size="{ record }">
        {{ formatFileSize(record.size) }}
      </template>
      <template #hash="{ record }">
        <a-typography-text code style="white-space: nowrap">{{
          record.infoHash
        }}</a-typography-text>
      </template>
      <template #count="{ record }">
        <a-space fill direction="vertical">
          <a-tooltip :content="`${t('page.torrentList.column.count.ban')}: ${record.peerBanCount}`">
            <a-typography-text>
              <icon-stop />
              {{ record.peerBanCount }}
            </a-typography-text>
          </a-tooltip>
          <a-tooltip
            :content="`${t('page.torrentList.column.count.access')}: ${record.peerAccessCount}`"
          >
            <a-typography-text>
              <icon-link />
              {{ record.peerAccessCount }}
            </a-typography-text>
          </a-tooltip>
        </a-space>
      </template>
      <template #action="{ record }">
        <a-space wrap>
          <a-tooltip
            :content="
              plusStatus?.activated
                ? t('page.torrentList.column.actions.history')
                : t('page.ipList.plusLock')
            "
            position="top"
            mini
          >
            <a-button
              class="edit-btn"
              shape="circle"
              type="text"
              :disabled="!plusStatus?.activated"
              @click="accessHistoryModal?.showModal(record.infoHash, record.name)"
            >
              <template #icon>
                <icon-history v-if="plusStatus?.activated" />
                <icon-lock v-else />
              </template>
            </a-button>
          </a-tooltip>
          <a-tooltip
            :content="
              plusStatus?.activated
                ? t('page.torrentList.column.actions.ban')
                : t('page.ipList.plusLock')
            "
            position="top"
            mini
          >
            <a-button
              class="edit-btn"
              shape="circle"
              type="text"
              :disabled="!plusStatus?.activated"
              @click="banHistoryModal?.showModal(record.infoHash, record.name)"
            >
              <template #icon>
                <icon-stop v-if="plusStatus?.activated" />
                <icon-lock v-else />
              </template>
            </a-button>
          </a-tooltip>
        </a-space>
      </template>
    </a-table>
  </a-space>
  <AccessHistoryModal ref="accessHistoryModal" />
  <BanHistoryModal ref="banHistoryModal" />
</template>
<script lang="ts" setup>
import { GetTorrentInfoList } from '@/service/data'
import { useAutoUpdatePlugin } from '@/stores/autoUpdate'
import { useEndpointStore } from '@/stores/endpoint'
import { formatFileSize } from '@/utils/file'
import { debounce } from 'lodash'
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { usePagination } from 'vue-request'
import AccessHistoryModal from './accessHistoryModal.vue'
import BanHistoryModal from './banHistoryModal.vue'
const forceLoading = ref(true)
const endpointState = useEndpointStore()
const { t } = useI18n()
const { data, total, current, loading, pageSize, changeCurrent, changePageSize, refresh, run } =
  usePagination(
    GetTorrentInfoList,
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
        `${endpointState.endpoint}-torrentInfoList-${params?.[0].page || 1}-${params?.[0].pageSize || 10}`,
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
    title: () => t('page.torrentList.column.name'),
    dataIndex: 'name',
    ellipsis: true,
    tooltip: true,
    width: 500
  },
  {
    title: 'Hash',
    slotName: 'hash',
    width: 340
  },
  {
    title: () => t('page.torrentList.column.size'),
    slotName: 'size',
    width: 120
  },
  {
    title: () => t('page.torrentList.column.count'),
    slotName: 'count'
  },
  {
    title: () => t('page.torrentList.column.actions'),
    slotName: 'action'
  }
]
const list = computed(() => data.value?.data.results)
const accessHistoryModal = ref<InstanceType<typeof AccessHistoryModal>>()
const banHistoryModal = ref<InstanceType<typeof BanHistoryModal>>()

const handleSearch = debounce((value: string) => {
  run({ page: current.value, pageSize: pageSize.value, keyword: value })
}, 300)
const endpointStore = useEndpointStore()
const plusStatus = computed(() => endpointStore.plusStatus)
</script>
<style scoped>
.edit-btn {
  color: rgb(var(--gray-8)) !important;
  font-size: 16px;
}

.align-right {
  display: flex;
  justify-content: flex-end;
}
</style>
