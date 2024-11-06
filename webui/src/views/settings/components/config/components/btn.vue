<template>
  <a-space direction="vertical" fill>
    <a-typography-title :heading="3"> BTN </a-typography-title>
    <a-alert>
      {{ t('page.settings.tab.config.btn.doc') }}
      <a-link target="_blank" href="https://docs.pbh-btn.com/docs/btn/intro/">
        https://docs.pbh-btn.com/docs/btn/intro/
      </a-link>
    </a-alert>
    <a-form-item :label="t('page.settings.tab.config.btn.enable')" field="btn.enabled">
      <a-switch v-model="model.enabled" />
    </a-form-item>
    <div v-if="model.enabled">
      <a-form-item :label="t('page.settings.tab.config.btn.enableSubmit')" field="btn.submit">
        <a-switch v-model="model.submit" :before-change="beforeEnableSubmit" />
      </a-form-item>
      <a-form-item
        label="URL"
        field="btn.config_url"
        validate-trigger="focus"
        :rules="[{ type: 'url', required: true }]"
      >
        <a-input v-model="model.config_url" style="width: 400px" />
      </a-form-item>
      <a-form-item label="App ID" field="btn.app_id">
        <a-input v-model="model.app_id" style="width: 400px" />
      </a-form-item>
      <a-form-item label="App Secret" field="btn.app_secret">
        <a-input-password v-model="model.app_secret" style="width: 400px" />
      </a-form-item>
      <a-form-item
        :label="t('page.settings.tab.config.btn.allowScript')"
        :tooltip="t('page.settings.tab.config.btn.allowScript.tips')"
        field="model.enabled"
      >
        <a-switch v-model="model.allow_script_execute" />
        <template v-if="model.allow_script_execute" #extra>
          <a-typography-text type="danger">
            {{ t('page.settings.tab.config.btn.allowScript.warning') }}
          </a-typography-text></template
        >
      </a-form-item>
    </div>
  </a-space>
</template>
<script setup lang="ts">
import { type Btn } from '@/api/model/config'
import { Modal, Typography, TypographyParagraph } from '@arco-design/web-vue'
import { h } from 'vue'
import { useI18n } from 'vue-i18n'
const { t } = useI18n()
const model = defineModel<Btn>({ required: true })

const beforeEnableSubmit = async (value: string | number | boolean): Promise<boolean> => {
  if (value) {
    return new Promise((res) =>
      Modal.warning({
        title: t('page.settings.tab.config.btn.enableSubmit.modal.title'),
        content: () =>
          h(Typography, {}, [
            h(
              TypographyParagraph,
              null,
              t('page.settings.tab.config.btn.enableSubmit.modal.content')
            ),
            h(
              TypographyParagraph,
              null,
              t('page.settings.tab.config.btn.enableSubmit.modal.content2')
            ),
            h(
              TypographyParagraph,
              null,
              t('page.settings.tab.config.btn.enableSubmit.modal.content3')
            )
          ]),
        closable: false,
        hideCancel: false,
        onOk: () => res(true),
        onCancel: () => res(false)
      })
    )
  } else {
    return true
  }
}
</script>
