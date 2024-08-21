<template>
  <a-space direction="vertical" fill>
    <a-space class="align-right" fill>
      <a-button type="primary" @click="handleAdd">
        <template #icon>
          <icon-plus-circle />
        </template>
        {{ t('page.rule_management.ruleSubscribe.addRule') }}
      </a-button>
      <a-button-group>
        <a-button :loading="updateAllLoading" @click="handleUpdateAll">
          <template #icon> <icon-refresh /> </template>
          {{ t('page.rule_management.ruleSubscribe.updateAll') }}
        </a-button>
        <a-tooltip :content="t('page.rule_management.ruleSubscribe.settingsTips')">
          <a-button @click="settingsModal?.showModal">
            <template #icon>
              <icon-settings />
            </template>
          </a-button>
        </a-tooltip>
        <a-tooltip :content="t('page.rule_management.ruleSubscribe.updateLog')">
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
      <template #url="{ record }">
        <a-button @click="handleCopy(record.subUrl)">{{
          t('page.rule_management.ruleSubscribe.column.clickToCopy')
        }}</a-button>
      </template>
      <template #status="{ record }">
        <a-space>
          <a-switch v-model="record.enabled" :beforeChange="async (newStatus: string | number | boolean) => {
            const result = await ToggleRuleEnable(record.ruleId, newStatus as boolean)
            if (!result.success) {
              Message.error(result.message)
              return false
            }
            refresh()
            return true
          }
            " />
        </a-space>
      </template>
      <template #lastUpdated="{ record }">
        <a-typography-text>{{
          record.lastUpdate > 0
            ? d(record.lastUpdate, 'long')
            : t('page.rule_management.ruleSubscribe.column.notUpdated')
        }}</a-typography-text>
      </template>
      <template #rulesCount="{ record }">
        <a-typography-text>{{ record.enabled ? record.entCount : 'N/A' }}</a-typography-text>
      </template>
      <template #action="{ record }">
        <a-space warp>
          <a-tooltip :content="t('page.rule_management.ruleSubscribe.column.actions.edit')" position="top" mini>
            <a-button class="edit-btn" shape="circle" type="text" @click="() => handleEdit(record)">
              <template #icon>
                <icon-edit />
              </template>
            </a-button>
          </a-tooltip>
          <AsyncMethod once :async-fn="() => handleRefresh(record.ruleId)" v-slot="{ run, loading }">
            <a-tooltip :content="t('page.rule_management.ruleSubscribe.column.actions.update')" position="top" mini>
              <a-button class="edit-btn" shape="circle" type="text" @click="run">
                <template #icon>
                  <icon-refresh :spin="loading" />
                </template>
              </a-button>
            </a-tooltip>
          </AsyncMethod>
          <a-popconfirm :content="t('page.rule_management.ruleSubscribe.column.deleteConfirm')" type="warning"
            @before-ok="() => handleDelete(record.ruleId)">
            <a-button class="edit-btn" status="danger" shape="circle" type="text">
              <template #icon>
                <icon-delete />
              </template>
            </a-button>
          </a-popconfirm>
        </a-space>
      </template>
    </a-table>
    <EditRuleModal ref="editModal" />
    <SettingsModal ref="settingsModal" />
    <UpdateLog ref="updateLog" />
  </a-space>
</template>
<script setup lang="ts">
import {
  DeleteRule,
  getRuleList,
  RefreshRule,
  ToggleRuleEnable,
  UpdateAll
} from '@/service/ruleSubscribe'
import type { ruleBrief } from '@/api/model/ruleSubscribe'
import { useRequest } from 'vue-request'
import { useI18n } from 'vue-i18n'
import { getColor } from '@/utils/color'
import { Message } from '@arco-design/web-vue'
import { defineAsyncComponent, ref } from 'vue'
import AsyncMethod from '@/components/asyncMethod.vue'
import copy from 'copy-to-clipboard'
const EditRuleModal = defineAsyncComponent(() => import('./editRuleItemModal.vue'))
const SettingsModal = defineAsyncComponent(() => import('./settingsModal.vue'))
const UpdateLog = defineAsyncComponent(() => import('./logModal.vue'))
const { t, d } = useI18n()
const { data, loading, refresh } = useRequest(getRuleList, {})
const editModal = ref<InstanceType<typeof EditRuleModal>>()
const settingsModal = ref<InstanceType<typeof SettingsModal>>()
const updateLog = ref<InstanceType<typeof UpdateLog>>()
const columns = [
  {
    title: () => t('page.rule_management.ruleSubscribe.column.status'),
    slotName: 'status'
  },
  {
    title: 'ID',
    slotName: 'ruleId'
  },
  {
    title: () => t('page.rule_management.ruleSubscribe.column.ruleName'),
    dataIndex: 'ruleName'
  },
  {
    title: 'URL',
    slotName: 'url'
  },

  {
    title: () => t('page.rule_management.ruleSubscribe.column.lastUpdated'),
    slotName: 'lastUpdated'
  },
  {
    title: () => t('page.rule_management.ruleSubscribe.column.rulesCount'),
    slotName: 'rulesCount'
  },
  {
    title: () => t('page.rule_management.ruleSubscribe.column.actions'),
    slotName: 'action'
  }
]
const handleEdit = (record: ruleBrief) => {
  editModal.value?.showModal(false, () => refresh(), record)
}
const handleAdd = () => {
  editModal.value?.showModal(true, () => refresh())
}
const handleRefresh = (ruleId: string) =>
  RefreshRule(ruleId).then((result) => {
    if (!result.success) {
      Message.error(result.message)
    } else {
      Message.info(result.message)
    }
    refresh()
  })
const handleDelete = async (ruleId: string) => {
  const result = await DeleteRule(ruleId)
  if (!result.success) {
    Message.error(result.message)
  } else {
    Message.success(result.message)
  }
  refresh()
  return true
}

const updateAllLoading = ref(false)
const handleUpdateAll = async () => {
  updateAllLoading.value = true
  const result = await UpdateAll()
  if (!result.success) {
    Message.error(result.message)
  } else {
    Message.success(result.message)
  }
  refresh()
  updateAllLoading.value = false
}

const handleCopy = (text: string) => {
  copy(text)
  Message.success(t('page.rule_management.ruleSubscribe.copySuccess'))
}
</script>
<style scoped>
.edit-btn {
  color: rgb(var(--gray-8));
  font-size: 16px;
}

.align-right {
  display: flex;
  justify-content: flex-end;
}
</style>
