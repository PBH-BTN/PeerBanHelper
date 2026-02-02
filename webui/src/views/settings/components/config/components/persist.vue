<template>
  <a-space direction="vertical" fill>
    <a-typography-title :heading="3">{{
      t('page.settings.tab.config.persist.title')
    }}</a-typography-title>
    <a-form-item :label="t('page.settings.tab.config.persist.banlist')" field="persist.banlist">
      <a-switch v-model="persistModel.banlist" />
    </a-form-item>
    <a-form-item
      :label="t('page.settings.tab.config.persist.ban_logs_keep_days')"
      field="persist.ban_logs_keep_days"
    >
      <a-input-number v-model="persistModel.ban_logs_keep_days" style="width: 100px" :precision="0">
        <template #suffix> {{ t('page.settings.tab.config.unit.day') }} </template>
      </a-input-number>
    </a-form-item>
    <a-form-item :label="t('page.settings.tab.config.database.type')" field="database.type">
      <a-radio-group v-model="dbType" @change="onDBTypeChanged">
        <a-space>
          <a-radio value="sqlite">SQLite</a-radio>
          <a-radio value="h2">H2</a-radio>
          <a-radio value="mysql">MySQL</a-radio>
          <a-radio value="postgresql">PostgreSQL</a-radio></a-space
        >
      </a-radio-group>
    </a-form-item>
    <a-form-item
      v-if="databaseModel.type === 'mysql' || databaseModel.type === 'postgresql'"
      :label="t('page.settings.tab.config.database.host')"
      field="database.host"
      required
    >
      <a-input v-model="databaseModel.host" style="width: 250px" />
    </a-form-item>
    <a-form-item
      v-if="databaseModel.type === 'mysql' || databaseModel.type === 'postgresql'"
      :label="t('page.settings.tab.config.database.port')"
      field="database.port"
      required
    >
      <a-input-number v-model="databaseModel.port" style="width: 100px" :precision="0" />
    </a-form-item>
    <a-form-item
      v-if="databaseModel.type === 'mysql' || databaseModel.type === 'postgresql'"
      :label="t('page.settings.tab.config.database.database')"
      field="database.database"
      required
    >
      <a-input v-model="databaseModel.database" style="width: 250px" />
    </a-form-item>
    <a-form-item
      v-if="databaseModel.type === 'mysql' || databaseModel.type === 'postgresql'"
      :label="t('page.settings.tab.config.database.username')"
      field="database.username"
      required
    >
      <a-input v-model="databaseModel.username" style="width: 250px" />
    </a-form-item>
    <a-form-item
      v-if="databaseModel.type === 'mysql' || databaseModel.type === 'postgresql'"
      :label="t('page.settings.tab.config.database.password')"
      field="database.password"
      required
    >
      <a-input-password v-model="databaseModel.password" style="width: 250px" />
    </a-form-item>
    <a-form-item v-if="databaseModel.type === 'mysql' || databaseModel.type === 'postgresql'">
      <a-button :loading="testing" @click="handleTest">{{
        t('page.oobe.advance.database.test')
      }}</a-button>
    </a-form-item>
  </a-space>
</template>
<script setup lang="ts">
import { type DatabaseConfig, type DatabaseType, type Persist } from '@/api/model/config'
import { TestDatabaseConnection } from '@/service/init'
import { Message, Modal } from '@arco-design/web-vue'
import { computed, ref } from 'vue'

import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const persistModel = defineModel<Persist>('persist', { required: true })
const databaseModel = defineModel<DatabaseConfig>('database', { required: true })
const dbType = computed(() => databaseModel.value.type)
const onDBTypeChanged = (v: string | number | boolean) => {
  Modal.warning({
    title: t('page.settings.tab.config.database.type.changeWarning'),
    content: t('page.settings.tab.config.database.type.changeWarningContent'),
    hideCancel: false,
    onOk: () => {
      databaseModel.value.type = v as DatabaseType
    },
    onCancel: () => {
      //do nothing
    }
  })
}
const testing = ref(false)
const handleTest = async () => {
  try {
    const testResult = await TestDatabaseConnection(databaseModel.value)
    if (!testResult.success) throw new Error(testResult.message)
  } catch (e: unknown) {
    if (e instanceof Error) Message.error({ content: e.message, resetOnHover: true })
    return false
  } finally {
    testing.value = false
  }
  Message.success({ content: t('page.oobe.advance.addDatabase.test.success'), resetOnHover: true })
}
</script>
