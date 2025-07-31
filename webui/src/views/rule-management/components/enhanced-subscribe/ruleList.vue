<template>
  <a-space direction="vertical" fill>
    <a-space class="align-right" fill>
      <a-button type="primary" @click="handleAdd">
        <template #icon>
          <icon-plus-circle />
        </template>
        {{ t('page.rule_management.enhancedRuleSubscribe.addRule') }}
      </a-button>
      <a-button-group>
        <a-button :loading="updateAllLoading" @click="handleUpdateAll">
          <template #icon> <icon-refresh /> </template>
          {{ t('page.rule_management.enhancedRuleSubscribe.updateAll') }}
        </a-button>
        <a-tooltip :content="t('page.rule_management.enhancedRuleSubscribe.settingsTips')">
          <a-button @click="settingsModal?.showModal">
            <template #icon>
              <icon-settings />
            </template>
          </a-button>
        </a-tooltip>
        <a-tooltip :content="t('page.rule_management.enhancedRuleSubscribe.updateLog')">
          <a-button @click="updateLog?.showModal">
            <template #icon>
              <icon-history />
            </template>
          </a-button>
        </a-tooltip>
      </a-button-group>
    </a-space>
    <a-table stripe :columns="columns" :data="data?.data" :loading="loading">
      <template #ruleId="{ record }">
        <a-tag :color="getColor(record.ruleId)">{{ record.ruleId }}</a-tag>
      </template>
      <template #ruleType="{ record }">
        <a-tag :color="getRuleTypeColor(record.ruleType)">
          {{ getRuleTypeLabel(record.ruleType) }}
        </a-tag>
      </template>
      <template #url="{ record }">
        <a-button @click="handleCopy(record.subUrl)">{{
          t('page.rule_management.enhancedRuleSubscribe.column.clickToCopy')
        }}</a-button>
      </template>
      <template #status="{ record }">
        <a-space>
          <a-switch
            v-model="record.enabled"
            :before-change="
              async (newStatus: string | number | boolean) => {
                const result = await ToggleRuleEnable(record.ruleId, newStatus as boolean)
                if (!result.success) {
                  Message.error({ content: result.message, resetOnHover: true })
                  return false
                }
                Message.success({ content: result.message, resetOnHover: true })
                return true
              }
            "
          />
        </a-space>
      </template>
      <template #entCount="{ record }">
        <a-statistic
          :title="t('page.rule_management.enhancedRuleSubscribe.column.entCount')"
          :value="record.entCount"
          show-group-separator
        />
      </template>
      <template #lastUpdate="{ record }">
        {{ dayjs(record.lastUpdate).fromNow() }}
      </template>
      <template #optional="{ record }">
        <a-dropdown>
          <a-button> {{ t('page.rule_management.enhancedRuleSubscribe.actions') }} </a-button>
          <template #content>
            <a-doption @click="handleEdit(record)">
              <template #icon>
                <icon-edit />
              </template>
              {{ t('page.rule_management.enhancedRuleSubscribe.edit') }}
            </a-doption>
            <a-doption @click="handleUpdate(record)">
              <template #icon>
                <icon-refresh />
              </template>
              {{ t('page.rule_management.enhancedRuleSubscribe.update') }}
            </a-doption>
            <a-doption @click="handleDelete(record)">
              <template #icon>
                <icon-delete />
              </template>
              {{ t('page.rule_management.enhancedRuleSubscribe.delete') }}
            </a-doption>
          </template>
        </a-dropdown>
      </template>
    </a-table>
  </a-space>
  
  <!-- Edit Rule Modal -->
  <editRuleItemModal ref="editRuleModal" @refresh="handleRefresh" />
  
  <!-- Settings Modal -->
  <settingsModal ref="settingsModal" @refresh="handleRefresh" />
  
  <!-- Update Log Modal -->
  <logModal ref="updateLog" />
</template>

<script lang="ts" setup>
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Message, Modal } from '@arco-design/web-vue'
import {
  IconPlusCircle,
  IconRefresh,
  IconSettings,
  IconHistory,
  IconEdit,
  IconDelete
} from '@arco-design/web-vue/es/icon'
import dayjs from 'dayjs'
import {
  GetEnhancedRuleList,
  UpdateAllEnhancedRules,
  UpdateEnhancedRule,
  DeleteEnhancedRule,
  ToggleEnhancedRuleEnable
} from '@/api/enhancedRuleSubscription'
import editRuleItemModal from './editRuleItemModal.vue'
import settingsModal from './settingsModal.vue'
import logModal from './logModal.vue'
import { useUserStore } from '@/stores'

