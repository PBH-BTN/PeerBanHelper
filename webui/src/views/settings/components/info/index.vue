<template>
  <!--骨架屏-->
  <a-space v-if="loading" direction="vertical" fill>
    <a-typography-title :heading="3">
      {{ t('page.settings.tab.info.title') }}
    </a-typography-title>
    <!--Peerbanhelper info-->
    <a-descriptions
      :column="1"
      size="large"
      :title="t('page.settings.tab.info.version')"
      :align="{ label: 'right' }"
    >
      <a-descriptions-item :label="t('page.settings.tab.info.version.current')">
        <a-skeleton-line :rows="1" :animation="true" />
      </a-descriptions-item>
      <a-descriptions-item :label="t('page.settings.tab.info.version.webui')">
        {{ webuiVersion }} (<a-link
          :href="`https://github.com/Ghost-chu/PeerBanHelper/commit/${webuiHash}`"
        >
          {{ webuiHash.substring(0, 8) }} </a-link
        >)
      </a-descriptions-item>
      <a-descriptions-item :label="t('page.settings.tab.info.version.ReleaseType')">
        <a-skeleton-line :rows="1" :animation="true" />
      </a-descriptions-item>
      <a-descriptions-item :label="t('page.settings.tab.info.version.compileTime')">
        <a-skeleton-line :rows="1" :animation="true" />
      </a-descriptions-item>
      <a-descriptions-item :label="t('page.settings.tab.info.version.uptime')">
        <a-skeleton-line :rows="1" :animation="true" />
      </a-descriptions-item>
      <a-descriptions-item :label="t('page.settings.tab.info.version.plus')">
        <a-skeleton-line :rows="1" :animation="true" />
      </a-descriptions-item>
    </a-descriptions>
    <a-divider />
    <!---System Info-->
    <a-descriptions
      :column="1"
      size="large"
      :title="t('page.settings.tab.info.system')"
      :align="{ label: 'right' }"
    >
      <a-descriptions-item :label="t('page.settings.tab.info.system.os')">
        <a-skeleton-line :rows="1" :animation="true" />
      </a-descriptions-item>
      <a-descriptions-item :label="t('page.settings.tab.info.system.version')">
        <a-skeleton-line :rows="1" :animation="true" />
      </a-descriptions-item>
      <a-descriptions-item :label="t('page.settings.tab.info.system.architecture')">
        <a-skeleton-line :rows="1" :animation="true" />
      </a-descriptions-item>
      <a-descriptions-item :label="t('page.settings.tab.info.system.cores')">
        <a-skeleton-line :rows="1" :animation="true" />
      </a-descriptions-item>
      <a-descriptions-item :label="t('page.settings.tab.info.system.memory.total')">
        <a-skeleton-line :rows="1" :animation="true" />
      </a-descriptions-item>
      <a-descriptions-item :label="t('page.settings.tab.info.system.memory.free')">
        <a-skeleton-line :rows="1" :animation="true" />
      </a-descriptions-item>
      <a-descriptions-item :label="t('page.settings.tab.info.system.load')">
        <a-skeleton-line :rows="1" :animation="true" />
      </a-descriptions-item>
    </a-descriptions>
    <a-divider />
    <!---Network Info-->
    <a-descriptions
      :column="1"
      size="large"
      :title="t('page.settings.tab.info.network')"
      :align="{ label: 'right' }"
    >
      <a-descriptions-item :label="t('page.settings.tab.info.network.internet')">
        <a-skeleton-line :rows="1" :animation="true" />
      </a-descriptions-item>
      <a-descriptions-item :label="t('page.settings.tab.info.network.proxy')">
        <a-skeleton-line :rows="1" :animation="true" />
      </a-descriptions-item>
      <a-descriptions-item :label="t('page.settings.tab.info.network.reverseProxy')">
        <a-skeleton-line :rows="1" :animation="true" />
      </a-descriptions-item>
      <a-descriptions-item>
        <a-skeleton-line :rows="1" :animation="true" />
      </a-descriptions-item>
    </a-descriptions>
    <a-divider />
    <!--Runtime Info-->
    <a-descriptions
      :column="1"
      size="large"
      :title="t('page.settings.tab.info.runtime')"
      :align="{ label: 'right' }"
    >
      <a-descriptions-item :label="t('page.settings.tab.info.runtime.version')">
        <a-skeleton-line :rows="1" :animation="true" />
      </a-descriptions-item>
      <a-descriptions-item :label="t('page.settings.tab.info.runtime.vendor')">
        <a-skeleton-line :rows="1" :animation="true" />
      </a-descriptions-item>
      <a-descriptions-item :label="t('page.settings.tab.info.runtime.runtime')">
        <a-skeleton-line :rows="1" :animation="true" />
      </a-descriptions-item>
      <a-descriptions-item :label="t('page.settings.tab.info.runtime.bitness')">
        <a-skeleton-line :rows="1" :animation="true" />
      </a-descriptions-item>
    </a-descriptions>
  </a-space>
  <!--End 骨架屏-->
  <a-space v-else direction="vertical" fill>
    <a-typography-title :heading="3">
      {{ t('page.settings.tab.info.title') }}
    </a-typography-title>
    <!--Peerbanhelper info-->
    <a-descriptions
      :column="1"
      size="large"
      :title="t('page.settings.tab.info.version')"
      :align="{ label: 'right' }"
    >
      <a-descriptions-item :label="t('page.settings.tab.info.version.current')">
        {{ data?.data.peerbanhelper.version }} (<a-link
          :href="`https://github.com/Ghost-chu/PeerBanHelper/commit/${data?.data.peerbanhelper.commit_id}`"
        >
          {{ data?.data.peerbanhelper.commit_id.substring(0, 8) }} </a-link
        >)
      </a-descriptions-item>
      <a-descriptions-item :label="t('page.settings.tab.info.version.webui')">
        {{ webuiVersion }} (<a-link
          :href="`https://github.com/Ghost-chu/PeerBanHelper/commit/${webuiHash}`"
        >
          {{ webuiHash.substring(0, 8) }} </a-link
        >)
      </a-descriptions-item>
      <a-descriptions-item :label="t('page.settings.tab.info.version.ReleaseType')">
        <a-tag :color="getColor(data?.data.peerbanhelper.release ?? 'unknown')">
          {{ data?.data.peerbanhelper.release }}
        </a-tag>
      </a-descriptions-item>
      <a-descriptions-item :label="t('page.settings.tab.info.version.compileTime')">
        {{ d(dayjs.unix(data?.data.peerbanhelper.compile_time ?? 0).toDate(), 'long') }}
      </a-descriptions-item>
      <a-descriptions-item :label="t('page.settings.tab.info.version.uptime')">
        {{ dayjs.duration({ seconds: data?.data.peerbanhelper.uptime }).humanize() }}
      </a-descriptions-item>
      <a-descriptions-item :label="t('page.settings.tab.info.version.plus')">
        <button @click="endpointStore.emitter.emit('open-plus-modal')">
          <a-tag :color="endpointStore.plusStatus?.activated ? 'green' : 'red'">
            <template v-if="endpointStore.plusStatus?.activated" #icon>
              <icon-check-circle-fill />
            </template>
            {{
              t(
                endpointStore.plusStatus?.activated
                  ? 'page.settings.tab.info.version.plus.active'
                  : 'page.settings.tab.info.version.plus.inactive'
              )
            }}
          </a-tag>
        </button>
      </a-descriptions-item>
    </a-descriptions>
    <a-divider />
    <!---System Info-->
    <a-descriptions
      :column="1"
      size="large"
      :title="t('page.settings.tab.info.system')"
      :align="{ label: 'right' }"
    >
      <a-descriptions-item :label="t('page.settings.tab.info.system.os')">
        {{ data?.data.system.os }}
      </a-descriptions-item>
      <a-descriptions-item :label="t('page.settings.tab.info.system.version')">
        {{ data?.data.system.version }}
      </a-descriptions-item>
      <a-descriptions-item :label="t('page.settings.tab.info.system.architecture')">
        <a-tag :color="getColor(data?.data.system.architecture ?? '')">
          {{ data?.data.system.architecture }}
        </a-tag>
      </a-descriptions-item>
      <a-descriptions-item :label="t('page.settings.tab.info.system.cores')">
        {{ data?.data.system.cores }}
      </a-descriptions-item>
      <a-descriptions-item :label="t('page.settings.tab.info.system.memory.total')">
        {{ formatFileSize(data?.data.system.memory.total ?? 0) }}
      </a-descriptions-item>
      <a-descriptions-item :label="t('page.settings.tab.info.system.memory.free')">
        <a-space>
          <a-popover
            :content="`${Math.round(((data?.data.system.memory.free ?? 0) / (data?.data.system.memory.total ?? 1)) * 100)}%`"
          >
            <a-progress
              type="circle"
              size="mini"
              :status="memoryProgressBarColor"
              :percent="memoryStatus"
            />
          </a-popover>
          {{ formatFileSize(data?.data.system.memory.free ?? 0) }}
        </a-space>
      </a-descriptions-item>
      <a-descriptions-item
        :label="t('page.settings.tab.info.system.load')"
        v-if="data?.data.system.load > 0"
      >
        {{ data?.data.system.load.toFixed(2) }}
      </a-descriptions-item>
    </a-descriptions>
    <a-divider />
    <!---Network Info-->
    <a-descriptions
      :column="1"
      size="large"
      :title="t('page.settings.tab.info.network')"
      :align="{ label: 'right' }"
    >
      <a-descriptions-item :label="t('page.settings.tab.info.network.internet')">
        Unknown
        <!-- {{
          data?.data.system.network.internet_access
            ? t('page.settings.tab.info.network.yes')
            : t('page.settings.tab.info.network.no')
        }} -->
      </a-descriptions-item>
      <a-descriptions-item :label="t('page.settings.tab.info.network.proxy')">
        {{
          data?.data.system.network.use_proxy
            ? t('page.settings.tab.info.network.yes')
            : t('page.settings.tab.info.network.no')
        }}
      </a-descriptions-item>
      <a-descriptions-item :label="t('page.settings.tab.info.network.reverseProxy')">
        {{
          data?.data.system.network.reverse_proxy
            ? t('page.settings.tab.info.network.yes')
            : t('page.settings.tab.info.network.no')
        }}
      </a-descriptions-item>
      <a-descriptions-item>
        <template #label>
          {{ t('page.settings.tab.info.network.clientIP') }}
          <a-popover :content="t('page.settings.tab.info.network.clientIP.tips')">
            <icon-info-circle />
          </a-popover>
        </template>
        <a-typography-text code copyable>
          {{ data?.data.system.network.client_ip }}
        </a-typography-text>
      </a-descriptions-item>
    </a-descriptions>
    <a-divider />
    <!--Runtime Info-->
    <a-descriptions
      :column="1"
      size="large"
      :title="t('page.settings.tab.info.runtime')"
      :align="{ label: 'right' }"
    >
      <a-descriptions-item :label="t('page.settings.tab.info.runtime.version')">
        {{ data?.data.jvm.version }}
      </a-descriptions-item>
      <a-descriptions-item :label="t('page.settings.tab.info.runtime.vendor')">
        {{ data?.data.jvm.vendor }}
      </a-descriptions-item>
      <a-descriptions-item :label="t('page.settings.tab.info.runtime.runtime')">
        {{ data?.data.jvm.runtime }}
      </a-descriptions-item>
      <a-descriptions-item :label="t('page.settings.tab.info.runtime.bitness')">
        <a-tag :color="data?.data.jvm.bitness === 64 ? 'arcoblue' : 'orange'">
          {{ data?.data.jvm.bitness }}
        </a-tag>
      </a-descriptions-item>
    </a-descriptions>
  </a-space>
