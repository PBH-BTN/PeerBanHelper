<template>
  <div class="ip-query-page">
    <header class="query-header">
      <a-typography-title>{{ t('page.ipList.title') }}</a-typography-title>
      <a-typography-text>{{ t('page.ipList.description') }}</a-typography-text>
      <a-input-search
        v-model="searchInput"
        search-button
        size="large"
        placeholder="192.168.1.1 / 192.168.1.0/24"
        class="search-box"
        :loading="loading"
        @search="handleSearch"
      />
    </header>

    <div v-if="currentIp" class="query-results">
      <a-typography-title :heading="3" class="result-title">{{ currentIp }}</a-typography-title>
      <a-spin v-if="loading" class="local-loading" />
      <a-alert v-if="!isCidr && error" type="error">{{ error.message }}</a-alert>
      <section v-if="!isCidr && data?.data" class="data-section">
        <a-typography-title :heading="4" class="section-title">
          {{ t('page.ipList.label.localData') }}
        </a-typography-title>
        <a-alert v-if="!data.data.found" type="info">{{ t('page.ipList.notfound.tips') }}</a-alert>
        <a-descriptions :column="{ xs: 1, md: 2 }" layout="vertical" size="large">
          <a-descriptions-item v-if="data?.data.found" :label="t('page.ipList.label.banCount')">
            {{ data?.data.banCount }}
          </a-descriptions-item>
          <a-descriptions-item
            v-if="data?.data.found"
            :label="t('page.ipList.label.torrentAccessCount')"
          >
            {{ data?.data.torrentAccessCount }}
          </a-descriptions-item>
          <a-descriptions-item
            v-if="data?.data.found"
            :label="t('page.ipList.label.uploadedToPeer')"
          >
            <a-typography-text>
              <icon-arrow-up class="green" />
              {{ formatFileSize(data?.data.uploadedToPeer ?? 0) }}</a-typography-text
            >
          </a-descriptions-item>
          <a-descriptions-item
            v-if="data?.data.found"
            :label="t('page.ipList.label.downloadedFromPeer')"
          >
            <a-typography-text>
              <icon-arrow-down class="red" />
              {{ formatFileSize(data?.data.downloadedFromPeer ?? 0) }}</a-typography-text
            >
          </a-descriptions-item>
          <a-descriptions-item
            v-if="data?.data.found"
            :label="t('page.ipList.label.firstTimeSeen')"
          >
            {{ data?.data.firstTimeSeen ? d(data.data.firstTimeSeen, 'long') : '—' }}
          </a-descriptions-item>
          <a-descriptions-item v-if="data?.data.found" :label="t('page.ipList.label.lastTimeSeen')">
            {{ data?.data.lastTimeSeen ? d(data.data.lastTimeSeen, 'long') : '—' }}
          </a-descriptions-item>
          <a-descriptions-item
            v-if="data?.data.geo?.country?.iso || data?.data.geo?.city?.name"
            :label="t('page.banlist.banlist.listItem.geo')"
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
          >
            {{ data.data.geo?.network?.isp }}
          </a-descriptions-item>
          <a-descriptions-item
            v-if="data?.data.geo?.network?.netType"
            :label="t('page.banlist.banlist.listItem.network.netType')"
          >
            {{ data.data.geo?.network?.netType }}
          </a-descriptions-item>
          <a-descriptions-item
            v-if="data?.data.geo?.as"
            :label="t('page.banlist.banlist.listItem.asn')"
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
                  :href="`https://2ip.io/analytics/asn-list/?asnId=${data?.data.geo?.as?.number}`"
                  :hoverable="false"
                >
                  <icon-info-circle />
                </a-link>
              </a-tooltip>
            </a-space>
          </a-descriptions-item>
          <a-descriptions-item :span="2">
            <template #label>
              <a-space>
                {{ t('page.ipList.shortcut') }}
                <a-tooltip :content="t('page.ipList.shortcut.tips')">
                  <icon-info-circle />
                </a-tooltip> </a-space
            ></template>
            <a-space>
              <a-button-group>
                <a-button
                  target="_blank"
                  :href="`https://ip.ping0.cc/ip/${encodeURIComponent(currentIp)}`"
                  type="outline"
                >
                  ping0
                </a-button>
                <a-button
                  target="_blank"
                  :href="`https://search.censys.io/hosts/${encodeURIComponent(currentIp)}`"
                  type="outline"
                >
                  Censys
                </a-button>
                <a-button
                  target="_blank"
                  :href="`https://ip.skk.moe/query?q=${encodeURIComponent(currentIp)}`"
                  type="outline"
                >
                  Sukka
                </a-button>
              </a-button-group>
            </a-space>
          </a-descriptions-item>
        </a-descriptions>
        <a-collapse v-if="data?.data.found" :bordered="false" destroy-on-hide>
          <a-collapse-item
            key="1"
            :header="t('page.ipList.label.accessHistory')"
            :disabled="!pbhPlusActivited"
            class="collapse-table"
          >
            <template #expand-icon="{ active }">
              <icon-plus v-if="pbhPlusActivited && !active" />
              <icon-minus v-else-if="pbhPlusActivited && active" />
              <icon-lock v-else />
            </template>
            <template v-if="!pbhPlusActivited" #extra>
              <a-tooltip :content="t('page.ipList.plusLock')">
                <a-tag size="small">Plus</a-tag>
              </a-tooltip>
            </template>
            <accessHistoryTable :ip="data.data.address" />
          </a-collapse-item>
          <a-collapse-item
            key="2"
            :header="t('page.ipList.label.banHistory')"
            :disabled="!pbhPlusActivited"
            class="collapse-table"
          >
            <template #expand-icon="{ active }">
              <icon-plus v-if="pbhPlusActivited && !active" />
              <icon-minus v-else-if="pbhPlusActivited && active" />
              <icon-lock v-else />
            </template>
            <template v-if="!pbhPlusActivited" #extra>
              <a-tooltip :content="t('page.ipList.plusLock')">
                <a-tag size="small">Plus</a-tag>
              </a-tooltip>
            </template>
            <banHistoryTable :ip="data.data.address" />
          </a-collapse-item>
        </a-collapse>
      </section>
      <section
        v-if="!loading && (isCidr || data?.data?.btnQueryAvailable !== false)"
        class="data-section"
      >
        <a-typography-title :heading="4" class="section-title">
          {{ t('page.ipList.label.btnData') }}
        </a-typography-title>
        <btnDataPanel
          :key="searchRevision"
          :ip="currentIp"
          :initial-tab="showComments ? 'comments' : undefined"
        />
      </section>
    </div>
  </div>
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
import btnDataPanel from './components/btnDataPanel.vue'

