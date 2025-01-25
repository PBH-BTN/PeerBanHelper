<template>
  <!--骨架屏-->
  <a-space v-if="loading" direction="vertical" fill>
    <a-typography-title :heading="3">
      {{ t('page.settings.tab.info.title') }}
    </a-typography-title>
    <!--Peerbanhelper info-->
    <a-split :size="0.5" disabled>
      <template #first>
        <a-descriptions
          :column="1"
          size="large"
          :title="t('page.settings.tab.info.version')"
          :align="{ label: 'right' }"
        >
          <a-descriptions-item :label="t('page.settings.tab.info.version.current')">
            <a-skeleton-line :rows="1" />
          </a-descriptions-item>
          <a-descriptions-item :label="t('page.settings.tab.info.version.webui')">
            {{ webuiVersion }} (<a-link
              :href="`https://github.com/Ghost-chu/PeerBanHelper/commit/${webuiHash}`"
            >
              {{ webuiHash.substring(0, 8) }} </a-link
            >)
          </a-descriptions-item>
          <a-descriptions-item :label="t('page.settings.tab.info.version.ReleaseType')">
            <a-skeleton-line :rows="1" />
          </a-descriptions-item>
          <a-descriptions-item :label="t('page.settings.tab.info.version.compileTime')">
            <a-skeleton-line :rows="1" />
          </a-descriptions-item>
          <a-descriptions-item :label="t('page.settings.tab.info.version.uptime')">
            <a-skeleton-line :rows="1" />
          </a-descriptions-item>
          <a-descriptions-item :label="t('page.settings.tab.info.version.plus')">
            <a-skeleton-line :rows="1" />
          </a-descriptions-item>
        </a-descriptions>
      </template>
      <template #second>
        <!---System Info-->
        <a-descriptions
          :column="1"
          size="large"
          :title="t('page.settings.tab.info.system')"
          :align="{ label: 'right' }"
        >
          <a-descriptions-item :label="t('page.settings.tab.info.system.os')">
            <a-skeleton-line :rows="1" />
          </a-descriptions-item>
          <a-descriptions-item :label="t('page.settings.tab.info.system.version')">
            <a-skeleton-line :rows="1" />
          </a-descriptions-item>
          <a-descriptions-item :label="t('page.settings.tab.info.system.cores')">
            <a-skeleton-line :rows="1" />
          </a-descriptions-item>
          <a-descriptions-item :label="t('page.settings.tab.info.system.memory.total')">
            <a-skeleton-line :rows="1" />
          </a-descriptions-item>
          <a-descriptions-item :label="t('page.settings.tab.info.system.memory.free')">
            <a-skeleton-line :rows="1" />
          </a-descriptions-item>
          <a-descriptions-item :label="t('page.settings.tab.info.system.load')">
            <a-skeleton-line :rows="1" />
          </a-descriptions-item>
        </a-descriptions>
      </template>
    </a-split>
    <a-divider />
    <!---Network Info-->
    <a-split :size="0.5" disabled>
      <template #first>
        <a-descriptions
          :column="1"
          size="large"
          :title="t('page.settings.tab.info.network')"
          :align="{ label: 'right' }"
        >
          <a-descriptions-item :label="t('page.settings.tab.info.network.internet')">
            <a-skeleton-line :rows="1" />
          </a-descriptions-item>
          <a-descriptions-item :label="t('page.settings.tab.info.network.proxy')">
            <a-skeleton-line :rows="1" />
          </a-descriptions-item>
          <a-descriptions-item :label="t('page.settings.tab.info.network.reverseProxy')">
            <a-skeleton-line :rows="1" />
          </a-descriptions-item>
          <a-descriptions-item>
            <template #label>
              {{ t('page.settings.tab.info.network.clientIP') }}
              <a-tooltip :content="t('page.settings.tab.info.network.clientIP.warning')">
                <icon-info-circle />
              </a-tooltip>
            </template>
            <a-skeleton-line :rows="1" />
          </a-descriptions-item>
        </a-descriptions>
      </template>
      <template #second>
        <!--Runtime Info-->
        <a-descriptions
          :column="1"
          size="large"
          :title="t('page.settings.tab.info.runtime')"
          :align="{ label: 'right' }"
        >
          <a-descriptions-item :label="t('page.settings.tab.info.runtime.version')">
            <a-skeleton-line :rows="1" />
          </a-descriptions-item>
          <a-descriptions-item :label="t('page.settings.tab.info.runtime.vendor')">
            <a-skeleton-line :rows="1" />
          </a-descriptions-item>
          <a-descriptions-item :label="t('page.settings.tab.info.runtime.runtime')">
            <a-skeleton-line :rows="1" />
          </a-descriptions-item>
          <a-descriptions-item :label="t('page.settings.tab.info.runtime.heapMemory')">
            <a-skeleton-line :rows="1" />
          </a-descriptions-item>
        </a-descriptions>
      </template>
    </a-split>
    <a-divider />
    <a-descriptions
      :column="1"
      size="large"
      :title="t('page.settings.tab.info.btn')"
      :align="{ label: 'right' }"
    >
      <a-descriptions-item :label="t('page.settings.tab.info.btn.module')">
        <a-skeleton-line :rows="1" :widths="[300]" />
      </a-descriptions-item>
    </a-descriptions>
    <a-skeleton-line :widths="[400]" />
    <a-skeleton-line :widths="[400]" />
    <a-skeleton-line :widths="[400]" />
    <a-skeleton-line :widths="[400]" />
    <a-skeleton-line :widths="[400]" />
  </a-space>
  <!--End 骨架屏-->
  <a-space v-else direction="vertical" fill>
    <a-space style="display: flex; justify-content: space-between">
      <a-typography-title :heading="3">
        {{ t('page.settings.tab.info.title') }}
      </a-typography-title>
      <a-button type="primary" @click="showLog = true">
        <template #icon> <icon-eye /> </template>
        {{ t('page.settings.tab.info.log.button') }}
      </a-button>
    </a-space>
    <a-split :size="0.5" disabled>
      <template #first>
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
            <a-tooltip
              v-if="
                data?.data.peerbanhelper.commit_id.substring(0, 8) !== webuiHash.substring(0, 8) &&
                data?.data.peerbanhelper.commit_id
              "
              :content="t('page.settings.tab.info.version.webui.versionNotMatch')"
            >
              <a-typography-text type="warning" style="font-size: 1">
                <svg
                  class="arco-icon"
                  t="1733594203567"
                  viewBox="0 0 1024 1024"
                  version="1.1"
                  xmlns="http://www.w3.org/2000/svg"
                  p-id="2704"
                  width="200"
                  height="200"
                >
                  <path
                    d="M951 799.49l-383.89-651a64 64 0 0 0-110.26 0L73 799.49C47.83 842.16 78.59 896 128.12 896h767.77c49.52 0 80.28-53.84 55.11-96.51zM128.12 832l383.62-651a1 1 0 0 1 0.26 0l383.89 651z"
                    fill="currentColor"
                    p-id="2705"
                  ></path>
                  <path
                    d="M470 393.88l8.59 244.51a33.18 33.18 0 0 0 66.31 0.06L554 394a42 42 0 1 0-84-0.08z"
                    fill="currentColor"
                    p-id="2706"
                  ></path>
                  <path
                    d="M512 758.4m-40 0a40 40 0 1 0 80 0 40 40 0 1 0-80 0Z"
                    fill="currentColor"
                    p-id="2707"
                  ></path>
                </svg>
              </a-typography-text>
            </a-tooltip>
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
            <button class="tag-button" @click="endpointStore.emitter.emit('open-plus-modal')">
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
      </template>
      <template #second>
        <!---System Info-->
        <a-descriptions
          :column="1"
          size="large"
          :title="t('page.settings.tab.info.system')"
          :align="{ label: 'right' }"
        >
          <a-descriptions-item :label="t('page.settings.tab.info.system.os')">
            <component :is="osLogo[data?.data.system.os ?? 'Other']"></component>
            {{
              data?.data.system.os === OSType.MacOS
                ? compare(data?.data.system.version, '11.0.0', '>')
                  ? 'macOS'
                  : 'data?.data.system.os'
                : data?.data.system.os
            }}
          </a-descriptions-item>
          <a-descriptions-item :label="t('page.settings.tab.info.system.version')">
            <a-space>
              {{ data?.data.system.version }}
              <a-tag color="arcoblue">
                {{ data?.data.system.architecture }}
              </a-tag>
            </a-space>
          </a-descriptions-item>
          <a-descriptions-item :label="t('page.settings.tab.info.system.cores')">
            {{ data?.data.system.cores }}
          </a-descriptions-item>
          <a-descriptions-item :label="t('page.settings.tab.info.system.memory.total')">
            {{ formatFileSize(data?.data.system.memory.total ?? 0) }}
          </a-descriptions-item>
          <a-descriptions-item :label="t('page.settings.tab.info.system.memory.free')">
            <a-space style="display: flex; align-items: center">
              <a-tooltip
                :content="
                  `${Math.round((1 - (data?.data.system.memory.free ?? 0) / (data?.data.system.memory.total ?? 1)) * 100)}% ` +
                  t('page.settings.tab.info.system.memory.used')
                "
              >
                <a-progress
                  type="circle"
                  size="mini"
                  :status="memoryProgressBarColor"
                  :percent="memoryStatus"
                />
              </a-tooltip>
              {{ formatFileSize(data?.data.system.memory.free ?? 0) }} &nbsp;
              {{ t('page.settings.tab.info.system.memory.available') }}
            </a-space>
          </a-descriptions-item>
          <a-descriptions-item
            v-if="(data?.data.system.load ?? 0) > 0"
            :label="t('page.settings.tab.info.system.load')"
          >
            {{ data?.data.system.load.toFixed(2) }}
          </a-descriptions-item>
        </a-descriptions>
      </template>
    </a-split>
    <a-divider />
    <a-split :size="0.5" disabled>
      <template #first>
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
              <a-tooltip :content="t('page.settings.tab.info.network.clientIP.tips')">
                <icon-info-circle />
              </a-tooltip>
            </template>
            <a-typography-text code copyable>
              {{ data?.data.system.network.client_ip }}
            </a-typography-text>
            <a-tooltip
              v-if="isClientIpLocal && data?.data.system.network.reverse_proxy"
              :content="t('page.settings.tab.info.network.clientIP.warning')"
            >
              <a-typography-text type="warning">
                <icon-exclamation-polygon-fill />
              </a-typography-text>
            </a-tooltip>
          </a-descriptions-item>
        </a-descriptions>
      </template>
      <template #second>
        <!--Runtime Info-->
        <a-descriptions
          :column="1"
          size="large"
          :title="t('page.settings.tab.info.runtime')"
          :align="{ label: 'right' }"
        >
          <a-descriptions-item :label="t('page.settings.tab.info.runtime.version')">
            <a-space
              >{{ data?.data.jvm.version
              }}<a-tag :color="data?.data.jvm.bitness === 64 ? 'arcoblue' : 'orange'">
                {{ data?.data.jvm.bitness }}-Bit
              </a-tag></a-space
            >
          </a-descriptions-item>
          <a-descriptions-item :label="t('page.settings.tab.info.runtime.vendor')">
            {{ data?.data.jvm.vendor }}
          </a-descriptions-item>
          <a-descriptions-item :label="t('page.settings.tab.info.runtime.runtime')">
            {{ data?.data.jvm.runtime }}
          </a-descriptions-item>
          <a-descriptions-item :label="t('page.settings.tab.info.runtime.heapMemory')">
            <multiClick :required="5" :time-limit="3000" @multi-click="downloadHeap">
              <a-space style="display: flex; align-items: center; color: var(--color-text-1)">
                <a-tooltip
                  :content="
                    `${Math.round((1 - (data?.data.jvm.memory.heap.free ?? 0) / (data?.data.jvm.memory.heap.max ?? 1)) * 100)}% ` +
                    t('page.settings.tab.info.system.memory.used')
                  "
                >
                  <a-progress
                    type="circle"
                    size="mini"
                    :status="heapMemoryProgressBarColor"
                    :percent="heapMemoryStatus"
                  />
                </a-tooltip>
                {{ formatFileSize(data?.data.jvm.memory.heap.free ?? 0) }} &nbsp;
                {{ t('page.settings.tab.info.system.memory.available') }}
              </a-space>
            </multiClick>
          </a-descriptions-item>
        </a-descriptions>
      </template>
    </a-split>
    <a-divider />
    <!--BTN Info-->
    <a-descriptions
      :column="1"
      size="large"
      :title="t('page.settings.tab.info.btn')"
      :align="{ label: 'right' }"
    >
      <a-descriptions-item :label="t('page.settings.tab.info.btn.module')">
        <a-skeleton-line v-if="btnLoading" :rows="1" />
        <div v-else>
          <a-typography-text v-if="!btnEnable?.data">{{
            t('page.settings.tab.info.btn.disable')
          }}</a-typography-text>
          <a-typography-text v-else>{{ t('page.settings.tab.info.btn.enable') }}</a-typography-text>
        </div>
      </a-descriptions-item>
      <a-descriptions-item v-if="btnEnable?.data" :label="t('page.settings.tab.info.btn.status')">
        <a-skeleton-line v-if="btnStatusLoading.value" :rows="1" />
        <a-space v-else>
          <a-tooltip :content="btnStatus?.data.configResult">
            <a-typography-text :type="btnStatus?.data.configSuccess ? 'success' : 'warning'">
              {{
                btnStatus?.data.configSuccess
                  ? t('page.settings.tab.info.btn.status.success')
                  : t('page.settings.tab.info.btn.status.fail')
              }}
            </a-typography-text>
          </a-tooltip>
          <a-tooltip
            v-if="!btnStatus?.data.configSuccess"
            :content="t('page.settings.tab.info.btn.status.fail.tips')"
          >
            <a-button
              status="warning"
              type="text"
              shape="circle"
              href="https://docs.pbh-btn.com/en/docs/btn/connect/"
              target="_blank"
            >
              <template #icon>
                <icon-info-circle />
              </template>
            </a-button>
          </a-tooltip>
        </a-space>
      </a-descriptions-item>
      <a-descriptions-item
        v-if="btnEnable?.data"
        :label="t('page.settings.tab.info.btn.status.configUrl')"
      >
        <a-skeleton-line v-if="btnStatusLoading.value" :rows="1" />
        <a-typography-text v-else code copyable>
          {{ btnStatus?.data.configUrl }}
        </a-typography-text>
      </a-descriptions-item>
      <a-descriptions-item v-if="btnEnable?.data" label="App ID">
        <a-skeleton-line v-if="btnStatusLoading.value" :rows="1" />
        <a-typography-text v-else code copyable>
          {{ btnStatus?.data.appId }}
        </a-typography-text>
      </a-descriptions-item>
      <a-descriptions-item v-if="btnEnable?.data" label="App Secret">
        <a-skeleton-line v-if="btnStatusLoading.value" :rows="1" />
        <div v-else>
          {{ btnStatus?.data.appSecret }}
        </div>
      </a-descriptions-item>
      <a-descriptions-item
        v-if="btnEnable?.data"
        :label="t('page.settings.tab.info.btn.abilities')"
      >
        <a-skeleton-line v-if="btnStatusLoading.value" :rows="1" />
        <a-space v-else size="mini">
          {{
            t('page.settings.tab.info.btn.abilities.enable', {
              number: btnStatus?.data.abilities.length ?? 0
            })
          }}
          <a-button
            shape="circle"
            type="text"
            @click="btnAbilityList?.showModal(btnStatus?.data.abilities ?? [])"
          >
            <template #icon>
              <icon-eye />
            </template>
          </a-button>
        </a-space>
      </a-descriptions-item>
    </a-descriptions>
  </a-space>
  <btnAbilitiesModal ref="btnAbilityList" />
  <a-modal
    v-model:visible="showLog"
    :title="t('page.settings.tab.info.log.title')"
    unmount-on-close
    hide-cancel
    :mask-closable="false"
    width="auto"
  >
    <logViewer />
  </a-modal>
