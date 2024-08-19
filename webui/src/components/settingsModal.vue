<template>
  <a-modal
    :modal-style="{ 'max-width': '80vw' }"
    v-model:visible="showModal"
    @before-ok="handleOk"
    @cancel="handleCancel"
    :closable="!forceModal"
    :maskClosable="!forceModal"
    :hide-cancel="forceModal"
    :ok-loading="loading"
  >
    <template #title> {{ t('settings.modal.title') }} </template>
    <a-form
      :model="form"
      @submit="handleOk"
      :layout="(['vertical', 'horizontal'] as const)[formLayout]"
      :label-col-props="{ span: 6 }"
      :wrapper-col-props="{ span: 18 }"
    >
      <a-form-item
        field="endpoint"
        label="Endpoint:"
        :tooltip="t('settings.modal.endpointTips')"
        validate-trigger="input"
      >
        <a-input v-model="form.endpoint" placeholder="http://localhost:8989" allow-clear />
      </a-form-item>
      <a-form-item
        field="interval"
        :label="t('settings.modal.pollInterval')"
        validate-trigger="input"
      >
        <a-input-number v-model="form.interval" placeholder="3000" :min="100" hide-button>
          <template #suffix> ms </template>
        </a-input-number>
      </a-form-item>
      <a-form-item field="accessToken" label="Access Token:" validate-trigger="input">
        <template #extra>
          <i18n-t keypath="settings.modal.accessTokenTips">
            <template v-slot:here>
              <a href="https://github.com/settings/tokens">{{
                t('settings.modal.accessTokenTips.here')
              }}</a>
            </template>
          </i18n-t>
        </template>
        <a-input v-model="form.accessToken" allow-clear />
      </a-form-item>
    </a-form>
  </a-modal>
</template>
<script setup lang="ts">
import { IncorrectTokenError, NeedInitError } from '@/service/login'
import { GetManifestError } from '@/service/version'
import { useAutoUpdate } from '@/stores/autoUpdate'
import { useEndpointStore } from '@/stores/endpoint'
import { Message } from '@arco-design/web-vue'
import { useResponsiveState } from '@arco-design/web-vue/es/grid/hook/use-responsive-state'
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
const endPointStore = useEndpointStore()
const autoUpdateState = useAutoUpdate()
const showModal = ref(false)
const loading = computed(() => endPointStore.loading)
const forceModal = computed(() => endPointStore.status === 'fail')

const form = ref({
  endpoint: endPointStore.endpointSaved,
  interval: autoUpdateState.interval,
  accessToken: endPointStore.accessToken
})

const { t } = useI18n()
function initForm() {
  form.value.endpoint = endPointStore.endpointSaved
  form.value.interval = autoUpdateState.interval
}

defineExpose({
  showModal: () => {
    showModal.value = true
    initForm()
  }
})
const handleOk = () => {
  if (loading.value) return
  autoUpdateState.interval = form.value.interval
  endPointStore.setAccessToken(form.value.accessToken)
  return endPointStore.setEndpoint(form.value.endpoint)
}

watch(
  () => endPointStore.error,
  (error) => {
    if (IncorrectTokenError.is(error) || NeedInitError.is(error)) {
      handleCancel()
    } else if (GetManifestError.is(error)) {
      !error.isManual && Message.error(t(error.message))
      if (!showModal.value && error.isApiWrong) {
        showModal.value = true
        initForm()
      }
    } else if (error) {
      Message.error(`${t('settings.endpoint.error')},error:${error}`)
      if (!showModal.value) {
        showModal.value = true
        initForm()
      }
    }
  },
  { immediate: true }
)
const handleCancel = () => {
  showModal.value = false
  initForm()
}

const formLayout = useResponsiveState(
  ref({
    md: 1
  }),
  0
)
</script>

<style scoped>
a {
  text-decoration: none;
}
</style>
