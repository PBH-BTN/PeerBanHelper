<template>
  <a-card :bordered="false">
    <a-space direction="vertical" fill>
      <a-spin v-if="loading" />
      <a-alert v-if="error" type="error">
        {{ error.message || t('page.ipList.btn.error') }}
        <template #action
          ><a-link @click="refresh()">{{ t('page.ipList.btn.retry') }}</a-link></template
        >
      </a-alert>
      <a-descriptions v-if="activity">
        <a-descriptions-item :label="t('page.ipList.btn.labels')">
          <a-tag
            v-for="label in activity.labels"
            :key="label"
            :color="activity.color"
            size="medium"
          >
            {{ label }}
          </a-tag>
          <a-tag v-if="activity.labels.length === 0" color="gray">
            {{ t('page.ipList.btn.noLabels') }}
          </a-tag>
        </a-descriptions-item>
      </a-descriptions>

      <!-- Tabs展示 -->
      <a-tabs default-active-key="bans" lazy-load>
        <!-- Bans Tab -->
        <a-tab-pane key="bans" class="tab" :title="t('page.ipList.btn.bans')">
          <a-space v-if="activity?.bans" direction="vertical" fill>
            <a-grid :cols="24" :row-gap="16">
              <a-grid-item :span="{ xs: 12, sm: 8 }">
                <a-statistic
                  :title="t('page.ipList.btn.bans.total')"
                  :value="activity.bans.total"
                  show-group-separator
                />
              </a-grid-item>
              <a-grid-item :span="{ xs: 12, sm: 8 }">
                <a-space direction="vertical" size="mini">
                  <a-typography-text type="secondary">{{
                    t('page.ipList.btn.bans.duration')
                  }}</a-typography-text>
                  <a-typography-text>{{
                    formatDuration(activity.bans.duration)
                  }}</a-typography-text>
                </a-space>
              </a-grid-item>
              <a-grid-item :span="{ xs: 24, sm: 8 }">
                <a-button
                  v-if="activity.bans.records.length > 0"
                  type="outline"
                  @click="showBansModal = true"
                >
                  {{ t('page.ipList.btn.viewRecords') }}
                </a-button>
              </a-grid-item>
            </a-grid>
          </a-space>
          <a-empty v-else-if="query" />
        </a-tab-pane>

        <!-- Swarms Tab -->
        <a-tab-pane
          v-if="activity?.swarms"
          key="swarms"
          class="tab"
          :title="t('page.ipList.btn.swarms')"
        >
          <a-space direction="vertical" fill>
            <a-grid :cols="24" :row-gap="16">
              <a-grid-item :span="{ xs: 12, sm: 6 }">
                <a-statistic
                  :title="t('page.ipList.btn.swarms.total')"
                  :value="activity.swarms.total"
                  show-group-separator
                />
              </a-grid-item>
              <a-grid-item :span="{ xs: 12, sm: 6 }">
                <a-statistic
                  :title="t('page.ipList.btn.swarms.downloading')"
                  :value="activity.swarms.concurrent_download_torrents_count"
                  show-group-separator
                />
              </a-grid-item>
              <a-grid-item :span="{ xs: 12, sm: 6 }">
                <a-statistic
                  :title="t('page.ipList.btn.swarms.seeding')"
                  :value="activity.swarms.concurrent_seeding_torrents_count"
                  show-group-separator
                />
              </a-grid-item>
              <a-grid-item :span="{ xs: 24, sm: 6 }">
                <a-button
                  v-if="activity.swarms.records.length > 0"
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
        <a-tab-pane
          v-if="activity?.traffic"
          key="traffic"
          class="tab"
          :title="t('page.ipList.btn.traffic')"
        >
          <a-grid :cols="24" :row-gap="16">
            <a-grid-item :span="{ xs: 12, sm: 6 }">
              <a-statistic
                :title="t('page.ipList.btn.traffic.fromPeerTraffic')"
                :value="getFileSizeValue(activity.traffic.from_peer_traffic)"
              >
                <template #prefix>
                  <icon-arrow-up class="green" />
                </template>
                <template #suffix>
                  {{ getFileSizeUnit(activity.traffic.from_peer_traffic) }}
                </template>
              </a-statistic>
            </a-grid-item>
            <a-grid-item :span="{ xs: 12, sm: 6 }">
              <a-statistic
                :title="t('page.ipList.btn.traffic.toPeerTraffic')"
                :value="getFileSizeValue(activity.traffic.to_peer_traffic)"
              >
                <template #prefix>
                  <icon-arrow-down class="red" />
                </template>
                <template #suffix>
                  {{ getFileSizeUnit(activity.traffic.to_peer_traffic) }}
                </template>
              </a-statistic>
            </a-grid-item>
            <a-grid-item :span="{ xs: 12, sm: 6 }">
              <a-statistic
                :title="t('page.ipList.btn.traffic.ratio')"
                :value="activity.traffic.share_ratio"
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
                  formatDuration(activity.traffic.duration)
                }}</a-typography-text>
              </a-space>
            </a-grid-item>
          </a-grid>
        </a-tab-pane>

        <!-- Torrents Tab -->
        <a-tab-pane v-if="activity?.torrents" key="torrents" :title="t('page.ipList.btn.torrents')">
          <a-grid :cols="24" :row-gap="16">
            <a-grid-item :span="{ xs: 12, sm: 12 }">
              <a-statistic
                :title="t('page.ipList.btn.torrents.count')"
                :value="activity.torrents.count"
                show-group-separator
              />
            </a-grid-item>
            <a-grid-item :span="{ xs: 12, sm: 12 }">
              <a-space direction="vertical" size="mini">
                <a-typography-text type="secondary">{{
                  t('page.ipList.btn.torrents.duration')
                }}</a-typography-text>
                <a-typography-text>{{
                  formatDuration(activity.torrents.duration)
                }}</a-typography-text>
              </a-space>
            </a-grid-item>
          </a-grid>
        </a-tab-pane>
        <a-tab-pane key="comments" :title="t('page.ipList.btn.comments')">
          <a-empty
            v-if="query?.details?.lookup?.commentAccess?.visible === false"
            :description="t('page.ipList.btn.commentsUnavailable')"
          />
          <btnComments v-else-if="scopes.length" :scopes="scopes" />
          <a-empty v-else :description="t('page.ipList.btn.noCommentScope')" />
        </a-tab-pane>
      </a-tabs>
    </a-space>

    <!-- Bans Records Modal -->
    <a-modal
      v-model:visible="showBansModal"
      :title="t('page.ipList.btn.bans.records')"
      width="70%"
      :footer="false"
    >
      <a-table
        :columns="bansColumns"
        :data="activity?.bans?.records"
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
                ><icon-minus /> {{ formatFileSize(record.to_peer_traffic) }}</a-typography-text
              >
              <a-typography-text
                ><icon-plus /> {{ formatFileSize(record.from_peer_traffic) }}</a-typography-text
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
        :data="activity?.swarms?.records"
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
  </a-card>
</template>

<script setup lang="ts">
import { getBtnLookup } from '@/service/btnIpQuery'
import { GetBTNIPData } from '@/service/data'
import { formatFileSize } from '@/utils/file'
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRequest } from 'vue-request'
import btnComments from './btnComments.vue'
import { getCommentScopes } from '../commentScopes'

const { t, d } = useI18n()

const props = defineProps<{
  ip: string
}>()

const showBansModal = ref(false)
const showSwarmsModal = ref(false)

const {
  data: query,
  loading,
  error,
  refresh
} = useRequest(async () => {
  const details = await getBtnLookup(props.ip)
  if (details) return { details, activity: details.lookup?.activity }
  if (props.ip.includes('/')) throw new Error(t('page.ipList.btn.lookupUnavailable'))

  const response = await GetBTNIPData(props.ip)
  if (!response.success) throw new Error(response.message)
  return { activity: response.data }
})

const activity = computed(() => query.value?.activity)
const scopes = computed(() => getCommentScopes(props.ip))

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
    width: 100
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
    width: 100
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
  box-sizing: border-box;
  padding-inline-start: 16px;
}
</style>

<style>
.tag-list {
  .arco-space-item {
    margin-bottom: 0px !important;
  }
}
</style>
