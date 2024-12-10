<template>
  <a-row align="center" justify="center">
    <a-col class="footer">
      <a-descriptions :column="{ xs: 1, md: 3, lg: 4 }">
        <a-descriptions-item label="Backend Version">
          <a-space>
            <a
              v-if="hasNewVersion"
              :href="latestVersion?.url"
              :title="t('footer.newVersionTips', { version: latestVersion?.tagName })"
            >
              <a-badge dot :count="1" :offset="[8, -1]">
                {{ serverVersion?.version }}
              </a-badge>
            </a>
            <div v-else>{{ serverVersion?.version }}</div>
            <br />
            <a-button
              v-if="
                endpointStore.plusStatus?.activated &&
                endpointStore.plusStatus.keyData?.type !== LicenseType.LicenseLocal
              "
              class="plus-button"
              type="outline"
              size="mini"
              @click="plusInfo?.showModal()"
            >
              <icon-heart-fill />
              &nbsp;PBH Plus
            </a-button>
            <div>
              (<a-link
                :href="`https://github.com/Ghost-chu/PeerBanHelper/commit/${serverVersion?.commit}`"
                >{{ serverVersion?.commit.substring(0, 8) }} </a-link
              >)
            </div>
          </a-space>
        </a-descriptions-item>
        <a-descriptions-item label="WebUI Version">
          <a-space>
            {{ version }}
            <div v-if="hash">
              (<a-link :href="`https://github.com/PBH-BTN/PeerBanHelper/tree/${hash}/webui`">
                {{ hash.substring(0, 8) }} </a-link
              >)
            </div></a-space
          >
        </a-descriptions-item>
      </a-descriptions>
    </a-col>
  </a-row>
  <PlusModal ref="plusInfo" />
  <ChangelogViewer ref="changeLogViewer" />
</template>

<script setup lang="ts">
import { LicenseType } from '@/api/model/manifest'
import { useEndpointStore } from '@/stores/endpoint'
import { Button, Notification } from '@arco-design/web-vue'
import { IconHistory } from '@arco-design/web-vue/es/icon'
import { RequestError } from '@octokit/request-error'
import { compare } from 'compare-versions'
import { computed, h, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import ChangelogViewer from './changelogViewer.vue'
import PlusModal from './plus/plusModal.vue'

const { t } = useI18n()
const version = __APP_VERSION__
const hash = __APP_HASH__
const endpointStore = useEndpointStore()
const serverVersion = computed(() => endpointStore.serverManifest?.version)
const latestVersion = computed(() => endpointStore.latestVersion)
const hasNewVersion = computed(() => {
  return compare(
    endpointStore.latestVersion?.tagName ?? '1.0',
    endpointStore.serverManifest?.version.version ?? '1.0',
    '>'
  )
})
const changeLogViewer = ref<typeof ChangelogViewer>()
watch(hasNewVersion, () => {
  if (hasNewVersion.value) {
    Notification.info({
      title: t('footer.newVersion'),
      content: t('footer.newVersion.body', { version: latestVersion.value?.tagName }),
      footer: () =>
        h(
          Button,
          {
            onClick: () => changeLogViewer.value?.showModal(),
            type: 'primary'
          },
          {
            default: () => t('footer.newVersion.updateNow'),
            icon: () => h(IconHistory)
          }
        ),
      duration: 5000,
      closable: true
    })
  }
})

watch(
  () => endpointStore.checkUpgradeError,
  (error) => {
    if (error instanceof RequestError) {
      if (error?.message.includes('limit')) {
        Notification.error({
          title: t('settings.accessToken.error'),
          content: t('settings.accessToken.error.limit'),
          footer: () =>
            h(
              Button,
              {
                type: 'primary',
                onClick: () => endpointStore.emitter.emit('open-settings-modal')
              },
              () => t('settings.open')
            )
        })
      }
    } else
      Notification.error({
        title: t('settings.accessToken.error'),
        content: error?.message ?? ''
      })
  }
)

const plusInfo = ref<InstanceType<typeof PlusModal>>()
endpointStore.emitter.on('open-plus-modal', () => {
  plusInfo.value?.showModal()
})
</script>

<style scoped>
.footer {
  display: flex;
  justify-content: center;
}

.plus-button {
  color: #d9659b;
  border: #d9659b solid 1px;
}

.plus-button:hover {
  color: #d9659b;
  border: #d9659b solid 1px;
  box-shadow: 0 0 10px #d9659b;
}
body[arco-theme='dark'] .plus-button {
  color: #d0b15e;
  border: #c7aa68 solid 1px;
}
body[arco-theme='dark'] .plus-button:hover {
  color: #d0b15e;
  border: #c7aa68 solid 1px;
  box-shadow: 0 0 10px #c7aa68;
}
</style>
