<template>
  <div class="filter-bar">
    <!-- Active Filters and Controls -->
    <div class="filter-controls">
      <div class="filter-chips">
        <!-- Active filter chips -->
        <a-tag
          v-for="(value, key) in activeFilters"
          :key="key"
          closable
          @close="removeFilter(key as keyof BanLogFilters)"
          class="filter-chip"
        >
          <span class="filter-label">{{ getFilterLabel(key as keyof BanLogFilters) }}:</span>
          <span class="filter-value">{{ value }}</span>
        </a-tag>
      </div>

      <div class="filter-actions">
        <!-- Add Filter Dropdown -->
        <a-dropdown trigger="click" position="bottom">
          <a-button type="outline" size="small">
            <template #icon>
              <icon-plus />
            </template>
            {{ t('page.banlog.filters.addFilter') }}
          </a-button>
          <template #content>
            <a-doption
              v-for="filterType in availableFilters"
              :key="filterType.key"
              @click="addFilter(filterType.key)"
            >
              <template #icon>
                <component :is="filterType.icon" />
              </template>
              {{ filterType.label }}
            </a-doption>
          </template>
        </a-dropdown>

        <!-- Reset Button -->
        <a-button
          v-if="hasActiveFilters"
          type="outline"
          size="small"
          @click="resetFilters"
          status="warning"
        >
          <template #icon>
            <icon-refresh />
          </template>
          {{ t('page.banlog.filters.reset') }}
        </a-button>
      </div>
    </div>

    <!-- Active Filter Inputs -->
    <div v-if="hasActiveInputs" class="filter-inputs">
      <a-space wrap size="small">
        <!-- Reason Input -->
        <div v-if="showFilterInput('reason')" class="filter-input-group">
          <span class="input-label">{{ t('page.banlog.filters.reason') }}</span>
          <a-input
            v-model="localFilters.reason"
            :placeholder="t('page.banlog.filters.reasonPlaceholder')"
            size="small"
            style="width: 200px"
            allow-clear
          />
        </div>

        <!-- Client Name Select -->
        <div v-if="showFilterInput('clientName')" class="filter-input-group">
          <span class="input-label">{{ t('page.banlog.filters.clientName') }}</span>
          <a-select
            v-model="localFilters.clientName"
            :placeholder="t('page.banlog.filters.clientNamePlaceholder')"
            size="small"
            style="width: 200px"
            allow-clear
            allow-search
            :options="clientNameOptions"
          />
        </div>

        <!-- PeerID Input -->
        <div v-if="showFilterInput('peerId')" class="filter-input-group">
          <span class="input-label">{{ t('page.banlog.filters.peerId') }}</span>
          <a-input
            v-model="localFilters.peerId"
            :placeholder="t('page.banlog.filters.peerIdPlaceholder')"
            size="small"
            style="width: 200px"
            allow-clear
          />
        </div>

        <!-- Torrent Name Input -->
        <div v-if="showFilterInput('torrentName')" class="filter-input-group">
          <span class="input-label">{{ t('page.banlog.filters.torrentName') }}</span>
          <a-input
            v-model="localFilters.torrentName"
            :placeholder="t('page.banlog.filters.torrentNamePlaceholder')"
            size="small"
            style="width: 200px"
            allow-clear
          />
        </div>

        <!-- Module Select -->
        <div v-if="showFilterInput('module')" class="filter-input-group">
          <span class="input-label">{{ t('page.banlog.filters.module') }}</span>
          <a-select
            v-model="localFilters.module"
            :placeholder="t('page.banlog.filters.modulePlaceholder')"
            size="small"
            style="width: 200px"
            allow-clear
            allow-search
            :options="moduleOptions"
          />
        </div>

        <!-- Rule Select -->
        <div v-if="showFilterInput('rule')" class="filter-input-group">
          <span class="input-label">{{ t('page.banlog.filters.rule') }}</span>
          <a-select
            v-model="localFilters.rule"
            :placeholder="t('page.banlog.filters.rulePlaceholder')"
            size="small"
            style="width: 200px"
            allow-clear
            allow-search
            :options="ruleOptions"
          />
        </div>

        <!-- Context Select -->
        <div v-if="showFilterInput('context')" class="filter-input-group">
          <span class="input-label">{{ t('page.banlog.filters.context') }}</span>
          <a-select
            v-model="localFilters.context"
            :placeholder="t('page.banlog.filters.contextPlaceholder')"
            size="small"
            style="width: 200px"
            allow-clear
            allow-search
            :options="contextOptions"
          />
        </div>
      </a-space>
    </div>
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
const localFilters = ref<BanLogFilters>({ ...props.modelValue })

// Track which filters are currently being shown for input
const activeInputs = ref<Set<keyof BanLogFilters>>(new Set())

// Computed
const activeFilters = computed(() => {
  const active: Partial<BanLogFilters> = {}
  Object.entries(localFilters.value).forEach(([key, value]) => {
    if (value && value.trim()) {
      active[key as keyof BanLogFilters] = value.trim()
    }
  })
  return active
})