</template>
<script setup lang="ts">
import { OSType } from '@/api/model/status'
import { genIconComponent } from '@/components/iconFont'
import multiClick from '@/components/multiClick.vue'
import {
  CheckModuleEnable,
  GetBtnStatus,
  GetHeapDumpFile,
  GetRunningInfo
} from '@/service/settings'
import { useEndpointStore } from '@/stores/endpoint'
import { getColor } from '@/utils/color'
import { formatFileSize } from '@/utils/file'
import { compare } from 'compare-versions'
import dayjs from 'dayjs'
import Duration from 'dayjs/plugin/duration'
import RelativeTime from 'dayjs/plugin/relativeTime'
import { isInSubnet } from 'is-in-subnet'
import { isIP } from 'is-ip'
import { computed, defineAsyncComponent, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRequest } from 'vue-request'

import { Message } from '@arco-design/web-vue'
import btnAbilitiesModal from './components/btnAbilitiesModal.vue'

dayjs.extend(RelativeTime)
dayjs.extend(Duration)
const logViewer = defineAsyncComponent(() => import('./components/logViewer.vue'))
const { t, d } = useI18n()
const loading = ref(true)
const btnLoading = ref(true)
const { data } = useRequest(GetRunningInfo, {
  onSuccess: () => {
    loading.value = false
  }
})
const endpointStore = useEndpointStore()
const memoryStatus = computed(
  () =>
    1 -
    (data.value?.data.system.memory.free ?? 0) / ((data.value?.data.system.memory.total ?? 1) + 1)
) //+1保证永远不可能100%
const heapMemoryStatus = computed(
  () =>
    1 -
    (data.value?.data.jvm.memory.heap.free ?? 0) / ((data.value?.data.jvm.memory.heap.max ?? 1) + 1)
) //+1保证永远不可能100%
const memoryProgressBarColor = computed(() => {
  if (memoryStatus.value > 0.85) return 'danger'
  else if (memoryStatus.value > 0.7) return 'warning'
  else return 'normal'
})
const heapMemoryProgressBarColor = computed(() => {
  if (heapMemoryStatus.value > 0.85) return 'danger'
  else if (heapMemoryStatus.value > 0.7) return 'warning'
  else return 'normal'
})
const webuiVersion = __APP_VERSION__
const webuiHash = computed(() => {
  if (__APP_HASH__) return __APP_HASH__
  else {
    return data.value?.data.peerbanhelper.commit_id ?? ''
  }
})

