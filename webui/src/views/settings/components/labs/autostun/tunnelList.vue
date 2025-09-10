<template>
  <a-space direction="vertical" fill>
    <a-typography-title :heading="5">
      {{ t('page.settings.tab.autostun.tunnel_info') }}
    </a-typography-title>

    <a-spin :loading="loading" style="width: 100%; min-height: 200px">
      <div v-if="!loading" class="tunnel-grid">
        <div v-for="item in tunnels" :key="item.downloader.id" class="tunnel-card">
          <a-card size="small" hoverable>
            <!-- 顶部状态栏 -->
            <div class="tunnel-header">
              <div class="tunnel-title">
                <h4>
                  {{ item.downloader.name }} &nbsp;
                  <a-tag size="small">{{ item.downloader.type }}</a-tag>
                </h4>
              </div>
              <a-tag :color="item.tunnel.valid ? 'green' : 'red'" size="small">
                {{
                  item.tunnel.valid
                    ? t('page.settings.tab.autostun.tunnel_valid')
                    : t('page.settings.tab.autostun.tunnel_invalid')
                }}
              </a-tag>
            </div>

            <!-- 连接信息 -->
            <div class="tunnel-connection">
              <div class="connection-item">
                <icon-link class="connection-icon" />
                <span class="connection-text">
                  {{ item.tunnel.proxyHost }}:{{ item.tunnel.proxyPort }}
                  <icon-arrow-right class="arrow-icon" />
                  {{ item.tunnel.upstreamHost }}:{{ item.tunnel.upstreamPort }}
                </span>
              </div>
            </div>

            <!-- 统计信息 -->
            <div class="tunnel-stats">
              <div class="stats-row">
                <div class="stat-item">
                  <div class="stat-label">
                    {{ t('page.settings.tab.autostun.tunnel_connections') }}
                  </div>
                  <div class="stat-value">{{ item.tunnel.establishedConnections }}</div>
                </div>
                <div class="stat-item">
                  <div class="stat-label">{{ t('page.settings.tab.autostun.tunnel_handled') }}</div>
                  <div class="stat-value success">{{ item.tunnel.connectionsHandled }}</div>
                </div>
                <div class="stat-item">
                  <div class="stat-label">{{ t('page.settings.tab.autostun.tunnel_failed') }}</div>
                  <div class="stat-value error">{{ item.tunnel.connectionsFailed }}</div>
                </div>
                <div class="stat-item">
                  <div class="stat-label">{{ t('page.settings.tab.autostun.tunnel_blocked') }}</div>
                  <div class="stat-value warning">{{ item.tunnel.connectionsBlocked }}</div>
                </div>
              </div>

              <div class="stats-row">
                <div class="stat-item">
                  <div class="stat-label">
                    {{ t('page.settings.tab.autostun.tunnel_downstream_bytes') }}
                  </div>
                  <div class="stat-value">
                    {{ formatFileSize(item.tunnel.totalToDownstreamBytes) }}
                  </div>
                </div>
                <div class="stat-item">
                  <div class="stat-label">
                    {{ t('page.settings.tab.autostun.tunnel_upstream_bytes') }}
                  </div>
                  <div class="stat-value">
                    {{ formatFileSize(item.tunnel.totalToUpstreamBytes) }}
                  </div>
                </div>
              </div>
            </div>

            <!-- 操作按钮 -->
            <div class="tunnel-actions">
              <a-button
                type="primary"
                size="small"
                :disabled="!item.tunnel.valid"
                @click="$emit('viewConnections', item.downloader.id, item.downloader.name)"
              >
                <template #icon>
                  <icon-eye />
                </template>
                {{ t('page.settings.tab.autostun.view_connections') }}
              </a-button>
            </div>
          </a-card>
        </div>
      </div>
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
import { formatFileSize } from '@/utils/file.ts'
import { IconArrowRight, IconEye, IconLink } from '@arco-design/web-vue/es/icon'
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRequest } from 'vue-request'

const { t } = useI18n()

// Reactive data
const tunnels = ref<TunnelData[]>([])

// Auto-refresh tunnels using useAutoUpdatePlugin
const { loading } = useRequest(
  async () => {
    const res = await getAutoSTUNTunnels()
    if (res.success) {
      return res.data
    }
    return []
  },
  {
    manual: false,
    onSuccess: (data) => {
      tunnels.value = data || []
    },
    onError: (error) => {
      console.error('Failed to refresh tunnels:', error)
      tunnels.value = []
    }
  }
)

defineEmits<{
  viewConnections: [downloaderId: string, downloaderName: string]
}>()
</script>

<style scoped>
.empty-state {
  text-align: center;
  padding: 60px 20px;
}

.tunnel-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}

.tunnel-card {
  height: 100%;
}

.tunnel-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--color-border-2);
}

.tunnel-title {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.tunnel-title h4 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-1);
}

.type-tag {
  align-self: flex-start;
  font-size: 11px;
}

.tunnel-connection {
  margin-bottom: 16px;
  padding: 12px;
  background: var(--color-fill-1);
  border-radius: 6px;
}

.connection-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.connection-icon {
  color: var(--color-text-3);
  font-size: 14px;
}

.connection-text {
  font-size: 12px;
  font-family: 'Consolas', 'Monaco', monospace;
  color: var(--color-text-2);
  display: flex;
  align-items: center;
  gap: 6px;
}

.arrow-icon {
  font-size: 12px;
  color: var(--color-text-4);
}

.tunnel-stats {
  margin-bottom: 16px;
}

.stats-row {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
}

.stats-row:last-child {
  margin-bottom: 0;
}

.stat-item {
  flex: 1;
  min-width: 0;
}

.stat-label {
  font-size: 11px;
  color: var(--color-text-3);
  margin-bottom: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.stat-value {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-1);
  word-break: break-all;
}

.stat-value.success {
  color: var(--color-success-6);
}

.stat-value.error {
  color: var(--color-danger-6);
}

.tunnel-actions {
  padding-top: 12px;
  border-top: 1px solid var(--color-border-2);
}

/* 响应式设计 */
@media (max-width: 480px) {
  .tunnel-grid {
    grid-template-columns: 1fr;
    gap: 12px;
  }

  .tunnel-header {
    flex-direction: column;
    gap: 8px;
    align-items: flex-start;
  }

  .stats-row {
    flex-direction: column;
    gap: 8px;
  }

  .connection-text {
    flex-direction: column;
    align-items: flex-start;
    gap: 2px;
  }

  .arrow-icon {
    display: none;
  }
}

@media (max-width: 768px) {
  .tunnel-grid {
    grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  }
}
</style>
