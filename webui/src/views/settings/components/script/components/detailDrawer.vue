<template>
  <a-drawer
    :width="800"
    :visible="visible"
    unmount-on-close
    :hide-cancel="viewOnly"
    @cancel="handleCancel"
    @before-ok="handleOk"
  >
    <template #title>
      {{
        isNew
          ? t('page.settings.tab.script.add')
          : t(
              viewOnly
                ? 'page.settings.tab.script.detail.title.view'
                : 'page.settings.tab.script.detail.title.edit'
            ) + editId
      }}
    </template>
    <div
      v-if="!isNew && loading"
      style="display: flex; justify-content: center; align-items: center; width: 100%; height: 100%"
    >
      <a-spin />
    </div>
    <editor v-else v-model="content" :view-only="viewOnly" />
    <template v-if="!viewOnly" #footer>
      <a-space fill style="display: flex; justify-content: space-between">
        <a-form ref="formRef" :model="form" :disabled="!isNew" auto-label-width>
          <a-form-item
            field="name"
            :label="t('page.settings.tab.script.detail.form.name')"
            required
            style="margin-bottom: 0"
          >
            <a-input v-model="form.name" style="width: 20rem">
              <template #suffix> .av </template>
            </a-input>
          </a-form-item>
        </a-form>
        <a-space>
          <a-button @click="handleCancel">
            {{ t('page.settings.tab.script.detail.action.cancel') }}
          </a-button>
          <a-button
            type="primary"
            @click="
              async () => {
                if (await handleOk()) visible = false
              }
            "
          >
            {{ t('page.settings.tab.script.detail.action.ok') }}
          </a-button>
        </a-space>
      </a-space>
    </template>
  </a-drawer>
</template>
<script setup lang="ts">
import { GetScriptContent, UpsertScript } from '@/service/script'
import { Message, type Form } from '@arco-design/web-vue'
import { reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRequest } from 'vue-request'
import editor from './editor/index.vue'
const { t } = useI18n()

const visible = ref(false)
const isNew = ref(true)
const viewOnly = ref(false)
const editId = ref('')
const content = ref('')

const form = reactive<{
  name: string
}>({ name: '' })
const { run, loading } = useRequest(GetScriptContent, {
  manual: true,
  onSuccess: (res) => {
    content.value = res.data
  }
})
defineExpose({
  viewDetail: (id: string | undefined, readonly: boolean = false) => {
    if (!id) {
      isNew.value = true
      form.name = ''
      content.value = ''
      viewOnly.value = false
    } else {
      viewOnly.value = readonly
      isNew.value = false
      editId.value = id
      form.name = id
      run(editId.value)
    }
    visible.value = true
  }
})
const formRef = ref<typeof Form>()

const handleOk = async () => {
  // 只读模式直接返回
  if (!isNew.value && viewOnly.value) {
    visible.value = false
    return true
  }
  // 新增
  if (isNew.value) {
    const validateError = await formRef.value?.validate()
    if (validateError || form.name.length === 0) {
      return false
    }
    form.name = form.name + '.av'
  }
  const result = await UpsertScript(form.name, content.value)
  if (result.success) {
    Message.success(result.message)
    return true
  } else {
    Message.error(result.message)
    return false
  }
}
const handleCancel = () => {
  visible.value = false
}
</script>
