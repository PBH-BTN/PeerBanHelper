<template>
  <a-modal hide-cancel closable v-model:visible="visible" @ok="handleOk" draggable width="auto" @close="cancel()">
    <template #title> {{ t('page.dashboard.torrentList.title') }} </template>
    <a-table :columns="columns" :data="data?.data" :loading="!loading && !data"
      :pagination="{ pageSize: 5, baseSize: 4, bufferSize: 1 }">
      <template #name="{ record }">
        <a-typography-text bold style="margin-bottom: 0" :ellipsis="{ showTooltip: true }">
          {{ record.name }}
        </a-typography-text>
      </template>
      <template #size="{ record }">
        <a-typography-text>{{ formatFileSize(record.size) }}</a-typography-text>
      </template>
      <template #hash="{ record }">
        <a-typography-text copyable code>{{ record.hash }}</a-typography-text>
      </template>
      <template #progress="{ record }">
        <a-space>
          <a-progress :percent="record.progress" size="mini" />
          <a-typography-text>
            {{ (record.progress * 100).toFixed(2) + '%' }}
          </a-typography-text>
        </a-space>
      </template>
      <template #speed="{ record }">
        <a-space fill style="justify-content: space-between">
          <a-space fill direction="vertical">
            <a-typography-text><icon-arrow-up class="green" />
              {{ formatFileSize(record.rtUploadSpeed) }}/s</a-typography-text>
            <a-typography-text><icon-arrow-down class="red" />
              {{ formatFileSize(record.rtDownloadSpeed) }}/s</a-typography-text>
          </a-space>
        </a-space>
      </template>
      <template #peer="{ record }">
        <a-button @click="() => peerList?.showModal(downloader, record.id, record.name)">
          {{ t('page.dashboard.torrentList.column.view') }}
        </a-button>
      </template>
    </a-table>
    <peerListModal ref="peerList" />
  </a-modal>
</template>
<script setup lang="ts">
import { getTorrents } from '@/service/downloaders'
import { defineAsyncComponent, ref } from 'vue'
import { useRequest } from 'vue-request'
import { formatFileSize } from '@/utils/file'
import { useI18n } from 'vue-i18n'
const peerListModal = defineAsyncComponent(() => import('./peerListModal.vue'))
const { t } = useI18n()
const visible = ref(false)
const downloader = ref('')
defineExpose({
  showModal: (downloaderName: string) => {
    downloader.value = downloaderName
    visible.value = true
    run(downloaderName)
  }
})
const handleOk = () => {
  visible.value = false
  downloader.value = ''
}
const { data, loading, run, cancel } = useRequest(getTorrents, {
  manual: true,
  pollingInterval: 1000
})
const peerList = ref<InstanceType<typeof peerListModal>>()
const columns = [
  {
    title: () => t('page.dashboard.torrentList.column.name'),
    slotName: 'name',
    width: 400
  },
  {
    title: () => t('page.dashboard.torrentList.column.speed'),
    slotName: 'speed'
  },
  {
    title: () => t('page.dashboard.torrentList.column.size'),
    slotName: 'size'
  },
  {
    title: () => t('page.dashboard.torrentList.column.hash'),
    slotName: 'hash'
  },
  {
    title: () => t('page.dashboard.torrentList.column.progress'),
    slotName: 'progress'
  },
  {
    title: 'Peers',
    slotName: 'peer'
  }
]
</script>

<style scoped>
.red {
  color: rgb(var(--red-5));
}

.green {
  color: rgb(var(--green-5));
}
</style>
