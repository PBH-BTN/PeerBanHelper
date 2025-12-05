<template>
  <a-spin v-if="loading" class="center" :tip="t('page.oobe.result.initlizing')" />
  <div v-else>
    <a-result
      v-if="initSuccess"
      status="success"
      :title="t('page.oobe.result.title')"
      class="center"
    >
      <template #subtitle> {{ t('page.oobe.result.description') }} </template>
      <template #extra>
        <a-space>
          <a-button type="primary" href="/">{{ t('page.oobe.result.goto') }}</a-button>
        </a-space>
      </template>
    </a-result>
    <a-result v-else status="error" :title="t('page.oobe.result.title.error')" class="center">
      <template #subtitle> {{ errorMsg }} </template>
      <template #extra>
        <a-space>
          <a-button type="primary" @click="init()">{{ t('page.oobe.result.retry') }}</a-button>
        </a-space>
      </template>
    </a-result>
  </div>
</template>
<script lang="ts" setup>
import type {InitConfig} from '@/api/model/oobe'
import {InitPBH} from '@/service/init'
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useEndpointStore} from '@/stores/endpoint'
import {v1 as uuid} from 'uuid'

const { t } = useI18n()
const config = defineModel<InitConfig>({ required: true })
const loading = ref(true)
const initSuccess = ref(false)
const errorMsg = ref('')
const { setAuthToken } = useEndpointStore()
const init = () => {
  loading.value = true

  // 构建 BTN 配置
  const btnConfig =
      config.value.btnConfig.mode === 'disabled'
          ? {enabled: false, submit: false}
          : config.value.btnConfig.mode === 'anonymous'
              ? {enabled: true, submit: true, app_id: null, app_secret: null}
              : {
                enabled: true,
                submit: true,
                app_id: config.value.btnConfig.appId,
                app_secret: config.value.btnConfig.appSecret
              }

  InitPBH({
    token: config.value.token,
    downloader: {
      id: uuid(),
      config: config.value.downloaderConfig.config
    },
    btn: btnConfig
  })
    .then((res) => {
      if (res.success) {
        initSuccess.value = true
        setAuthToken(config.value.token)
      } else {
        errorMsg.value = res.message
      }
      loading.value = false
    })
    .catch((err: Error) => {
      errorMsg.value = err.message
      loading.value = false
    })
}
init()
</script>

<style lang="less" scoped>
.center {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
}
</style>
