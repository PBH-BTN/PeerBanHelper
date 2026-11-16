<template>
  <a-button v-if="status" size="mini" @click="visible = true">{{ t('updates.title') }}</a-button>
  <a-modal
    v-model:visible="visible"
    width="min(680px, 95vw)"
    :title="t('updates.title')"
    :footer="false"
  >
    <a-space v-if="status" direction="vertical" fill>
      <span>{{ status.currentVersion }} · {{ t('updates.state.' + status.state) }}</span>
      <a-select
        :model-value="status.mode"
        :disabled="pending"
        :aria-label="t('updates.mode')"
        @change="(mode) => changeMode(mode as UpdateMode)"
      >
        <a-option value="MANUAL">{{ t('updates.manual') }}</a-option>
        <a-option value="AUTOMATIC" :disabled="!status.canInstall">{{
          t('updates.automatic')
        }}</a-option>
        <a-option value="DISABLED">{{ t('updates.disabled') }}</a-option>
      </a-select>
      <a-button :disabled="working || status.mode === 'DISABLED'" @click="act('check', 'POST')">
        {{ t('updates.check') }}
      </a-button>
      <template v-if="status.release">
        <a-typography-title :heading="5">{{ status.release.version }}</a-typography-title>
        <Markdown
          v-if="status.release.changeLog"
          class="release-notes"
          :content="status.release.changeLog"
          use-github-markdown
        />
        <a-button
          v-if="status.canInstall && status.release.installable"
          type="primary"
          :disabled="working"
          @click="confirmInstall(status.release.id, status.release.version)"
        >
          {{ t('updates.install') }}
        </a-button>
        <a-link v-else :href="status.release.url" target="_blank">{{
          t('updates.external')
        }}</a-link>
      </template>
    </a-space>
    <a-empty v-else :description="t('updates.requestFailed')" />
  </a-modal>
</template>

<script setup lang="ts">
import { requestUpdateStatus, type UpdateMode, type UpdateStatus } from '@/service/updates'
import { useEndpointStore } from '@/stores/endpoint'
import { Message, Modal } from '@arco-design/web-vue'
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import Markdown from './markdown.vue'

const { t } = useI18n()
const endpoint = useEndpointStore()
const visible = ref(false)
const status = ref<UpdateStatus>()
const pending = ref(false)
const working = computed(
  () => pending.value || ['CHECKING', 'INSTALLING'].includes(status.value?.state ?? '')
)
let timer: ReturnType<typeof setInterval>
let active = true
let loading = false

async function refresh() {
  if (!active || loading || pending.value) return
  loading = true
  try {
    const result = await requestUpdateStatus()
    if (!active || pending.value) return
    status.value = result
    if (result.release && (!result.canInstall || !result.release.installable)) {
      const key = JSON.stringify(['pbh-update', endpoint.endpoint, result.release.version])
      if (!sessionStorage.getItem(key)) {
        sessionStorage.setItem(key, '1')
        visible.value = true
      }
    }
  } catch {
    // Older PBH servers do not expose the update endpoint.
  } finally {
    loading = false
  }
}

async function act(path: string, method: string, body?: object) {
  pending.value = true
  try {
    status.value = await requestUpdateStatus(path, method, body)
  } catch {
    Message.error(t('updates.requestFailed'))
  } finally {
    pending.value = false
  }
}

function changeMode(mode: UpdateMode) {
  return act('mode', 'PUT', { mode })
}

function confirmInstall(id: string, version: string) {
  Modal.confirm({
    title: t('updates.install'),
    content: t('updates.confirm', { version }),
    onOk: () => act('install', 'POST', { id })
  })
}

function openSettings() {
  visible.value = true
  void refresh()
}

onMounted(() => {
  endpoint.emitter.on('open-update-settings', openSettings)
  void refresh()
  timer = setInterval(() => void refresh(), 3000)
})
onUnmounted(() => {
  endpoint.emitter.off('open-update-settings', openSettings)
  active = false
  clearInterval(timer)
})
</script>

<style scoped>
.release-notes {
  max-height: 45vh;
  overflow: auto;
}
</style>
