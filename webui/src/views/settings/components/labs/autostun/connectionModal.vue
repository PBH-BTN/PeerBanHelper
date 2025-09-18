<template>
  <a-space direction="vertical" fill>
    <a-table
      :columns="connectionTableColumns"
      :bordered="{ cell: true }"
      :data="connections"
      :pagination="false"
      :loading="loading"
      :virtual-list-props="{ height: 800 }"
      stripe
      column-resizable
    >
      <template #empty>
        <a-empty :description="t('page.settings.tab.autostun.no_connections')" />
      </template>
      <template #region="{ record }">
        <div v-if="record.ipGeoData?.country?.iso">
          <CountryFlag
            v-if="record.ipGeoData?.country?.iso"
            :iso="record.ipGeoData?.country?.iso"
            :title="`${record.ipGeoData?.country?.name ?? ''}`"
          />
          <a-typography-text
            :ellipsis="{
              rows: 1,
              showTooltip: true
            }"
          >
            {{
              `${record.ipGeoData?.city?.name ?? ''} ${record.ipGeoData?.network?.isp ?? ''} ${record.ipGeoData?.network?.netType ?? ''}`
            }}
          </a-typography-text>
        </div>
        <div v-else>
          {{ 'N/A' }}
        </div>
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
      <template #upstream="{ record }">
        <a-typography-text code copyable>
          {{ record.upstreamHost }}:{{ record.upstreamPort }}
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
</template>

<script setup lang="ts">
import type { ConnectionInfo } from '@/api/model/autostun'
import CountryFlag from '@/components/countryFlag.vue'
import queryIpLink from '@/components/queryIpLink.vue'
import { getTunnelConnections } from '@/service/autostun'
import { useAutoUpdatePlugin } from '@/stores/autoUpdate'
import { formatFileSize } from '@/utils/file'
import Message from '@arco-design/web-vue/es/message'
import dayjs from 'dayjs'
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRequest } from 'vue-request'

const { t } = useI18n()

// Props
interface Props {
  downloaderId: string
  modalTitle: string
}

const props = defineProps<Props>()

// Internal state
const connections = ref<ConnectionInfo[]>([])
const loading = ref(false)

// Connection table columns definition
const connectionTableColumns = [
  {
    title: t('page.settings.tab.autostun.region'),
    slotName: 'region',
    width: 300
  },
  {
    title: t('page.settings.tab.autostun.connection_downstream'),
    slotName: 'downstream',
    width: 250
  },
  {
    title: t('page.settings.tab.autostun.connection_proxy'),
    slotName: 'proxy',
    width: 250
  },
  {
    title: t('page.settings.tab.autostun.connection_upstream'),
    slotName: 'upstream',
    width: 200
  },
  {
    title: t('page.settings.tab.autostun.connection_established'),
    slotName: 'established',
    width: 200
  },
  {
    title: t('page.settings.tab.autostun.connection_bytes'),
    slotName: 'bytes',
    width: 240
  }
]

// Auto-refresh connections logic
const { refresh: refreshConnections } = useRequest(
  async () => {
    if (!props.downloaderId) {
      return []
    }

    const res = await getTunnelConnections(props.downloaderId)
    if (res.success && res.data) {
      return Array.isArray(res.data) ? res.data : []
    }
    return []
  },
  {
    manual: true,
    onBefore: () => {
      loading.value = true
    },
    onSuccess: (data) => {
      connections.value = data || []
      loading.value = false
    },
    onError: (error) => {
      Message.error(error.message)
      connections.value = []
      loading.value = false
    }
  },
  [useAutoUpdatePlugin]
)

// Watch for downloaderId changes to fetch data
watch(
  () => props.downloaderId,
  (newId) => {
    if (newId) {
      refreshConnections()
    } else {
      connections.value = []
    }
  },
  { immediate: true }
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
