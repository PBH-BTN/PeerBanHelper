<template>
  <a-form-item
    :label="props.label"
    :required="props.required"
    field="model"
    :show-header="false"
    :tooltip="props.tooltip"
    :rules="[
      {
        validator: nonEmptyValidator
      }
    ]"
  >
    <a-button type="primary" @click="visible = true">{{
      t('page.settings.tab.profile.form.ignoreAddress.action')
    }}</a-button>
    <a-drawer
      :visible="visible"
      hide-cancel
      :title="props.label"
      mask-closable
      @ok="visible = false"
      @cancel="visible = false"
    >
      <a-space direction="vertical">
        <a-button @click="addNewItem">
          <template #icon>
            <icon-plus />
          </template>
        </a-button>
        <a-list
          style="min-width: 200px"
          :virtual-list-props="props.virtualListProps"
          :data="dataWithIndex"
          :pagination-props="controlledPaginationProps"
        >
          <template #item="{ item }">
            <a-list-item :key="item.index">
              <a-space>
                <a-input v-model="model[item.index]" />
                <br />
              </a-space>
              <template #actions>
                <a-button
                  class="edit-btn"
                  status="danger"
                  shape="circle"
                  type="text"
                  @click="model.splice(item.index, 1)"
                >
                  <template #icon>
                    <icon-delete />
                  </template>
                </a-button>
              </template>
            </a-list-item>
          </template>
        </a-list>
      </a-space>
    </a-drawer>
  </a-form-item>
</template>
<script setup lang="ts">
import type { PaginationProps } from '@arco-design/web-vue'
import type { VirtualListProps } from '@arco-design/web-vue/es/_components/virtual-list-v2/interface'
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

type Value = string
const model = defineModel<Value[]>({ required: true })
const props = defineProps<{
  label: string
  required?: boolean
  tooltip?: string
  virtualListProps?: VirtualListProps
  paginationProps?: PaginationProps
}>()

// Add pagination state management
const currentPage = ref(1)
const pageSize = computed(() => props.paginationProps?.pageSize || 10)

// Create computed pagination props that we can control
const controlledPaginationProps = computed(() => {
  if (!props.paginationProps) return undefined
  return {
    ...props.paginationProps,
    current: currentPage.value,
    onChange: (page: number) => {
      currentPage.value = page
    }
  }
})

const dataWithIndex = computed(() => {
  return model.value.map((item, index) => ({ item, index }))
})

const addNewItem = () => {
  model.value.push('')
  // Calculate which page the new item will be on and navigate there
  const totalItems = model.value.length
  const lastPage = Math.ceil(totalItems / pageSize.value)
  currentPage.value = lastPage
}

const nonEmptyValidator = (_: unknown, cb: (error?: string) => void) => {
  if (model.value.some((item: string) => item.trim() === ''))
    cb(t('page.settings.tab.profile.formArray.emptyTips'))
  else cb()
}
const visible = ref(false)
</script>
<style scoped>
.edit-btn {
  color: rgb(var(--gray-8));
  font-size: 16px;
}
</style>
