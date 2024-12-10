<template>
  <a-space fill direction="vertical">
    <a-space style="display: flex; justify-content: space-between">
      <a-space>
        <a-typography-text>
          {{ t('page.rule.custom-script.description') }}
        </a-typography-text>
        <a-tooltip v-if="readOnlyMode" :content="editable?.data.reason">
          <a-tag color="orange">
            {{ t('page.rule.custom-script.readonlyMode') }}
          </a-tag>
        </a-tooltip>
      </a-space>
      <a-space>
        <a-tooltip v-if="readOnlyMode" :content="t('page.rule.custom-script.readonlyMode.disable')">
          <a-button type="primary" disabled @click="handleAddOne">
            <template #icon>
              <icon-plus-circle />
            </template>
            {{ t('page.rule.custom-script.add') }}
          </a-button>
        </a-tooltip>
        <a-button v-else type="primary" @click="handleAddOne">
          <template #icon>
            <icon-plus-circle />
          </template>
          {{ t('page.rule.custom-script.add') }}
        </a-button>
      </a-space>
    </a-space>
    <a-table
      :columns="columns"
      :data="data?.data.results"
      :loading="loading"
      :pagination="{
        total,
        current,
        pageSize,
        showPageSize: true,
        baseSize: 4,
        bufferSize: 1
      }"
      column-resizable
      size="medium"
      @page-change="changeCurrent"
      @page-size-change="changePageSize"
    >
      <template #cacheableTitle>
        <a-space size="mini">
          {{ t('page.rule.custom-script.column.cacheable') }}
          <a-tooltip :content="t('page.rule.custom-script.column.cacheable.tips')">
            <icon-info-circle />
          </a-tooltip>
        </a-space>
      </template>
      <template #author="{ record }">
        <a-tag :color="getColor(record.author)">{{ record.author }}</a-tag>
      </template>
      <template #cacheable="{ record }">
        <a-typography-text v-if="record.cacheable" type="success">
          <icon-check-circle-fill />
        </a-typography-text>
        <a-typography-text v-else type="danger">
          <icon-close-circle-fill />
        </a-typography-text>
      </template>
      <template #threadSafe="{ record }">
        <a-typography-text v-if="record.threadSafe" type="success">
          <icon-check-circle-fill />
        </a-typography-text>
        <a-typography-text v-else type="danger">
          <icon-close-circle-fill />
        </a-typography-text>
      </template>
      <template #action="{ record }">
        <a-space warp size="mini">
          <a-tooltip
            :content="t('page.rule.custom-script.column.actions.view')"
            position="top"
            mini
          >
            <a-button
              class="edit-btn"
              shape="circle"
              type="text"
              @click="() => detailDrawer?.viewDetail(record.id, true)"
            >
              <template #icon>
                <icon-eye />
              </template>
            </a-button>
          </a-tooltip>
          <a-tooltip
            :content="
              readOnlyMode
                ? t('page.rule.custom-script.readonlyMode.disable')
                : t('page.rule.custom-script.column.actions.edit')
            "
            position="top"
            mini
          >
            <a-button
              class="edit-btn"
              shape="circle"
              :disabled="readOnlyMode"
              type="text"
              @click="() => handleEdit(record.id)"
            >
              <template #icon>
                <icon-edit />
              </template>
            </a-button>
          </a-tooltip>
          <a-tooltip
            :content="
              readOnlyMode
                ? t('page.rule.custom-script.readonlyMode.disable')
                : t('page.rule.custom-script.column.actions.delete')
            "
          >
            <a-popconfirm
              :content="t('page.rule.custom-script.column.actions.deleteConfirm')"
              type="warning"
              @before-ok="() => handleDelete(record.id)"
            >
              <a-button
                :disabled="readOnlyMode"
                class="edit-btn"
                status="danger"
                shape="circle"
                type="text"
              >
                <template #icon>
                  <icon-delete />
                </template>
              </a-button>
            </a-popconfirm>
          </a-tooltip>
        </a-space>
      </template>
    </a-table>
  </a-space>
  <DetailDrawer ref="detailDrawer" />
</template>
<script setup lang="ts">
import { CheckScriptEditable, DeleteScript, GetScriptList } from '@/service/script'
import { useUserStore } from '@/stores/userStore'
import { getColor } from '@/utils/color'
import { Message, Modal } from '@arco-design/web-vue'
import { IconInfoCircle } from '@arco-design/web-vue/es/icon'
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { usePagination, useRequest } from 'vue-request'
import DetailDrawer from './detailDrawer.vue'
const { t } = useI18n()
const userStore = useUserStore()
const columns = [
  {
    title: () => t('page.rule.custom-script.column.id'),
    dataIndex: 'id',
    ellipsis: true,
    tooltip: true
  },
  {
    title: () => t('page.rule.custom-script.column.name'),
    dataIndex: 'name',
    ellipsis: true,
    tooltip: true
  },
  {
    title: () => t('page.rule.custom-script.column.author'),
    slotName: 'author'
  },
  {
    titleSlotName: 'cacheableTitle',
    slotName: 'cacheable',
    width: 120
  },
  {
    title: () => t('page.rule.custom-script.column.threadSafe'),
    slotName: 'threadSafe',
    width: 100
  },
  {
    title: () => t('page.rule.custom-script.column.version'),
    dataIndex: 'version',
    width: 100
  },
  { title: () => t('page.rule.custom-script.column.actions'), slotName: 'action' }
]
const { data, total, current, loading, pageSize, changeCurrent, changePageSize, refresh } =
  usePagination(GetScriptList, {
    defaultParams: [
      {
        page: 1,
        pageSize: 10
      }
    ],
    pagination: {
      currentKey: 'page',
      pageSizeKey: 'pageSize',
      totalKey: 'data.total'
    }
  })

const { data: editable, loading: editableLoading } = useRequest(CheckScriptEditable)

const readOnlyMode = computed(() => !editable.value?.data.editable && !editableLoading)

const detailDrawer = ref<InstanceType<typeof DetailDrawer>>()
const handleDelete = async (id: string) => {
  const result = await DeleteScript(id)
  if (result.success) {
    Message.success(result.message)
    refresh()
    return true
  } else {
    Message.error(result.message)
    return false
  }
}
const handleAddOne = () => {
  if (!userStore.scriptWarningConfirm) {
    Modal.warning({
      title: t('page.rule.custom-script.warning'),
      content: t('page.rule.custom-script.warning.description'),
      hideCancel: false,
      onOk: () => {
        userStore.confirmScriptWarning()
        detailDrawer.value?.viewDetail(undefined, true)
      },
      okText: t('page.rule.custom-script.warning.confirm'),
      cancelText: t('page.rule.custom-script.warning.cancel')
    })
    return
  }

  detailDrawer.value?.viewDetail(undefined, true)
}
const handleEdit = (id: string) => {
  if (!userStore.scriptWarningConfirm) {
    Modal.warning({
      title: t('page.rule.custom-script.warning'),
      content: t('page.rule.custom-script.warning.description'),
      hideCancel: false,
      onOk: () => {
        userStore.confirmScriptWarning()
        detailDrawer.value?.viewDetail(id, false)
      },
      okText: t('page.rule.custom-script.warning.confirm'),
      cancelText: t('page.rule.custom-script.warning.cancel')
    })
    return
  }

  detailDrawer.value?.viewDetail(id, false)
}
</script>
<style scoped>
.edit-btn {
  color: rgb(var(--gray-8));
  font-size: 16px;
}
</style>
