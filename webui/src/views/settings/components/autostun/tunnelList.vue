<template>
  <a-card :title="t('page.settings.tab.autostun.tunnel_info')">
    <a-spin :loading="loading">
      <div v-if="tunnels.length === 0" style="text-align: center; padding: 40px">
        <a-empty :description="t('page.settings.tab.autostun.no_tunnels')" />
      </div>
      <a-space v-else direction="vertical" fill size="large">
        <a-descriptions
          v-for="item in tunnels"
          :key="item.downloader.id"
          :column="{ xs: 2, sm: 3, md: 4, lg: 6, xl: 8 }"
          size="medium"
          class="tunnel-item"
          :layout="(['inline-vertical', 'horizontal'] as const)[descriptionLayout]"
          table-layout="fixed"
          bordered
        >
          <template #title>
            <a-space fill style="display: flex; justify-content: space-between">
              <a-space wrap>
                <a-typography-text bold>
                  {{ item.downloader.name }} ({{ item.downloader.type }})
                </a-typography-text>
                <a-tag :color="item.tunnel.valid ? 'green' : 'red'">
                  {{
                    item.tunnel.valid
                      ? t('page.settings.tab.autostun.tunnel_valid')
                      : t('page.settings.tab.autostun.tunnel_invalid')
                  }}
                </a-tag>
              </a-space>
              <a-button
                size="small"
                type="outline"
                :disabled="!item.tunnel.valid"
                @click="$emit('viewConnections', item.downloader.id, item.downloader.name)"
              >
                {{ t('page.settings.tab.autostun.view_connections') }}
              </a-button>
            </a-space>
          </template>

          <a-descriptions-item :label="t('page.settings.tab.autostun.tunnel_proxy')" :span="2">
            <a-typography-text code copyable>
              {{ item.tunnel.proxyHost }}:{{ item.tunnel.proxyPort }}
            </a-typography-text>
          </a-descriptions-item>

          <a-descriptions-item :label="t('page.settings.tab.autostun.tunnel_upstream')" :span="2">
            <a-typography-text code copyable>
              {{ item.tunnel.upstreamHost }}:{{ item.tunnel.upstreamPort }}
            </a-typography-text>
          </a-descriptions-item>

          <a-descriptions-item
            :label="t('page.settings.tab.autostun.tunnel_connections')"
            :span="2"
          >
            <a-space>
              <icon-link />
              <a-typography-text>{{ item.tunnel.establishedConnections }}</a-typography-text>
            </a-space>
          </a-descriptions-item>

          <a-descriptions-item :label="t('page.settings.tab.autostun.tunnel_handled')" :span="2">
            <a-space>
              <icon-check-circle class="green" />
              <a-typography-text>{{ item.tunnel.connectionsHandled }}</a-typography-text>
            </a-space>
          </a-descriptions-item>

          <a-descriptions-item :label="t('page.settings.tab.autostun.tunnel_failed')" :span="2">
            <a-space>
              <icon-close-circle class="red" />
              <a-typography-text>{{ item.tunnel.connectionsFailed }}</a-typography-text>
            </a-space>
          </a-descriptions-item>

          <a-descriptions-item
            :label="t('page.settings.tab.autostun.tunnel_downstream_bytes')"
            :span="2"
          >
            <a-space>
              <icon-arrow-down class="red" />
              <a-typography-text>{{
                formatBytes(item.tunnel.totalToDownstreamBytes)
              }}</a-typography-text>
            </a-space>
          </a-descriptions-item>

          <a-descriptions-item
            :label="t('page.settings.tab.autostun.tunnel_upstream_bytes')"
            :span="2"
          >
            <a-space>
              <icon-arrow-up class="green" />
              <a-typography-text>{{
                formatBytes(item.tunnel.totalToUpstreamBytes)
              }}</a-typography-text>
            </a-space>
          </a-descriptions-item>
        </a-descriptions>
      </a-space>
    </a-spin>
  </a-card>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { ref } from 'vue'
import { useResponsiveState } from '@arco-design/web-vue/es/grid/hook/use-responsive-state'
import type { TunnelData } from '@/api/model/autostun'

const { t } = useI18n()

// Props
interface Props {
  tunnels: TunnelData[]
  loading: boolean
}

defineProps<Props>()

// Emits
defineEmits<{
  viewConnections: [downloaderId: string, downloaderName: string]
}>()

// Responsive layout state
const descriptionLayout = useResponsiveState(
  ref({
    md: 1
  }),
  0
)

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
</script>

<style lang="less" scoped>
.red {
  color: rgb(var(--red-6));
}

.green {
  color: rgb(var(--green-6));
}

.tunnel-item {
  &:not(:last-child) {
    margin-bottom: 16px;
  }
}
</style>

<style lang="less">
.tunnel-item {
  .arco-descriptions-item-label {
    min-width: 100px;
    font-weight: 500;
  }

  .arco-descriptions-title {
    margin-bottom: 12px;
  }
}
</style>
