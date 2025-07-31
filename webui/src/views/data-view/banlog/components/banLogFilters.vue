<template>
  <div class="ban-log-filters">
    <div class="filter-header">
      <a-button @click="toggleFilters" type="outline" size="small">
        <template #icon>
          <icon-filter v-if="!showFilters" />
          <icon-eye-invisible v-else />
        </template>
        {{ showFilters ? t('page.banlog.filters.hideFilters') : t('page.banlog.filters.showFilters') }}
      </a-button>
      <a-button 
        @click="resetFilters" 
        type="outline" 
        size="small" 
        :disabled="!hasActiveFilters"
        style="margin-left: 8px;"
      >
        <template #icon>
          <icon-refresh />
        </template>
        {{ t('page.banlog.filters.reset') }}
      </a-button>
    </div>
    
    <a-collapse v-model:active-key="activeKeys" :bordered="false">
      <a-collapse-item key="filters">
        <div class="filters-content">
          <a-row :gutter="16">
            <a-col :span="8">
              <a-form-item :label="t('page.banlog.filters.reason')" style="margin-bottom: 16px;">
                <a-input 
                  v-model="localFilters.reason" 
                  :placeholder="t('page.banlog.filters.reasonPlaceholder')"
                  allow-clear
                />
              </a-form-item>
            </a-col>
            <a-col :span="8">
              <a-form-item :label="t('page.banlog.filters.clientName')" style="margin-bottom: 16px;">
                <a-select 
                  v-model="localFilters.clientName" 
                  :placeholder="t('page.banlog.filters.clientNamePlaceholder')"
                  allow-clear
                  allow-search
                  :options="clientNameOptions"
                />
              </a-form-item>
            </a-col>
            <a-col :span="8">
              <a-form-item :label="t('page.banlog.filters.peerId')" style="margin-bottom: 16px;">
                <a-input 
                  v-model="localFilters.peerId" 
                  :placeholder="t('page.banlog.filters.peerIdPlaceholder')"
                  allow-clear
                />
              </a-form-item>
            </a-col>
          </a-row>
          
          <a-row :gutter="16">
            <a-col :span="8">
              <a-form-item :label="t('page.banlog.filters.torrentName')" style="margin-bottom: 16px;">
                <a-input 
                  v-model="localFilters.torrentName" 
                  :placeholder="t('page.banlog.filters.torrentNamePlaceholder')"
                  allow-clear
                />
              </a-form-item>
            </a-col>
            <a-col :span="8">
              <a-form-item :label="t('page.banlog.filters.module')" style="margin-bottom: 16px;">
                <a-select 
                  v-model="localFilters.module" 
                  :placeholder="t('page.banlog.filters.modulePlaceholder')"
                  allow-clear
                  allow-search
                  :options="moduleOptions"
                />
              </a-form-item>
            </a-col>
            <a-col :span="8">
              <a-form-item :label="t('page.banlog.filters.rule')" style="margin-bottom: 16px;">
                <a-select 
                  v-model="localFilters.rule" 
                  :placeholder="t('page.banlog.filters.rulePlaceholder')"
                  allow-clear
                  allow-search
                  :options="ruleOptions"
                />
              </a-form-item>
            </a-col>
          </a-row>
          
          <a-row :gutter="16">
            <a-col :span="8">
              <a-form-item :label="t('page.banlog.filters.context')" style="margin-bottom: 16px;">
                <a-select 
                  v-model="localFilters.context" 
                  :placeholder="t('page.banlog.filters.contextPlaceholder')"
                  allow-clear
                  allow-search
                  :options="contextOptions"
                />
              </a-form-item>
            </a-col>
          </a-row>
        </div>
      </a-collapse-item>
    </a-collapse>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { debounce } from 'lodash'
import type { BanLogFilters } from '@/service/banLogs'
import type { BanLog } from '@/api/model/banlogs'

const { t } = useI18n()

// Props
interface Props {
  modelValue: BanLogFilters
  banLogs: BanLog[]
}

const props = defineProps<Props>()

// Emits
const emit = defineEmits<{
  'update:modelValue': [value: BanLogFilters]
}>()

// State
const showFilters = ref(false)
const activeKeys = ref<string[]>([])
const localFilters = ref<BanLogFilters>({ ...props.modelValue })

// Computed
const hasActiveFilters = computed(() => {
  return Object.values(localFilters.value).some(value => value && value.trim() !== '')
})

// Dynamic options for dropdowns
const clientNameOptions = computed(() => {
  const names = [...new Set(props.banLogs.map(ban => ban.peerClientName).filter(Boolean))]
  return names.map(name => ({ label: name, value: name }))
})

const moduleOptions = computed(() => {
  const modules = [...new Set(props.banLogs.map(ban => ban.module).filter(Boolean))]
  return modules.map(module => ({ label: module, value: module }))
})

const ruleOptions = computed(() => {
  // Use description as rule for ban logs since rule property is not available
  const rules = [...new Set(props.banLogs.map(ban => ban.description).filter(Boolean))]
  return rules.map(rule => ({ label: rule, value: rule }))
})

const contextOptions = computed(() => {
  // Context is not directly available in BanLog, using description as fallback
  const contexts = [...new Set(props.banLogs.map(ban => ban.description).filter(Boolean))]
  return contexts.map(context => ({ label: context, value: context }))
})

// Methods
const toggleFilters = () => {
  showFilters.value = !showFilters.value
  activeKeys.value = showFilters.value ? ['filters'] : []
}

const resetFilters = () => {
  localFilters.value = {}
  emitFilters()
}

const emitFilters = debounce(() => {
  emit('update:modelValue', { ...localFilters.value })
}, 300)

// Watchers
watch(localFilters, () => {
  emitFilters()
}, { deep: true })

watch(() => props.modelValue, (newValue) => {
  localFilters.value = { ...newValue }
}, { deep: true })

watch(activeKeys, (newKeys) => {
  showFilters.value = newKeys.includes('filters')
})
</script>

<style scoped>
.ban-log-filters {
  margin-bottom: 16px;
}

.filter-header {
  margin-bottom: 8px;
}

.filters-content {
  padding: 16px;
  background-color: var(--color-fill-2);
  border-radius: 6px;
}

:deep(.arco-collapse-item-header) {
  display: none;
}

:deep(.arco-collapse-item-content) {
  padding: 0;
}
</style>