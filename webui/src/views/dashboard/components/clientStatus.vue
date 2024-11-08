<template>
  <a-space size="small" direction="vertical" fill>
    <a-typography-title :heading="3"
      >{{ t('page.dashboard.clientStatus.title') }}
      <a-button
        class="add-btn"
        type="outline"
        shape="circle"
        @click="() => editDownloaderModal?.showModal(true)"
      >
        <template #icon>
          <icon-plus />
        </template>
      </a-button>
    </a-typography-title>

    <a-row
      justify="start"
      align="stretch"
      :wrap="true"
      :gutter="[
        { xs: 8, sm: 8, md: 8, lg: 24, xl: 32 },
        { xs: 8, sm: 8, md: 8, lg: 24, xl: 32 }
      ]"
    >
      <!-- 骨架屏 -->
      <a-col v-if="!data || data?.length === 0 || firstLoading" :xs="24" :sm="12" :md="8" :lg="6">
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
              <a-skeleton-line
                :rows="4"
                :line-height="22"
                :line-spacing="14"
                :widths="['60%', '70%', '50%', '60%']"
              />
            </a-space>
          </a-skeleton>
        </a-card>
      </a-col>
      <!-- client 卡片 -->
      <a-col v-for="client in data" v-else :key="client.name" :xs="24" :sm="12" :md="8" :lg="6">
        <ClientStatusCard
          :disable-remove="data.length === 1"
          :downloader="client"
          @downloader-deleted="refresh"
          @edit-click="(e) => editDownloaderModal?.showModal(false, e)"
        />
      </a-col>
    </a-row>
    <EditDownloaderModal ref="editDownloaderModal" @changed="refresh" />
    <div>
      <a-typography-title :heading="3"
        >{{ t('page.dashboard.torrentList.title') }}
      </a-typography-title>
      <a-tabs size="large" animation lazy-load destroy-on-hide type="rounded">
        <a-tab-pane v-for="downloader in data" :key="downloader.name" :title="downloader.name">
          <torrentList :downloader="downloader.name" />
        </a-tab-pane>
      </a-tabs>
    </div>
  </a-space>
</template>
<script setup lang="ts">
import { type Downloader } from '@/api/model/downloader'
import { getDownloaders } from '@/service/downloaders'
import { useAutoUpdatePlugin } from '@/stores/autoUpdate'
import { useEndpointStore } from '@/stores/endpoint'
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRequest } from 'vue-request'
import ClientStatusCard from './clientStatusCard.vue'
import EditDownloaderModal from './editDownloaderModal.vue'
import torrentList from './torrentList.vue'
const { t } = useI18n()
const endpointState = useEndpointStore()
const data = ref<Downloader[]>()
const firstLoading = ref(true)
const { refresh } = useRequest(
  getDownloaders,
  {
    cacheKey: () => `${endpointState.endpoint}-downloader`,
    onSuccess: (res) => {
      data.value = res.data
      firstLoading.value = false
    }
  },
  [useAutoUpdatePlugin]
)
watch(() => endpointState.endpoint, refresh)

const editDownloaderModal = ref<InstanceType<typeof EditDownloaderModal>>()
</script>
<style scoped lang="less">
.add-btn {
  border-color: rgb(var(--gray-2));
  color: rgb(var(--gray-8));
  font-size: 16px;
}
</style>