const { t } = useI18n()
const userStore = useUserStore()

const data = ref()
const loading = ref(false)
const updateAllLoading = ref(false)

const editRuleModal = ref()
const settingsModal = ref()
const updateLog = ref()

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
    title: t('page.rule_management.enhancedRuleSubscribe.column.ruleId'),
    dataIndex: 'ruleId',
    slotName: 'ruleId'
  },
  {
    title: t('page.rule_management.enhancedRuleSubscribe.column.ruleName'),
    dataIndex: 'ruleName'
  },
  {
    title: t('page.rule_management.enhancedRuleSubscribe.column.ruleType'),
    dataIndex: 'ruleType',
    slotName: 'ruleType'
  },
  {
    title: t('page.rule_management.enhancedRuleSubscribe.column.url'),
    slotName: 'url'
  },
  {
    title: t('page.rule_management.enhancedRuleSubscribe.column.status'),
    slotName: 'status'
  },
  {
    title: t('page.rule_management.enhancedRuleSubscribe.column.entCount'),
    slotName: 'entCount'
  },
  {
    title: t('page.rule_management.enhancedRuleSubscribe.column.lastUpdate'),
    slotName: 'lastUpdate'
  },
  {
    title: t('page.rule_management.enhancedRuleSubscribe.column.optional'),
    slotName: 'optional'
  }
])

const handleRefresh = async () => {
  loading.value = true
  try {
    data.value = await GetEnhancedRuleList()
  } catch (error) {
    console.error('Failed to refresh enhanced rule list:', error)
  } finally {
    loading.value = false
  }
}

const handleAdd = () => {
  editRuleModal.value?.showModal('add')
}

const handleEdit = (record: any) => {
  editRuleModal.value?.showModal('edit', record)
}

const handleUpdate = async (record: any) => {
  try {
    const result = await UpdateEnhancedRule(record.ruleId)
    if (result.success) {
      Message.success({ content: result.message, resetOnHover: true })
      await handleRefresh()
    } else {
      Message.error({ content: result.message, resetOnHover: true })
    }
  } catch (error) {
    Message.error({ content: 'Update failed', resetOnHover: true })
  }
}

const handleUpdateAll = async () => {
  updateAllLoading.value = true
  try {
    const result = await UpdateAllEnhancedRules()
    if (result.success) {
      Message.success({ content: result.message, resetOnHover: true })
      await handleRefresh()
    } else {
      Message.error({ content: result.message, resetOnHover: true })
    }
  } catch (error) {
    Message.error({ content: 'Update all failed', resetOnHover: true })
  } finally {
    updateAllLoading.value = false
  }
}

const handleDelete = (record: any) => {
  Modal.confirm({
    title: t('page.rule_management.enhancedRuleSubscribe.deleteConfirm'),
    content: t('page.rule_management.enhancedRuleSubscribe.deleteConfirmContent', { name: record.ruleName }),
    onOk: async () => {
      try {
        const result = await DeleteEnhancedRule(record.ruleId)
        if (result.success) {
          Message.success({ content: result.message, resetOnHover: true })
          await handleRefresh()
        } else {
          Message.error({ content: result.message, resetOnHover: true })
        }
      } catch (error) {
        Message.error({ content: 'Delete failed', resetOnHover: true })
      }
    }
  })
}

const handleCopy = async (text: string) => {
  try {
    await navigator.clipboard.writeText(text)
    Message.success({ content: 'Copied to clipboard', resetOnHover: true })
  } catch (error) {
    Message.error({ content: 'Copy failed', resetOnHover: true })
  }
}

const ToggleRuleEnable = async (ruleId: string, enabled: boolean) => {
  return await ToggleEnhancedRuleEnable(ruleId, enabled)
}

const getColor = (id: string): string => {
  const colors = ['red', 'orangered', 'orange', 'gold', 'green', 'cyan', 'blue', 'purple', 'magenta']
  let hash = 0
  for (let i = 0; i < id.length; i++) {
    hash = id.charCodeAt(i) + ((hash << 5) - hash)
  }
  return colors[Math.abs(hash) % colors.length]
}

const getRuleTypeColor = (ruleType: string): string => {
  return ruleTypeColors[ruleType as keyof typeof ruleTypeColors] || 'gray'
}

const getRuleTypeLabel = (ruleType: string): string => {
  return ruleTypeLabels[ruleType as keyof typeof ruleTypeLabels] || ruleType
}

onMounted(async () => {
  await handleRefresh()
})
</script>

<style scoped>
.align-right {
  justify-content: space-between;
}
</style>