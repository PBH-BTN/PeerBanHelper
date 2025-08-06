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
      style="width: 1700px"
      :virtual-list-props="{ height: 500 }"
      :pagination="false"
      @sorter-change="onSorterChange"
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
      <template #actions="{ record }">
        <a-button
          type="primary"
          status="danger"
          size="small"
          @click="() => confirmBanPeer(record.peer.address.ip)"
        >
          <template #icon>
            <icon-stop />
          </template>
          {{ t('page.dashboard.peerList.banPeer') }}
        </a-button>
      </template>
    </a-table>
  </a-modal>

  <!-- Ban confirmation modal -->
  <a-modal
    v-model:visible="banModalVisible"
    :title="t('page.dashboard.peerList.banConfirmTitle')"
    @ok="executeBan"
    @cancel="banModalVisible = false"
  >
    <p>{{ t('page.dashboard.peerList.banConfirmMessage', { ip: selectedPeerIP }) }}</p>
  </a-modal>
</template>
<script setup lang="ts">
import countryFlag from '@/components/countryFlag.vue'
import queryIpLink from '@/components/queryIpLink.vue'
import { getPeer } from '@/service/downloaders'
import { addBlackList } from '@/service/blacklist'
import { formatFileSize } from '@/utils/file'
import { formatIPAddressPort } from '@/utils/string'
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRequest } from 'vue-request'
import { Message } from '@arco-design/web-vue'

const { t } = useI18n()
const visible = ref(false)
const downloader = ref('')
const tid = ref('')
const tname = ref('')
const banModalVisible = ref(false)
const selectedPeerIP = ref('')
const currentSortBy = ref('')
const currentSortOrder = ref('')

defineExpose({
  showModal: (downloaderId: string, torrentId: string, torrentName: string) => {
    downloader.value = downloaderId
    tid.value = torrentId
    tname.value = torrentName
    visible.value = true
    run(downloaderId, torrentId)
  }
})

const handleOk = () => {
  visible.value = false
  downloader.value = ''
  tid.value = ''
}

const { data, loading, run, cancel } = useRequest(
  (downloaderId: string, torrentId: string) => 
    getPeer(downloaderId, torrentId, currentSortBy.value, currentSortOrder.value),
  {
    defaultParams: [downloader.value, tid.value],
    manual: true,
    pollingInterval: 1000
  }
)

const onSorterChange = (dataIndex: string, direction: string) => {
  if (direction) {
    currentSortBy.value = dataIndex
    currentSortOrder.value = direction === 'ascend' ? 'asc' : 'desc'
  } else {
    currentSortBy.value = ''
    currentSortOrder.value = ''
  }
  // Re-fetch data with new sorting
  run(downloader.value, tid.value)
}

const confirmBanPeer = (ip: string) => {
  selectedPeerIP.value = ip
  banModalVisible.value = true
}

const executeBan = async () => {
  try {
    await addBlackList(selectedPeerIP.value, 'ip')
    Message.success(t('page.dashboard.peerList.banSuccess', { ip: selectedPeerIP.value }))
    banModalVisible.value = false
  } catch (error) {
    Message.error(t('page.dashboard.peerList.banError', { ip: selectedPeerIP.value }))
    console.error('Failed to ban peer:', error)
  }
}

const columns = [
  {
    title: () => t('page.dashboard.peerList.column.address'),
    slotName: 'peerAddress',
    dataIndex: 'address',
    width: 320,
    sortable: {
      sortDirections: ['ascend', 'descend'] as ('ascend' | 'descend')[],
      sorter: true
    }
  },
  {
    title: () => t('page.dashboard.peerList.column.flag'),
    slotName: 'flags',
    dataIndex: 'flags',
    width: 110,
    sortable: {
      sortDirections: ['ascend', 'descend'] as ('ascend' | 'descend')[],
      sorter: true
    }
  },
  {
    title: 'Peer ID',
    dataIndex: 'peer.id',
    width: 100
  },
  {
    title: () => t('page.dashboard.peerList.column.clientName'),
    dataIndex: 'clientName',
    width: 300,
    sortable: {
      sortDirections: ['ascend', 'descend'] as ('ascend' | 'descend')[],
      sorter: true
    }
  },
  {
    title: () => t('page.dashboard.peerList.column.speed'),
    slotName: 'speed',
    dataIndex: 'uploadSpeed',
    width: 140,
    sortable: {
      sortDirections: ['ascend', 'descend'] as ('ascend' | 'descend')[],
      sorter: true
    }
  },
  {
    title: () => t('page.dashboard.peerList.column.downloadSpeed'),
    dataIndex: 'downloadSpeed',
    width: 120,
    sortable: {
      sortDirections: ['ascend', 'descend'] as ('ascend' | 'descend')[],
      sorter: true
    },
    render: ({ record }: { record: any }) => formatFileSize(record.peer.downloadSpeed) + '/s'
  },
  {
    title: () => t('page.dashboard.peerList.column.uploadedDownloaded'),
    slotName: 'uploadDownload',
    dataIndex: 'uploaded',
    width: 140,
    sortable: {
      sortDirections: ['ascend', 'descend'] as ('ascend' | 'descend')[],
      sorter: true
    }
  },
  {
    title: () => t('page.dashboard.peerList.column.downloaded'),
    dataIndex: 'downloaded',
    width: 120,
    sortable: {
      sortDirections: ['ascend', 'descend'] as ('ascend' | 'descend')[],
      sorter: true
    },
    render: ({ record }: { record: any }) => formatFileSize(record.peer.downloaded)
  },
  {
    title: () => t('page.dashboard.peerList.column.progress'),
    slotName: 'progress',
    dataIndex: 'progress',
    width: 100,
    sortable: {
      sortDirections: ['ascend', 'descend'] as ('ascend' | 'descend')[],
      sorter: true
    }
  },
  {
    title: () => t('page.dashboard.peerList.column.actions'),
    slotName: 'actions',
    width: 120
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
