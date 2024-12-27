<template>
  <a-form-item
    field="config.host"
    :label="t('page.settings.tab.config.push.form.stmp.host')"
    required
  >
    <a-input v-model="model.host" />
  </a-form-item>
  <a-form-item
    field="config.port"
    :label="t('page.settings.tab.config.push.form.stmp.port')"
    required
  >
    <a-input-number v-model="model.port" style="width: 7em" :min="1" :max="65535" />
  </a-form-item>
  <a-form-item
    field="config.sender"
    :label="t('page.settings.tab.config.push.form.stmp.sender')"
    required
  >
    <a-input v-model="model.sender" />
  </a-form-item>

  <a-form-item
    field="config.receivers"
    :label="t('page.settings.tab.config.push.form.stmp.receivers')"
    required
  >
    <a-input-tag
      v-model="model.receivers"
      :placeholder="t('page.settings.tab.config.push.form.stmp.receivers.placeholder')"
    />
  </a-form-item>
  <a-form-item
    field="config.encryption"
    :label="t('page.settings.tab.config.push.form.stmp.encryption')"
    required
  >
    <a-select v-model="model.encryption" style="width: 12em">
      <a-option :value="SMTPEncryption.None">None</a-option>
      <a-option :value="SMTPEncryption.EnforceStartTLS">Force STARTTLS</a-option>
      <a-option :value="SMTPEncryption.StartTLS">{{ SMTPEncryption.StartTLS }}</a-option>
      <a-option :value="SMTPEncryption.SSLTLS">SSL/TLS</a-option>
    </a-select>
  </a-form-item>
  <a-form-item field="config.auth" :label="t('page.settings.tab.config.push.form.stmp.auth')">
    <a-switch v-model="model.auth" />
  </a-form-item>
  <a-form-item
    v-if="model.auth"
    :label="t('page.settings.tab.config.push.form.stmp.authInfo')"
    :content-flex="false"
    :merge-props="false"
  >
    <a-space direction="vertical" fill>
      <a-form-item
        required
        field="config.username"
        :label="t('page.settings.tab.config.push.form.stmp.username')"
      >
        <a-input v-model="model.username" />
      </a-form-item>
      <a-form-item
        field="config.password"
        :label="t('page.settings.tab.config.push.form.stmp.password')"
        required
      >
        <a-input-password v-model="model.password" />
      </a-form-item>
    </a-space>
  </a-form-item>
  <a-collapse :bordered="false">
    <a-collapse-item :header="t('page.settings.tab.config.push.form.stmp.advance')">
      <a-form-item
        field="config.sendPartial"
        :label="t('page.settings.tab.config.push.form.stmp.sendPartial')"
      >
        <a-switch v-model="model.sendPartial" />
      </a-form-item>
      <a-form-item
        field="config.senderName"
        :label="t('page.settings.tab.config.push.form.stmp.senderName')"
      >
        <a-input v-model="model.senderName" />
      </a-form-item>
    </a-collapse-item>
  </a-collapse>
</template>
<script setup lang="ts">
import { type SMTPConfig, SMTPEncryption } from '@/api/model/push'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const model = defineModel<SMTPConfig>({ required: true })
</script>
