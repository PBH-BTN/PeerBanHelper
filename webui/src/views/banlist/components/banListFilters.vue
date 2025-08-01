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
          @close="removeFilter(key as keyof BanListFilters)"
          class="filter-chip"
        >
          <span class="filter-label">{{ getFilterLabel(key as keyof BanListFilters) }}:</span>
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
            {{ t('page.banlist.banlist.filters.addFilter') }}
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
          {{ t('page.banlist.banlist.filters.reset') }}
        </a-button>
      </div>
    </div>

    <!-- Active Filter Inputs -->
    <div v-if="hasActiveInputs" class="filter-inputs">
      <a-space wrap size="small">
        <!-- Reason Input -->
        <div v-if="showFilterInput('reason')" class="filter-input-group">
          <span class="input-label">{{ t('page.banlist.banlist.filters.reason') }}</span>
          <a-input
            v-model="localFilters.reason"
            :placeholder="t('page.banlist.banlist.filters.reason.placeholder')"
            size="small"
            style="width: 200px"
            allow-clear
            @change="onFilterChange"
          />
        </div>

        <!-- Client Name Select -->
        <div v-if="showFilterInput('clientName')" class="filter-input-group">
          <span class="input-label">{{ t('page.banlist.banlist.filters.clientName') }}</span>
          <a-select
            v-model="localFilters.clientName"
            :placeholder="t('page.banlist.banlist.filters.clientName.placeholder')"
            size="small"
            style="width: 200px"
            allow-clear
            allow-search
            :options="clientNameOptions"
            @change="onFilterChange"
          />
        </div>

        <!-- PeerID Input -->
        <div v-if="showFilterInput('peerId')" class="filter-input-group">
          <span class="input-label">{{ t('page.banlist.banlist.filters.peerId') }}</span>
          <a-input
            v-model="localFilters.peerId"
            :placeholder="t('page.banlist.banlist.filters.peerId.placeholder')"
            size="small"
            style="width: 200px"
            allow-clear
            @change="onFilterChange"
          />
        </div>

        <!-- Country Select -->
        <div v-if="showFilterInput('country')" class="filter-input-group">
          <span class="input-label">{{ t('page.banlist.banlist.filters.country') }}</span>
          <a-select
            v-model="localFilters.country"
            :placeholder="t('page.banlist.banlist.filters.country.placeholder')"
            size="small"
            style="width: 200px"
            allow-clear
            allow-search
            :options="countryOptions"
            @change="onFilterChange"
          />
        </div>

        <!-- City Select -->
        <div v-if="showFilterInput('city')" class="filter-input-group">
          <span class="input-label">{{ t('page.banlist.banlist.filters.city') }}</span>
          <a-select
            v-model="localFilters.city"
            :placeholder="t('page.banlist.banlist.filters.city.placeholder')"
            size="small"
            style="width: 200px"
            allow-clear
            allow-search
            :options="cityOptions"
            @change="onFilterChange"
          />
        </div>

        <!-- ASN Select -->
        <div v-if="showFilterInput('asn')" class="filter-input-group">
          <span class="input-label">{{ t('page.banlist.banlist.filters.asn') }}</span>
          <a-select
            v-model="localFilters.asn"
            :placeholder="t('page.banlist.banlist.filters.asn.placeholder')"
            size="small"
            style="width: 200px"
            allow-clear
            allow-search
            :options="asnOptions"
            @change="onFilterChange"
          />
        </div>

        <!-- ISP Select -->
        <div v-if="showFilterInput('isp')" class="filter-input-group">
          <span class="input-label">{{ t('page.banlist.banlist.filters.isp') }}</span>
          <a-select
            v-model="localFilters.isp"
            :placeholder="t('page.banlist.banlist.filters.isp.placeholder')"
            size="small"
            style="width: 200px"
            allow-clear
            allow-search
            :options="ispOptions"
            @change="onFilterChange"
          />
        </div>

        <!-- Network Type Select -->
        <div v-if="showFilterInput('netType')" class="filter-input-group">
          <span class="input-label">{{ t('page.banlist.banlist.filters.netType') }}</span>
          <a-select
            v-model="localFilters.netType"
            :placeholder="t('page.banlist.banlist.filters.netType.placeholder')"
            size="small"
            style="width: 200px"
            allow-clear
            allow-search
            :options="netTypeOptions"
            @change="onFilterChange"
          />
        </div>

        <!-- Discovery Location Select -->
        <div v-if="showFilterInput('context')" class="filter-input-group">
          <span class="input-label">{{ t('page.banlist.banlist.filters.context') }}</span>
          <a-select
            v-model="localFilters.context"
            :placeholder="t('page.banlist.banlist.filters.context.placeholder')"
            size="small"
            style="width: 200px"
            allow-clear
            allow-search
            :options="contextOptions"
            @change="onFilterChange"
          />
        </div>

        <!-- Hit Rule Select -->
        <div v-if="showFilterInput('rule')" class="filter-input-group">
          <span class="input-label">{{ t('page.banlist.banlist.filters.rule') }}</span>
          <a-select
            v-model="localFilters.rule"
            :placeholder="t('page.banlist.banlist.filters.rule.placeholder')"
            size="small"
            style="width: 200px"
            allow-clear
            allow-search
            :options="ruleOptions"
            @change="onFilterChange"
          />
        </div>
      </a-space>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, computed, watch, ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useDebounceFn } from '@vueuse/core'
