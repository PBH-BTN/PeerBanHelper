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
          <a-spin :loading="loadingTunnelsRequest">
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
      unmount-on-close
    >
      <a-spin :loading="loadingConnections">
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
  </a-space>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRequest, usePagination } from 'vue-request'
import { Message } from '@arco-design/web-vue'
import { useAutoUpdatePlugin } from '@/stores/autoUpdate'
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

// Reactive data
const firstLoading = ref(true)
const refreshingNAT = ref(false)
const savingConfig = ref(false)
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
  try {
    await refreshTunnels()
  } catch (error) {
    console.error('Failed to load tunnels:', error)
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
    // Set up modal state
    connectionModalTitle.value = downloaderName
    loadingConnections.value = true

    // Clear previous connections to avoid showing stale data
    connections.value = []

    // Show modal after state is set
    connectionModalVisible.value = true

    console.log('Fetching connections for downloader:', downloaderId)
    const res = await getTunnelConnections(downloaderId)
    console.log('API response:', res)

    if (res.success && res.data) {
      console.log('Connection data:', res.data)
      console.log('Number of connections:', res.data.length)

      // Ensure we have an array
      const connectionData = Array.isArray(res.data) ? res.data : []

      // Wait for next tick to ensure reactivity
      await nextTick()

      // Set the data
      connections.value = connectionData

      console.log('Connections value set to:', connections.value)
      console.log('Connections length:', connections.value.length)

      if (connections.value.length === 0) {
        console.warn('API returned success but no connections data')
        Message.warning(t('page.settings.tab.autostun.no_connections'))
      } else {
        console.log('Successfully loaded', connections.value.length, 'connections')
      }
    } else {
      console.error('API returned error:', res.message)
      Message.error(res.message || 'Failed to load connections')
      connections.value = []
    }
  } catch (error) {
    console.error('Error loading connections:', error)
    Message.error(error instanceof Error ? error.message : 'Failed to load connections')
    // Keep modal open but show empty state
    connections.value = []
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

// Auto-refresh tunnels using useAutoUpdatePlugin
const { data: tunnelsData, loading: loadingTunnelsRequest, refresh: refreshTunnels } = useRequest(
  async () => {
    if (!config.enabled) {
      return []
    }

    const res = await getAutoSTUNTunnels()
    if (res.success) {
      return res.data
    }
    return []
  },
  {
    manual: true,
    onSuccess: (data) => {
      tunnels.value = data || []
    },
    onError: (error) => {
      console.error('Failed to refresh tunnels:', error)
      tunnels.value = []
    }
  },
  [useAutoUpdatePlugin]
)
</script>
