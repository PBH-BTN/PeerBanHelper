<template>
  <a-modal
    :visible="visible"
    :title="`${modalTitle} - ${t('page.settings.tab.autostun.connection_table')}`"
    width="90%"
    :footer="false"
    unmount-on-close
    @update:visible="$emit('update:visible', $event)"
  >
    <a-space direction="vertical" fill style="max-width: 100%">
      <a-spin :loading="loading">
        <a-table
          :columns="connectionTableColumns"
          :data="connections"
          :pagination="false"
          :scroll="{ x: 1200 }"
          stripe
          size="medium"
          column-resizable
          style="width: 100%"
        >
          <template #empty>
            <a-empty :description="t('page.settings.tab.autostun.no_connections')" />
          </template>
          <template #downstream="{ record }">
            <a-typography-text code>
              <queryIpLink :ip="record.downstreamHost" style="color: var(--color-text-2)">
                {{ record.downstreamHost }}:{{ record.downstreamPort }}
              </queryIpLink>
              <a-button
                type="text"
                size="mini"
                style="margin-left: 8px"
                @click="copyToClipboard(`${record.downstreamHost}:${record.downstreamPort}`)"
              >
                <template #icon>
                  <icon-copy />
                </template>
              </a-button>
            </a-typography-text>
          </template>
          <template #proxy="{ record }">
            <a-typography-text code>
              {{ record.proxyOutgoingHost }}:{{ record.proxyOutgoingPort }}
              <a-button
                type="text"
                size="mini"
                style="margin-left: 8px"
                @click="copyToClipboard(`${record.proxyOutgoingHost}:${record.proxyOutgoingPort}`)"
              >
                <template #icon>
                  <icon-copy />
                </template>
              </a-button>
            </a-typography-text>
          </template>
          <template #upstream="{ record }">
            <a-typography-text code>
              {{ record.upstreamHost }}:{{ record.upstreamPort }}
              <a-button
                type="text"
                size="mini"
                style="margin-left: 8px"
                @click="copyToClipboard(`${record.upstreamHost}:${record.upstreamPort}`)"
              >
                <template #icon>
                  <icon-copy />
                </template>
              </a-button>
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
          <template #activity="{ record }">
            <a-space direction="vertical">
              <a-typography-text>
                <icon-history />
                {{ formatTimestamp(record.lastActivityAt) }}
              </a-typography-text>
            </a-space>
          </template>
          <template #bytes="{ record }">
            <a-space direction="vertical">
              <a-typography-text>
                <icon-arrow-up class="green" />
                {{ formatFileSize(record.toUpstreamBytes) }}
              </a-typography-text>
              <a-typography-text>
                <icon-arrow-down class="red" />
                {{ formatFileSize(record.toDownstreamBytes) }}
              </a-typography-text>
            </a-space>
          </template>
        </a-table>
      </a-spin>
    </a-space>
  </a-modal>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { Message } from '@arco-design/web-vue'
import type { ConnectionInfo } from '@/api/model/autostun'
import { formatFileSize } from '@/utils/file'
import queryIpLink from '@/components/queryIpLink.vue'
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
    width: 220
  },
  {
    title: t('page.settings.tab.autostun.connection_proxy'),
    slotName: 'proxy',
    width: 220
  },
  {
    title: t('page.settings.tab.autostun.connection_upstream'),
    slotName: 'upstream',
    width: 220
  },
  {
    title: t('page.settings.tab.autostun.connection_established'),
    slotName: 'established',
    width: 180
  },
  {
    title: t('page.settings.tab.autostun.connection_activity'),
    slotName: 'activity',
    width: 180
  },
  {
    title: t('page.settings.tab.autostun.connection_bytes'),
    slotName: 'bytes',
    width: 160
  }
]

// Utility functions
const formatTimestamp = (timestamp: number): string => {
  return dayjs(timestamp).format('YYYY-MM-DD HH:mm:ss')
}

const copyToClipboard = async (text: string) => {
  try {
    await navigator.clipboard.writeText(text)
    Message.success('已复制到剪贴板')
  } catch (error) {
    console.error('Failed to copy to clipboard:', error)
    Message.error('复制失败')
  }
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
