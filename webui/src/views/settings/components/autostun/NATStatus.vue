<template>
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
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { NATType } from '@/api/model/autostun'

const { t } = useI18n()

// Props
interface Props {
  natType: NATType
  refreshingNAT: boolean
}

const props = defineProps<Props>()

// Emits
const emit = defineEmits<{
  refreshNAT: []
}>()

// Computed
const natTypeDisplayName = computed(() => {
  return t(`page.settings.tab.autostun.nat_type.${props.natType.toLowerCase()}`)
})

const natTypeColor = computed(() => {
  switch (props.natType) {
    case 'FullCone':
      return 'green'
    case 'RestrictedCone':
    case 'PortRestrictedCone':
      return 'orange'
    case 'Symmetric':
    case 'UdpBlocked':
      return 'red'
    default:
      return 'gray'
  }
})

const isNATCompatible = computed(() => {
  return props.natType === 'FullCone'
})

// Event handlers
const handleRefreshNAT = () => {
  emit('refreshNAT')
}
</script>