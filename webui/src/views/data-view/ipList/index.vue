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
        v-model="searchInput"
        search-button
        placeholder="192.168.1.1...."
        class="searchBox"
        :loading="loading"
        @search="run"
      />
    </div>
    <div class="result-container center">
      <a-card v-show="data?.data" class="result-card" hoverable>
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
            <a-descriptions-item :label="t('page.ipList.label.firstTimeSeen')" :span="2">
              {{ d(data?.data.firstTimeSeen ?? 0, 'long') }}
            </a-descriptions-item>
            <a-descriptions-item :label="t('page.ipList.label.lastTimeSeen')" :span="2">
              {{ d(data?.data.lastTimeSeen ?? 0, 'long') }}
            </a-descriptions-item>
            <a-descriptions-item
              v-if="data?.data.geo"
              :label="t('page.banlist.banlist.listItem.geo')"
              :span="2"
            >
              <CountryFlag
                :iso="data.data.geo?.country?.iso ?? t('page.banlist.banlist.listItem.empty')"
              />
              {{
                `${data.data.geo?.country?.name} ${
                  data.data.geo?.city?.name ?? t('page.banlist.banlist.listItem.empty')
                }`
              }}
            </a-descriptions-item>
            <a-descriptions-item
              v-if="data?.data.geo?.network?.isp"
              :label="t('page.banlist.banlist.listItem.network.isp')"
              :span="1"
            >
              {{ data.data.geo?.network?.isp }}
            </a-descriptions-item>
            <a-descriptions-item
              v-if="data?.data.geo?.network?.netType"
              :label="t('page.banlist.banlist.listItem.network.netType')"
              :span="2"
            >
              {{ data.data.geo?.network?.netType }}
            </a-descriptions-item>
            <a-descriptions-item
              v-if="data?.data.geo?.as"
              :label="t('page.banlist.banlist.listItem.asn')"
              :span="2"
            >
              <a-space>
                <a-typography-text> {{ data.data.geo?.as?.organization }}</a-typography-text>
                <a-tag :color="getColor((data.data.geo?.as?.number ?? 0).toString())">{{
                  data.data.geo?.as?.number
                }}</a-tag>
                <a-tooltip
                  :content="
                    t('page.banlist.banlist.listItem.asn.subnet') +
                    data.data.geo?.as?.network?.ipAddress +
                    '/' +
                    data.data.geo?.as?.network?.prefixLength
                  "
                >
                  <a-link
                    :href="`https://2ip.io/analytics/asn-list/?asnId=${data.data.geo?.as?.number}`"
                    :hoverable="false"
                  >
                    <icon-info-circle />
                  </a-link>
                </a-tooltip>
              </a-space>
            </a-descriptions-item>
          </a-descriptions>
          <a-space></a-space>
          <a-collapse :bordered="false" destroy-on-hide>
            <a-collapse-item
              key="1"
              :header="t('page.ipList.label.accessHistory')"
              :disabled="!plusStatus"
              class="collapse-table"
            >
              <template #expand-icon>
                <icon-plus v-if="plusStatus?.activated" />
                <icon-lock v-else />
              </template>
              <template v-if="!plusStatus" #extra>
                <a-popover :content="t('page.ipList.plusLock')">
                  <a-tag size="small">Plus</a-tag>
                </a-popover>
              </template>
              <accessHistoryTable :ip="searchInput" />
            </a-collapse-item>
            <a-collapse-item
              key="2"
              :header="t('page.ipList.label.banHistory')"
              :disabled="!plusStatus"
            >
              <template #expand-icon>
                <icon-plus v-if="plusStatus?.activated" />
                <icon-lock v-else />
              </template>
              <template v-if="!plusStatus" #extra>
                <a-popover :content="t('page.ipList.plusLock')">
                  <a-tag size="small">Plus</a-tag>
                </a-popover>
              </template>
              <banHistoryTable :ip="searchInput" />
            </a-collapse-item>
          </a-collapse>
        </a-space>
      </a-card>
    </div>
  </a-space>
</template>

<script setup lang="ts">
import CountryFlag from '@/components/countryFlag.vue'
import { GetIPBasicData } from '@/service/data'
import { useEndpointStore } from '@/stores/endpoint'
import { getColor } from '@/utils/color'
import { formatFileSize } from '@/utils/file'
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRequest } from 'vue-request'
import { useRoute } from 'vue-router'
import accessHistoryTable from './components/accessHistoryTable.vue'
import banHistoryTable from './components/banHistoryTable.vue'

const searchInput = ref('')
const { t, d } = useI18n()
const { data, loading, run } = useRequest(GetIPBasicData, {
  manual: true
})

const endpointStore = useEndpointStore()
const plusStatus = computed(() => endpointStore.plusStatus)

const { query } = useRoute()
onMounted(() => {
  if (query.ip) {
    searchInput.value = query.ip as string
    run(searchInput.value)
  }
})
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
  margin-top: 1em;
}
.result-card {
  min-width: 1150px;
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
