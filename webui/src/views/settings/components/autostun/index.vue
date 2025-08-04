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
        <NatStatus
          :nat-type="natType"
          :refreshing-n-a-t="refreshingNAT"
          @refresh-n-a-t="handleRefreshNAT"
        />

        <!-- Configuration Section -->
        <AutoStunConfigComponent
          :config="config"
          :downloaders="downloaders"
          :is-n-a-t-compatible="isNATCompatible"
          :config-changed="configChanged"
          :saving-config="savingConfig"
          @config-change="(partialConfig) => Object.assign(config, partialConfig)"
          @save-config="handleSaveConfig"
        />

        <!-- Tunnel Information Section -->
        <TunnelList
          :tunnels="tunnels"
          :loading="loadingTunnelsRequest"
          @view-connections="handleViewConnections"
        />
      </a-space>
    </a-spin>

    <!-- Connection Table Modal -->
    <ConnectionModal
      v-model:visible="connectionModalVisible"
      :modal-title="connectionModalTitle"
      :connections="connections"
      :loading="loadingConnections"
    />
  </a-space>
</template>

<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRequest } from 'vue-request'
import { Message } from '@arco-design/web-vue'
import { useAutoUpdatePlugin } from '@/stores/autoUpdate'
import {
  getAutoSTUNStatus,
  getAutoSTUNTunnels,
  getTunnelConnections,
  refreshNATType,
  saveAutoSTUNConfig
} from '@/service/autostun'
import { getDownloaders } from '@/service/downloaders'
import type {
  AutoSTUNConfig,
  ConnectionInfo,
  DownloaderBasicInfo,
  NATType,
  TunnelData
} from '@/api/model/autostun'
import NatStatus from './natStatus.vue'
import AutoStunConfigComponent from './autoStunConfig.vue'
import TunnelList from './tunnelList.vue'
import ConnectionModal from './connectionModal.vue'

const { t } = useI18n()

// Reactive data
const firstLoading = ref(true)
const refreshingNAT = ref(false)
const savingConfig = ref(false)
const loadingConnections = ref(false)
const connectionModalVisible = ref(false)
const connectionModalTitle = ref('')
const configChanged = ref(false)
const currentConnectionDownloaderId = ref<string>('')

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

const natType = ref<NATType>('Unknown')
const tunnels = ref<TunnelData[]>([])
const connections = ref<ConnectionInfo[]>([])
const downloaders = ref<DownloaderBasicInfo[]>([])

// Computed properties
const isNATCompatible = computed(() => {
  return natType.value === 'FullCone'
})

// Watch for config changes
watch(
  () => ({ ...config }),
  () => {
    configChanged.value =
      config.enabled !== originalConfig.enabled ||
      config.useFriendlyLoopbackMapping !== originalConfig.useFriendlyLoopbackMapping ||
      JSON.stringify(config.downloaders) !== JSON.stringify(originalConfig.downloaders)
  },
  { deep: true }
)

// Watch for modal visibility changes to clean up connection data
watch(connectionModalVisible, (visible) => {
  if (!visible) {
    currentConnectionDownloaderId.value = ''
    connections.value = []
  }
})

// Auto-refresh NAT status using useAutoUpdatePlugin
const { refresh: refreshStatus } = useRequest(
  async () => {
    const res = await getAutoSTUNStatus()
    if (res.success) {
      return res.data
    }
    throw new Error(res.message || 'Failed to get AutoSTUN status')
  },
  {
    manual: true,
    onSuccess: (data) => {
      natType.value = data.natType
      // Map the selected downloaders to an array of IDs
      const downloaderIds = data.selectedDownloaders.map((d) => d.id)
      Object.assign(config, {
        enabled: data.enabled,
        useFriendlyLoopbackMapping: data.useFriendlyLoopbackMapping,
        downloaders: downloaderIds
      })
      Object.assign(originalConfig, {
        enabled: data.enabled,
        useFriendlyLoopbackMapping: data.useFriendlyLoopbackMapping,
        downloaders: downloaderIds
      })
      configChanged.value = false
    },
    onError: (error) => {
      console.error('Failed to refresh status:', error)
    }
  },
  [useAutoUpdatePlugin]
)

// Auto-refresh tunnels using useAutoUpdatePlugin
const { loading: loadingTunnelsRequest, refresh: refreshTunnels } = useRequest(
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

// Auto-refresh connections when modal is visible
const { refresh: refreshConnections } = useRequest(
  async () => {
    if (!connectionModalVisible.value || !currentConnectionDownloaderId.value) {
      return []
    }

    const res = await getTunnelConnections(currentConnectionDownloaderId.value)
    if (res.success && res.data) {
      return Array.isArray(res.data) ? res.data : []
    }
    return []
  },
  {
    manual: true,
    refreshDeps: [connectionModalVisible],
    onSuccess: (data) => {
      if (connectionModalVisible.value) {
        connections.value = data || []
      }
    },
    onError: (error) => {
      console.error('Failed to refresh connections:', error)
      if (connectionModalVisible.value) {
        connections.value = []
      }
    }
  },
  [useAutoUpdatePlugin]
)

// Initialize data
const init = async () => {
  try {
    firstLoading.value = true

    // Load AutoSTUN status
    await refreshStatus()

    // Load downloaders
    const downloadersRes = await getDownloaders()
    if (downloadersRes.success) {
      downloaders.value = downloadersRes.data
    }

    // Load tunnels if enabled
    if (config.enabled) {
      await loadTunnels()
    }
  } catch (error) {
    console.error('Failed to initialize AutoSTUN page:', error)
    Message.error('Failed to load AutoSTUN status')
  } finally {
    firstLoading.value = false
  }
}

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
      Message.success(t('page.settings.tab.autostun.nat_type.refreshing'))
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
    currentConnectionDownloaderId.value = downloaderId
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

      // Start auto-refresh for connections
      refreshConnections()
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

// Initialize on mount
init()
</script>
