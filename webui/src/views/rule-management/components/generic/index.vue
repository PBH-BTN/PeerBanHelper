<template>
  <a-space direction="vertical" fill>
    <a-typography-text style="font-size: 1.2em">
      {{
        t('page.rule_management.generic.description', {
          type: t('page.rule_management.' + type)
        })
      }}
    </a-typography-text>
    <a-space class="align-right" fill>
      <a-button type="primary" @click="handleAddOne">
        <template #icon>
          <icon-plus-circle />
        </template>
        {{ t('page.rule_management.generic.addOne') }}
      </a-button>
    </a-space>
    <a-table :columns="columns" :data="dataSource" :loading="loading">
      <template #data="{ record, rowIndex }">
        <a-space v-if="!record.editing" style="display: flex; justify-content: space-between" fill>
          <a-typography-text>{{ record.data }}</a-typography-text>
          <a-space>
            <a-button
              class="edit-btn"
              shape="circle"
              type="text"
              @click="record.editing = !record.editing"
            >
              <template #icon>
                <icon-edit />
              </template>
            </a-button>
            <a-popconfirm
              :content="t('page.rule_management.ruleSubscribe.column.deleteConfirm')"
              type="warning"
              @before-ok="() => handleDelete(record.data)"
            >
              <a-button class="edit-btn" shape="circle" status="danger" type="text">
                <template #icon>
                  <icon-delete />
                </template>
              </a-button>
            </a-popconfirm>
          </a-space>
        </a-space>
        <a-space v-else style="display: flex; justify-content: space-between" fill>
          <a-input
            v-model="record.data"
            :placeholder="t(`page.rule_management.${type}.placeholder`)"
            style="max-width: 150px"
          />
          <a-space>
            <AsyncMethod
              v-slot="{ run, loading: load }"
              once
              :async-fn="() => handleSubmit(rowIndex)"
            >
              <a-button class="edit-btn" shape="circle" type="text" status="success" @click="run">
                <template #icon>
                  <icon-refresh v-if="load" :spin="load" />
                  <icon-check v-else />
                </template>
              </a-button>
              <a-button
                class="edit-btn"
                shape="circle"
                status="danger"
                type="text"
                :disabled="loading"
                @click="
                  record.isNew
                    ? dataSource.splice(rowIndex, 1)
                    : ((record.data = record.oldData), (record.editing = false))
                "
              >
                <template #icon>
                  <icon-close />
                </template>
              </a-button>
            </AsyncMethod>
          </a-space>
        </a-space>
      </template>
    </a-table>
  </a-space>
</template>
<script lang="ts" setup>
import { type ruleType } from '@/api/model/blacklist'
import AsyncMethod from '@/components/asyncMethod.vue'
import { addBlackList, deleteBlackList, getBlackList } from '@/service/blacklist'
import { Message, type TableColumnData } from '@arco-design/web-vue'
import { computed, reactive, type Reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRequest } from 'vue-request'
const { t } = useI18n()
const props = defineProps<{
  type: ruleType
}>()
const type = computed(() => props.type)
type dataSourceItem<T extends ruleType> = {
  data: T extends 'port' | 'asn' ? number : string
  oldData: T extends 'port' | 'asn' ? number : string
  editing: boolean
  isNew: boolean
}
const dataSource = reactive([]) as Reactive<dataSourceItem<ruleType>[]>
const columns: TableColumnData[] = [
  {
    title: () => t('page.rule_management.' + type.value),
    slotName: 'data'
  }
]
const { loading, refresh } = useRequest(
  async () => {
    const data = await getBlackList(type.value)
    dataSource.splice(0, dataSource.length)
    dataSource.push(
      ...data.data[type.value].map((item) => ({
        data: item,
        oldData: item,
        editing: false,
        isNew: false
      }))
    )
  },
  {
    refreshDeps: type
  }
)
const handleAddOne = () => {
  dataSource.unshift({
    data: '',
    oldData: '',
    editing: true,
    isNew: true
  })
}
const handleSubmit = async (index: number) => {
  try {
    if (dataSource[index].isNew) {
      // add new item
      const resp = await addBlackList(dataSource[index].data, type.value)
      if (!resp.success) {
        throw new Error(resp.message)
      }
      Message.success({ content: resp.message, resetOnHover: true })
    } else {
      if (dataSource[index].data === dataSource[index].oldData) {
        //没有变化直接返回
        dataSource[index].editing = false
        return
      }
      // update item 先添加，再删除，避免添加失败
      let resp = await addBlackList(dataSource[index].data, type.value)
      if (!resp.success) {
        throw new Error(resp.message)
      }
      resp = await deleteBlackList(dataSource[index].oldData, type.value)
      if (!resp.success) {
        throw new Error(resp.message)
      }
      Message.success({ content: resp.message, resetOnHover: true })
    }
    refresh()
  } catch (e: unknown) {
    if (e instanceof Error) {
      Message.error({ content: e.message, resetOnHover: true })
    }
  }
}

const handleDelete = async (target: number | string) => {
  try {
    const resp = await deleteBlackList(target, type.value)
    if (!resp.success) {
      throw new Error(resp.message)
    }
    Message.success({ content: resp.message, resetOnHover: true })
    refresh()
    return true
  } catch (e: unknown) {
    if (e instanceof Error) Message.error({ content: e.message, resetOnHover: true })
    return false
  }
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
