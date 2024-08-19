<template>
  <a-modal v-model:visible="showModal" :title="newItem
      ? t('page.rule_management.ruleSubscribe.editModal.title.new')
      : t('page.rule_management.ruleSubscribe.editModal.title')
    " unmountOnClose @before-ok="handleBeforeOk">
    <a-form ref="formRef" :model="form" :rules="rules">
      <a-form-item field="ruleId" label="ID">
        <a-input v-model="form.ruleId" :disabled="!newItem" allow-clear />
        <template #extra>{{ t('page.rule_management.ruleSubscribe.editModal.form.id.extra') }}
        </template>
      </a-form-item>
      <a-form-item field="ruleName" :label="t('page.rule_management.ruleSubscribe.editModal.form.name')">
        <a-input v-model="form.ruleName" allow-clear />
      </a-form-item>
      <a-form-item field="subUrl" label="URL">
        <a-textarea v-model="form.subUrl" allow-clear @change="handleUrlChange" :auto-size="{
          minRows: 2,
          maxRows: 5
        }" />
      </a-form-item>
    </a-form>
  </a-modal>
</template>
<script setup lang="ts">
import type { ruleBrief } from '@/api/model/ruleSubscribe'
import { useI18n } from 'vue-i18n'
import { reactive, ref } from 'vue'
import { Message, type FieldRule, type Form } from '@arco-design/web-vue'
import { AddRuleItem, UpdateRuleItem } from '@/service/ruleSubscribe'
import path from 'path'
const { t } = useI18n()
const showModal = ref(false)
const newItem = ref(false)
const form = reactive({
  ruleId: '',
  ruleName: '',
  subUrl: ''
})
const rules: Record<string, FieldRule[]> = {
  ruleId: [
    {
      required: true
    }
  ],
  ruleName: [
    {
      required: true,
      message: t('page.rule_management.ruleSubscribe.editModal.form.name.required')
    }
  ],
  subUrl: [
    {
      type: 'url',
      required: true
    }
  ]
}
let callbackFn: ((record: Partial<ruleBrief>) => void) | undefined
defineExpose({
  showModal: (
    isNewItem: boolean,
    finishedCallback?: (record: Partial<ruleBrief>) => void,
    ruleItem?: ruleBrief
  ) => {
    newItem.value = isNewItem
    callbackFn = finishedCallback
    if (!isNewItem && ruleItem) {
      form.ruleId = ruleItem.ruleId
      form.ruleName = ruleItem.ruleName
      form.subUrl = ruleItem.subUrl
    } else {
      form.ruleId = Math.random().toString(36).slice(2, 10) // random new ruleId
      form.ruleName = ''
      form.subUrl = ''
    }
    showModal.value = true
  }
})
const formRef = ref<InstanceType<typeof Form>>()
const handleBeforeOk = async () => {
  const validateError = await formRef.value?.validate()
  if (validateError) {
    return false
  }
  if (!newItem.value) {
    // edit
    const result = await UpdateRuleItem(form)
    if (result.success) {
      Message.error(result.message)
      return true
    }
    if (callbackFn) callbackFn(form)
    return true
  } else {
    // new rule item
    const result = await AddRuleItem(form)
    if (result.success) {
      Message.error(result.message)
      return true
    }
    if (callbackFn) callbackFn(form)
    return true
  }
}
const handleUrlChange = (value: string) => {
  if (!form.ruleName) {
    try {
      const url = new URL(value)
      const fileName = path.parse(url.pathname).name
      form.ruleName = fileName
    } catch (_) {
      // ignore
    }
  }
}
</script>
