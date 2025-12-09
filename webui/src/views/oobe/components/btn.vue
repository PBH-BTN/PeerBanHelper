<template>
  <a-space direction="vertical">
    <a-typography style="text-align: left">
      <a-typography-title style="margin-top: 0">
        {{ t('page.oobe.btnConfig.title') }}
      </a-typography-title>
      <a-typography-paragraph>
        {{ t('page.oobe.btnConfig.description') }}
      </a-typography-paragraph>
    </a-typography>
    <a-form :model="config.btn" style="margin-top: 5vh; text-align: center">
      <a-form-item hide-label>
        <a-radio-group v-model="mode">
          <a-radio value="disabled">
            {{ t('page.oobe.btnConfig.mode.disabled.title') }}
            <a-tooltip :content="t('page.oobe.btnConfig.mode.disabled.description')">
              <icon-question-circle style="margin-left: 4px; color: var(--color-text-3)" />
            </a-tooltip>
          </a-radio>
          <a-radio value="anonymous">
            {{ t('page.oobe.btnConfig.mode.anonymous.title') }}
            <a-tooltip :content="t('page.oobe.btnConfig.mode.anonymous.description')">
              <icon-question-circle style="margin-left: 4px; color: var(--color-text-3)" />
            </a-tooltip>
          </a-radio>
          <a-radio value="account">
            {{ t('page.oobe.btnConfig.mode.account.title') }}
            <a-tooltip :content="t('page.oobe.btnConfig.mode.account.description')">
              <icon-question-circle style="margin-left: 4px; color: var(--color-text-3)" />
            </a-tooltip>
          </a-radio>
        </a-radio-group>
      </a-form-item>
      <template v-if="mode === 'account'">
        <a-form-item label="App ID" required>
          <a-input
            v-model="appId"
            :style="{ width: '27em' }"
            :placeholder="t('page.oobe.btnConfig.appId.placeholder')"
            allow-clear
          />
        </a-form-item>
        <a-form-item label="App Secret" required>
          <a-input-password
            v-model="appSecret"
            :style="{ width: '27em' }"
            :placeholder="t('page.oobe.btnConfig.appSecret.placeholder')"
            allow-clear
          />
        </a-form-item>
      </template>
    </a-form>
  </a-space>
</template>

<script lang="ts" setup>
import type { InitConfig } from '@/api/model/oobe'
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const config = defineModel<InitConfig>({ required: true })

type BtnMode = 'disabled' | 'anonymous' | 'account'

const mode = computed<BtnMode>({
  get() {
    if (!config.value.btn.enabled && !config.value.btn.submit) {
      return 'disabled'
    }
    if (config.value.btn.app_id === null && config.value.btn.app_secret === null) {
      return 'anonymous'
    }
    return 'account'
  },
  set(value: BtnMode) {
    switch (value) {
      case 'disabled':
        config.value.btn.enabled = false
        config.value.btn.submit = true
        config.value.btn.app_id = null
        config.value.btn.app_secret = null
        break
      case 'anonymous':
        config.value.btn.enabled = true
        config.value.btn.submit = true
        config.value.btn.app_id = null
        config.value.btn.app_secret = null
        break
      case 'account':
        config.value.btn.enabled = true
        config.value.btn.submit = true
        if (config.value.btn.app_id === null) {
          config.value.btn.app_id = ''
        }
        if (config.value.btn.app_secret === null) {
          config.value.btn.app_secret = ''
        }
        break
    }
  }
})

const appId = computed({
  get() {
    return config.value.btn.app_id ?? ''
  },
  set(value: string) {
    config.value.btn.app_id = value || null
  }
})

const appSecret = computed({
  get() {
    return config.value.btn.app_secret ?? ''
  },
  set(value: string) {
    config.value.btn.app_secret = value || null
  }
})
</script>
