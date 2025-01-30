<template>
  <a-modal
    v-model:visible="visible"
    hide-cancel
    closable
    unmount-on-close
    width="auto"
    @ok="handleOk"
    @close="cancel()"
  >
    <template #title> {{ t('page.dashboard.peerList.title') + tname }} </template>
    <a-table
      :columns="columns"
      :data="data?.data"
      :loading="!loading && !data"
      style="width: 1600px"
      :virtual-list-props="{ height: 500 }"
      :pagination="false"
    >
      <template #peerAddress="{ record }">
        <a-space :wrap="false">
          <countryFlag v-if="record.geo?.country?.iso" :iso="record.geo.country.iso" />
          <a-typography-text copyable code style="white-space: nowrap">
            <queryIpLink :ip="record.peer.address.ip" style="color: var(--color-text-2)">
              {{ formatIPAddressPort(record.peer.address.ip, record.peer.address.port) }}
            </queryIpLink>
          </a-typography-text>
        </a-space>
      </template>
      <template #speed="{ record }">
        <a-space fill style="justify-content: space-between">
          <a-space fill direction="vertical">
            <a-typography-text>
              <icon-arrow-up class="green" />
              {{ formatFileSize(record.peer.uploadSpeed) }}/s
            </a-typography-text>
            <a-typography-text>
              <icon-arrow-down class="red" />
              {{ formatFileSize(record.peer.downloadSpeed) }}/s
            </a-typography-text>
          </a-space>
        </a-space>
      </template>
      <template #uploadDownload="{ record }">
        <a-space fill style="justify-content: space-between">
          <a-space fill direction="vertical">
            <a-typography-text
              ><icon-arrow-up class="green" />
              {{ formatFileSize(record.peer.uploaded) }}</a-typography-text
            >
            <a-typography-text
              ><icon-arrow-down class="red" />
              {{ formatFileSize(record.peer.downloaded) }}</a-typography-text
            >
          </a-space>
        </a-space>
      </template>
      <template #progress="{ record }">
        <a-space>
          <a-progress :percent="record.peer.progress" size="mini" />
          <a-typography-text>
            {{ (record.peer.progress * 100).toFixed(2) + '%' }}
          </a-typography-text>
        </a-space>
      </template>
      <template #flags="{ record }">
        <p>
          {{ record.peer.flags }}
          <a-tooltip v-if="record.peer.flags">
            <template #content>
              <p v-for="description in parseFlags(record.peer.flags)" :key="description">
                {{ description }}
              </p>
            </template>
            <icon-info-circle />
          </a-tooltip>
        </p>
      </template>
    </a-table>
  </a-modal>
</template>
<script setup lang="ts">
import countryFlag from '@/components/countryFlag.vue'
import queryIpLink from '@/components/queryIpLink.vue'
import { getPeer } from '@/service/downloaders'
import { formatFileSize } from '@/utils/file'
import { formatIPAddressPort } from '@/utils/string'
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRequest } from 'vue-request'
const { t } = useI18n()
const visible = ref(false)
const downloader = ref('')
const tid = ref('')
const tname = ref('')
defineExpose({
  showModal: (downloaderName: string, torrentId: string, torrentName: string) => {
    downloader.value = downloaderName
    tid.value = torrentId
    tname.value = torrentName
    visible.value = true
    run(downloaderName, torrentId)
  }
})
const handleOk = () => {
  visible.value = false
  downloader.value = ''
  tid.value = ''
}
const { data, loading, run, cancel } = useRequest(getPeer, {
  defaultParams: [downloader.value, tid.value],
  manual: true,
  pollingInterval: 1000
})
const columns = [
  {
    title: () => t('page.dashboard.peerList.column.address'),
    slotName: 'peerAddress',
    width: 320
  },
  {
    title: () => t('page.dashboard.peerList.column.flag'),
    slotName: 'flags',
    width: 110
  },
  {
    title: 'Peer ID',
    dataIndex: 'peer.id',
    width: 100
  },
  {
    title: () => t('page.dashboard.peerList.column.clientName'),
    dataIndex: 'peer.clientName',
    width: 300
  },
  {
    title: () => t('page.dashboard.peerList.column.speed'),
    slotName: 'speed',
    width: 140
  },
  {
    title: () => t('page.dashboard.peerList.column.uploadedDownloaded'),
    slotName: 'uploadDownload',
    width: 140
  },
  {
    title: () => t('page.dashboard.peerList.column.progress'),
    slotName: 'progress',
    width: 100
  }
]
const parseFlags = (flags: string) =>
  flags
    .split(' ')
    .map((flag) => flag + ' - ' + t('page.dashboard.peerList.column.flags.' + flag.trim()))
</script>

<style scoped>
.red {
  color: rgb(var(--red-5));
}

.green {
  color: rgb(var(--green-5));
}
</style>
