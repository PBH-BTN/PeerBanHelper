<template>
  <a-card hoverable style="height: 100%" :header-style="{ height: 'auto' }" class="card">
    <template #extra>
      <a-space v-if="client" size="mini">
        <a-button
          class="edit-btn"
          shape="circle"
          type="text"
          @click="
            () => emits('edit-click', { name: downloader.name, config: client?.data?.config!! })
          "
        >
          <template #icon>
            <icon-edit />
          </template>
        </a-button>
        <a-tooltip
          v-if="props.disableRemove"
          :content="t('page.dashboard.clientStatus.card.lastDelete')"
        >
          <a-button class="edit-btn" status="danger" shape="circle" type="text" disabled>
            <template #icon>
              <icon-delete />
            </template>
          </a-button>
        </a-tooltip>
        <a-popconfirm
          v-else
          :content="t('page.rule_management.ruleSubscribe.column.deleteConfirm')"
          type="warning"
          @before-ok="handleDelete"
        >
          <a-button
            class="edit-btn"
            status="danger"
            shape="circle"
            type="text"
            :disabled="props.disableRemove"
          >
            <template #icon>
              <icon-delete />
            </template>
          </a-button>
        </a-popconfirm>
      </a-space>
    </template>
    <template #title>
      <a-typography-title
        :style="{ margin: '0px' }"
        :ellipsis="{
          rows: 2,
          showTooltip: true
        }"
        :heading="3"
      >
        {{ downloader.name }}
      </a-typography-title>
    </template>
    <a-skeleton v-if="!client" :animation="true">
      <a-space direction="vertical" :style="{ width: '100%' }" :size="0">
        <a-skeleton-line
          :rows="4"
          :line-height="22"
          :line-spacing="14"
          :widths="['60%', '70%', '50%', '60%']"
        />
      </a-space>
    </a-skeleton>
    <a-descriptions
      v-if="client"
      :column="1"
      layout="inline-horizontal"
      class="space"
      :label-style="{ paddingRight: '10px' }"
    >
      <a-descriptions-item :label="t('page.dashboard.clientStatus.card.type')">
        <a-space>
          <a-tag bordered>{{ downloader.type }}</a-tag>
          <a-tooltip :content="downloader.endpoint">
            <icon-info-circle size="large" />
          </a-tooltip>
        </a-space>
      </a-descriptions-item>

      <a-descriptions-item :label="t('page.dashboard.clientStatus.card.status')">
        <a-tooltip :content="client.data.lastStatusMessage">
          <a-typography-text :type="getStatusSafe(client.data)[0]">
            <icon-check-circle-fill v-if="client.data.lastStatus == ClientStatusEnum.HEALTHY" />
            <icon-close-circle-fill v-if="client.data.lastStatus == ClientStatusEnum.ERROR" />
            <icon-question-circle-fill v-if="client.data.lastStatus == ClientStatusEnum.UNKNOWN" />
            <icon-pause-circle-fill v-if="client.data.lastStatus == ClientStatusEnum.PAUSED" />
            <icon-exclamation-polygon-fill
              v-if="client.data.lastStatus == ClientStatusEnum.NEED_TAKE_ACTION"
            />
            {{ t(getStatusSafe(client.data)[1]) }}
          </a-typography-text>
        </a-tooltip>
      </a-descriptions-item>

      <a-descriptions-item
        v-if="client.data.lastStatus == ClientStatusEnum.HEALTHY"
        :label="t('page.dashboard.clientStatus.card.status.torrentNumber')"
      >
        <a-typography-text>{{ client.data.activeTorrents }}</a-typography-text>
      </a-descriptions-item>

      <a-descriptions-item
        v-if="client.data.lastStatus == ClientStatusEnum.HEALTHY"
        :label="t('page.dashboard.clientStatus.card.status.peerNumber')"
      >
        <a-typography-text>{{ client.data.activePeers }}</a-typography-text>
      </a-descriptions-item>
    </a-descriptions>
  </a-card>
</template>
<script setup lang="ts">
import {
  type ClientStatus,
  ClientStatusEnum,
  type Downloader,
  type downloaderConfig
} from '@/api/model/downloader'
import { DeleteDownloader, getClientStatus } from '@/service/downloaders'
import { useAutoUpdatePlugin } from '@/stores/autoUpdate'
import { useEndpointStore } from '@/stores/endpoint'
import { Message } from '@arco-design/web-vue'
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRequest } from 'vue-request'

const { t } = useI18n()
const statusMap: Record<ClientStatusEnum, [string, string]> = {
  [ClientStatusEnum.HEALTHY]: ['success', 'page.dashboard.clientStatus.card.status.normal'],
  [ClientStatusEnum.ERROR]: ['warning', 'page.dashboard.clientStatus.card.status.error'],
  [ClientStatusEnum.UNKNOWN]: ['info', 'page.dashboard.clientStatus.card.status.unknown'],
  [ClientStatusEnum.PAUSED]: ['danger', 'page.dashboard.clientStatus.card.status.paused'],
  [ClientStatusEnum.NEED_TAKE_ACTION]: [
    'danger',
    'page.dashboard.clientStatus.card.status.need_take_action'
  ]
}
const props = withDefaults(
  defineProps<{
    downloader: Downloader
    disableRemove?: boolean
  }>(),
  { disableRemove: false }
)

const emits = defineEmits<{
  (e: 'downloader-deleted'): void
  (
    e: 'edit-click',
    downloader: {
      name: string
      config: downloaderConfig
    }
  ): void
}>()

const downloader = computed(() => props.downloader)
const endpointState = useEndpointStore()

const getStatusSafe = (status: ClientStatus | undefined): string[] =>
  statusMap[status?.lastStatus ?? ClientStatusEnum.UNKNOWN] ?? statusMap[ClientStatusEnum.UNKNOWN]

const { data: client } = useRequest(
  getClientStatus,
  {
    cacheKey: () => `${endpointState.endpoint}-clientStatus-${downloader.value.name}`,
    defaultParams: [downloader.value.name],
    refreshDeps: [() => downloader.value.name]
  },
  [useAutoUpdatePlugin]
)

const handleDelete = async () => {
  try {
    const result = await DeleteDownloader(downloader.value.name)
    if (!result.success) {
      throw new Error(result.message)
    } else {
      Message.success({ content: result.message, resetOnHover: true })
      emits('downloader-deleted')
      return true
    }
  } catch (e: unknown) {
    if (e instanceof Error) Message.error({ content: e.message, resetOnHover: true })
    return false
  }
}
</script>

<style scoped lang="less">
.space {
  :deep(.arco-descriptions-body) {
    .arco-descriptions-row:not(:last-child) .arco-descriptions-item {
      padding-bottom: 14px;
    }
  }
}

.edit-btn {
  color: rgb(var(--gray-8));
  font-size: 16px;
  opacity: 0;
}

.card:hover .edit-btn {
  opacity: 1;
  visibility: visible;
}
</style>