const { data: btnEnable } = useRequest(CheckModuleEnable, {
  defaultParams: ['btn'],
  onSuccess: () => (btnLoading.value = false)
})

const { data: btnStatus, loading: statusLoading } = useRequest(GetBtnStatus, {
  ready: computed(() => btnEnable.value?.data ?? false)
})

const btnStatusLoading = computed(() => statusLoading || btnLoading.value)

const btnAbilityList = ref<InstanceType<typeof btnAbilitiesModal>>()

const isClientIpLocal = computed(() => {
  if (data.value?.data.system.network.client_ip && isIP(data.value.data.system.network.client_ip)) {
    if (
      data.value.data.system.network.client_ip === '::1' || // localhost v6
      isInSubnet(data.value.data.system.network.client_ip, '127.0.0.1/8') || // localhost
      isInSubnet(data.value.data.system.network.client_ip, '172.17.0.0/16') ||
      isInSubnet(data.value.data.system.network.client_ip, '172.18.0.0/16') // docker0 ip
    ) {
      return true
    }
  }
  return false
})

const osLogo = {
  Windows: genIconComponent('icon-Windows'),
  Linux: genIconComponent('icon-linux'),
  FreeBSD: genIconComponent('icon-freebsd'),
  'Mac OS X': genIconComponent('icon-mac-os'),
  Solaris: genIconComponent('icon-solaris'),
  Other: genIconComponent('icon-other')
}

const showLog = ref(false)

const downloadHeap = () => {
  Message.info(t('page.settings.tab.info.downloadHeap'))
  setTimeout(async () => {
    const url = await GetHeapDumpFile()
    const a = document.createElement('a')
    a.href = url.toString()
    a.target = '_blank'
    a.download = 'heapdump.hprof.gz'
    a.click()
  }, 1000)
}
</script>

<style scoped>
.tag-button {
  border: none;
  margin: 0;
  padding: 0;
  outline: none;
  border-radius: 0;
  background-color: transparent;
  line-height: normal;
  cursor: pointer;
}

.tag-button::after {
  border: none;
}
</style>
