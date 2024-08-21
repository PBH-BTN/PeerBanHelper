<template>
  <a-space direction="vertical" size="small">
    <a-input-search
      :style="{ width: '250px' }"
      :placeholder="t('page.banlist.banlist.searchPlaceHolder')"
      @change="handleSearch"
      allow-clear
      search-button
    />
    <a-table
      stripe
      sticky-header
      :columns="columns"
      :data="data?.data.results"
      column-resizable
      :loading="loading"
      :pagination="{
        total,
        current,
        pageSize,
        showPageSize: true,
        baseSize: 4,
        bufferSize: 1
      }"
      filter-icon-align-left
      @page-change="changeCurrent"
      @page-size-change="changePageSize"
    >
      <template
        #ip-filter="{ filterValue, setFilterValue, handleFilterConfirm, handleFilterReset }"
      >
        <div class="search-box">
          <a-space direction="vertical">
            <a-input-search
              :model-value="filterValue[0]"
              :placeholder="t('page.topban.top50Table.searchPlaceholder')"
              allow-clear
              @search="handleFilterConfirm"
              @clear="handleFilterReset"
              @input="(value: string) => setFilterValue([value])"
            />
          </a-space>
        </div>
      </template>
    </a-table>
  </a-space>
</template>
<script setup lang="ts">
import { getRanks } from '@/service/ranks'
import { usePagination } from 'vue-request'
import { useAutoUpdatePlugin } from '@/stores/autoUpdate'
import { useI18n } from 'vue-i18n'
import type { TableColumnData } from '@arco-design/web-vue'
const { t } = useI18n()
const columns: TableColumnData[] = [
  {
    title: () => t('page.topban.top50Table.column.ipaddress'),
    dataIndex: 'peerIp'
  },
  {
    title: () => t('page.topban.top50Table.column.historyCount'),
    dataIndex: 'count'
  }
]

const { data, total, current, loading, pageSize, changeCurrent, changePageSize, run } =
  usePagination(
    getRanks,
    {
      defaultParams: [
        {
          page: 1,
          pageSize: 20
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

const handleSearch = (filter: string) => {
  run({
    page: 1,
    pageSize: 20,
    filter
  })
}
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