import type { BanListFilters } from '@/service/banList'
import { getBanListFilterOptions, type FilterOptions } from '@/service/banList'

const { t } = useI18n()

interface Props {
  filters?: BanListFilters
}

const props = withDefaults(defineProps<Props>(), {
  filters: () => ({})
})

const emits = defineEmits<{
  (e: 'filter-change', filters: BanListFilters): void
}>()

// Filter options from backend
const filterOptions = ref<FilterOptions>({
  clientNames: [],
  countries: [],
  cities: [],
  asns: [],
  isps: [],
  netTypes: [],
  torrents: [],
  rules: []
})

// Load filter options from backend
const loadFilterOptions = async () => {
  try {
    const response = await getBanListFilterOptions()
    if (response.success) {
      filterOptions.value = response.data
    }
  } catch (error) {
    console.error('Failed to load filter options:', error)
  }
}

onMounted(() => {
  loadFilterOptions()
})

const localFilters = reactive<BanListFilters>({...props.filters})

// Track which filters are currently being shown for input
const activeInputs = ref<Set<keyof BanListFilters>>(new Set())

// Track if this is the initial load to avoid re-initializing active inputs inappropriately
const isInitialLoad = ref(true)

// Initialize active inputs based on existing filters
const initializeActiveInputs = () => {
  activeInputs.value.clear()
  Object.entries(localFilters).forEach(([key, value]) => {
    if (value && value.trim()) {
      activeInputs.value.add(key as keyof BanListFilters)
    }
  })
}

// Initialize on mount
onMounted(() => {
  initializeActiveInputs()
  isInitialLoad.value = false
})

