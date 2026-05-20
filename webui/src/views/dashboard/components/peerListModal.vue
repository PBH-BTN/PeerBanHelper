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
        <a-space direction="vertical" :wrap="false" fill>
          <div>
            <CountryFlag
              v-if="record.geo?.country?.iso"
              :iso="record.geo?.country?.iso"
              :title="`${record.geo?.country?.name ?? ''}`"
            />
            {{
              `${record.geo?.city?.name ?? ''} ${record.geo?.network?.isp ?? ''} ${record.geo?.network?.netType ?? ''}`
            }}
          </div>
          <div>
            <a-typography-text copyable code style="white-space: nowrap">
              <queryIpLink :ip="record.peer.address.ip" style="color: var(--color-text-2)">
                {{ formatIPAddressPort(record.peer.address.ip, record.peer.address.port) }}
              </queryIpLink>
            </a-typography-text>
          </div>
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
        <a-popconfirm
          :content="
            t('page.dashboard.peerList.action.block.confirm', {
              ip: formatIPAddressPort(record.peer.address.ip, record.peer.address.port)
            })
          "
          type="warning"
          @before-ok="() => handleBlockPeer(record.peer.address.ip)"
        >
          <a-button type="text" status="danger" size="mini">
            {{ t('page.dashboard.peerList.action.block') }}
          </a-button>
        </a-popconfirm>
      </template>
    </a-table>
  </a-modal>
</template>
<script setup lang="ts">
import CountryFlag from '@/components/countryFlag.vue'
import queryIpLink from '@/components/queryIpLink.vue'
import { addBlackList } from '@/service/blacklist'
import { getPeer } from '@/service/downloaders'
import { formatFileSize } from '@/utils/file'
import { formatIPAddressPort } from '@/utils/string'
import { Message, type TableData, type TableSortable } from '@arco-design/web-vue'
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRequest } from 'vue-request'
const { t } = useI18n()
const visible = ref(false)
const downloader = ref('')
const tid = ref('')
const tname = ref('')
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
const { data, loading, run, cancel } = useRequest(getPeer, {
  defaultParams: [downloader.value, tid.value],
  manual: true,
  pollingInterval: 1000
})
type PeerListRow = {
  peer: {
    address: { ip: string; port: number }
    flags: string
    id: string
    clientName: string
    uploadSpeed: number
    downloadSpeed: number
    uploaded: number
    downloaded: number
    progress: number
  }
}
const comparePeerString = (a?: string, b?: string) => (a ?? '').localeCompare(b ?? '')
const comparePeerNumber = (a?: number, b?: number) => (a ?? 0) - (b ?? 0)
const asPeerListRow = (row: TableData) => row as unknown as PeerListRow
const columns = [
  {
    title: () => t('page.dashboard.peerList.column.address'),
    slotName: 'peerAddress',
    sortable: {
      sortDirections: ['ascend', 'descend'] as TableSortable['sortDirections'],
      sorter: (a: TableData, b: TableData) =>
        comparePeerString(asPeerListRow(a).peer.address.ip, asPeerListRow(b).peer.address.ip) ||
        comparePeerNumber(asPeerListRow(a).peer.address.port, asPeerListRow(b).peer.address.port)
    },
    width: 320
  },
  {
    title: () => t('page.dashboard.peerList.column.flag'),
    slotName: 'flags',
    sortable: {
      sortDirections: ['ascend', 'descend'] as TableSortable['sortDirections'],
      sorter: (a: TableData, b: TableData) =>
        comparePeerString(asPeerListRow(a).peer.flags, asPeerListRow(b).peer.flags)
    },
    width: 110
  },
  {
    title: 'Peer ID',
    dataIndex: 'peer.id',
    sortable: {
      sortDirections: ['ascend', 'descend'] as TableSortable['sortDirections'],
      sorter: (a: TableData, b: TableData) =>
        comparePeerString(asPeerListRow(a).peer.id, asPeerListRow(b).peer.id)
    },
    width: 100
  },
  {
    title: () => t('page.dashboard.peerList.column.clientName'),
    dataIndex: 'peer.clientName',
    sortable: {
      sortDirections: ['ascend', 'descend'] as TableSortable['sortDirections'],
      sorter: (a: TableData, b: TableData) =>
        comparePeerString(asPeerListRow(a).peer.clientName, asPeerListRow(b).peer.clientName)
    },
    width: 300
  },
  {
    title: () => t('page.dashboard.peerList.column.speed'),
    slotName: 'speed',
    sortable: {
      sortDirections: ['ascend', 'descend'] as TableSortable['sortDirections'],
      sorter: (a: TableData, b: TableData) =>
        comparePeerNumber(asPeerListRow(a).peer.uploadSpeed, asPeerListRow(b).peer.uploadSpeed) ||
        comparePeerNumber(asPeerListRow(a).peer.downloadSpeed, asPeerListRow(b).peer.downloadSpeed)
    },
    width: 140
  },
  {
    title: () => t('page.dashboard.peerList.column.uploadedDownloaded'),
    slotName: 'uploadDownload',
    sortable: {
      sortDirections: ['ascend', 'descend'] as TableSortable['sortDirections'],
      sorter: (a: TableData, b: TableData) =>
        comparePeerNumber(asPeerListRow(a).peer.uploaded, asPeerListRow(b).peer.uploaded) ||
        comparePeerNumber(asPeerListRow(a).peer.downloaded, asPeerListRow(b).peer.downloaded)
    },
    width: 140
  },
  {
    title: () => t('page.dashboard.peerList.column.progress'),
    slotName: 'progress',
    sortable: {
      sortDirections: ['ascend', 'descend'] as TableSortable['sortDirections'],
      sorter: (a: TableData, b: TableData) =>
        comparePeerNumber(asPeerListRow(a).peer.progress, asPeerListRow(b).peer.progress)
    },
    width: 100
  },
  {
    title: () => t('page.dashboard.peerList.column.actions'),
    slotName: 'actions',
    width: 100
  }
]
const parseFlags = (flags: string) =>
  flags
    .split(' ')
    .map((flag) => flag + ' - ' + t('page.dashboard.peerList.column.flags.' + flag.trim()))

const handleBlockPeer = async (ip: string) => {
  try {
    const resp = await addBlackList(ip, 'ip')
    if (!resp.success) {
      throw new Error(resp.message)
    }
    Message.success({ content: resp.message, resetOnHover: true })
    return true
  } catch (error: unknown) {
    Message.error({
      content:
        error instanceof Error
          ? error.message
          : t('page.dashboard.clientStatus.card.status.unknown'),
      resetOnHover: true
    })
    return false
  }
}
</script>

<style scoped>
.red {
  color: rgb(var(--red-5));
}

.green {
  color: rgb(var(--green-5));
}
</style>
