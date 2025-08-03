<template>
  <a-space direction="vertical" fill>
    <a-typography-title :heading="3">
      {{ t('page.settings.tab.autostun.title') }}
    </a-typography-title>

    <a-alert type="info" style="margin-bottom: 20px">
      {{ t('page.settings.tab.autostun.description') }}
    </a-alert>

    <a-alert type="warning" style="margin-bottom: 20px">
      {{ t('page.settings.tab.autostun.warning') }}
    </a-alert>

    <a-spin :loading="firstLoading" style="width: 100%; min-height: 400px">
      <a-space v-if="!firstLoading" direction="vertical" fill size="large">
        <!-- NAT Status Section -->
        <a-card :title="t('page.settings.tab.autostun.nat_status')">
          <a-row :gutter="20">
            <a-col :span="12">
              <a-space>
                <span>{{ t('page.settings.tab.autostun.nat_type') }}:</span>
                <a-tag :color="natTypeColor">{{ natTypeDisplayName }}</a-tag>
                <a-tag v-if="isNATCompatible" color="green">
                  {{ t('page.settings.tab.autostun.nat_compatible') }}
                </a-tag>
                <a-tag v-else color="red">
                  {{ t('page.settings.tab.autostun.nat_incompatible') }}
                </a-tag>
              </a-space>
            </a-col>
            <a-col :span="12" style="text-align: right">
              <a-button size="small" :loading="refreshingNAT" @click="handleRefreshNAT">
                {{
                  refreshingNAT
                    ? t('page.settings.tab.autostun.nat_type.refreshing')
                    : t('page.settings.tab.autostun.nat_type.refresh')
                }}
              </a-button>
            </a-col>
          </a-row>
        </a-card>

        <!-- Configuration Section -->
        <a-card :title="t('page.settings.tab.autostun.downloader_config')">
          <a-form :model="config" layout="vertical">
            <a-form-item field="enabled" :label="t('page.settings.tab.autostun.enable')">
              <a-switch
                v-model="config.enabled"
                :disabled="!isNATCompatible"
                @change="handleConfigChange"
              />
              <template #extra>
                {{ t('page.settings.tab.autostun.enable.tips') }}
              </template>
            </a-form-item>

            <a-form-item
              field="useFriendlyLoopbackMapping"
              :label="t('page.settings.tab.autostun.friendly_mapping')"
            >
              <a-switch
                v-model="config.useFriendlyLoopbackMapping"
                :disabled="!config.enabled"
                @change="handleConfigChange"
              />
              <template #extra>
                {{ t('page.settings.tab.autostun.friendly_mapping.tips') }}
              </template>
            </a-form-item>

            <a-form-item :label="t('page.settings.tab.autostun.select_downloaders')">
              <a-checkbox-group
                v-model="config.downloaders"
                :disabled="!config.enabled"
                @change="handleConfigChange"
              >
                <a-space v-if="downloaders.length > 0" direction="vertical">
                  <a-checkbox
                    v-for="downloader in downloaders"
                    :key="downloader.id"
                    :value="downloader.id"
                  >
                    {{ downloader.name }} ({{ downloader.type }})
                  </a-checkbox>
                </a-space>
                <a-empty v-else :description="t('page.settings.tab.autostun.no_downloaders')" />
              </a-checkbox-group>
            </a-form-item>

            <a-form-item>
              <a-button
                type="primary"
                :disabled="!configChanged"
                :loading="savingConfig"
                @click="handleSaveConfig"
              >
                {{ t('page.settings.tab.autostun.save_config') }}
              </a-button>
            </a-form-item>
          </a-form>
        </a-card>

        <!-- Tunnel Information Section -->
        <a-card :title="t('page.settings.tab.autostun.tunnel_info')">
          <a-spin :loading="loadingTunnels">
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
                      @click="handleViewConnections(item.downloader.id, item.downloader.name)"
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
                            <strong
                              >{{ t('page.settings.tab.autostun.tunnel_connections') }}:</strong
                            >
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
                              >{{
                                t('page.settings.tab.autostun.tunnel_downstream_bytes')
                              }}:</strong
                            >
                            {{ formatBytes(item.tunnel.totalToDownstreamBytes) }}
                          </div>
                          <div>
                            <strong
                              >{{ t('page.settings.tab.autostun.tunnel_upstream_bytes') }}:</strong
                            >
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
      </a-space>
    </a-spin>

    <!-- Connection Table Modal -->
    <a-modal
      v-model:visible="connectionModalVisible"
      :title="`${connectionModalTitle} - ${t('page.settings.tab.autostun.connection_table')}`"
      width="80%"
      :footer="false"
    >
      <a-spin :loading="loadingConnections">
        <div v-if="connections.length === 0 && !loadingConnections" style="text-align: center; padding: 40px">
          <a-empty :description="t('page.settings.tab.autostun.no_connections')" />
        </div>
        <div v-else-if="connections.length > 0">
          <p>Debug: Found {{ connections.length }} connections</p>
          <a-table :data="connections" :pagination="false" :scroll="{ x: 1000 }">
            <template #empty>
              <a-empty :description="t('page.settings.tab.autostun.no_connections')" />
            </template>
          <a-table-column
            :title="t('page.settings.tab.autostun.connection_downstream')"
            data-index="downstreamHost"
            :width="180"
          >
            <template #cell="{ record }">
              {{ record.downstreamHost }}:{{ record.downstreamPort }}
            </template>
          </a-table-column>
          <a-table-column
            :title="t('page.settings.tab.autostun.connection_proxy')"
            data-index="proxyHost"
            :width="180"
          >
            <template #cell="{ record }"> {{ record.proxyHost }}:{{ record.proxyPort }} </template>
          </a-table-column>
          <a-table-column
            :title="t('page.settings.tab.autostun.connection_upstream')"
            data-index="upstreamHost"
            :width="180"
          >
            <template #cell="{ record }">
              {{ record.upstreamHost }}:{{ record.upstreamPort }}
            </template>
          </a-table-column>
          <a-table-column
            :title="t('page.settings.tab.autostun.connection_established')"
            data-index="establishedAt"
            :width="150"
          >
            <template #cell="{ record }">
              {{ formatTimestamp(record.establishedAt) }}
            </template>
          </a-table-column>
          <a-table-column
            :title="t('page.settings.tab.autostun.connection_activity')"
            data-index="lastActivityAt"
            :width="150"
          >
            <template #cell="{ record }">
              {{ formatTimestamp(record.lastActivityAt) }}
            </template>
          </a-table-column>
          <a-table-column
            :title="t('page.settings.tab.autostun.connection_bytes')"
            data-index="bytes"
            :width="200"
          >
            <template #cell="{ record }">
              <div>↓ {{ formatBytes(record.toDownstreamBytes) }}</div>
              <div>↑ {{ formatBytes(record.toUpstreamBytes) }}</div>
            </template>
          </a-table-column>
        </a-table>
        </div>
      </a-spin>
    </a-modal>
  </a-space>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRequest } from 'vue-request'
