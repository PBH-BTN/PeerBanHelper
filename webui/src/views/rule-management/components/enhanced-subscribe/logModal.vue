<template>
  <a-modal
    v-model:visible="visible"
    :title="t('page.rule_management.enhancedRuleSubscribe.updateLog')"
    :footer="false"
    width="80%"
  >
    <a-table 
      :columns="columns" 
      :data="data?.results" 
      :loading="loading"
      :pagination="pagination"
      @page-change="handlePageChange"
    >
      <template #ruleType="{ record }">
        <a-tag :color="getRuleTypeColor(record.ruleType)">
          {{ getRuleTypeLabel(record.ruleType) }}
        </a-tag>
      </template>
      <template #updateTime="{ record }">
        {{ dayjs(record.updateTime).format('YYYY-MM-DD HH:mm:ss') }}
      </template>
      <template #updateType="{ record }">
        <a-tag :color="record.updateType === 'MANUAL' ? 'blue' : 'green'">
          {{ record.updateType }}
        </a-tag>
      </template>
      <template #errorMessage="{ record }">
        <a-tooltip v-if="record.errorMessage" :content="record.errorMessage">
          <a-tag color="red">Error</a-tag>
        </a-tooltip>
        <a-tag v-else color="green">Success</a-tag>
      </template>
    </a-table>
  </a-modal>
</template>

<script lang="ts" setup>
import { ref, reactive, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import dayjs from 'dayjs'
import { GetEnhancedRuleLogs } from '@/api/enhancedRuleSubscription'

const { t } = useI18n()

const visible = ref(false)
const loading = ref(false)
const data = ref()

const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showTotal: true,
  showPageSize: true
})

// Rule type color mapping
const ruleTypeColors = {
  'ip_blacklist': 'red',
  'peer_id': 'blue',
  'client_name': 'green', 
  'substring_match': 'orange',
  'prefix_match': 'purple',
  'exception_list': 'cyan',
  'script_engine': 'magenta'
}

// Rule type label mapping
const ruleTypeLabels = {
  'ip_blacklist': 'IP Blacklist',
  'peer_id': 'PeerID',
  'client_name': 'Client Name',
  'substring_match': 'Substring Match',
  'prefix_match': 'Prefix Match',
  'exception_list': 'Exception List',
  'script_engine': 'Script Engine'
}

const columns = computed(() => [
  {
    title: t('page.rule_management.enhancedRuleSubscribe.log.ruleId'),
    dataIndex: 'ruleId'
  },
  {
    title: t('page.rule_management.enhancedRuleSubscribe.log.ruleType'),
    dataIndex: 'ruleType',
    slotName: 'ruleType'
  },
  {
    title: t('page.rule_management.enhancedRuleSubscribe.log.updateTime'),
    slotName: 'updateTime'
  },
  {
    title: t('page.rule_management.enhancedRuleSubscribe.log.count'),
    dataIndex: 'count'
  },
  {
    title: t('page.rule_management.enhancedRuleSubscribe.log.updateType'),
    slotName: 'updateType'
  },
  {
    title: t('page.rule_management.enhancedRuleSubscribe.log.status'),
    slotName: 'errorMessage'
  }
])

const showModal = async () => {
  visible.value = true
  await loadLogs()
}

const loadLogs = async () => {
  loading.value = true
  try {
    const result = await GetEnhancedRuleLogs({
      page: pagination.current,
      size: pagination.pageSize
    })
    
    if (result.success) {
      data.value = result.data
      pagination.total = result.data?.total || 0
    }
  } catch (error) {
    console.error('Failed to load logs:', error)
  } finally {
    loading.value = false
  }
}

const handlePageChange = (page: number) => {
  pagination.current = page
  loadLogs()
}

const getRuleTypeColor = (ruleType: string): string => {
  return ruleTypeColors[ruleType as keyof typeof ruleTypeColors] || 'gray'
}

const getRuleTypeLabel = (ruleType: string): string => {
  return ruleTypeLabels[ruleType as keyof typeof ruleTypeLabels] || ruleType
}

defineExpose({
  showModal
})
</script>