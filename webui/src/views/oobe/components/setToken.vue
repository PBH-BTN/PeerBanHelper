<template>
  <a-space direction="vertical">
    <a-typography style="text-align: left">
      <a-typography-title style="margin-top: 0">
        {{ t('page.oobe.setToken.title') }}
      </a-typography-title>
      <a-typography-paragraph>
        {{ t('page.oobe.setToken.description') }}
      </a-typography-paragraph>
    </a-typography>
    <a-form :model="config" style="margin-top: 15vh">
      <a-form-item label="Token" required>
        <a-space>
          <a-input
            v-model="config.token"
            :style="{ width: '27em' }"
            placeholder="aa-bb-cc-dd-ee-ff"
            allow-clear
            :rules="
              [
                { required: true, message: t('login.form.password.errMsg') },
                {
                  validator: (value, callback) => {
                    if (/[a-zA-Z0-9-_]+/.test(value)) {
                      callback()
                    } else {
                      callback(t('login.form.password.errMsg'))
                    }
                  }
                }
              ] as FieldRule[]
            "
            validate-trigger="blur"
          >
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
import GenerateUUID from '@/utils/uuid'
import type { FieldRule } from '@arco-design/web-vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const config = defineModel<InitConfig>({ required: true })
const generateToken = async () => {
  config.value.token = await GenerateUUID()
}
</script>
<style scoped>
.generate-btn {
  color: rgb(var(--gray-8));
  font-size: 16px;
}
</style>