import { Message } from '@arco-design/web-vue'
import {
  getAutoSTUNStatus,
  saveAutoSTUNConfig,
  getAutoSTUNTunnels,
  getTunnelConnections,
  refreshNATType
} from '@/service/autostun'
import { getDownloaders } from '@/service/downloaders'
import type {
  AutoSTUNConfig,
  TunnelData,
  ConnectionInfo,
  NATType,
  DownloaderBasicInfo
} from '@/api/model/autostun'
import dayjs from 'dayjs'

const { t } = useI18n()

// Reactive data
const firstLoading = ref(true)
const refreshingNAT = ref(false)
const savingConfig = ref(false)
const loadingTunnels = ref(false)
const loadingConnections = ref(false)
const connectionModalVisible = ref(false)
const connectionModalTitle = ref('')
const configChanged = ref(false)

const config = reactive<AutoSTUNConfig>({
  enabled: false,
  useFriendlyLoopbackMapping: false,
  downloaders: []
})

const originalConfig = reactive<AutoSTUNConfig>({
  enabled: false,
  useFriendlyLoopbackMapping: false,
  downloaders: []
})

const currentNATType = ref<NATType>('Unknown')
const tunnels = ref<TunnelData[]>([])
const connections = ref<ConnectionInfo[]>([])
const downloaders = ref<DownloaderBasicInfo[]>([])

// Computed properties
const isNATCompatible = computed(() => currentNATType.value === 'FullCone')

