<template>
  <div class="result-container">
    <a-spin v-if="loading" :tip="t('page.oobe.result.initlizing')" />
    <a-result v-else-if="initSuccess" status="success" :title="t('page.oobe.result.title')">
      <template #subtitle> {{ t('page.oobe.result.description') }} </template>
      <template #extra>
        <a-space>
          <a-button type="primary" href="/">{{ t('page.oobe.result.goto') }}</a-button>
        </a-space>
      </template>
    </a-result>
    <a-result v-else status="error" :title="t('page.oobe.result.title.error')">
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
import type { InitConfig } from '@/api/model/oobe'
import { InitPBH } from '@/service/init'
import { useEndpointStore } from '@/stores/endpoint'
import { v1 as uuid } from 'uuid'
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
const { t } = useI18n()
const config = defineModel<InitConfig>({ required: true })
const loading = ref(true)
const initSuccess = ref(false)
const errorMsg = ref('')
const { setAuthToken } = useEndpointStore()
const init = () => {
  loading.value = true
  InitPBH({
    token: config.value.token,
    downloader: {
      id: uuid(),
      config: config.value.downloaderConfig.config
    }
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
.result-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  margin-top: 100px;
  min-height: 300px;
}
</style>
