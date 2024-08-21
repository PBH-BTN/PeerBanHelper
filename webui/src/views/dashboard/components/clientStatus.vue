<template>
  <a-typography-title :heading="3">{{ t('page.dashboard.clientStatus.title') }}
    <a-button class="add-btn" type="outline" shape="circle" @click="() => editDownloaderModal?.showModal(true)">
      <template #icon>
        <icon-plus />
      </template>
    </a-button>
  </a-typography-title>

  <a-row justify="start" align="stretch" :wrap="true" :gutter="[
    { xs: 8, sm: 8, md: 8, lg: 24, xl: 32 },
    { xs: 8, sm: 8, md: 8, lg: 24, xl: 32 }
  ]">
    <!-- 骨架屏 -->
    <a-col v-if="!data || data?.length === 0 || loading" :xs="24" :sm="12" :md="8" :lg="6">
      <a-card hoverable :header-style="{ height: 'auto' }">
        <template #title>
          <a-skeleton :animation="true">
            <a-space direction="vertical" :style="{ width: '100%' }" :size="0">
              <a-skeleton-line :line-height="44" :line-spacing="0" />
            </a-space>
          </a-skeleton>
        </template>
        <a-skeleton :animation="true">
          <a-space direction="vertical" :style="{ width: '100%' }" :size="0">
            <a-skeleton-line :rows="4" :line-height="22" :line-spacing="14" :widths="['60%', '70%', '50%', '60%']" />
          </a-space>
        </a-skeleton>
      </a-card>
    </a-col>
    <!-- client 卡片 -->
    <a-col v-else :xs="24" :sm="12" :md="8" :lg="6" v-for="client in data" :key="client.name">
      <ClientStatusCard :disable-remove="data.length === 1" :downloader="client"
        @torrent-view-click="() => torrentList?.showModal(client.name)" @downloader-deleted="refresh"
        @edit-click="(e) => editDownloaderModal?.showModal(false, e)" />
    </a-col>
  </a-row>
  <TorrentListModal ref="torrentList" />
  <EditDownloaderModal ref="editDownloaderModal" @changed="refresh" />
</template>
<script setup lang="ts">
import { useEndpointStore } from '@/stores/endpoint'
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import TorrentListModal from './torrentListModal.vue'
import ClientStatusCard from './clientStatusCard.vue'
import EditDownloaderModal from './editDownloaderModal.vue'
import { useAutoUpdatePlugin } from '@/stores/autoUpdate'
import { useRequest } from 'vue-request'
import { getDownloaders } from '@/service/downloaders'
import { type Downloader } from '@/api/model/downloader'
const { t } = useI18n()
const endpointState = useEndpointStore()
const data = ref<Downloader[]>()
const { refresh, loading } = useRequest(
  getDownloaders,
  {
    cacheKey: () => `${endpointState.endpoint}-downloader`,
    onSuccess: (res) => {
      data.value = res.data
    }
  },
  [useAutoUpdatePlugin]
)
watch(() => endpointState.endpoint, refresh)

const torrentList = ref<InstanceType<typeof TorrentListModal>>()
const editDownloaderModal = ref<InstanceType<typeof EditDownloaderModal>>()
</script>
<style scoped lang="less">
.add-btn {
  border-color: rgb(var(--gray-2));
  color: rgb(var(--gray-8));
  font-size: 16px;
}
</style>