const hasActiveFilters = computed(() => {
  return Object.keys(activeFilters.value).length > 0
})

const hasActiveInputs = computed(() => {
  return activeInputs.value.size > 0
})

const availableFilters = computed(() => {
  const filters = [
    { key: 'reason', label: t('page.banlog.filters.reason'), icon: 'icon-message' },
    { key: 'clientName', label: t('page.banlog.filters.clientName'), icon: 'icon-user' },
    { key: 'peerId', label: t('page.banlog.filters.peerId'), icon: 'icon-idcard' },
    { key: 'torrentName', label: t('page.banlog.filters.torrentName'), icon: 'icon-file' },
    { key: 'module', label: t('page.banlog.filters.module'), icon: 'icon-apps' },
    { key: 'rule', label: t('page.banlog.filters.rule'), icon: 'icon-shield' },
    { key: 'context', label: t('page.banlog.filters.context'), icon: 'icon-compass' }
  ]

  // Filter out already active inputs
  return filters.filter(f => !activeInputs.value.has(f.key as keyof BanLogFilters))
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
const getFilterLabel = (key: keyof BanLogFilters): string => {
  const labels: Record<keyof BanLogFilters, string> = {
    reason: t('page.banlog.filters.reason'),
    clientName: t('page.banlog.filters.clientName'),
    peerId: t('page.banlog.filters.peerId'),
    torrentName: t('page.banlog.filters.torrentName'),
    module: t('page.banlog.filters.module'),
    rule: t('page.banlog.filters.rule'),
    context: t('page.banlog.filters.context'),
    // These fields are included in the interface but not currently used in ban log UI
    country: 'Country',
    city: 'City', 
    asn: 'ASN',
    isp: 'ISP',
    netType: 'Network Type'
  }
  return labels[key] || key
}

const showFilterInput = (key: keyof BanLogFilters): boolean => {
  return activeInputs.value.has(key)
}

const addFilter = (key: string) => {
  activeInputs.value.add(key as keyof BanLogFilters)
}

const removeFilter = (key: keyof BanLogFilters) => {
  if (localFilters.value[key] !== undefined) {
    delete localFilters.value[key]
  }
  activeInputs.value.delete(key)
  emitFilters()
}

const resetFilters = () => {
  localFilters.value = {}
  activeInputs.value.clear()
  emitFilters()
}

const emitFilters = debounce(() => {
  const cleanFilters = { ...localFilters.value }
  // Remove empty values
  Object.keys(cleanFilters).forEach(key => {
    if (!cleanFilters[key as keyof BanLogFilters] || !cleanFilters[key as keyof BanLogFilters]?.trim()) {
      delete cleanFilters[key as keyof BanLogFilters]
    }
  })
  emit('update:modelValue', cleanFilters)
}, 300)

// Watchers
watch(localFilters, () => {
  emitFilters()
}, { deep: true })

watch(() => props.modelValue, (newValue, oldValue) => {
  // Only update if the value actually changed (prevent infinite loops)
  const newValueStr = JSON.stringify(newValue || {})
  const oldValueStr = JSON.stringify(oldValue || {})
  const currentLocalStr = JSON.stringify(localFilters.value || {})
  
  if (newValueStr !== oldValueStr && newValueStr !== currentLocalStr) {
    localFilters.value = { ...newValue }
    
    // Update active inputs based on current filters
    activeInputs.value.clear()
    Object.entries(newValue || {}).forEach(([key, value]) => {
      if (value && value.trim()) {
        activeInputs.value.add(key as keyof BanLogFilters)
      }
    })
  }
}, { deep: true, immediate: true })
</script>

<style scoped>
.filter-bar {
  background: var(--color-fill-1);
  border-radius: 6px;
  padding: 12px;
  margin-bottom: 16px;
}

.filter-controls {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.filter-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  flex: 1;
  min-height: 24px;
  align-items: center;
}

.filter-chip {
  display: inline-flex;
  align-items: center;
  background: var(--color-primary-light-1);
  border: 1px solid var(--color-primary-3);
  color: var(--color-primary-6);
  
  .filter-label {
    font-weight: 500;
    margin-right: 4px;
  }
  
  .filter-value {
    font-family: var(--font-mono-family);
    opacity: 0.8;
  }
}

.filter-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.filter-inputs {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--color-border-2);
}

.filter-input-group {
  display: flex;
  align-items: center;
  gap: 8px;
  
  .input-label {
    font-size: 12px;
    color: var(--color-text-2);
    white-space: nowrap;
    font-weight: 500;
    min-width: 60px;
  }
}

/* Responsive adjustments */
@media (max-width: 768px) {
  .filter-controls {
    flex-direction: column;
    align-items: stretch;
  }
  
  .filter-actions {
    justify-content: center;
  }
  
  .filter-input-group {
    flex-direction: column;
    align-items: stretch;
    gap: 4px;
    
    .input-label {
      min-width: auto;
    }
  }
}
</style>