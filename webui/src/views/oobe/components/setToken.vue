<template>
  <a-space direction="vertical">
    <a-typography style="text-align: left">
      <a-typography-title>
        {{ t('page.oobe.setToken.title') }}
      </a-typography-title>
      <a-typography-paragraph>
        {{ t('page.oobe.setToken.description') }}
      </a-typography-paragraph>
    </a-typography>
    <a-form :model="config" style="margin-top: 15vh">
      <a-form-item label="Token" required>
        <a-space>
          <a-input :style="{ width: '27em' }" placeholder="aa-bb-cc-dd-ee-ff" allow-clear v-model="config.token"
            :rules="[{ required: true }]" validate-trigger="blur">
            <template #prefix>
              <icon-lock />
            </template>
          </a-input>
          <a-tooltip :content="t('page.oobe.setToken.generate')">
            <a-button class="generate-btn" type="text" shape="circle" @click="generateToken">
              <template #icon>
                <icon-refresh />
              </template>
            </a-button>
          </a-tooltip>
        </a-space>
      </a-form-item>
    </a-form>
  </a-space>
</template>

<script lang="ts" setup>
import type { InitConfig } from '@/api/model/oobe'
import { useI18n } from 'vue-i18n'
const { t } = useI18n()
const config = defineModel<InitConfig>({ required: true })
const generateToken = async () => {
  config.value.token = await uuidFunc()
}
const uuidFunc = async () => {
  if (crypto.randomUUID) {
    return crypto.randomUUID()
  } else {
    const uuid = await import('uuid')
    return uuid.v4()
  }
}
</script>
<style scoped>
.generate-btn {
  color: rgb(var(--gray-8));
  font-size: 16px;
}
</style>