const natTypeDisplayName = computed(() => {
  return t(`page.settings.tab.autostun.nat_type.${currentNATType.value}`)
})

const natTypeColor = computed(() => {
  const typeColors: Record<NATType, string> = {
    UdpBlocked: 'red',
    OpenInternet: 'green',
    SymmetricUdpFirewall: 'orange',
    FullCone: 'green',
    RestrictedCone: 'orange',
    PortRestrictedCone: 'orange',
    Symmetric: 'red',
    Unknown: 'gray'
  }
  return typeColors[currentNATType.value] || 'gray'
})

// Watch for config changes
watch(
  config,
  () => {
    configChanged.value = JSON.stringify(config) !== JSON.stringify(originalConfig)
  },
  { deep: true }
)

// Load initial data
const { refresh: refreshStatus } = useRequest(
  async () => {
    const [statusRes, downloadersRes] = await Promise.all([getAutoSTUNStatus(), getDownloaders()])
    return { statusRes, downloadersRes }
  },
  {
    onSuccess: ({ statusRes, downloadersRes }) => {
      if (statusRes.success) {
        const status = statusRes.data
        config.enabled = status.enabled
        config.useFriendlyLoopbackMapping = status.useFriendlyLoopbackMapping
        config.downloaders = status.selectedDownloaders.map((d) => d.id)

        // Save original config
        Object.assign(originalConfig, { ...config })

        currentNATType.value = status.natType
        configChanged.value = false
      }

      if (downloadersRes.success) {
        downloaders.value = downloadersRes.data.map((d) => ({
          id: d.id,
          name: d.name,
          type: d.type
        }))
      }

      firstLoading.value = false
      loadTunnels()
    },
    onError: (error) => {
      firstLoading.value = false
      Message.error(error.message || 'Failed to load AutoSTUN status')
    }
  }
)

// Load tunnels
const loadTunnels = async () => {
  if (!config.enabled) {
    tunnels.value = []
    return
  }

  try {
    loadingTunnels.value = true
    const res = await getAutoSTUNTunnels()
    if (res.success) {
      tunnels.value = res.data
    }
  } catch (error) {
    console.error('Failed to load tunnels:', error)
  } finally {
    loadingTunnels.value = false
  }
}

// Handlers
const handleRefreshNAT = async () => {
  try {
    refreshingNAT.value = true
    const res = await refreshNATType()
    if (res.success) {
      Message.success('NAT type refresh requested')
      // Refresh status after a delay
      setTimeout(() => {
        refreshStatus()
      }, 3000)
    } else {
      throw new Error(res.message)
    }
  } catch (error) {
    Message.error(error instanceof Error ? error.message : 'Failed to refresh NAT type')
  } finally {
    refreshingNAT.value = false
  }
}

const handleConfigChange = () => {
  // Config change is automatically tracked by the watcher
}

const handleSaveConfig = async () => {
  try {
    savingConfig.value = true
    const res = await saveAutoSTUNConfig(config)
    if (res.success) {
      Message.success(t('page.settings.tab.autostun.save_success'))
      Object.assign(originalConfig, { ...config })
      configChanged.value = false
      loadTunnels()
    } else {
      throw new Error(res.message)
    }
  } catch (error) {
    Message.error(
      error instanceof Error ? error.message : t('page.settings.tab.autostun.save_failed')
    )
  } finally {
    savingConfig.value = false
  }
}

const handleViewConnections = async (downloaderId: string, downloaderName: string) => {
  try {
    connectionModalTitle.value = downloaderName
    connectionModalVisible.value = true
    loadingConnections.value = true

    console.log('Fetching connections for downloader:', downloaderId)
    const res = await getTunnelConnections(downloaderId)
    console.log('API response:', res)
    
    if (res.success) {
      console.log('Connection data:', res.data)
      connections.value = res.data
      console.log('Connections value set to:', connections.value)
    } else {
      console.error('API returned error:', res.message)
      throw new Error(res.message)
    }
  } catch (error) {
    console.error('Error loading connections:', error)
    Message.error(error instanceof Error ? error.message : 'Failed to load connections')
  } finally {
    loadingConnections.value = false
  }
}

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

// Auto-refresh tunnels every 30 seconds when enabled
setInterval(() => {
  if (config.enabled && !firstLoading.value) {
    loadTunnels()
  }
}, 30000)
</script>
