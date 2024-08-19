<template>
  <a-modal
    v-model:visible="showModal"
    title="PeerBanHelper Plus"
    unmountOnClose
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
              {{ t(status?.activated ? 'plus.status.activated' : 'plus.status.inactive') }}
            </a-typography-text>
          </a-descriptions-item>
          <a-descriptions-item v-if="status?.activated" :label="t('plus.key')">
            {{ status?.key }}
          </a-descriptions-item>
          <a-descriptions-item v-if="status?.activated" :label="t('plus.licenseTo')">
            {{ status?.keyData?.licenseTo }}
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
          direction="vertical"
          style="display: flex; flex-direction: column; text-align: center"
          v-if="!status?.activated"
        >
          <a-typography-text>{{ t('plus.begging') }}</a-typography-text>
          <a href="https://afdian.com/a/Ghost_chu?tab=shop" target="_blank">
            <img src="@/assets/support_aifadian.svg" alt="support us!" style="width: 30%" />
          </a>
        </a-space>
        <a-space direction="vertical" size="small" v-if="!status?.activated">
          <a-typography-text type="secondary">{{ t('plus.activeTips') }}</a-typography-text>
          <a-input-search
            button-text="Go!"
            search-button
            @search="submitKey"
            :loading="loading"
          ></a-input-search>
        </a-space>
      </a-space>
      <medal
        :text="
          status?.keyData?.licenseTo
            ? status.keyData.licenseTo.length > 13
              ? 'PBH Plus'
              : status.keyData.licenseTo
            : 'PBH Plus'
        "
        v-if="status?.activated"
        style="margin-right: 40px; margin-left: 40px"
      />
    </a-space>
  </a-modal>
</template>
<script lang="ts" setup>
import { computed, ref } from 'vue'
import { useEndpointStore } from '@/stores/endpoint'
import { useI18n } from 'vue-i18n'
import { Message } from '@arco-design/web-vue'
import medal from '@/components/plusMedal.vue'

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
    Message.info(t('plus.activeSuccess'))
  } catch (e: any) {
    Message.error((e as Error).message)
  } finally {
    loading.value = false
  }
}
</script>
