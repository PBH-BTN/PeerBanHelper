<template>
  <a-spin style="width: 100%" :loading="loading" dot>
    <div id="top" style="position: relative">
      <a-form :model="form" :scroll-to-first-error="true">
        <a-space direction="vertical" fill size="large">
          <a-space direction="vertical" fill style="width: 100%">
            <a-typography-title :heading="3">{{
              t('page.settings.tab.config.title')
            }}</a-typography-title>
            <a-typography-text>
              {{ t('page.settings.tab.config.tips') }}
            </a-typography-text>
          </a-space>

          <a-form-item :label="t('page.settings.tab.config.language')">
            <a-radio-group v-model="form.language">
              <a-radio value="en_us">English</a-radio>
              <a-radio value="zh_cn">简体中文</a-radio>
              <a-radio value="zh_tw">繁體中文</a-radio>
              <a-radio value="default">{{
                t('page.settings.tab.config.language.default')
              }}</a-radio>
            </a-radio-group>
            <template #extra>
              <i18n-t keypath="page.settings.tab.config.language.tips">
                <template #not
                  ><strong>{{ t('page.settings.tab.config.language.tips.not') }}</strong></template
                >
              </i18n-t>
            </template>
          </a-form-item>
          <a-form-item
            :label="t('page.settings.tab.config.privacy.errorReport')"
            field="privacy.error_reporting"
          >
            <a-switch v-model="form.privacy.error_reporting" />
          </a-form-item>
          <a-divider />
          <webui v-model="form.server" />
          <a-divider />
          <logger v-model="form.logger" />
          <a-divider />
          <lookup v-model="form.lookup" />
          <a-divider />
          <persist v-model="form.persist" />
          <a-divider />
          <btn v-model="form.btn" />
          <a-divider />
          <network v-model:proxy="form.proxy" v-model:resolvers="form.resolvers" />
          <a-divider />
          <ipDatabase v-model="form.ip_database" />
          <a-divider />
          <performance v-model="form.performance" />
          <a-divider />
          <push />
          <br />
          <a-form-item label-col-flex="0">
            <a-button html-type="submit" type="primary" :loading="saving" @click="submitConfig()">
              <template #icon><icon-save /></template>
              {{ t('page.settings.tab.profile.save') }}
            </a-button>
          </a-form-item>
        </a-space>
      </a-form>
    </div>
  </a-spin>
</template>
<script setup lang="ts">
import type { Config } from '@/api/model/config'
import { GetConfig, SaveConfig } from '@/service/settings'
import { reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRequest } from 'vue-request'

import { Message } from '@arco-design/web-vue'
import btn from './components/btn.vue'
import ipDatabase from './components/ipDatabase.vue'
import logger from './components/logger.vue'
import lookup from './components/lookup.vue'
import network from './components/network.vue'
import performance from './components/performance.vue'
import persist from './components/persist.vue'
import push from './components/push.vue'
import webui from './components/webui.vue'

const { t } = useI18n()
const form = reactive({
  server: {},
  logger: {},
  lookup: {},
  persist: {},
  btn: {},
  ip_database: {},
  privacy: {},
  proxy: {},
  performance: {},
  resolvers: {}
} as Config)
const { loading } = useRequest(GetConfig, {
  onSuccess: (data) => {
    Object.assign(form, data.data)
  }
})

const saving = ref(false)
const submitConfig = () => {
  saving.value = true
  SaveConfig(form)
    .then((data) => {
      Message.success(data.message)
    })
    .catch((err) => {
      Message.error(err.message)
    })
    .finally(() => {
      saving.value = false
    })
}
</script>
