<template>
  <a-modal
    v-model:visible="showModal"
    title="PeerBanHelper Plus"
    unmount-on-close
    hide-cancel
    width="48rem"
    draggable
  >
    <a-space v-if="!pbhPlusActivited">
      <a-space
        direction="vertical"
        fill
        style="padding-left: 30px; padding-right: 30px"
        size="large"
      >
        <a-descriptions :title="t('plus.subscription')" :column="1">
          <a-descriptions-item :label="t('plus.status')">
            <a-typography-text>
              {{ t('plus.status.inactive') }}
            </a-typography-text>
          </a-descriptions-item>
        </a-descriptions>
        <a-space
          direction="vertical"
          style="display: flex; flex-direction: column; text-align: center"
        >
          <a-typography-paragraph style="max-width: 50em; text-align: left">
            {{ t('plus.begging') }}
          </a-typography-paragraph>
          <a href="https://mbd.pub/o/ghostchu" style="text-decoration: none" target="_blank">
            <!-- <img src="@/assets/support_aifadian.svg" alt="support us!" style="width: 100%" /> -->
            <mbd :width="228" :height="83" />
          </a>
        </a-space>
        <a-split :size="0.5" disabled style="width: 100%">
          <template #first>
            <a-space direction="vertical" size="small" style="max-width: 20rem; height: 6rem">
              <a-typography-text type="secondary">{{ t('plus.activeTips') }}</a-typography-text>
              <a-input-search
                button-text="Go!"
                search-button
                :loading="loading"
                @search="submitKey"
              ></a-input-search>
            </a-space>
          </template>
          <template #second>
            <a-space style="display: flex; justify-content: center; align-items: center">
              <a-typography-text type="secondary">{{ t('plus.or') }}</a-typography-text>
              &nbsp;
              <a-button :loading="loading" @click="handleTry">
                <template #icon><icon-face-frown-fill /></template>{{ t('plus.try') }}
              </a-button>
            </a-space>
          </template>
        </a-split>
      </a-space>
      <tryModal ref="modalCountDown" />
    </a-space>
    <a-space v-else direction="vertical" fill>
      <a-tabs
        v-model:active-key="currentActiveKey"
        position="left"
        scroll-position="auto"
        style="height: 18rem"
      >
        <a-tab-pane
          v-for="(license, index) in endpointStore.plusStatus?.licenses"
          :key="index"
          :title="`License ${index + 1}`"
        >
          <LicenseV1 v-if="license.version === 1" :license="license" />
          <LicenseV2 v-else-if="license.version === 2" :license="license" />
        </a-tab-pane>
      </a-tabs>
      <div style="width: 90%; display: flex; justify-content: space-between; margin: 0 auto">
        <a-trigger :popup-translate="[0, -10]" position="top" trigger="click" show-arrow>
          <template #content>
            <a-card style="box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1)">
              <a-space direction="vertical" size="small" style="max-width: 20rem">
                <a-typography-text type="secondary">{{ t('plus.addLicense') }}</a-typography-text>
                <a-input-search
                  button-text="Go!"
                  search-button
                  :loading="loading"
                  @search="submitKey"
                ></a-input-search>
              </a-space>
            </a-card>
          </template>
          <a-button type="primary">
            <template #icon>
              <icon-plus />
            </template>
          </a-button>
        </a-trigger>
        <a-popconfirm
          :content="t('plus.license.delete.confirm')"
          type="warning"
          @before-ok="
            () =>
              handleDeleteLicense(endpointStore.plusStatus?.licenses[currentActiveKey]?.licenseId)
          "
        >
          <a-button>
            <template #icon>
              <icon-delete />
            </template>
            <!-- Use the default slot to avoid extra spaces -->
            <template #default>{{ t('plus.license.delete') }}</template>
          </a-button>
        </a-popconfirm>
      </div>
    </a-space>
  </a-modal>
</template>
<script lang="ts" setup>
import { deletePHBPlusKey } from '@/service/plus'
import { useEndpointStore } from '@/stores/endpoint'
import { Message } from '@arco-design/web-vue'
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import mbd from './mbdBadge.vue'
import LicenseV1 from './plusLicenseV1.vue'
import LicenseV2 from './plusLicenseV2.vue'
import tryModal from './plusTryModal.vue'
const endpointStore = useEndpointStore()

const { t } = useI18n()
const pbhPlusActivited = computed(
  () =>
    endpointStore.plusStatus?.enabledFeatures?.includes('basic') &&
    endpointStore.plusStatus?.enabledFeatures?.includes('paid')
)

const showModal = ref(false)
const currentActiveKey = ref(0)

defineExpose({
  showModal: () => {
    showModal.value = true
  }
})

const loading = ref(false)
const submitKey = async (key: string) => {
  loading.value = true
  try {
    await endpointStore.setPlusKey(key)
    Message.success({ content: t('plus.activeSuccess'), resetOnHover: true })
  } catch (e: unknown) {
    if (e instanceof Error) Message.error({ content: e.message, resetOnHover: true })
  } finally {
    loading.value = false
  }
}
const modalCountDown = ref<typeof tryModal>()
const handleTry = async () => {
  modalCountDown.value?.try()
}

const handleDeleteLicense = async (licenseId: string | undefined) => {
  if (!licenseId) {
    endpointStore.getPlusStatus()
    return true
  }
  try {
    const res = await deletePHBPlusKey(licenseId)
    Message.success(res.message)
    endpointStore.getPlusStatus()
    return true
  } catch (e: unknown) {
    if (e instanceof Error) Message.error({ content: e.message, resetOnHover: true })
    return false
  }
}
</script>
