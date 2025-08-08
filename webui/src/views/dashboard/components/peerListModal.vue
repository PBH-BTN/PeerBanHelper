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
      style="width: 1050px"
      :virtual-list-props="{ height: 500 }"
      :pagination="false"
      size="small"
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
        <div style="font-size: 12px; line-height: 1.2">
          <div style="margin-bottom: 2px">
            <icon-arrow-up class="green" style="margin-right: 4px" />
            {{ formatFileSize(record.peer.uploadSpeed) }}/s
          </div>
          <div>
            <icon-arrow-down class="red" style="margin-right: 4px" />
            {{ formatFileSize(record.peer.downloadSpeed) }}/s
          </div>
        </div>
      </template>
      <template #uploadDownload="{ record }">
        <div style="font-size: 12px; line-height: 1.2">
          <div style="margin-bottom: 2px">
            <icon-arrow-up class="green" style="margin-right: 4px" />
            {{ formatFileSize(record.peer.uploaded) }}
          </div>
          <div>
            <icon-arrow-down class="red" style="margin-right: 4px" />
            {{ formatFileSize(record.peer.downloaded) }}
          </div>
        </div>
      </template>
      <template #progress="{ record }">
        <div style="display: flex; align-items: center; gap: 8px">
          <a-progress
            :percent="record.peer.progress"
            size="mini"
            style="flex: 1; min-width: 60px"
          />
          <span style="font-size: 12px; white-space: nowrap">
            {{ (record.peer.progress * 100).toFixed(1) + '%' }}
          </span>
        </div>
      </template>
      <template #flags="{ record }">
        <div style="font-size: 12px">
          {{ record.peer.flags }}
          <a-tooltip v-if="record.peer.flags">
            <template #content>
              <p v-for="description in parseFlags(record.peer.flags)" :key="description">
                {{ description }}
              </p>
            </template>
            <icon-info-circle style="margin-left: 4px" />
          </a-tooltip>
        </div>
      </template>
      <template #actions="{ record }">
        <a-button
          type="primary"
          status="danger"
          size="mini"
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
    width: 300,
    sortable: {
      sortDirections: ['ascend', 'descend'] as ('ascend' | 'descend')[],
      sorter: true
    }
  },
  {
    title: () => t('page.dashboard.peerList.column.flag'),
    slotName: 'flags',
    dataIndex: 'flags',
    width: 70,
    sortable: {
      sortDirections: ['ascend', 'descend'] as ('ascend' | 'descend')[],
      sorter: true
    }
  },
  {
    title: 'Peer ID',
    dataIndex: 'peer.id',
    width: 70
  },
  {
    title: () => t('page.dashboard.peerList.column.clientName'),
    dataIndex: 'peer.clientName',
    width: 180,
    sortable: {
      sortDirections: ['ascend', 'descend'] as ('ascend' | 'descend')[],
      sorter: true
    }
  },
  {
    title: () => t('page.dashboard.peerList.column.speed'),
    slotName: 'speed',
    dataIndex: 'uploadSpeed',
    width: 110,
    sortable: {
      sortDirections: ['ascend', 'descend'] as ('ascend' | 'descend')[],
      sorter: true
    }
  },
  {
    title: () => t('page.dashboard.peerList.column.uploadedDownloaded'),
    slotName: 'uploadDownload',
    dataIndex: 'uploaded',
    width: 110,
    sortable: {
      sortDirections: ['ascend', 'descend'] as ('ascend' | 'descend')[],
      sorter: true
    }
  },
  {
    title: () => t('page.dashboard.peerList.column.progress'),
    slotName: 'progress',
    dataIndex: 'progress',
    width: 120,
    sortable: {
      sortDirections: ['ascend', 'descend'] as ('ascend' | 'descend')[],
      sorter: true
    }
  },
  {
    title: () => t('page.dashboard.peerList.column.actions'),
    slotName: 'actions',
    width: 90
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
