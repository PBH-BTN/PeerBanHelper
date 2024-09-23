<template>
  <a-space direction="vertical" fill class="center">
    <a-typography style="text-align: center">
      <a-typography-title>{{ t('page.ipList.title') }}</a-typography-title>
      <a-typography-text>
        {{ t('page.ipList.description') }}
      </a-typography-text>
    </a-typography>
    <div class="center searchContainer">
      <a-input-search
        search-button
        placeholder="192.168.1.1...."
        class="searchBox"
        :loading="loading"
        @search="run"
      />
    </div>
    <div class="result-container center">
      <a-card class="result-card" hoverable>
        <a-space direction="vertical" fill>
          <a-descriptions :title="data?.data.address">
            <a-descriptions-item :label="t('page.ipList.label.banCount')" :span="2">
              {{ data?.data.banCount }}
            </a-descriptions-item>
            <a-descriptions-item :label="t('page.ipList.label.torrentAccessCount')" :span="2">
              {{ data?.data.torrentAccessCount }}
            </a-descriptions-item>
            <a-descriptions-item :label="t('page.ipList.label.uploadedToPeer')" :span="2">
              <a-typography-text>
                <icon-arrow-up class="green" />
                {{ formatFileSize(data?.data.uploadedToPeer ?? 0) }}</a-typography-text
              >
            </a-descriptions-item>
            <a-descriptions-item :label="t('page.ipList.label.downloadedFromPeer')" :span="2">
              <a-typography-text>
                <icon-arrow-down class="red" />
                {{ formatFileSize(data?.data.downloadedFromPeer ?? 0) }}</a-typography-text
              >
            </a-descriptions-item>
            <a-descriptions-item :label="t('page.ipList.label.firstTimeSeen')" :span="4">
              {{ d(data?.data.firstTimeSeen ?? 0, 'long') }}
            </a-descriptions-item>
            <a-descriptions-item :label="t('page.ipList.label.lastTimeSeen')" :span="4">
              {{ d(data?.data.lastTimeSeen ?? 0, 'long') }}
            </a-descriptions-item>
          </a-descriptions>
        </a-space>
      </a-card>
    </div>
  </a-space>
</template>

<script setup lang="ts">
import { GetIPBasicData } from '@/service/data'
import { useI18n } from 'vue-i18n'
import { useRequest } from 'vue-request'
import { formatFileSize } from '@/utils/file'
const { t, d } = useI18n()
const { data, loading, run } = useRequest(GetIPBasicData, {
  manual: true
})
run('183.11.30.71')
</script>

<style scoped>
.searchContainer {
  margin-top: 2em;
  margin-bottom: 1em;
}
.center {
  display: flex;
  justify-content: center;
}
.searchBox {
  transform: scale(2);
  width: 400px;
}
.result-container {
  margin-top: 2em;
  min-height: 50vh;
}
.result-card {
  height: auto;
  transition-property: all;
  padding: 2em;
}
.red {
  color: rgb(var(--red-5));
}
.green {
  color: rgb(var(--green-5));
}
</style>
