<template>
  <a-modal
    v-model:visible="showModal"
    :modal-style="{ 'max-width': '80vw' }"
    :ok-loading="loading"
    @before-ok="handleOk"
    @cancel="handleCancel"
  >
    <template #title> {{ t('settings.modal.title') }} </template>
    <a-form
      :model="form"
      :layout="(['vertical', 'horizontal'] as const)[formLayout]"
      :label-col-props="{ span: 6 }"
      :wrapper-col-props="{ span: 18 }"
      @submit="handleOk"
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

const form = ref({
  endpoint: endPointStore.endpointSaved,
  interval: autoUpdateState.interval
})

const { t } = useI18n()
function initForm() {
  form.value.endpoint = endPointStore.endpointSaved
  form.value.interval = autoUpdateState.interval
  form.value.accessToken = endPointStore.accessToken
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
  return endPointStore.setEndpoint(form.value.endpoint)
}
const handleCancel = () => {
  showModal.value = false
  initForm()
}

watch(
  () => endPointStore.error,
  (error) => {
    if (IncorrectTokenError.is(error) || NeedInitError.is(error)) {
      handleCancel()
    } else if (GetManifestError.is(error)) {
      if (!error.isManual) Message.error({ content: t(error.message), resetOnHover: true })
    } else if (error) {
      Message.error({
        content: `${t('settings.endpoint.error')},error:${error}`,
        resetOnHover: true
      })
    }
  },
  { immediate: true }
)

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
