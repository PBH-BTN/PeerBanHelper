<template>
  <a-modal
    v-model:visible="showModal"
    :title="t('page.rule_management.ruleSubscribe.updateLog')"
    unmount-on-close
    :modal-style="{ width: '30vw' }"
  >
    <a-table
      :stripe="true"
      :columns="columns"
      :data="list"
      :loading="tableLoading"
      :pagination="{
        total,
        current,
        pageSize,
        showPageSize: true,
        baseSize: 4,
        bufferSize: 1
      }"
      size="medium"
      class="banlog-table"
      @page-change="changeCurrent"
      @page-size-change="changePageSize"
    >
      <template #ruleId="{ record }">
        <a-tag :color="getColor(record.ruleId)">{{ record.ruleId }}</a-tag>
      </template>
      <template #updateTime="{ record }">
        <a-typography-text>{{ d(record.updateTime, 'long') }}</a-typography-text>
      </template>
      <template #updateType="{ record }">
        <a-tag :color="record.updateType === updateType.MANUAL ? 'green' : 'blue'">
          {{
            t(
              updateTypeMap[record.updateType as updateType] ??
                'page.rule_management.ruleSubscribe.updateLog.updateType.unknown'
            )
          }}
        </a-tag>
      </template>
    </a-table>
  </a-modal>
</template>
<script setup lang="ts">
import { updateType } from '@/api/model/ruleSubscribe'
import { GetUpdateLogs } from '@/service/ruleSubscribe'
import { getColor } from '@/utils/color'
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { usePagination } from 'vue-request'
const { t, d } = useI18n()
const showModal = ref(false)
defineExpose({
  showModal: () => {
    showModal.value = true
  }
})

const columns = [
  {
    title: 'ID',
    slotName: 'ruleId'
  },
  {
    title: () => t('page.rule_management.ruleSubscribe.updateLog.updateTime'),
    slotName: 'updateTime'
  },
  {
    title: () => t('page.rule_management.ruleSubscribe.updateLog.ruleCount'),
    dataIndex: 'count'
  },
  {
    title: () => t('page.rule_management.ruleSubscribe.updateLog.updateType'),
    slotName: 'updateType'
  }
]
const forceLoading = ref(true)

const updateTypeMap = {
  [updateType.MANUAL]: 'page.rule_management.ruleSubscribe.updateLog.updateType.manual',
  [updateType.AUTO]: 'page.rule_management.ruleSubscribe.updateLog.updateType.auto'
}

const tableLoading = computed(() => {
  return forceLoading.value || loading.value || !list.value
})

const { data, total, current, loading, pageSize, changeCurrent, changePageSize } = usePagination(
  GetUpdateLogs,
  {
    defaultParams: [
      {
        page: 1,
        pageSize: 5
      }
    ],
    pagination: {
      currentKey: 'page',
      pageSizeKey: 'pageSize',
      totalKey: 'data.total'
    },
    onAfter: () => {
      forceLoading.value = false
    }
  }
)
watch([pageSize, current], () => {
  forceLoading.value = true
})
const list = computed(() => data.value?.data.results)
</script>