const searchInput = ref('')
const { t, d } = useI18n()
const { data, loading, run, error, cancel } = useRequest(GetIPBasicData, {
  manual: true
})
const currentIp = ref('')
const searchRevision = ref(0)
const isCidr = computed(() => currentIp.value.includes('/'))

const endpointStore = useEndpointStore()

const { query } = useRoute()
const showComments = query.tab === 'comments'

const handleSearch = (input: string) => {
  const value = input.trim()
  if (value) {
    cancel()
    data.value = undefined
    error.value = undefined
    searchRevision.value++
    const url = new URL(window.location.href)
    if (url.searchParams.get('ip') !== value) {
      url.searchParams.set('ip', value)
      window.history.pushState({}, '', url)
    }
    currentIp.value = value
    if (!isCidr.value) run(value)
  }
}
onMounted(async () => {
  if (query.ip) {
    searchInput.value = query.ip as string
    handleSearch(searchInput.value)
  }
})

const pbhPlusActivited = computed(() =>
  endpointStore.plusStatus?.enabledFeatures?.includes('basic')
)
</script>
<style scoped>
.ip-query-page {
  width: min(100%, 1120px);
  margin-inline: auto;
  padding: 0 clamp(12px, 4vw, 32px) 48px;
  box-sizing: border-box;
}

.query-header {
  display: grid;
  justify-items: center;
  gap: 16px;
  padding: clamp(32px, 6vw, 64px) 0 36px;
  text-align: center;
}

.query-header > :deep(.arco-typography) {
  margin: 0;
}

.search-box {
  width: min(100%, 600px);
  margin-top: 8px;
}

.query-results {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 24px;
}

.result-title,
.section-title {
  margin: 0;
  overflow-wrap: anywhere;
}

.result-title {
  font-variant-numeric: tabular-nums;
}

.data-section {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 24px;
  padding: clamp(18px, 4vw, 32px);
  border: 1px solid var(--color-border-2);
  border-radius: 12px;
  background: var(--color-bg-2);
}

.local-loading {
  justify-self: start;
}

.collapse-table > :deep(.arco-collapse-item-content) {
  padding: 0;
}

.red {
  color: rgb(var(--red-5));
}

.green {
  color: rgb(var(--green-5));
}
</style>
