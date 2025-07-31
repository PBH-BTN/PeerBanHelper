<template>
  <a-space direction="vertical" size="medium">
    <a-space wrap>
      <a-typography-text bold>{{ t('page.banlist.banlist.filters.title') }}</a-typography-text>
      <a-button size="small" @click="resetFilters">
        {{ t('page.banlist.banlist.filters.reset') }}
      </a-button>
    </a-space>

    <a-row :gutter="16" wrap>
      <!-- Ban Reason Filter -->
      <a-col :span="24" :md="12" :lg="8">
        <a-form-item :label="t('page.banlist.banlist.filters.reason')">
          <a-input
            v-model="localFilters.reason"
            :placeholder="t('page.banlist.banlist.filters.reason.placeholder')"
            allow-clear
            @change="onFilterChange"
          />
        </a-form-item>
      </a-col>

      <!-- Client Name Filter -->
      <a-col :span="24" :md="12" :lg="8">
        <a-form-item :label="t('page.banlist.banlist.filters.clientName')">
          <a-select
            v-model="localFilters.clientName"
            :placeholder="t('page.banlist.banlist.filters.clientName.placeholder')"
            allow-clear
            allow-search
            :options="clientNameOptions"
            @change="onFilterChange"
          />
        </a-form-item>
      </a-col>

      <!-- PeerID Filter -->
      <a-col :span="24" :md="12" :lg="8">
        <a-form-item :label="t('page.banlist.banlist.filters.peerId')">
          <a-input
            v-model="localFilters.peerId"
            :placeholder="t('page.banlist.banlist.filters.peerId.placeholder')"
            allow-clear
            @change="onFilterChange"
          />
        </a-form-item>
      </a-col>

      <!-- Country Filter -->
      <a-col :span="24" :md="12" :lg="8">
        <a-form-item :label="t('page.banlist.banlist.filters.country')">
          <a-select
            v-model="localFilters.country"
            :placeholder="t('page.banlist.banlist.filters.country.placeholder')"
            allow-clear
            allow-search
            :options="countryOptions"
            @change="onFilterChange"
          />
        </a-form-item>
      </a-col>

      <!-- City Filter -->
      <a-col :span="24" :md="12" :lg="8">
        <a-form-item :label="t('page.banlist.banlist.filters.city')">
          <a-select
            v-model="localFilters.city"
            :placeholder="t('page.banlist.banlist.filters.city.placeholder')"
            allow-clear
            allow-search
            :options="cityOptions"
            @change="onFilterChange"
          />
        </a-form-item>
      </a-col>

      <!-- ASN Filter -->
      <a-col :span="24" :md="12" :lg="8">
        <a-form-item :label="t('page.banlist.banlist.filters.asn')">
          <a-input
            v-model="localFilters.asn"
            :placeholder="t('page.banlist.banlist.filters.asn.placeholder')"
            allow-clear
            @change="onFilterChange"
          />
        </a-form-item>
      </a-col>

      <!-- ISP Filter -->
      <a-col :span="24" :md="12" :lg="8">
        <a-form-item :label="t('page.banlist.banlist.filters.isp')">
          <a-select
            v-model="localFilters.isp"
            :placeholder="t('page.banlist.banlist.filters.isp.placeholder')"
            allow-clear
            allow-search
            :options="ispOptions"
            @change="onFilterChange"
          />
        </a-form-item>
      </a-col>

      <!-- Network Type Filter -->
      <a-col :span="24" :md="12" :lg="8">
        <a-form-item :label="t('page.banlist.banlist.filters.netType')">
          <a-select
            v-model="localFilters.netType"
            :placeholder="t('page.banlist.banlist.filters.netType.placeholder')"
            allow-clear
            allow-search
            :options="netTypeOptions"
            @change="onFilterChange"
          />
        </a-form-item>
      </a-col>

      <!-- Discovery Location Filter -->
      <a-col :span="24" :md="12" :lg="8">
        <a-form-item :label="t('page.banlist.banlist.filters.context')">
          <a-select
            v-model="localFilters.context"
            :placeholder="t('page.banlist.banlist.filters.context.placeholder')"
            allow-clear
            allow-search
            :options="contextOptions"
            @change="onFilterChange"
          />
        </a-form-item>
      </a-col>

      <!-- Hit Rule Filter -->
      <a-col :span="24" :md="12" :lg="8">
        <a-form-item :label="t('page.banlist.banlist.filters.rule')">
          <a-select
            v-model="localFilters.rule"
            :placeholder="t('page.banlist.banlist.filters.rule.placeholder')"
            allow-clear
            allow-search
            :options="ruleOptions"
            @change="onFilterChange"
          />
        </a-form-item>
      </a-col>
    </a-row>
  </a-space>
</template>

<script setup lang="ts">
import { reactive, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useDebounceFn } from '@vueuse/core'
import type { BanListFilters } from '@/service/banList'

const { t } = useI18n()

interface Props {
  filters?: BanListFilters
  clientNameOptions?: { label: string; value: string }[]
  countryOptions?: { label: string; value: string }[]
  cityOptions?: { label: string; value: string }[]
  ispOptions?: { label: string; value: string }[]
  netTypeOptions?: { label: string; value: string }[]
  contextOptions?: { label: string; value: string }[]
  ruleOptions?: { label: string; value: string }[]
}

const props = withDefaults(defineProps<Props>(), {
  filters: () => ({}),
  clientNameOptions: () => [],
  countryOptions: () => [],
  cityOptions: () => [],
  ispOptions: () => [],
  netTypeOptions: () => [],
  contextOptions: () => [],
  ruleOptions: () => []
})

const emits = defineEmits<{
  (e: 'filter-change', filters: BanListFilters): void
}>()

const localFilters = reactive<BanListFilters>({
  reason: props.filters.reason || '',
  clientName: props.filters.clientName || '',
  peerId: props.filters.peerId || '',
  country: props.filters.country || '',
  city: props.filters.city || '',
  asn: props.filters.asn || '',
  isp: props.filters.isp || '',
  netType: props.filters.netType || '',
  context: props.filters.context || '',
  rule: props.filters.rule || ''
})

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

const resetFilters = () => {
  localFilters.reason = ''
  localFilters.clientName = ''
  localFilters.peerId = ''
  localFilters.country = ''
  localFilters.city = ''
  localFilters.asn = ''
  localFilters.isp = ''
  localFilters.netType = ''
  localFilters.context = ''
  localFilters.rule = ''
  onFilterChange()
}

// Watch for external filter changes
watch(
  () => props.filters,
  (newFilters) => {
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
  },
  { deep: true }
)
</script>

<style scoped lang="less">
.a-form-item {
  margin-bottom: 0;
}
</style>
