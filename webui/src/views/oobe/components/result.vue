<template>
  <a-spin v-if="loading" class="center" :tip="t('page.oobe.result.initlizing')" />
  <div v-else>
    <a-result v-if="initSuccess" status="success" :title="t('page.oobe.result.title')" class="center">
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
import type { InitConfig } from '@/api/model/oobe'
import { InitPBH } from '@/service/init'
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useEndpointStore } from '@/stores/endpoint'
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
      name: config.value.downloaderConfig.name,
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
.center {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
}
</style>
