<template>
  <a-table
    :columns="columns"
    :data="data?.data.data"
    size="large"
    :loading="loading"
    column-resizable
    filter-icon-align-left
    :pagination="{ showPageSize: true, baseSize: 4, bufferSize: 1 }"
  >
    <template #empty> <a-empty /> </template>
    <template #type="{ record }">
      <a-tag :color="getTagColor(record.type)">{{
        data?.data.dict[record.type] ?? record.type
      }}</a-tag>
    </template>
    <template #ruleName="{ record }">
      <a-typography-text code>
        {{ record.metadata.rule }}
      </a-typography-text>
    </template>
    <template #hit-filter="{ filterValue, handleFilterConfirm, handleFilterReset }">
      <div class="search-box">
        <a-space>
          <a-switch
            v-model="filterValue[0]"
            checked-value="yes"
            unchecked-value="no"
            @change="
              (value: string | number | boolean) =>
                value === 'yes' ? handleFilterConfirm() : handleFilterReset()
            "
          />
          <a-typography-text>{{ t('page.ruleMetrices.metricsTable.filter') }}</a-typography-text>
        </a-space>
      </div>
    </template>
  </a-table>
</template>
<script setup lang="ts">
import { useRequest } from 'vue-request'
import { useAutoUpdatePlugin } from '@/stores/autoUpdate'
import { useEndpointStore } from '@/stores/endpoint'
import { watch } from 'vue'
import { getRuleStatic } from '@/service/ruleStatics'
import type { RuleMetric } from '@/api/model/ruleStatics'
import { useI18n } from 'vue-i18n'
import type { TableColumnData } from '@arco-design/web-vue'
import { getColor } from '@/utils/color'
const { t } = useI18n()
const endpointStore = useEndpointStore()
const { data, refresh, loading } = useRequest(
  getRuleStatic,
  {
    cacheKey: () => `${endpointStore.endpoint}-ruleStatic`
  },
  [useAutoUpdatePlugin]
)

const getTagColor = (value: string): string => {
  if (!data.value?.data.dict[value]) {
    return 'gray'
  }
  return getColor(value)
}

const columns: TableColumnData[] = [
  {
    title: () => t('page.ruleMetrices.metricsTable.column.type'),
    slotName: 'type',
    width: 200
  },
  {
    title: () => t('page.ruleMetrices.metricsTable.column.content'),
    slotName: 'ruleName',
    width: 200
  },
  {
    title: () => t('page.ruleMetrices.metricsTable.column.run'),
    dataIndex: 'query',
    width: 200
  },
  {
    title: () => t('page.ruleMetrices.metricsTable.column.hit'),
    dataIndex: 'hit',
    filterable: {
      filter: (value, record) => value[0] === 'yes' && (record as RuleMetric).hit > 0,
      slotName: 'hit-filter'
    },
    width: 200
  }
]

watch(() => endpointStore.endpoint, refresh)
</script>
<style scoped>
.search-box {
  padding: 20px;
  background: var(--color-bg-5);
  border: 1px solid var(--color-neutral-3);
  border-radius: var(--border-radius-medium);
  box-shadow: 0 2px 5px rgb(0 0 0 / 10%);
}

.search-box-footer {
  display: flex;
  justify-content: space-between;
}
</style>
