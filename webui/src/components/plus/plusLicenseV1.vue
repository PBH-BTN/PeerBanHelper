<template>
  <a-split :size="0.65" disabled direction="horizontal" style="width: 100%">
    <template #first>
      <a-descriptions :column="1">
        <template #title>
          <a-space>
            <a-typography-title :heading="6" style="margin-bottom: 0; margin-top: 0">{{
              t('plus.subscription')
            }}</a-typography-title>
            <a-tag size="small">V1</a-tag>
          </a-space>
        </template>
        <a-descriptions-item :label="t('plus.status')">
          <a-typography-text v-if="status.status === LicenseStatus.Valid" type="success">
            {{
              t(
                status.data?.type === LicenseType.Local
                  ? 'plus.status.activated.local'
                  : 'plus.status.activated'
              )
            }}
          </a-typography-text>
          <a-tag v-else-if="status.status === LicenseStatus.Revoked" color="red">{{
            t('plus.status.status.' + status.status)
          }}</a-tag>
          <a-tag v-else color="orange">{{ t('plus.status.status.' + status.status) }}</a-tag>
        </a-descriptions-item>
        <a-descriptions-item :label="t('plus.licenseTo')">
          <a-typography-text
            style="margin-bottom: 0"
            :ellipsis="{
              rows: 1,
              showTooltip: true
            }"
          >
            {{ status?.data?.licenseTo }}
          </a-typography-text>
        </a-descriptions-item>
        <a-descriptions-item :label="t('plus.type')">
          <a-tag :color="status?.data?.type === LicenseType.Local ? 'orange' : 'green'">{{
            t('plus.type.' + status?.data?.type)
          }}</a-tag>
        </a-descriptions-item>
        <a-descriptions-item :label="t('plus.startAt')">
          {{ d(status?.data?.createAt ?? 0, 'long') }}
        </a-descriptions-item>
        <a-descriptions-item :label="t('plus.expireAt')">
          {{ d(status?.data?.expireAt ?? 0, 'long') }}
        </a-descriptions-item>
        <a-descriptions-item v-if="status?.data?.description" :label="t('plus.description')">
          <a-typography-text
            style="margin-bottom: 0"
            :ellipsis="{
              rows: 1,
              showTooltip: true
            }"
          >
            {{ status?.data?.description }}
          </a-typography-text>
        </a-descriptions-item>
      </a-descriptions>
    </template>
    <template #second>
      <div
        v-if="status.data?.type !== LicenseType.Local && status.status !== LicenseStatus.Revoked"
        :style="{
          display: 'flex',
          height: '100%',
          width: '100%',
          filter: status.status !== LicenseStatus.Valid ? 'grayscale(100%)' : 'none'
        }"
      >
        <medal
          :text="
            status?.data?.licenseTo
              ? status.data.licenseTo.length > 13
                ? 'PBH Plus'
                : status.data.licenseTo
              : 'PBH Plus'
          "
          style="margin: auto"
        />
      </div>
      <a-space
        v-else
        direction="vertical"
        style="display: flex; flex-direction: column; text-align: center"
      >
        <a-typography-paragraph style="max-width: 50em; text-align: left">
          {{ t(status?.data?.type === LicenseType.Local ? 'plug.begging.local' : 'plus.begging') }}
        </a-typography-paragraph>
        <a href="https://ifdian.net/a/Ghost_chu" style="text-decoration: none" target="_blank">
          <img src="@/assets/support_aifadian.svg" alt="support us!" style="width: 100%" />
          <!-- <mbd :width="228" :height="83" /> -->
        </a>
      </a-space>
    </template>
  </a-split>
</template>
<script lang="ts" setup>
import { LicenseStatus, LicenseType, type LicenseV1 } from '@/api/model/plus'
import { useI18n } from 'vue-i18n'
import medal from './plusMedal.vue'

const { t, d } = useI18n()

const { license: status } = defineProps<{
  license: LicenseV1
}>()
</script>
