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
        @search="handleSearch"
      />
    </div>
    <div class="result-container center">
      <a-card
        v-show="data?.data || error"
        class="result-card"
        :style="{ minWidth: data?.data.found ? '1150px' : '400px' }"
        hoverable
      >
        <a-space v-if="!error" direction="vertical" fill>
          <a-descriptions>
            <template #title>
              <a-space>
                {{ data?.data.address }}
                <a-tooltip v-if="!data?.data.found" :content="t('page.ipList.notfound.tips')">
                  <a-tag>Not found</a-tag>
                </a-tooltip>
              </a-space>
            </template>
            <a-descriptions-item
              v-if="data?.data.found"
              :label="t('page.ipList.label.banCount')"
              :span="2"
            >
              {{ data?.data.banCount }}
            </a-descriptions-item>
            <a-descriptions-item
              v-if="data?.data.found"
              :label="t('page.ipList.label.torrentAccessCount')"
              :span="2"
            >
              {{ data?.data.torrentAccessCount }}
            </a-descriptions-item>
            <a-descriptions-item
              v-if="data?.data.found"
              :label="t('page.ipList.label.uploadedToPeer')"
              :span="2"
            >
              <a-typography-text>
                <icon-arrow-up class="green" />
                {{ formatFileSize(data?.data.uploadedToPeer ?? 0) }}</a-typography-text
              >
            </a-descriptions-item>
            <a-descriptions-item
              v-if="data?.data.found"
              :label="t('page.ipList.label.downloadedFromPeer')"
              :span="2"
            >
              <a-typography-text>
                <icon-arrow-down class="red" />
                {{ formatFileSize(data?.data.downloadedFromPeer ?? 0) }}</a-typography-text
              >
            </a-descriptions-item>
            <a-descriptions-item
              v-if="data?.data.found"
              :label="t('page.ipList.label.firstTimeSeen')"
              :span="2"
            >
              {{ d(data?.data.firstTimeSeen ?? 0, 'long') }}
            </a-descriptions-item>
            <a-descriptions-item
              v-if="data?.data.found"
              :label="t('page.ipList.label.lastTimeSeen')"
              :span="2"
            >
              {{ d(data?.data.lastTimeSeen ?? 0, 'long') }}
            </a-descriptions-item>
            <a-descriptions-item
              v-if="data?.data.geo?.country?.iso || data?.data.geo?.city?.name"
              :label="t('page.banlist.banlist.listItem.geo')"
              :span="2"
            >
              <CountryFlag
                v-if="data.data.geo?.country?.iso"
                :iso="data.data.geo?.country?.iso ?? ''"
              />
              {{ `${data.data.geo?.country?.name ?? ''} ${data.data.geo?.city?.name ?? ''}` }}
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
            <a-descriptions-item :span="8">
              <template #label>
                <a-space>
                  {{ t('page.ipList.shortcut') }}
                  <a-tooltip :content="t('page.ipList.shortcut.tips')">
                    <icon-info-circle />
                  </a-tooltip> </a-space
              ></template>
              <a-space>
                <a-button :href="`https://ip.ping0.cc/ip/${searchInput}`" type="outline">
                  ping0
                </a-button>
                <a-button :href="`https://search.censys.io/hosts/${searchInput}`" type="outline">
                  Censys
                </a-button>
              </a-space>
            </a-descriptions-item>
          </a-descriptions>
          <a-collapse
            v-if="data?.data.found"
            v-model:active-key="activatedTab"
            :bordered="false"
            destroy-on-hide
          >
            <a-collapse-item
              key="1"
              :header="t('page.ipList.label.accessHistory')"
              :disabled="!plusStatus?.activated"
              class="collapse-table"
            >
              <template #expand-icon="{ active }">
                <icon-plus v-if="plusStatus?.activated && !active" />
                <icon-minus v-else-if="plusStatus?.activated && active" />
                <icon-lock v-else />
              </template>
              <template v-if="!plusStatus?.activated" #extra>
                <a-tooltip :content="t('page.ipList.plusLock')">
                  <a-tag size="small">Plus</a-tag>
                </a-tooltip>
              </template>
              <accessHistoryTable :ip="data.data.address" />
            </a-collapse-item>
            <a-collapse-item
              key="2"
              :header="t('page.ipList.label.banHistory')"
              :disabled="!plusStatus?.activated"
              class="collapse-table"
            >
              <template #expand-icon="{ active }">
                <icon-plus v-if="plusStatus?.activated && !active" />
                <icon-minus v-else-if="plusStatus?.activated && active" />
                <icon-lock v-else />
              </template>
              <template v-if="!plusStatus?.activated" #extra>
                <a-tooltip :content="t('page.ipList.plusLock')">
                  <a-tag size="small">Plus</a-tag>
                </a-tooltip>
              </template>
              <banHistoryTable :ip="data.data.address" />
            </a-collapse-item>
          </a-collapse>
        </a-space>
        <a-result v-else status="500" :title="t('page.ipList.error')" :subtitle="error.message">
          <a-typography style="background: var(--color-fill-2); padding: 24px">
            <a-typography-paragraph>Details:</a-typography-paragraph>
            <ul>
              <li v-for="line of error.stack?.split('\n')" :key="line">{{ line }}</li>
            </ul>
          </a-typography>
        </a-result>
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
const { data, loading, run, error } = useRequest(GetIPBasicData, {
  manual: true
})

const endpointStore = useEndpointStore()
const plusStatus = computed(() => endpointStore.plusStatus)

const activatedTab = ref<(string | number)[]>([])

const { query } = useRoute()

const handleSearch = (value: string) => {
  if (value) {
    data.value = undefined
    activatedTab.value = []
    if (value !== query.ip) {
      const search = new URLSearchParams(window.location.search)
      search.set('ip', value)
      window.history.pushState(
        {},
        '',
        new URL(`${window.location.origin}${window.location.pathname}?${search.toString()}`)
      )
    }
    run(value)
  }
}
onMounted(() => {
  if (query.ip) {
    searchInput.value = query.ip as string
    handleSearch(searchInput.value)
  }
})
</script>
<style>
.collapse-table {
  .arco-collapse-item-content-expend {
    padding: 0;
    .arco-collapse-item-content-box {
      padding-top: 0px;
    }
  }
}
</style>

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
