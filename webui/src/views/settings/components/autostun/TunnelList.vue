<template>
  <a-card :title="t('page.settings.tab.autostun.tunnel_info')">
    <a-spin :loading="loading">
      <div v-if="tunnels.length === 0" style="text-align: center; padding: 40px">
        <a-empty :description="t('page.settings.tab.autostun.no_tunnels')" />
      </div>
      <a-list v-else :data="tunnels">
        <template #item="{ item }">
          <a-list-item action-layout="horizontal">
            <template #actions>
              <a-button
                size="small"
                type="outline"
                :disabled="!item.tunnel.valid"
                @click="$emit('viewConnections', item.downloader.id, item.downloader.name)"
              >
                {{ t('page.settings.tab.autostun.view_connections') }}
              </a-button>
            </template>
            <a-list-item-meta>
              <template #title>
                <a-space>
                  {{ item.downloader.name }} ({{ item.downloader.type }})
                  <a-tag :color="item.tunnel.valid ? 'green' : 'red'">
                    {{
                      item.tunnel.valid
                        ? t('page.settings.tab.autostun.tunnel_valid')
                        : t('page.settings.tab.autostun.tunnel_invalid')
                    }}
                  </a-tag>
                </a-space>
              </template>
              <template #description>
                <a-row :gutter="20">
                  <a-col :span="8">
                    <div>
                      <strong>{{ t('page.settings.tab.autostun.tunnel_proxy') }}:</strong>
                      {{ item.tunnel.proxyHost }}:{{ item.tunnel.proxyPort }}
                    </div>
                    <div>
                      <strong>{{ t('page.settings.tab.autostun.tunnel_upstream') }}:</strong>
                      {{ item.tunnel.upstreamHost }}:{{ item.tunnel.upstreamPort }}
                    </div>
                  </a-col>
                  <a-col :span="8">
                    <div>
                      <strong>{{ t('page.settings.tab.autostun.tunnel_connections') }}:</strong>
                      {{ item.tunnel.establishedConnections }}
                    </div>
                    <div>
                      <strong>{{ t('page.settings.tab.autostun.tunnel_handled') }}:</strong>
                      {{ item.tunnel.connectionsHandled }} /
                      {{ t('page.settings.tab.autostun.tunnel_failed') }}:
                      {{ item.tunnel.connectionsFailed }}
                    </div>
                  </a-col>
                  <a-col :span="8">
                    <div>
                      <strong
                        >{{ t('page.settings.tab.autostun.tunnel_downstream_bytes') }}:</strong
                      >
                      {{ formatBytes(item.tunnel.totalToDownstreamBytes) }}
                    </div>
                    <div>
                      <strong>{{ t('page.settings.tab.autostun.tunnel_upstream_bytes') }}:</strong>
                      {{ formatBytes(item.tunnel.totalToUpstreamBytes) }}
                    </div>
                  </a-col>
                </a-row>
              </template>
            </a-list-item-meta>
          </a-list-item>
        </template>
      </a-list>
    </a-spin>
  </a-card>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
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
