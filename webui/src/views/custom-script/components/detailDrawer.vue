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
          ? t('page.rule.custom-script.add')
          : t(
              viewOnly
                ? 'page.rule.custom-script.detail.title.view'
                : 'page.rule.custom-script.detail.title.edit'
            ) + editId
      }}
    </template>
    <div
      v-if="!isNew && loading"
      style="display: flex; justify-content: center; align-items: center; width: 100%; height: 100%"
    >
      <a-spin />
    </div>
    <template v-else>
      <a-form v-if="isNew" ref="formRef" :model="form" layout="inline">
        <a-form-item :label="t('page.rule.custom-script.language')">
          <a-select v-model="extension" style="width: 10rem">
            <a-option v-for="ext in engines" :key="ext" :value="ext">
              {{ scriptLanguages[ext].label }}
            </a-option>
          </a-select>
        </a-form-item>
        <a-form-item field="name" :label="t('page.rule.custom-script.detail.form.name')" required>
          <a-input v-model="form.name">
            <template #suffix>{{ extension }}</template>
          </a-input>
        </a-form-item>
      </a-form>
      <a-typography-paragraph v-if="!viewOnly && extension !== '.av'">
        {{ t('page.rule.custom-script.serverValidation') }}
      </a-typography-paragraph>
      <a-result v-if="loadError" status="error" :title="loadError" />
      <ScriptEditor
        v-else
        :key="extension"
        ref="scriptEditorRef"
        v-model="content"
        :extension="extension"
        :view-only="viewOnly"
      />
    </template>
    <template v-if="!viewOnly" #footer>
      <a-space>
        <a-button @click="handleCancel">
          {{ t('page.rule.custom-script.detail.action.cancel') }}
        </a-button>
        <a-button
          type="primary"
          :disabled="loading || !!loadError"
          :loading="saving"
          @click="
            async () => {
              if (await handleOk()) visible = false
            }
          "
        >
          {{ t('page.rule.custom-script.detail.action.ok') }}
        </a-button>
      </a-space>
    </template>
  </a-drawer>
</template>
<script setup lang="ts">
import {
  scriptLanguages,
  scriptExtension,
  scriptFilename,
  type ScriptExtension
} from '@/api/model/scriptLanguage'
import { GetScriptContent, UpsertScript } from '@/service/script'
import { Message, type Form } from '@arco-design/web-vue'
import { reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRequest } from 'vue-request'
import ScriptEditor from './editor/index.vue'
const props = defineProps<{ engines: ScriptExtension[] }>()
const emit = defineEmits<{ saved: [] }>()
const { t } = useI18n()
const extension = ref<ScriptExtension>()
const loadError = ref('')
const saving = ref(false)

const visible = ref(false)
const isNew = ref(true)
const viewOnly = ref(false)
const editId = ref('')
const content = ref('')

const form = reactive<{
  name: string
}>({ name: '' })
const { run, loading, cancel } = useRequest(GetScriptContent, {
  manual: true,
  onSuccess: (res) => {
    if (res.success) content.value = res.data
    else loadError.value = res.message || t('page.rule.custom-script.detail.failed')
  },
  onError: (error) => {
    loadError.value = error.message
  }
})
defineExpose({
  viewDetail: (id: string | undefined, readonly: boolean = false) => {
    cancel()
    content.value = ''
    loadError.value = ''
    extension.value = id ? scriptExtension(id) : props.engines[0]
    if (!id) {
      isNew.value = true
      form.name = ''
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
const scriptEditorRef = ref<InstanceType<typeof ScriptEditor>>()

const handleOk = async () => {
  // 只读模式直接返回
  if (!isNew.value && viewOnly.value) {
    visible.value = false
    return true
  }
  if (saving.value || loading.value || loadError.value || !extension.value) return false
  let filename = editId.value
  if (isNew.value) {
    if (await formRef.value?.validate()) return false
    const name = scriptFilename(form.name, extension.value)
    if (!name) {
      Message.error(t('page.rule.custom-script.filenameError'))
      return false
    }
    filename = name
  }
  if (!scriptEditorRef.value?.validate()) {
    Message.error(t('page.rule.custom-script.detail.syntaxError'))
    return false
  }
  saving.value = true
  try {
    const result = await UpsertScript(filename, content.value)
    if (!result.success) {
      Message.error(result.message)
      return false
    }
    Message.success(result.message)
    emit('saved')
    return true
  } catch (error) {
    Message.error((error as Error).message)
    return false
  } finally {
    saving.value = false
  }
}
const handleCancel = () => {
  if (!saving.value) visible.value = false
}
</script>
