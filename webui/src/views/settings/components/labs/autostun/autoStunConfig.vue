<template>
  <a-space direction="vertical" fill>
    <a-typography-title :heading="6">
      {{ t('page.settings.tab.autostun.downloader_config') }}
    </a-typography-title>

    <a-form :model="config">
      <a-form-item field="enabled">
        <template #label>
          {{ t('page.settings.tab.autostun.enable') }}
          <a-tooltip :content="t('page.settings.tab.autostun.enable.tips')">
            <icon-info-circle style="margin-left: 4px; color: var(--color-text-3)" />
          </a-tooltip>
        </template>
        <a-switch
          :model-value="config.enabled"
          @update:model-value="(value) => handleConfigChange({ enabled: value as boolean })"
        />
      </a-form-item>

      <a-form-item field="useFriendlyLoopbackMapping">
        <template #label>
          {{ t('page.settings.tab.autostun.friendly_mapping') }}
          <a-tooltip :content="t('page.settings.tab.autostun.friendly_mapping.tips')">
            <icon-info-circle style="margin-left: 4px; color: var(--color-text-3)" />
          </a-tooltip>
        </template>
        <a-switch
          :model-value="config.useFriendlyLoopbackMapping"
          :disabled="!config.enabled"
          @update:model-value="
            (value) => handleConfigChange({ useFriendlyLoopbackMapping: value as boolean })
          "
        />
      </a-form-item>

      <a-form-item :label="t('page.settings.tab.autostun.select_downloaders')">
        <a-transfer
          v-if="(allDownloaders?.data?.length ?? 0) > 0"
          v-model="config.downloaders"
          simple
          :data="transferData"
          :disabled="!config.enabled"
          :title="[
            t('page.settings.tab.autostun.available_downloaders'),
            t('page.settings.tab.autostun.enabled_downloaders')
          ]"
        />
        <a-empty v-else :description="t('page.settings.tab.autostun.no_downloaders')" />
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
  </a-space>
</template>

<script setup lang="ts">
import type { AutoSTUNConfig } from '@/api/model/autostun'
import { getAutoSTUNStatus, saveAutoSTUNConfig } from '@/service/autostun'
import { getDownloaders } from '@/service/downloaders'
import { useAutoUpdatePlugin } from '@/stores/autoUpdate'
import { Message } from '@arco-design/web-vue'
import { computed, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRequest } from 'vue-request'

const { t } = useI18n()

// Reactive data
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

const savingConfig = ref(false)

// Load all downloaders (rarely changes, independent request)
const { data: allDownloaders } = useRequest(getDownloaders, {
  manual: false,
  onError: (error) => {
    console.error('Failed to load downloaders:', error)
  }
})

// Load AutoSTUN status (depends on downloaders being loaded)
const { refresh } = useRequest(
  getAutoSTUNStatus,
  {
    ready: computed(() => (allDownloaders?.value?.data?.length ?? 0) > 0),
    manual: false,
    onSuccess: (response) => {
      if (response.success) {
        const data = response.data
        // Map the selected downloaders to an array of IDs for the transfer component
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
      }
    },
    onError: (error) => {
      console.error('Failed to refresh config status:', error)
    }
  },
  [useAutoUpdatePlugin]
)

// Computed
const configChanged = computed(() => {
  return (
    config.enabled !== originalConfig.enabled ||
    config.useFriendlyLoopbackMapping !== originalConfig.useFriendlyLoopbackMapping ||
    JSON.stringify(config.downloaders) !== JSON.stringify(originalConfig.downloaders)
  )
})

const transferData = computed(() => {
  return (
    allDownloaders.value?.data?.map((downloader) => ({
      key: downloader.id,
      label: `${downloader.name} (${downloader.type})`,
      value: downloader.id,
      disabled: false
    })) || []
  )
})

// Handle config changes
const handleConfigChange = (partialConfig: Partial<AutoSTUNConfig>) => {
  Object.assign(config, partialConfig)
}

// Handle save config
const handleSaveConfig = async () => {
  try {
    savingConfig.value = true
    const res = await saveAutoSTUNConfig(config)
    if (res.success) {
      Message.success(t('page.settings.tab.autostun.save_success'))
      Object.assign(originalConfig, { ...config })
    } else {
      throw new Error(res.message)
    }
  } catch (error) {
    Message.error(
      error instanceof Error ? error.message : t('page.settings.tab.autostun.save_failed')
    )
  } finally {
    savingConfig.value = false
    refresh()
  }
}
</script>
