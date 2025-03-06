<template>
  <a-modal
    v-model:visible="showModal"
    :mask-closable="false"
    :title="
      newItem
        ? t('page.settings.tab.config.push.form.title.new')
        : t('page.settings.tab.config.push.form.title.edit')
    "
    width="auto"
    unmount-on-close
    @cancel="() => resetFields()"
    @before-ok="handleBeforeOk"
  >
    <template #footer>
      <a-space style="display: flex; justify-content: space-between">
        <a-button :loading="testLoading" @click="handleTest">{{
          t('page.settings.tab.config.push.form.action.test')
        }}</a-button>
        <a-space>
          <a-button @click="handleCancel">{{
            t('page.settings.tab.config.push.form.action.cancel')
          }}</a-button>
          <a-button type="primary" :loading="okLoading" @click="handleOk">{{
            t('page.settings.tab.config.push.form.action.ok')
          }}</a-button>
        </a-space>
      </a-space>
    </template>
    <a-form ref="formRef" :model="form" auto-label-width>
      <a-form-item field="name" :label="t('page.settings.tab.config.push.form.name')" required>
        <a-input
          v-model="form.name"
          :placeholder="t('page.settings.tab.config.push.form.name.placeholder')"
          allow-clear
        />
      </a-form-item>
      <a-form-item
        field="type"
        :label="t('page.settings.tab.config.push.form.type')"
        required
        :disabled="!newItem"
      >
        <a-radio-group v-model="form.type" style="width: 33rem">
          <a-grid :cols="5" :row-gap="16">
            <a-radio :value="PushType.Email">{{
              t('page.settings.tab.config.push.form.type.' + PushType.Email)
            }}</a-radio>
            <a-radio :value="PushType.Telegram">{{
              t('page.settings.tab.config.push.form.type.' + PushType.Telegram)
            }}</a-radio>
            <a-radio :value="PushType.PushPlus">{{
              t('page.settings.tab.config.push.form.type.' + PushType.PushPlus)
            }}</a-radio>
            <a-radio :value="PushType.ServerChan">{{
              t('page.settings.tab.config.push.form.type.' + PushType.ServerChan)
            }}</a-radio>
            <a-radio :value="PushType.Bark">{{
              t('page.settings.tab.config.push.form.type.' + PushType.Bark)
            }}</a-radio>
          </a-grid>
        </a-radio-group>
      </a-form-item>
      <component :is="formMap[form.type]" v-model="form.config" />
    </a-form>
  </a-modal>
</template>
<script setup lang="ts">
import { PushType, type PushConfig } from '@/api/model/push'
import { CreatePushChannel, TestPushChannel, UpdatePushChannel } from '@/service/push'
import { Message, type Form } from '@arco-design/web-vue'
import { defineAsyncComponent, ref, type Component } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const showModal = ref(false)
const newItem = ref(false)
const okLoading = ref(false)
const testLoading = ref(false)

const form = ref({ config: {} } as PushConfig)
const oldName = ref('')
defineExpose({
  showModal: (isNewItem: boolean, currentConfig?: PushConfig) => {
    newItem.value = isNewItem
    if (!isNewItem && currentConfig) {
      form.value.name = currentConfig.name
      oldName.value = currentConfig.name
      form.value.type = currentConfig.type
      form.value.config = currentConfig.config
    } else {
      form.value = { config: {} } as PushConfig
    }
    showModal.value = true
  }
})
const formMap: Record<PushType, Component> = {
  [PushType.Email]: defineAsyncComponent(
    () => import('@/views/settings/components/config/components/push/forms/mailForm.vue')
  ),
  [PushType.Telegram]: defineAsyncComponent(
    () => import('@/views/settings/components/config/components/push/forms/telegramForm.vue')
  ),
  [PushType.PushPlus]: defineAsyncComponent(
    () => import('@/views/settings/components/config/components/push/forms/pushplusForm.vue')
  ),
  [PushType.ServerChan]: defineAsyncComponent(
    () => import('@/views/settings/components/config/components/push/forms/serverchanForm.vue')
  ),
  [PushType.Bark]: defineAsyncComponent(
    () => import('@/views/settings/components/config/components/push/forms/barkForm.vue')
  )
}

const emits = defineEmits<{
  (e: 'changed'): void
}>()

const formRef = ref<InstanceType<typeof Form>>()
const handleBeforeOk = async () => {
  const validateError = await formRef.value?.validate()
  if (validateError) {
    return false
  }
  try {
    if (newItem.value) {
      const res = await CreatePushChannel(form.value)
      if (res.success) {
        emits('changed')
        resetFields()
        showModal.value = false
        Message.success(res.message)
        return true
      } else {
        throw new Error(res.message)
      }
    } else {
      const res = await UpdatePushChannel(oldName.value, form.value)
      if (res.success) {
        emits('changed')
        resetFields()
        showModal.value = false
        Message.success(res.message)
        return true
      } else {
        throw new Error(res.message)
      }
    }
  } catch (e: unknown) {
    if (e instanceof Error) Message.error({ content: e.message, resetOnHover: true })
    return false
  }
}
const resetFields = () => {
  formRef.value?.resetFields()
}
const handleCancel = () => {
  resetFields()
  showModal.value = false
}

const handleOk = async () => {
  okLoading.value = true
  const res = await handleBeforeOk()
  okLoading.value = false
  if (res) {
    showModal.value = false
  }
}
const handleTest = async () => {
  const validateError = await formRef.value?.validate()
  if (validateError) {
    return
  }
  testLoading.value = true
  try {
    const res = await TestPushChannel(form.value)
    if (res.success) {
      Message.success(res.message)
    } else {
      throw new Error(res.message)
    }
  } catch (e: unknown) {
    if (e instanceof Error) Message.error({ content: e.message, resetOnHover: true })
  } finally {
    testLoading.value = false
  }
}
</script>
<style scoped>
a {
  text-decoration: none;
}
</style>
