<template>
  <a-space direction="vertical">
    <a-typography style="text-align: left">
      <a-typography-title>
        {{ t('page.oobe.welcome.title') }}
      </a-typography-title>
      <a-typography-paragraph>
        {{ t('page.oobe.welcome.description') }}
      </a-typography-paragraph>
      <a-typography-paragraph>
        <i18n-t keypath="page.oobe.steps.welcome.docTips">
          <template #doc>
            <a-link href="https://doc.pbh-btn.com/" target="_blank">
              {{ t('page.oobe.steps.welcome.doc') }}
            </a-link>
          </template>
          <template #privacy>
            <a-link
              href="https://github.com/PBH-BTN/terms/blob/master/peerbanhelper-privacy-zh-CN.md"
              target="_blank"
            >
              {{ t('page.oobe.steps.welcome.privacy') }}
            </a-link>
          </template>
        </i18n-t>
      </a-typography-paragraph>

      <a-form :model="config" style="margin-top: 15vh">
        <a-form-item field="acceptPrivacy" required hide-label>
          <a-checkbox v-model="config.acceptPrivacy">
            <i18n-t keypath="page.oobe.steps.welcome.privacy.accept">
              <template #privacy>
                <a-link
                  href="https://github.com/PBH-BTN/terms/blob/master/peerbanhelper-privacy-zh-CN.md"
                  target="_blank"
                >
                  {{ t('page.oobe.steps.welcome.privacy') }}
                </a-link>
              </template>
            </i18n-t>
          </a-checkbox>
        </a-form-item>
      </a-form>
    </a-typography>
  </a-space>
</template>
<script lang="ts" setup>
import type { InitConfig } from '@/api/model/oobe'
import { useI18n } from 'vue-i18n'
const { t } = useI18n()
const config = defineModel<InitConfig>({ required: true })
</script>