</template>
<script setup lang="ts">
import { GetRunningInfo } from '@/service/settings'
import { useEndpointStore } from '@/stores/endpoint'
import { getColor } from '@/utils/color'
import { formatFileSize } from '@/utils/file'
import dayjs from 'dayjs'
import Duration from 'dayjs/plugin/duration'
import RelativeTime from 'dayjs/plugin/relativeTime'
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRequest } from 'vue-request'

dayjs.extend(RelativeTime)
dayjs.extend(Duration)
const { t, d } = useI18n()
const loading = ref(true)
const { data } = useRequest(GetRunningInfo, {
  onSuccess: () => {
    loading.value = false
  }
})
const endpointStore = useEndpointStore()
const memoryStatus = computed(
  () =>
    (data.value?.data.system.memory.free ?? 0) / ((data.value?.data.system.memory.total ?? 1) + 1)
) //+1保证永远不可能100%
const memoryProgressBarColor = computed(() => {
  if (memoryStatus.value > 0.8) return 'danger'
  else if (memoryStatus.value > 0.5) return 'warning'
  else return 'normal'
})
const webuiVersion = __APP_VERSION__
const webuiHash = computed(() => {
  if (__APP_HASH__) return __APP_HASH__
  else {
    return data.value?.data.peerbanhelper.commit_id ?? ''
  }
})
</script>

<style scoped>
button {
  border: none;
  margin: 0;
  padding: 0;
  outline: none;
  border-radius: 0;
  background-color: transparent;
  line-height: normal;
  cursor: pointer;
}

button::after {
  border: none;
}
</style>
