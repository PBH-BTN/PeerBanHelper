<template>
  <a-space direction="vertical" fill>
    <a-typography-title :heading="5" style="text-indent: 2em">
      {{ t('page.settings.tab.autostun.title') }}
    </a-typography-title>

    <div style="width: 100%; display: flex; justify-content: center">
      <div style="max-width: 60rem; width: 100%">
        <a-alert type="info" style="margin-bottom: 20px">
          {{ t('page.settings.tab.autostun.description') }}
          <br />
          {{ t('page.settings.tab.autostun.warning') }}
        </a-alert>

        <a-spin :loading="firstLoading" style="width: 100%; min-height: 400px">
          <a-space v-if="!firstLoading" direction="vertical" fill size="large">
            <!-- NAT Status Section -->
            <NatStatus />

            <!-- Configuration Section -->
            <AutoStunConfigComponent />

            <!-- Tunnel Information Section -->
            <TunnelList @view-connections="handleViewConnections" />
          </a-space>
        </a-spin>

        <!-- Connection Table Modal -->
        <ConnectionModal
          v-model:visible="connectionModalVisible"
          :modal-title="connectionModalTitle"
          :connections="connections"
          :loading="loadingConnections"
        />
      </div>
    </div>
  </a-space>
</template>

<script setup lang="ts">
import type { ConnectionInfo } from '@/api/model/autostun'
import { getTunnelConnections } from '@/service/autostun'
import { useAutoUpdatePlugin } from '@/stores/autoUpdate'
import { Message } from '@arco-design/web-vue'
import { nextTick, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRequest } from 'vue-request'
import AutoStunConfigComponent from './autoStunConfig.vue'
import ConnectionModal from './connectionModal.vue'
import NatStatus from './natStatus.vue'
import TunnelList from './tunnelList.vue'

const { t } = useI18n()

// Reactive data
const firstLoading = ref(true)
const loadingConnections = ref(false)
const connectionModalVisible = ref(false)
const connectionModalTitle = ref('')
const currentConnectionDownloaderId = ref<string>('')

const connections = ref<ConnectionInfo[]>([])

// Watch for modal visibility changes to clean up connection data
watch(connectionModalVisible, (visible) => {
  if (!visible) {
    currentConnectionDownloaderId.value = ''
    connections.value = []
  }
})

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
    // Initialization complete
  } catch (error) {
    console.error('Failed to initialize AutoSTUN page:', error)
  } finally {
    firstLoading.value = false
  }
}

// Handlers

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
    const res = await getTunnelConnections(downloaderId)

    if (res.success && res.data) {
      // Ensure we have an array
      const connectionData = Array.isArray(res.data) ? res.data : []
      // Wait for next tick to ensure reactivity
      await nextTick()
      // Set the data
      connections.value = connectionData

      if (connections.value.length === 0) {
        console.warn('API returned success but no connections data')
        Message.warning(t('page.settings.tab.autostun.no_connections'))
      }

      // Start auto-refresh for connections
      refreshConnections()
    } else {
      Message.error(res.message || 'Failed to load connections')
      connections.value = []
    }
  } catch (error) {
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
