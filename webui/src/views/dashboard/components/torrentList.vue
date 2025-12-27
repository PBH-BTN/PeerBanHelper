<template>
  <a-table
    v-if="isMd"
    :columns="columns"
    :data="data?.data"
    column-resizable
    :bordered="{ cell: true }"
    :loading="!loading && !data"
    :pagination="false"
  >
    <template #name="{ record }">
      <a-typography-text
        :ellipsis="{
          showTooltip: true
        }"
        style="margin-bottom: 0em"
      >
        {{ record.name }}
      </a-typography-text>
    </template>
    <template #speed="{ record }">
      <a-space fill style="justify-content: space-between">
        <a-space fill direction="vertical">
          <a-typography-text style="white-space: nowrap">
            <icon-arrow-up class="green" />
            {{ formatFileSize(record.rtUploadSpeed) }}/s
          </a-typography-text>
          <a-typography-text style="white-space: nowrap">
            <icon-arrow-down class="red" />
            {{ formatFileSize(record.rtDownloadSpeed) }}/s
          </a-typography-text>
        </a-space>
      </a-space>
    </template>
    <template #size="{ record }">
      <a-typography-text style="white-space: nowrap">
        {{ formatFileSize(record.size) }}
      </a-typography-text>
    </template>
    <template #hash="{ record }">
      <a-button @click="handleCopy(record.hash)">
        {{ t('page.rule_management.ruleSubscribe.column.clickToCopy') }}
      </a-button>
    </template>
    <template #progress="{ record }">
      <a-space>
        <a-progress :percent="record.progress" size="mini" />
        <a-typography-text style="white-space: nowrap">
          {{ (record.progress * 100).toFixed(2) + '%' }}
        </a-typography-text>
      </a-space>
    </template>
    <template #peer="{ record }">
      <a-button @click="() => peerList?.showModal(downloader, record.id, record.name)">
        {{ t('page.dashboard.torrentList.column.view') }}
      </a-button>
    </template>
  </a-table>

  <a-list v-else :loading="!loading && !data" :bordered="true" hoverable>
    <a-list-item v-for="record in data?.data" :key="record.id" action-layout="vertical">
      <a-list-item-meta style="width: 100%">
        <template #title>
          <div style="margin-bottom: 8px">{{ record.name }}</div>
        </template>
        <template #description>
          <a-space direction="vertical" fill>
            <a-space>
              {{
                typeof columns[1]?.title === 'function' ? columns[1].title() : columns[1]?.title
              }}:
              <span class="green"
                ><icon-arrow-up /> {{ formatFileSize(record.rtUploadSpeed) }}/s</span
              >
              <span class="red"
                ><icon-arrow-down /> {{ formatFileSize(record.rtDownloadSpeed) }}/s</span
              >
            </a-space>
            <a-space>
              {{
                typeof columns[2]?.title === 'function' ? columns[2].title() : columns[2]?.title
              }}: {{ formatFileSize(record.size) }}</a-space
            >
            <a-space>
              {{
                typeof columns[4]?.title === 'function' ? columns[4].title() : columns[4]?.title
              }}:
              <a-progress :percent="record.progress" size="mini" />
              <a-typography-text>{{ (record.progress * 100).toFixed(2) }}%</a-typography-text>
            </a-space>
          </a-space>
        </template>
      </a-list-item-meta>

      <template #actions>
        <a-button size="small" @click="handleCopy(record.hash)">Hash</a-button>
        <a-button
          size="small"
          @click="() => peerList?.showModal(downloader, record.id, record.name)"
        >
          Peers
        </a-button>
      </template>
    </a-list-item>
  </a-list>

  <peerListModal ref="peerList" />
</template>
<script setup lang="ts">
import { Message } from '@arco-design/web-vue'
import { getTorrents } from '@/service/downloaders'
import { computed, defineAsyncComponent, onMounted, onUnmounted, ref } from 'vue'
import { useRequest } from 'vue-request'
import { formatFileSize } from '@/utils/file'
import { useI18n } from 'vue-i18n'
import { useAutoUpdatePlugin } from '@/stores/autoUpdate'
import copy from 'copy-to-clipboard'
const peerListModal = defineAsyncComponent(() => import('./peerListModal.vue'))
const { t } = useI18n()
const { downloader } = defineProps<{
  downloader: string
}>()
const { data, loading } = useRequest(
  getTorrents,
  {
    defaultParams: [downloader],
    ready: () => !!downloader
  },
  [useAutoUpdatePlugin]
)

const handleCopy = (text: string) => {
  copy(text)
  Message.success({
    content: t('page.rule_management.ruleSubscribe.copySuccess'),
    resetOnHover: true
  })
}

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

const windowWidth = ref(window.innerWidth)
const updateWidth = () => {
  windowWidth.value = window.innerWidth
}

onMounted(() => window.addEventListener('resize', updateWidth))
onUnmounted(() => window.removeEventListener('resize', updateWidth))

const isMd = computed(() => windowWidth.value >= 768)
</script>

<style scoped>
.red {
  color: rgb(var(--red-5));
}

.green {
  color: rgb(var(--green-5));
}
</style>
