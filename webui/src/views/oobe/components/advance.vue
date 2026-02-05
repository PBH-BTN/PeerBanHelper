<template>
  <a-space direction="vertical" size="medium">
    <a-typography-text>{{ t('page.oobe.advance.description') }}</a-typography-text>
    <a-form :model="pageConfig">
      <a-form-item field="pageConfig.known" :label="t('page.oobe.advance.known')">
        <a-switch v-model="pageConfig.known" @change="switchChange"></a-switch>
      </a-form-item>
    </a-form>
    <a-form
      v-if="pageConfig.known"
      ref="formRef"
      :model="config"
      style="margin-top: 5vh; text-align: center"
    >
      <a-form-item
        required
        :label="t('page.settings.tab.config.database.type')"
        field="database.type"
      >
        <a-radio-group v-model="config.database.type">
          <a-space>
            <a-radio value="sqlite">SQLite</a-radio>
            <a-radio value="h2">H2</a-radio>
            <a-radio value="mysql">MySQL</a-radio>
            <a-radio value="postgresql">PostgreSQL</a-radio></a-space
          >
        </a-radio-group>
      </a-form-item>
      <a-form-item
        v-if="config.database.type === 'mysql' || config.database.type === 'postgresql'"
        :label="t('page.settings.tab.config.database.host')"
        field="database.host"
        required
      >
        <a-input v-model="config.database.host" style="width: 250px" />
      </a-form-item>
      <a-form-item
        v-if="config.database.type === 'mysql' || config.database.type === 'postgresql'"
        :label="t('page.settings.tab.config.database.port')"
        field="database.port"
        required
      >
        <a-input-number v-model="config.database.port" style="width: 100px" :precision="0" />
      </a-form-item>
      <a-form-item
        v-if="config.database.type === 'mysql' || config.database.type === 'postgresql'"
        :label="t('page.settings.tab.config.database.database')"
        field="database.database"
        required
      >
        <a-input v-model="config.database.database" style="width: 250px" />
      </a-form-item>
      <a-form-item
        v-if="config.database.type === 'mysql' || config.database.type === 'postgresql'"
        :label="t('page.settings.tab.config.database.username')"
        field="database.username"
        required
      >
        <a-input v-model="config.database.username" style="width: 250px" />
      </a-form-item>
      <a-form-item
        v-if="config.database.type === 'mysql' || config.database.type === 'postgresql'"
        :label="t('page.settings.tab.config.database.password')"
        field="database.password"
        required
      >
        <a-input-password v-model="config.database.password" style="width: 250px" />
      </a-form-item>
      <a-form-item v-if="config.database.type === 'mysql' || config.database.type === 'postgresql'">
        <a-button :loading="testing" @click="handleTest">{{
          t('page.oobe.advance.database.test')
        }}</a-button>
      </a-form-item>
    </a-form>
  </a-space>
</template>
<script lang="ts" setup>
import type { InitConfig } from '@/api/model/oobe'
import { TestDatabaseConnection } from '@/service/init'
import { Message, type Form } from '@arco-design/web-vue'
import { reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
const { t } = useI18n()
const config = defineModel<InitConfig>({ required: true })
const pageConfig = reactive({
  known: false
})
const testing = ref(false)

const formRef = ref<typeof Form>()
const handleTest = async () => {
  const validateError = await formRef.value?.validate()
  if (validateError) {
    return
  }
  try {
    const testResult = await TestDatabaseConnection(config.value.database)
    if (!testResult.success) throw new Error(testResult.message)
  } catch (e: unknown) {
    if (e instanceof Error) Message.error({ content: e.message, resetOnHover: true })
    return false
  } finally {
    testing.value = false
  }
  Message.success({ content: t('page.oobe.advance.addDatabase.test.success'), resetOnHover: true })
  config.value.databaseValid = true
}
const switchChange = () => {
  // 还原默认配置
  if (!pageConfig.known) {
    config.value.database = { type: 'sqlite' }
    config.value.databaseValid = true
  }
}
</script>
