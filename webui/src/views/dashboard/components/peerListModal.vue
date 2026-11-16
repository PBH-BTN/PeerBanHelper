<template>
  <a-modal
    v-model:visible="visible"
    hide-cancel
    closable
    unmount-on-close
    width="min(1600px, 95vw)"
    @ok="handleOk"
    @close="cancel()"
  >
    <template #title> {{ t('page.dashboard.peerList.title') + tname }} </template>
    <a-table
      :columns="columns"
      :data="data?.data"
      :loading="loading && !data"
      :scroll="{ x: 1450 }"
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
      <template #uploadSpeed="{ record }">
        {{ formatFileSize(record.peer.uploadSpeed) }}/s
      </template>
      <template #downloadSpeed="{ record }">
        {{ formatFileSize(record.peer.downloadSpeed) }}/s
      </template>
      <template #uploaded="{ record }">
        {{ formatFileSize(record.peer.uploaded) }}
      </template>
      <template #downloaded="{ record }">
        {{ formatFileSize(record.peer.downloaded) }}
      </template>
      <template #actions="{ record }">
        <a-tooltip :content="t('page.dashboard.peerList.block')">
          <a-button
            type="text"
            status="danger"
            :aria-label="t('page.dashboard.peerList.block')"
            @click="confirmBlock(record.peer.address.ip)"
          >
            <template #icon><icon-stop /></template>
          </a-button>
        </a-tooltip>
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
import type { PeerInfo } from '@/api/model/downloader'
import CountryFlag from '@/components/countryFlag.vue'
import queryIpLink from '@/components/queryIpLink.vue'
import { addBlackList } from '@/service/blacklist'
import { getPeer } from '@/service/downloaders'
import { formatFileSize } from '@/utils/file'
import { formatIPAddressPort } from '@/utils/string'
import { Message, Modal, type TableColumnData, type TableSortable } from '@arco-design/web-vue'
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
    data.value = undefined
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
const confirmBlock = (ip: string) => {
  Modal.confirm({
    title: t('page.dashboard.peerList.block'),
    content: t('page.dashboard.peerList.block.confirm', { ip }),
    okText: t('page.dashboard.peerList.block'),
    okButtonProps: { status: 'danger' },
    onBeforeOk: async () => {
      try {
        const response = await addBlackList(ip, 'ip')
        if (response.success) {
          Message.success(t('page.dashboard.peerList.block.success', { ip }))
          return true
        }
        Message.error(response.message || t('page.dashboard.peerList.block.error'))
      } catch {
        Message.error(t('page.dashboard.peerList.block.error'))
      }
      return false
    }
  })
}

const collator = new Intl.Collator(undefined, { numeric: true })
const sortableBy = (value: (peer: PeerInfo['peer']) => string | number): TableSortable => ({
  sortDirections: ['ascend', 'descend'],
  sorter: (a, b, { direction }) => {
    const left = value(a.peer)
    const right = value(b.peer)
    const order =
      typeof left === 'number' && typeof right === 'number'
        ? left - right
        : collator.compare(String(left), String(right))
    return direction === 'descend' ? -order : order
  }
})

const columns: TableColumnData[] = [
  {
    title: () => t('page.dashboard.peerList.column.address'),
    dataIndex: 'peer.address.ip',
    slotName: 'peerAddress',
    width: 300,
    sortable: sortableBy(({ address }) => formatIPAddressPort(address.ip, address.port))
  },
  {
    title: () => t('page.dashboard.peerList.column.flag'),
    dataIndex: 'peer.flags',
    slotName: 'flags',
    width: 100,
    sortable: sortableBy(({ flags }) => flags ?? '')
  },
  {
    title: 'Peer ID',
    dataIndex: 'peer.id',
    width: 140,
    sortable: sortableBy(({ id }) => id ?? '')
  },
  {
    title: () => t('page.dashboard.peerList.column.clientName'),
    dataIndex: 'peer.clientName',
    width: 220,
    sortable: sortableBy(({ clientName }) => clientName ?? '')
  },
  {
    title: () => t('page.dashboard.peerList.column.uploadSpeed'),
    dataIndex: 'peer.uploadSpeed',
    slotName: 'uploadSpeed',
    width: 120,
    sortable: sortableBy(({ uploadSpeed }) => uploadSpeed)
  },
  {
    title: () => t('page.dashboard.peerList.column.downloadSpeed'),
    dataIndex: 'peer.downloadSpeed',
    slotName: 'downloadSpeed',
    width: 130,
    sortable: sortableBy(({ downloadSpeed }) => downloadSpeed)
  },
  {
    title: () => t('page.dashboard.peerList.column.uploaded'),
    dataIndex: 'peer.uploaded',
    slotName: 'uploaded',
    width: 110,
    sortable: sortableBy(({ uploaded }) => uploaded)
  },
  {
    title: () => t('page.dashboard.peerList.column.downloaded'),
    dataIndex: 'peer.downloaded',
    slotName: 'downloaded',
    width: 120,
    sortable: sortableBy(({ downloaded }) => downloaded)
  },
  {
    title: () => t('page.dashboard.peerList.column.progress'),
    dataIndex: 'peer.progress',
    slotName: 'progress',
    width: 130,
    sortable: sortableBy(({ progress }) => progress)
  },
  {
    title: () => t('page.dashboard.peerList.column.actions'),
    slotName: 'actions',
    width: 80
  }
]
const parseFlags = (flags: string) =>
  flags
    .split(' ')
    .map((flag) => flag + ' - ' + t('page.dashboard.peerList.column.flags.' + flag.trim()))
</script>
