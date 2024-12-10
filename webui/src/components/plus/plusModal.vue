<template>
  <a-modal
    v-model:visible="showModal"
    title="PeerBanHelper Plus"
    unmount-on-close
    hide-cancel
    width="auto"
    draggable
  >
    <a-space>
      <a-space
        direction="vertical"
        fill
        style="padding-left: 30px; padding-right: 30px"
        size="large"
      >
        <a-descriptions :title="t('plus.subscription')" :column="1">
          <a-descriptions-item :label="t('plus.status')">
            <a-typography-text :type="status?.activated ? 'success' : ''">
              {{
                t(
                  status?.activated
                    ? status.keyData?.type === LicenseType.LicenseLocal
                      ? 'plus.status.activated.local'
                      : 'plus.status.activated'
                    : 'plus.status.inactive'
                )
              }}
            </a-typography-text>
          </a-descriptions-item>
          <a-descriptions-item v-if="status?.activated" :label="t('plus.key')">
            {{ status?.key }}
          </a-descriptions-item>
          <a-descriptions-item v-if="status?.activated" :label="t('plus.licenseTo')">
            {{ status?.keyData?.licenseTo }}
          </a-descriptions-item>
          <a-descriptions-item v-if="status?.activated" :label="t('plus.type')">
            <a-tag
              :color="status?.keyData?.type === LicenseType.LicenseLocal ? 'orange' : 'green'"
              >{{ t('plus.type.' + status?.keyData?.type) }}</a-tag
            >
          </a-descriptions-item>
          <a-descriptions-item v-if="status?.activated" :label="t('plus.startAt')">
            {{ d(status?.keyData?.createAt ?? 0, 'long') }}
          </a-descriptions-item>
          <a-descriptions-item v-if="status?.activated" :label="t('plus.expireAt')">
            {{ d(status?.keyData?.expireAt ?? 0, 'long') }}
          </a-descriptions-item>
          <a-descriptions-item
            v-if="status?.activated && status?.keyData?.description"
            :label="t('plus.description')"
          >
            {{ status?.keyData?.description }}
          </a-descriptions-item>
        </a-descriptions>
        <a-space
          v-if="!status?.activated || status?.keyData?.type === LicenseType.LicenseLocal"
          direction="vertical"
          style="display: flex; flex-direction: column; text-align: center"
        >
          <a-typography-paragraph style="max-width: 50em; text-align: left">
            {{
              t(
                status?.keyData?.type === LicenseType.LicenseLocal
                  ? 'plug.begging.local'
                  : 'plus.begging'
              )
            }}
          </a-typography-paragraph>
          <a href="https://afdian.com/a/Ghost_chu?tab=shop" target="_blank">
            <img src="@/assets/support_aifadian.svg" alt="support us!" style="width: 100%" />
          </a>
        </a-space>
        <a-split
          v-if="status?.keyData?.type !== LicenseType.LicenseAifadian"
          :size="0.5"
          disabled
          style="width: 100%"
        >
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
          <template v-if="status?.keyData?.type !== LicenseType.LicenseLocal" #second>
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
      <medal
        v-if="status?.activated && status.keyData?.type !== LicenseType.LicenseLocal"
        :text="
          status?.keyData?.licenseTo
            ? status.keyData.licenseTo.length > 13
              ? 'PBH Plus'
              : status.keyData.licenseTo
            : 'PBH Plus'
        "
        style="margin-right: 40px; margin-left: 40px"
      />
    </a-space>
  </a-modal>
  <tryModal ref="modalCountDown" />
</template>
<script lang="ts" setup>
import { useEndpointStore } from '@/stores/endpoint'
import { Message } from '@arco-design/web-vue'
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import medal from './plusMedal.vue'
import tryModal from './plusTryModal.vue'

import { LicenseType } from '@/api/model/manifest'
const { t, d } = useI18n()
const endpointStore = useEndpointStore()
const showModal = ref(false)

defineExpose({
  showModal: () => {
    showModal.value = true
  }
})
const status = computed(() => endpointStore.plusStatus)
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
</script>
