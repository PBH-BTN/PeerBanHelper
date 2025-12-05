<template>
  <a-space direction="vertical">
    <a-typography style="text-align: left">
      <a-typography-title>
        {{ t('page.oobe.btnConfig.title') }}
      </a-typography-title>
      <a-typography-paragraph>
        {{ t('page.oobe.btnConfig.description') }}
      </a-typography-paragraph>
    </a-typography>
    <a-form :model="config.btnConfig" style="margin-top: 10vh">
      <a-form-item field="mode" required hide-label>
        <a-radio-group v-model="config.btnConfig.mode" direction="vertical">
          <a-radio value="disabled">
            <template #radio="{ checked }">
              <a-space
                  align="start"
                  class="custom-radio-card"
                  :class="{ 'custom-radio-card-checked': checked }"
              >
                <div className="custom-radio-card-dot">
                  <div className="custom-radio-card-dot-icon"/>
                </div>
                <div>
                  <div class="custom-radio-card-title">
                    {{ t('page.oobe.btnConfig.mode.disabled.title') }}
                  </div>
                  <a-typography-text type="secondary">
                    {{ t('page.oobe.btnConfig.mode.disabled.description') }}
                  </a-typography-text>
                </div>
              </a-space>
            </template>
          </a-radio>
          <a-radio value="anonymous">
            <template #radio="{ checked }">
              <a-space
                  align="start"
                  class="custom-radio-card"
                  :class="{ 'custom-radio-card-checked': checked }"
              >
                <div className="custom-radio-card-dot">
                  <div className="custom-radio-card-dot-icon"/>
                </div>
                <div>
                  <div class="custom-radio-card-title">
                    {{ t('page.oobe.btnConfig.mode.anonymous.title') }}
                  </div>
                  <a-typography-text type="secondary">
                    {{ t('page.oobe.btnConfig.mode.anonymous.description') }}
                  </a-typography-text>
                </div>
              </a-space>
            </template>
          </a-radio>
          <a-radio value="account">
            <template #radio="{ checked }">
              <a-space
                  align="start"
                  class="custom-radio-card"
                  :class="{ 'custom-radio-card-checked': checked }"
              >
                <div className="custom-radio-card-dot">
                  <div className="custom-radio-card-dot-icon"/>
                </div>
                <div>
                  <div class="custom-radio-card-title">
                    {{ t('page.oobe.btnConfig.mode.account.title') }}
                  </div>
                  <a-typography-text type="secondary">
                    {{ t('page.oobe.btnConfig.mode.account.description') }}
                  </a-typography-text>
                </div>
              </a-space>
            </template>
          </a-radio>
        </a-radio-group>
      </a-form-item>

      <a-form-item v-if="config.btnConfig.mode === 'account'" field="appId" label="App ID" required>
        <a-input
            v-model="config.btnConfig.appId"
            :placeholder="t('page.oobe.btnConfig.appId.placeholder')"
            allow-clear
        />
      </a-form-item>

      <a-form-item
          v-if="config.btnConfig.mode === 'account'"
          field="appSecret"
          label="App Secret"
          required
      >
        <a-input-password
            v-model="config.btnConfig.appSecret"
            :placeholder="t('page.oobe.btnConfig.appSecret.placeholder')"
            allow-clear
        />
      </a-form-item>
    </a-form>
  </a-space>
</template>

<script lang="ts" setup>
import type {InitConfig} from '@/api/model/oobe'
import {useI18n} from 'vue-i18n'

const {t} = useI18n()
const config = defineModel<InitConfig>({required: true})
</script>

<style scoped>
.custom-radio-card {
  padding: 16px;
  border: 1px solid var(--color-border-2);
  border-radius: var(--border-radius-medium);
  width: 100%;
  box-sizing: border-box;
}

.custom-radio-card-checked {
  border-color: rgb(var(--primary-6));
  background-color: var(--color-primary-light-1);
}

.custom-radio-card-title {
  font-weight: 500;
  font-size: 14px;
  margin-bottom: 4px;
  color: var(--color-text-1);
}

.custom-radio-card-dot {
  display: inline-flex;
  align-items: center;
  width: 16px;
  height: 16px;
  border: 1px solid var(--color-border-2);
  border-radius: 50%;
  margin-top: 2px;
}

.custom-radio-card-checked .custom-radio-card-dot {
  border-color: rgb(var(--primary-6));
  background-color: rgb(var(--primary-6));
}

.custom-radio-card-dot-icon {
  width: 8px;
  height: 8px;
  margin: 0 auto;
  border-radius: 50%;
  background-color: transparent;
}

.custom-radio-card-checked .custom-radio-card-dot-icon {
  background-color: var(--color-white);
}

:deep(.arco-radio) {
  display: block;
  margin-bottom: 12px;
}

:deep(.arco-radio:last-child) {
  margin-bottom: 0;
}
</style>
