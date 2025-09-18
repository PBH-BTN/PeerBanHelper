<template>
  <a-modal
    v-model:visible="visible"
    :title="`${title} - ${t('page.settings.tab.autostun.connection_table')}`"
    width="140vh"
    hide-cancel
    unmount-on-close
    :footer="false"
    @close="close()"
  >
    <a-space direction="vertical" fill>
      <a-table
        :columns="connectionTableColumns"
        :bordered="{ cell: true }"
        :data="data?.data.results"
        :pagination="{
          total,
          current,
          pageSize,
          showPageSize: true,
          baseSize: 5,
          bufferSize: 1
        }"
        :total="total"
        :current="current"
        :page-size="pageSize"
        show-page-size
        :loading="loading"
        stripe
        @page-change="changeCurrent"
        @page-size-change="changePageSize"
      >
        <template #empty>
          <a-empty :description="t('page.settings.tab.autostun.no_connections')" />
        </template>
        <template #region="{ record }">
          <a-typography-text
            v-if="record.ipGeoData?.country?.iso"
            style="margin-bottom: 0"
            :ellipsis="{
              rows: 1,
              showTooltip: true
            }"
          >
            <CountryFlag
              v-if="record.ipGeoData?.country?.iso"
              :iso="record.ipGeoData?.country?.iso"
              :title="`${record.ipGeoData?.country?.name ?? ''}`"
            />
            {{
              `${record.ipGeoData?.city?.name ?? ''} ${record.ipGeoData?.network?.isp ?? ''} ${record.ipGeoData?.network?.netType ?? ''}`
            }}
          </a-typography-text>
          <a-typography-text v-else style="margin-bottom: 0">
            {{ 'N/A' }}
          </a-typography-text>
        </template>
        <template #downstream="{ record }">
          <a-typography-text code copyable>
            <queryIpLink :ip="record.downstreamHost" style="color: var(--color-text-2)">
              {{ record.downstreamHost }}:{{ record.downstreamPort }}
            </queryIpLink>
          </a-typography-text>
        </template>
        <template #proxy="{ record }">
          <a-typography-text code copyable>
            {{ record.proxyOutgoingHost }}:{{ record.proxyOutgoingPort }}
          </a-typography-text>
        </template>
        <template #established="{ record }">
          <a-space direction="vertical">
            <a-typography-text>
              <icon-clock-circle />
              {{ formatTimestamp(record.establishedAt) }}
            </a-typography-text>
          </a-space>
        </template>
        <template #bytes="{ record }">
          <a-space direction="horizontal">
            <a-typography-text>
              <icon-arrow-up class="green" />
              {{ formatFileSize(record.toDownstreamBytes) }}
            </a-typography-text>
            <a-typography-text>
              <icon-arrow-down class="red" />
              {{ formatFileSize(record.toUpstreamBytes) }}
            </a-typography-text>
          </a-space>
        </template>
      </a-table>
    </a-space>
  </a-modal>
</template>

<script setup lang="ts">
import CountryFlag from '@/components/countryFlag.vue'
import queryIpLink from '@/components/queryIpLink.vue'
import { getTunnelConnections } from '@/service/autostun'
import { useAutoUpdatePlugin } from '@/stores/autoUpdate'
import { formatFileSize } from '@/utils/file'
import dayjs from 'dayjs'
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { usePagination } from 'vue-request'

const { t } = useI18n()
const visible = ref(false)
const title = ref('')
defineExpose({
  showModal: (downloaderId: string, downloaderName: string) => {
    title.value = downloaderName
    run({ downloaderId, page: 1, pageSize: 10 })
    visible.value = true
  }
})

// Connection table columns definition
const connectionTableColumns = [
  {
    title: t('page.settings.tab.autostun.region'),
    slotName: 'region',
    width: 350
  },
  {
    title: t('page.settings.tab.autostun.connection_downstream'),
    slotName: 'downstream',
    width: 230
  },
  {
    title: t('page.settings.tab.autostun.connection_proxy'),
    slotName: 'proxy',
    width: 230
  },
  {
    title: t('page.settings.tab.autostun.connection_established'),
    slotName: 'established',
    width: 200
  },
  {
    title: t('page.settings.tab.autostun.connection_bytes'),
    slotName: 'bytes',
    width: 200
  }
]

const close = () => {
  cancel()
  visible.value = false
}

// Auto-refresh connections logic
const { run, cancel, data, loading, total, current, pageSize, changeCurrent, changePageSize } =
  usePagination(
    getTunnelConnections,
    {
      defaultParams: [
        {
          downloaderId: '',
          page: 1,
          pageSize: 20
        }
      ],
      manual: true,
      pagination: {
        currentKey: 'page',
        pageSizeKey: 'pageSize',
        totalKey: 'data.total'
      }
    },
    [useAutoUpdatePlugin]
  )

// Utility functions
const formatTimestamp = (timestamp: number): string => {
  return dayjs(timestamp).format('YYYY-MM-DD HH:mm:ss')
}
</script>

<style scoped>
.green {
  color: #52c41a;
}

.red {
  color: #ff4d4f;
}
</style>
