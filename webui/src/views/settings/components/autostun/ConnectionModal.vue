<template>
  <a-modal
    v-model:visible="visible"
    :title="`${modalTitle} - ${t('page.settings.tab.autostun.connection_table')}`"
    width="80%"
    :footer="false"
    unmount-on-close
  >
    <a-spin :loading="loading">
      <a-table
        :columns="connectionTableColumns"
        :data="connections"
        :pagination="false"
        :scroll="{ x: 1000 }"
        stripe
        size="medium"
      >
        <template #empty>
          <a-empty :description="t('page.settings.tab.autostun.no_connections')" />
        </template>
        <template #downstream="{ record }">
          {{ record.downstreamHost }}:{{ record.downstreamPort }}
        </template>
        <template #proxy="{ record }">
          {{ record.proxyHost }}:{{ record.proxyPort }}
        </template>
        <template #upstream="{ record }">
          {{ record.upstreamHost }}:{{ record.upstreamPort }}
        </template>
        <template #established="{ record }">
          {{ formatTimestamp(record.establishedAt) }}
        </template>
        <template #activity="{ record }">
          {{ formatTimestamp(record.lastActivityAt) }}
        </template>
        <template #bytes="{ record }">
          <div>↓ {{ formatBytes(record.toDownstreamBytes) }}</div>
          <div>↑ {{ formatBytes(record.toUpstreamBytes) }}</div>
        </template>
      </a-table>
    </a-spin>
  </a-modal>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { ConnectionInfo } from '@/api/model/autostun'
import dayjs from 'dayjs'

const { t } = useI18n()

// Props
interface Props {
  visible: boolean
  modalTitle: string
  connections: ConnectionInfo[]
  loading: boolean
}

defineProps<Props>()

// Emits
defineEmits<{
  'update:visible': [value: boolean]
}>()

// Connection table columns definition
const connectionTableColumns = [
  {
    title: t('page.settings.tab.autostun.connection_downstream'),
    slotName: 'downstream',
    width: 180
  },
  {
    title: t('page.settings.tab.autostun.connection_proxy'),
    slotName: 'proxy',
    width: 180
  },
  {
    title: t('page.settings.tab.autostun.connection_upstream'),
    slotName: 'upstream',
    width: 180
  },
  {
    title: t('page.settings.tab.autostun.connection_established'),
    slotName: 'established',
    width: 150
  },
  {
    title: t('page.settings.tab.autostun.connection_activity'),
    slotName: 'activity',
    width: 150
  },
  {
    title: t('page.settings.tab.autostun.connection_bytes'),
    slotName: 'bytes',
    width: 200
  }
]

// Utility functions
const formatBytes = (bytes: number): string => {
  if (bytes === 0) return `0 ${t('page.settings.tab.autostun.bytes')}`

  const sizes = [
    t('page.settings.tab.autostun.bytes'),
    t('page.settings.tab.autostun.kb'),
    t('page.settings.tab.autostun.mb'),
    t('page.settings.tab.autostun.gb')
  ]

  const i = Math.floor(Math.log(bytes) / Math.log(1024))
  return `${(bytes / Math.pow(1024, i)).toFixed(2)} ${sizes[i]}`
}

const formatTimestamp = (timestamp: number): string => {
  return dayjs(timestamp).format('YYYY-MM-DD HH:mm:ss')
}
</script>