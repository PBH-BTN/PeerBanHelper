<template>
  <a-card :bordered="false">
    <a-spin :loading="loading" style="width: 100%">
      <a-space v-if="data?.data" direction="vertical" fill>
        <a-descriptions>
          <a-descriptions-item :label="t('page.ipList.btn.labels')">
            <a-tag
              v-for="label in data.data.labels"
              :key="label"
              :color="data.data.color"
              size="medium"
            >
              {{ label }}
            </a-tag>
            <a-tag v-if="data.data.labels.length === 0" color="gray">
              {{ t('page.ipList.btn.noLabels') }}
            </a-tag>
          </a-descriptions-item>
        </a-descriptions>

        <!-- Tabs展示 -->
        <a-tabs default-active-key="bans">
          <!-- Bans Tab -->
          <a-tab-pane key="bans" class="tab" :title="t('page.ipList.btn.bans')">
            <a-space direction="vertical" fill>
              <a-grid :cols="24" :row-gap="16">
                <a-grid-item :span="{ xs: 12, sm: 8 }">
                  <a-statistic
                    :title="t('page.ipList.btn.bans.total')"
                    :value="data.data.bans.total"
                    show-group-separator
                  />
                </a-grid-item>
                <a-grid-item :span="{ xs: 12, sm: 8 }">
                  <a-space direction="vertical" size="mini">
                    <a-typography-text type="secondary">{{
                      t('page.ipList.btn.bans.duration')
                    }}</a-typography-text>
                    <a-typography-text>{{
                      formatDuration(data.data.bans.duration)
                    }}</a-typography-text>
                  </a-space>
                </a-grid-item>
                <a-grid-item :span="{ xs: 24, sm: 8 }">
                  <a-button
                    v-if="data.data.bans.records.length > 0"
                    type="outline"
                    @click="showBansModal = true"
                  >
                    {{ t('page.ipList.btn.viewRecords') }}
                  </a-button>
                </a-grid-item>
              </a-grid>
            </a-space>
          </a-tab-pane>

          <!-- Swarms Tab -->
          <a-tab-pane key="swarms" class="tab" :title="t('page.ipList.btn.swarms')">
            <a-space direction="vertical" fill>
              <a-grid :cols="24" :row-gap="16">
                <a-grid-item :span="{ xs: 12, sm: 6 }">
                  <a-statistic
                    :title="t('page.ipList.btn.swarms.total')"
                    :value="data.data.swarms.total"
                    show-group-separator
                  />
                </a-grid-item>
                <a-grid-item :span="{ xs: 12, sm: 6 }">
                  <a-statistic
                    :title="t('page.ipList.btn.swarms.downloading')"
                    :value="data.data.swarms.concurrent_download_torrents_count"
                    show-group-separator
                  />
                </a-grid-item>
                <a-grid-item :span="{ xs: 12, sm: 6 }">
                  <a-statistic
                    :title="t('page.ipList.btn.swarms.seeding')"
                    :value="data.data.swarms.concurrent_seeding_torrents_count"
                    show-group-separator
                  />
                </a-grid-item>
                <a-grid-item :span="{ xs: 24, sm: 6 }">
                  <a-button
                    v-if="data.data.swarms.records.length > 0"
                    type="outline"
                    @click="showSwarmsModal = true"
                  >
                    {{ t('page.ipList.btn.viewRecords') }}
                  </a-button>
                </a-grid-item>
              </a-grid>
            </a-space>
          </a-tab-pane>

          <!-- Traffic Tab -->
          <a-tab-pane key="traffic" class="tab" :title="t('page.ipList.btn.traffic')">
            <a-grid :cols="24" :row-gap="16">
              <a-grid-item :span="{ xs: 12, sm: 6 }">
                <a-statistic
                  :title="t('page.ipList.btn.traffic.upload')"
                  :value="getFileSizeValue(data.data.traffic.to_peer_traffic)"
                >
                  <template #prefix>
                    <icon-arrow-up class="green" />
                  </template>
                  <template #suffix>
                    {{ getFileSizeUnit(data.data.traffic.to_peer_traffic) }}
                  </template>
                </a-statistic>
              </a-grid-item>
              <a-grid-item :span="{ xs: 12, sm: 6 }">
                <a-statistic
                  :title="t('page.ipList.btn.traffic.download')"
                  :value="getFileSizeValue(data.data.traffic.from_peer_traffic)"
                >
                  <template #prefix>
                    <icon-arrow-down class="red" />
                  </template>
                  <template #suffix>
                    {{ getFileSizeUnit(data.data.traffic.from_peer_traffic) }}
                  </template>
                </a-statistic>
              </a-grid-item>
              <a-grid-item :span="{ xs: 12, sm: 6 }">
                <a-statistic
                  :title="t('page.ipList.btn.traffic.ratio')"
                  :value="data.data.traffic.share_ratio"
                  :precision="2"
                  show-group-separator
                />
              </a-grid-item>
              <a-grid-item :span="{ xs: 12, sm: 6 }">
                <a-space direction="vertical" size="mini">
                  <a-typography-text type="secondary">{{
                    t('page.ipList.btn.traffic.duration')
                  }}</a-typography-text>
                  <a-typography-text>{{
                    formatDuration(data.data.traffic.duration)
                  }}</a-typography-text>
                </a-space>
              </a-grid-item>
            </a-grid>
          </a-tab-pane>

          <!-- Torrents Tab -->
          <a-tab-pane key="torrents" :title="t('page.ipList.btn.torrents')">
            <a-grid :cols="24" :row-gap="16">
              <a-grid-item :span="{ xs: 12, sm: 12 }">
                <a-statistic
                  :title="t('page.ipList.btn.torrents.count')"
                  :value="data.data.torrents.count"
                  show-group-separator
                />
              </a-grid-item>
              <a-grid-item :span="{ xs: 12, sm: 12 }">
                <a-space direction="vertical" size="mini">
                  <a-typography-text type="secondary">{{
                    t('page.ipList.btn.torrents.duration')
                  }}</a-typography-text>
                  <a-typography-text>{{
                    formatDuration(data.data.torrents.duration)
                  }}</a-typography-text>
                </a-space>
              </a-grid-item>
            </a-grid>
          </a-tab-pane>
        </a-tabs>
      </a-space>

      <a-empty v-else-if="error">
        <template #description>
          {{ t('page.ipList.btn.error') }}
        </template>
      </a-empty>
      <!-- Bans Records Modal -->
      <a-modal
        v-model:visible="showBansModal"
        :title="t('page.ipList.btn.bans.records')"
        width="70%"
        :footer="false"
      >
        <a-table
          :columns="bansColumns"
          :data="data?.data.bans.records"
          :pagination="{ pageSize: 10 }"
          :scroll="{ x: 1200 }"
          size="small"
        >
          <template #populate_time="{ record }">
            {{ d(record.populate_time, 'long') }}
          </template>
          <template #traffic="{ record }">
            <a-space fill style="justify-content: space-between">
              <a-space fill direction="vertical">
                <a-typography-text
                  ><icon-arrow-up class="green" />
                  {{ formatFileSize(record.to_peer_traffic) }}</a-typography-text
                >
                <a-typography-text
                  ><icon-arrow-down class="red" />
                  {{ formatFileSize(record.from_peer_traffic) }}</a-typography-text
                >
              </a-space>
              <a-tooltip :content="(record.peer_progress * 100).toFixed(2) + '%'">
                <a-progress :percent="record.peer_progress" size="mini" />
              </a-tooltip>
            </a-space>
          </template>
        </a-table>
      </a-modal>

      <!-- Swarms Records Modal -->
      <a-modal
        v-model:visible="showSwarmsModal"
        :title="t('page.ipList.btn.swarms.records')"
        width="70%"
        :footer="false"
      >
        <a-table
          :columns="swarmsColumns"
          :data="data?.data.swarms.records"
          :pagination="{ pageSize: 10 }"
          :scroll="{ x: 1200 }"
          size="small"
        >
          <template #first_time_seen="{ record }">
            {{ d(record.first_time_seen, 'long') }}
          </template>
          <template #last_time_seen="{ record }">
            {{ d(record.last_time_seen, 'long') }}
          </template>
          <template #traffic="{ record }">
            <a-space fill style="justify-content: space-between">
              <a-space fill direction="vertical">
                <a-typography-text
                  ><icon-arrow-up class="green" />
                  {{ formatFileSize(record.to_peer_traffic) }}</a-typography-text
                >
                <a-typography-text
                  ><icon-arrow-down class="red" />
                  {{ formatFileSize(record.from_peer_traffic) }}</a-typography-text
                >
              </a-space>
              <a-tooltip :content="(record.peer_progress * 100).toFixed(2) + '%'">
                <a-progress :percent="record.peer_progress" size="mini" />
              </a-tooltip>
            </a-space>
          </template>
        </a-table>
      </a-modal>
    </a-spin>
  </a-card>