// Computed properties
const activeFilters = computed(() => {
  const active: Partial<BanListFilters> = {}
  Object.entries(localFilters).forEach(([key, value]) => {
    if (value && value.trim()) {
      active[key as keyof BanListFilters] = value.trim()
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
    { key: 'reason', label: t('page.banlist.banlist.filters.reason'), icon: 'icon-message' },
    { key: 'clientName', label: t('page.banlist.banlist.filters.clientName'), icon: 'icon-user' },
    { key: 'peerId', label: t('page.banlist.banlist.filters.peerId'), icon: 'icon-idcard' },
    { key: 'country', label: t('page.banlist.banlist.filters.country'), icon: 'icon-location' },
    { key: 'city', label: t('page.banlist.banlist.filters.city'), icon: 'icon-location' },
    { key: 'asn', label: t('page.banlist.banlist.filters.asn'), icon: 'icon-global' },
    { key: 'isp', label: t('page.banlist.banlist.filters.isp'), icon: 'icon-wifi' },
    { key: 'netType', label: t('page.banlist.banlist.filters.netType'), icon: 'icon-link' },
    { key: 'context', label: t('page.banlist.banlist.filters.context'), icon: 'icon-compass' },
    { key: 'rule', label: t('page.banlist.banlist.filters.rule'), icon: 'icon-shield' }
  ]

  // Filter out already active inputs
  return filters.filter(f => !activeInputs.value.has(f.key as keyof BanListFilters))
})

// Computed options for dropdowns using backend data
const clientNameOptions = computed(() => 
  filterOptions.value.clientNames.map(name => ({ label: name, value: name }))
)

const countryOptions = computed(() => 
  filterOptions.value.countries.map(name => ({ label: name, value: name }))
)

const cityOptions = computed(() => 
  filterOptions.value.cities.map(name => ({ label: name, value: name }))
)

const asnOptions = computed(() => 
  filterOptions.value.asns.map(name => ({ label: name, value: name }))
)

const ispOptions = computed(() => 
  filterOptions.value.isps.map(name => ({ label: name, value: name }))
)

const netTypeOptions = computed(() => 
  filterOptions.value.netTypes.map(name => ({ label: name, value: name }))
)

const contextOptions = computed(() => 
  filterOptions.value.torrents.map(torrent => ({ label: torrent.name, value: torrent.id }))
)

const ruleOptions = computed(() => 
  filterOptions.value.rules.map(name => ({ label: name, value: name }))
)

// Methods
const getFilterLabel = (key: keyof BanListFilters): string => {
  const labels = {
    reason: t('page.banlist.banlist.filters.reason'),
    clientName: t('page.banlist.banlist.filters.clientName'),
    peerId: t('page.banlist.banlist.filters.peerId'),
    country: t('page.banlist.banlist.filters.country'),
    city: t('page.banlist.banlist.filters.city'),
    asn: t('page.banlist.banlist.filters.asn'),
    isp: t('page.banlist.banlist.filters.isp'),
    netType: t('page.banlist.banlist.filters.netType'),
    context: t('page.banlist.banlist.filters.context'),
    rule: t('page.banlist.banlist.filters.rule')
  }
  return labels[key] || key
}

const showFilterInput = (key: keyof BanListFilters): boolean => {
  return activeInputs.value.has(key)
}

const addFilter = (key: string) => {
  activeInputs.value.add(key as keyof BanListFilters)
}

const removeFilter = (key: keyof BanListFilters) => {
  // Clear the filter value
  localFilters[key] = ''
  // Remove from active inputs
  activeInputs.value.delete(key)
  // Trigger change
  debouncedFilterChange()
}

const resetFilters = () => {
  // Clear all filter values
  Object.keys(localFilters).forEach(key => {
    localFilters[key as keyof BanListFilters] = ''
  })
  // Clear all active inputs
  activeInputs.value.clear()
  // Trigger change
  debouncedFilterChange()
}

const debouncedFilterChange = useDebounceFn(() => {
  // Remove empty values before emitting
  const activeFilters: BanListFilters = {}
  if (localFilters.reason?.trim()) activeFilters.reason = localFilters.reason.trim()
  if (localFilters.clientName?.trim()) activeFilters.clientName = localFilters.clientName.trim()
  if (localFilters.peerId?.trim()) activeFilters.peerId = localFilters.peerId.trim()
  if (localFilters.country?.trim()) activeFilters.country = localFilters.country.trim()
  if (localFilters.city?.trim()) activeFilters.city = localFilters.city.trim()
  if (localFilters.asn?.trim()) activeFilters.asn = localFilters.asn.trim()
  if (localFilters.isp?.trim()) activeFilters.isp = localFilters.isp.trim()
  if (localFilters.netType?.trim()) activeFilters.netType = localFilters.netType.trim()
  if (localFilters.context?.trim()) activeFilters.context = localFilters.context.trim()
  if (localFilters.rule?.trim()) activeFilters.rule = localFilters.rule.trim()

  emits('filter-change', activeFilters)
}, 300)

const onFilterChange = () => {
  debouncedFilterChange()
}

// Watch for external filter changes - prevent infinite loops
let internalUpdate = false

watch(
  () => props.filters,
  (newFilters) => {
    if (internalUpdate) return
    
    // Update local filters
    Object.assign(localFilters, {
      reason: newFilters.reason || '',
      clientName: newFilters.clientName || '',
      peerId: newFilters.peerId || '',
      country: newFilters.country || '',
      city: newFilters.city || '',
      asn: newFilters.asn || '',
      isp: newFilters.isp || '',
      netType: newFilters.netType || '',
      context: newFilters.context || '',
      rule: newFilters.rule || ''
    })

    // Only re-initialize active inputs on initial load or when explicitly requested
    // This prevents deleted filters from reappearing when toggling visibility or adding new filters
    if (isInitialLoad.value) {
      initializeActiveInputs()
    }
  },
  { deep: true, immediate: true }
)

// Watch local filter changes to emit updates
watch(
  localFilters,
  () => {
    if (!internalUpdate) {
      internalUpdate = true
      debouncedFilterChange()
      setTimeout(() => { internalUpdate = false }, 350) // Slightly longer than debounce
    }
  },
  { deep: true }
)
</script>

<style scoped lang="less">
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

/* Empty state for filter chips */
.filter-chips:empty::before {
  content: attr(data-empty-text);
  color: var(--color-text-3);
  font-style: italic;
  font-size: 14px;
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
