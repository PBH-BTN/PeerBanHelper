<template>
  <a-space direction="vertical" fill>
    <a-space class="list-header" wrap>
      <a-typography-text>{{ t('page.banlist.banlist.description') }}</a-typography-text>
      <a-space class="list-header-right-group" wrap>
        <AsyncMethod
          v-slot="{ run: unban, loading: unbaning }"
          once
          :async-fn="() => handleUnban('*')"
        >
          <a-button
            type="secondary"
            :disabled="(list?.length ?? 0) === 0"
            :loading="unbaning"
            @click="unban"
          >
            {{ t('page.banlist.banlist.listItem.unbanall') }}
          </a-button>
        </AsyncMethod>
        <a-button @click="toggleFilters">
          <template #icon>
            <icon-filter />
          </template>
          {{
            showFilters
              ? t('page.banlist.banlist.filters.hide')
              : t('page.banlist.banlist.filters.show')
          }}
        </a-button>
        <a-input-search
          :style="{ width: '250px' }"
          :placeholder="t('page.banlist.banlist.searchPlaceHolder')"
          allow-clear
          search-button
          @search="handleSearch"
        />
      </a-space>
    </a-space>

    <!-- Filters -->
    <a-collapse v-model:active-key="filtersActiveKey" :bordered="false">
      <a-collapse-item key="filters" :show-expand-icon="false">
        <template #header>
          <div></div>
        </template>
        <BanListFiltersComponent
          :filters="currentFilters"
          :client-name-options="clientNameOptions"
          :country-options="countryOptions"
          :city-options="cityOptions"
          @filter-change="handleFilterChange"
        />
      </a-collapse-item>
    </a-collapse>

    <!-- paginated list (no virtual scroll / infinite load) -->
    <a-list :data="list">
      <template #item="{ item }">
        <a-list-item>
          <banListItem :item="item" @unban="refresh()" />
        </a-list-item>
      </template>
      <template #scroll-loading>
        <a-space v-if="loading && list.length === 0" style="height: 150px">
          <a-spin style="width: 100%" />
        </a-space>
        <a-empty v-else-if="list.length === 0" style="width: 100%; height: 150px" />
      </template>
    </a-list>

    <!-- pagination controls -->
    <a-space fill style="display: flex; justify-content: end">
      <a-pagination
        :total="total"
        :current="current"
        :page-size="pageSize"
        show-page-size
        @change="changeCurrent"
        @page-size-change="changePageSize"
      />
    </a-space>
  </a-space>
</template>

<script setup lang="ts">
import AsyncMethod from '@/components/asyncMethod.vue'
import { type BanList } from '@/api/model/banlist'
import { getBanListPaginated, unbanIP, type BanListFilters } from '@/service/banList'
import { useAutoUpdate, useAutoUpdatePlugin } from '@/stores/autoUpdate'
import { useEndpointStore } from '@/stores/endpoint'
import { Message } from '@arco-design/web-vue'
import { IconFilter } from '@arco-design/web-vue/es/icon'
import { useDebounceFn } from '@vueuse/core'
import { computed, onUnmounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { usePagination } from 'vue-request'
import banListItem from './banListItem.vue'
import BanListFiltersComponent from './banListFilters.vue'

const endpointState = useEndpointStore()
const autoUpdateStore = useAutoUpdate()
const searchString = ref('')
const currentFilters = reactive<BanListFilters>({})
const showFilters = ref(false)
const filtersActiveKey = ref<string[]>([])
const { t } = useI18n()

// Extract unique values for filter options
const clientNameOptions = ref<{ label: string; value: string }[]>([])
const countryOptions = ref<{ label: string; value: string }[]>([])
const cityOptions = ref<{ label: string; value: string }[]>([])

const { total, data, current, pageSize, loading, changeCurrent, changePageSize, refresh, run } =
  usePagination(
    getBanListPaginated,
    {
      defaultParams: [
        {
          page: 1,
          pageSize: 10,
          search: '',
          filters: {}
        }
      ],
      pagination: {
        currentKey: 'page',
        pageSizeKey: 'pageSize',
        totalKey: 'data.total'
      }
    },
    [useAutoUpdatePlugin]
  )

const pollingHandler = autoUpdateStore.polling(() => {
  if (current.value === 1) refresh()
})

onUnmounted(() => {
  if (pollingHandler) {
    pollingHandler('unmount')
  }
})

const handleUnban = async (address: string) => {
  const { count } = await (await unbanIP(address)).data
  if (!count || count < 1) {
    Message.error({
      content: t('page.banlist.banlist.listItem.unbanUnexcepted'),
      resetOnHover: true
    })
    return false
  } else {
    Message.success({
      content: t('page.banlist.banlist.listItem.unbanSuccess', { count: count }),
      resetOnHover: true
    })
    refresh()
    return true
  }
}

watch(
  () => endpointState.endpoint,
  () => {
    refresh()
  }
)

run({ page: 1, pageSize: 10, search: '', filters: {} })

const list = computed(() => data.value?.data.results ?? [])

// Extract unique filter options from the current data
const updateFilterOptions = () => {
  const items = list.value || []
  const clientNames = new Set<string>()
  const countries = new Set<string>()
  const cities = new Set<string>()

  items.forEach((item) => {
    if (item.banMetadata?.peer?.clientName) {
      clientNames.add(item.banMetadata.peer.clientName)
    }
    if (item.ipGeoData?.country?.name) {
      countries.add(item.ipGeoData.country.name)
    }
    if (item.ipGeoData?.city?.name) {
      cities.add(item.ipGeoData.city.name)
    }
  })

  clientNameOptions.value = Array.from(clientNames).map((name) => ({ label: name, value: name }))
  countryOptions.value = Array.from(countries).map((name) => ({ label: name, value: name }))
  cityOptions.value = Array.from(cities).map((name) => ({ label: name, value: name }))
}

watch(list, updateFilterOptions, { immediate: true })

const debouncedSearch = useDebounceFn((v: string) => {
  searchString.value = v
  changeCurrent(1)
  run({ page: 1, pageSize: pageSize.value, search: v, filters: currentFilters })
}, 300)

const handleSearch = (v: string) => {
  if (searchString.value === v) return
  debouncedSearch(v)
}

const debouncedFilterChange = useDebounceFn((filters: BanListFilters) => {
  Object.assign(currentFilters, filters)
  changeCurrent(1)
  run({ page: 1, pageSize: pageSize.value, search: searchString.value, filters })
}, 300)

const handleFilterChange = (filters: BanListFilters) => {
  debouncedFilterChange(filters)
}

const toggleFilters = () => {
  showFilters.value = !showFilters.value
  filtersActiveKey.value = showFilters.value ? ['filters'] : []
}
</script>

<style scoped lang="less">
.list-header {
  display: flex;
  justify-content: space-between;
}

a {
  color: var(--color-text-1);
  text-decoration: none;
}
</style>
