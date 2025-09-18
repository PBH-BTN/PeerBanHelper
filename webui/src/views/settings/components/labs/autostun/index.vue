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
        <a-modal
          v-model:visible="connectionModalVisible"
          :title="`${connectionModalTitle} - ${t('page.settings.tab.autostun.connection_table')}`"
          width="100rem"
          :footer="false"
          unmount-on-close
        >
          <ConnectionModal
            :downloader-id="currentConnectionDownloaderId"
            :modal-title="connectionModalTitle"
          />
        </a-modal>
      </div>
    </div>
  </a-space>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import AutoStunConfigComponent from './autoStunConfig.vue'
import ConnectionModal from './connectionModal.vue'
import NatStatus from './natStatus.vue'
import TunnelList from './tunnelList.vue'

const { t } = useI18n()

// Reactive data
const firstLoading = ref(true)
const connectionModalVisible = ref(false)
const connectionModalTitle = ref('')
const currentConnectionDownloaderId = ref<string>('')

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

const handleViewConnections = (downloaderId: string, downloaderName: string) => {
  connectionModalTitle.value = downloaderName
  currentConnectionDownloaderId.value = downloaderId
  connectionModalVisible.value = true
}

// Initialize on mount
init()
</script>
