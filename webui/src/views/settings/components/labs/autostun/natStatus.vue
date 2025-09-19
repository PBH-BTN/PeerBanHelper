<template>
  <a-descriptions :column="1" size="large" :align="{ label: 'right' }">
    <template #title>
      <a-space align="center">
        {{ t('page.settings.tab.autostun.nat_status') }}
        <AsyncMethod v-slot="{ run, loading }" once :async-fn="handleRefreshNAT">
          <a-tooltip :content="t('page.settings.tab.autostun.nat_type.refresh')">
            <a-button size="small" shape="circle" type="text" class="refresh-btn" @click="run">
              <template #icon>
                <icon-refresh :spin="loading" />
              </template>
            </a-button>
          </a-tooltip>
        </AsyncMethod>
      </a-space>
    </template>

    <a-descriptions-item :label="t('page.settings.tab.autostun.nat_type')">
      <a-space>
        {{ t(`page.settings.tab.autostun.nat_type.${natType}`) }}
        <a-tooltip
          v-if="!isNATCompatible"
          :content="t('page.settings.tab.autostun.nat_incompatible.tooltip')"
        >
          <a-tag color="red">
            <template #icon>
              <icon-close />
            </template>
            {{ t('page.settings.tab.autostun.nat_incompatible') }}
          </a-tag>
        </a-tooltip>
      </a-space>
    </a-descriptions-item>

    <a-descriptions-item :label="t('page.settings.tab.autostun.network_driver')">
      <a-space>
        <a-tag :color="isBridgeNetDriver ? 'red' : 'green'">
          <template #icon>
            <icon-check v-if="!isBridgeNetDriver" />
            <icon-close v-else />
          </template>
          {{
            isBridgeNetDriver
              ? t('page.settings.tab.autostun.netdriver_incompatible')
              : t('page.settings.tab.autostun.netdriver_compatible')
          }}&nbsp;<a-tooltip
            v-if="isBridgeNetDriver"
            :content="t('page.settings.tab.autostun.netdriver_incompatible.tooltip')"
          >
            <icon-info-circle />
          </a-tooltip>
        </a-tag>
      </a-space>
    </a-descriptions-item>

    <a-descriptions-item :label="t('page.settings.tab.autostun.compatibility')">
      <a-tag :color="isNATCompatible && !isBridgeNetDriver ? 'green' : 'red'">
        <template #icon>
          <icon-check v-if="isNATCompatible && !isBridgeNetDriver" />
          <icon-close v-else />
        </template>
        {{
          isNATCompatible && !isBridgeNetDriver
            ? t('page.settings.tab.autostun.compatible')
            : t('page.settings.tab.autostun.incompatible')
        }}
      </a-tag>
    </a-descriptions-item>
  </a-descriptions>
</template>

<script setup lang="ts">
import type { NATType } from '@/api/model/autostun'
import AsyncMethod from '@/components/asyncMethod.vue'
import { getAutoSTUNStatus, refreshNATType } from '@/service/autostun'
import { useAutoUpdatePlugin } from '@/stores/autoUpdate'
import { Message } from '@arco-design/web-vue'
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRequest } from 'vue-request'

const { t } = useI18n()

// Reactive data
const natType = ref<NATType>('Unknown')
const isBridgeNetDriverRef = ref<boolean>(false)

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
    manual: false,
    onSuccess: (data) => {
      natType.value = data.natType
      isBridgeNetDriverRef.value = data.isBridgeNetDriver
    },
    onError: (error) => {
      console.error('Failed to refresh NAT status:', error)
    }
  },
  [useAutoUpdatePlugin]
)

const isNATCompatible = computed(() => {
  return natType.value === 'FullCone'
})

const isBridgeNetDriver = computed(() => {
  return isBridgeNetDriverRef.value
})

// Event handlers
const handleRefreshNAT = async () => {
  try {
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
    throw error // Re-throw for AsyncMethod to handle loading state
  }
}
</script>

<style scoped>
.refresh-btn {
  color: var(--color-text-3);
  transition: color 0.2s ease;
}

.refresh-btn:hover {
  color: rgb(var(--primary-6));
}
</style>
