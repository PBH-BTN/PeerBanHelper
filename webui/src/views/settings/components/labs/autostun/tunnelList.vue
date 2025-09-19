<template>
  <a-space direction="vertical" fill style="margin-bottom: 10px">
    <a-typography-title :heading="5">
      {{ t('page.settings.tab.autostun.tunnel_info') }}
    </a-typography-title>

    <a-spin :loading="loading" style="width: 100%; min-height: 200px">
      <a-grid
        v-if="!loading"
        :cols="{ xs: 1, sm: 1, md: 2, lg: 2, xl: 2, xxl: 2 }"
        :col-gap="24"
        :row-gap="24"
      >
        <a-grid-item v-for="item in tunnels" :key="item.downloader.id">
          <a-card size="small" hoverable style="height: 100%">
            <template #title>
              <a-space>
                {{ item.downloader.name }}
                <a-tag size="small">{{ item.downloader.type }}</a-tag>
                <a-tag :color="item.tunnel.valid ? 'green' : 'red'" size="small">
                  {{
                    item.tunnel.valid
                      ? t('page.settings.tab.autostun.tunnel_valid')
                      : t('page.settings.tab.autostun.tunnel_invalid')
                  }}
                </a-tag>
              </a-space>
            </template>

            <a-space direction="vertical" fill>
              <!-- 连接信息 -->
              <div style="display: flex; justify-content: center; align-items: center; gap: 8px">
                <a-typography-text code size="mini">
                  {{ item.tunnel.proxyHost }}:{{ item.tunnel.proxyPort }}
                </a-typography-text>
                <icon-arrow-right />
                <a-typography-text code size="mini">
                  {{ item.tunnel.upstreamHost }}:{{ item.tunnel.upstreamPort }}
                </a-typography-text>
              </div>

              <a-divider margin="12px" />
              <!-- 统计信息 -->
              <a-grid :cols="2" :col-gap="16" :row-gap="16">
                <a-grid-item>
                  <a-statistic
                    :title="t('page.settings.tab.autostun.tunnel_connections')"
                    :value="item.tunnel.establishedConnections"
                  />
                </a-grid-item>
                <a-grid-item>
                  <a-statistic
                    :title="t('page.settings.tab.autostun.tunnel_handled')"
                    :value="item.tunnel.connectionsHandled"
                    :value-style="{ color: 'rgb(var(--success-6))' }"
                  />
                </a-grid-item>
                <a-grid-item>
                  <a-statistic
                    :title="t('page.settings.tab.autostun.tunnel_failed')"
                    :value="item.tunnel.connectionsFailed"
                    :value-style="{ color: 'rgb(var(--danger-6))' }"
                  />
                </a-grid-item>
                <a-grid-item>
                  <a-statistic
                    :title="t('page.settings.tab.autostun.tunnel_blocked')"
                    :value="item.tunnel.connectionsBlocked"
                    :value-style="{ color: 'rgb(var(--warning-6))' }"
                  />
                </a-grid-item>
                <a-grid-item>
                  <a-statistic
                    :title="t('page.settings.tab.autostun.tunnel_downstream_bytes')"
                    :value="parseFloat(formatFileSize(item.tunnel.totalToDownstreamBytes))"
                  >
                    <template #suffix>
                      {{
                        formatFileSize(item.tunnel.totalToDownstreamBytes).replace(/[\d.]+\s*/, '')
                      }}
                    </template>
                  </a-statistic>
                </a-grid-item>
                <a-grid-item>
                  <a-statistic
                    :title="t('page.settings.tab.autostun.tunnel_upstream_bytes')"
                    :value="parseFloat(formatFileSize(item.tunnel.totalToUpstreamBytes))"
                  >
                    <template #suffix>
                      {{
                        formatFileSize(item.tunnel.totalToUpstreamBytes).replace(/[\d.]+\s*/, '')
                      }}
                    </template>
                  </a-statistic>
                </a-grid-item>
              </a-grid>
            </a-space>

            <template #actions>
              <a-button
                type="primary"
                size="small"
                :disabled="!item.tunnel.valid || item.tunnel.establishedConnections === 0"
                @click="$emit('viewConnections', item.downloader.id, item.downloader.name)"
              >
                <template #icon>
                  <icon-eye />
                </template>
                {{ t('page.settings.tab.autostun.view_connections') }}
              </a-button>
            </template>
          </a-card>
        </a-grid-item>
      </a-grid>
      <a-empty
        v-if="!loading && tunnels.length === 0"
        :description="t('page.settings.tab.autostun.no_tunnels')"
      />
    </a-spin>
  </a-space>
</template>

<script setup lang="ts">
import type { TunnelData } from '@/api/model/autostun'
import { getAutoSTUNTunnels } from '@/service/autostun'
import { useAutoUpdatePlugin } from '@/stores/autoUpdate'
import { formatFileSize } from '@/utils/file.ts'
import { IconArrowRight, IconEye } from '@arco-design/web-vue/es/icon'
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRequest } from 'vue-request'

const { t } = useI18n()

// Reactive data
const tunnels = ref<TunnelData[]>([])

// Auto-refresh tunnels using useAutoUpdatePlugin
const { loading } = useRequest(
  getAutoSTUNTunnels,

  {
    manual: false,
    onSuccess: (data) => {
      tunnels.value = data.data || []
    },
    onError: (error) => {
      console.error('Failed to refresh tunnels:', error)
      tunnels.value = []
    }
  },
  [useAutoUpdatePlugin]
)

defineEmits<{
  viewConnections: [downloaderId: string, downloaderName: string]
}>()
</script>