</template>

<script setup lang="ts">
import { GetBTNIPData } from '@/service/data'
import { formatFileSize } from '@/utils/file'
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRequest } from 'vue-request'

const { t, d } = useI18n()

const props = defineProps<{
  ip: string
}>()

const showBansModal = ref(false)
const showSwarmsModal = ref(false)

const { data, loading, error, run } = useRequest(GetBTNIPData, {
  manual: true
})

watch(
  () => props.ip,
  (newIp) => {
    if (newIp) {
      run(newIp)
    }
  },
  { immediate: true }
)

const formatDuration = (ms: number): string => {
  const days = Math.floor(ms / (1000 * 60 * 60 * 24))
  if (days > 0) {
    return `${days} ${t('page.ipList.btn.days')}`
  }
  const hours = Math.floor(ms / (1000 * 60 * 60))
  if (hours > 0) {
    return `${hours} ${t('page.ipList.btn.hours')}`
  }
  const minutes = Math.floor(ms / (1000 * 60))
  return `${minutes} ${t('page.ipList.btn.minutes')}`
}

const getFileSizeValue = (bytes: number, decimals = 2): number => {
  if (bytes === 0 || bytes === -1) return 0
  const k = 1024
  const dm = decimals < 0 ? 0 : decimals
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  if (i < 0) return bytes
  return parseFloat((bytes / Math.pow(k, i)).toFixed(dm))
}

