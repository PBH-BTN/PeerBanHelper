<template>
  <a-card :title="t('page.settings.tab.autostun.downloader_config')">
    <a-form :model="config" layout="vertical">
      <a-form-item field="enabled" :label="t('page.settings.tab.autostun.enable')">
        <a-switch
          :model-value="config.enabled"
          :disabled="(!isNATCompatible || isBridgeNetDriver) && !config.enabled"
          @update:model-value="(value) => $emit('configChange', { enabled: value as boolean })"
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
          :model-value="config.useFriendlyLoopbackMapping"
          :disabled="!config.enabled"
          @update:model-value="
            (value) => $emit('configChange', { useFriendlyLoopbackMapping: value as boolean })
          "
        />
        <template #extra>
          {{ t('page.settings.tab.autostun.friendly_mapping.tips') }}
        </template>
      </a-form-item>

      <a-form-item :label="t('page.settings.tab.autostun.select_downloaders')">
        <a-checkbox-group
          :model-value="config.downloaders"
          :disabled="!config.enabled"
          @update:model-value="(value) => $emit('configChange', { downloaders: value as string[] })"
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
          @click="$emit('saveConfig')"
        >
          {{ t('page.settings.tab.autostun.save_config') }}
        </a-button>
      </a-form-item>
    </a-form>
  </a-card>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { AutoSTUNConfig, DownloaderBasicInfo } from '@/api/model/autostun'

const { t } = useI18n()

// Props
interface Props {
  config: AutoSTUNConfig
  downloaders: DownloaderBasicInfo[]
  isNATCompatible: boolean
  isBridgeNetDriver: boolean
  configChanged: boolean
  savingConfig: boolean
}

defineProps<Props>()

// Emits
defineEmits<{
  configChange: [config: Partial<AutoSTUNConfig>]
  saveConfig: []
}>()
</script>