const getFileSizeUnit = (bytes: number): string => {
  if (bytes === -1) return 'N/A'
  if (bytes === 0) return 'Bytes'
  const k = 1024
  const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB', 'PB', 'EB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  if (i >= sizes.length) return 'Too large'
  if (i < 0) return 'Bytes'
  return sizes[i] ?? '0'
}

const bansColumns = [
  {
    title: () => t('page.ipList.btn.bans.time'),
    slotName: 'populate_time',
    width: 180
  },
  {
    title: () => t('page.ipList.btn.bans.torrent'),
    dataIndex: 'torrent',
    ellipsis: true,
    tooltip: true
  },
  {
    title: () => t('page.ipList.btn.bans.port'),
    dataIndex: 'peer_port',
    width: 80
  },
  {
    title: () => t('page.ipList.btn.bans.client'),
    dataIndex: 'peer_client_name',
    ellipsis: true,
    tooltip: true,
    width: 120
  },
  {
    title: () => t('page.ipList.btn.bans.traffic'),
    slotName: 'traffic',
    width: 150
  },
  {
    title: () => t('page.ipList.btn.bans.rule'),
    dataIndex: 'rule',
    ellipsis: true,
    tooltip: true,
    width: 120
  },
  {
    title: () => t('page.ipList.btn.bans.description'),
    dataIndex: 'description',
    ellipsis: true,
    tooltip: true
  }
]

const swarmsColumns = [
  {
    title: () => t('page.ipList.btn.swarms.firstSeen'),
    slotName: 'first_time_seen',
    width: 180
  },
  {
    title: () => t('page.ipList.btn.swarms.lastSeen'),
    slotName: 'last_time_seen',
    width: 180
  },
  {
    title: () => t('page.ipList.btn.swarms.torrent'),
    dataIndex: 'torrent',
    ellipsis: true,
    tooltip: true
  },
  {
    title: () => t('page.ipList.btn.swarms.port'),
    dataIndex: 'peer_port',
    width: 80
  },
  {
    title: () => t('page.ipList.btn.swarms.client'),
    dataIndex: 'peer_client_name',
    ellipsis: true,
    tooltip: true,
    width: 120
  },
  {
    title: () => t('page.ipList.btn.swarms.traffic'),
    slotName: 'traffic',
    width: 150
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
.tab {
  margin-left: 16px;
}
</style>

<style>
.tag-list {
  .arco-space-item {
    margin-bottom: 0px !important;
  }
}
</style>
