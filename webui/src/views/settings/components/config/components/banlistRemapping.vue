<template>
  <a-space direction="vertical" fill>
    <a-typography-title :heading="3">{{
      t('page.settings.tab.config.banlistRemapping.title')
    }}</a-typography-title>
    <a-typography-text>
      {{ t('page.settings.tab.config.banlistRemapping.description') }}
    </a-typography-text>

    <a-divider orientation="left">{{
      t('page.settings.tab.config.banlistRemapping.ipv4.title')
    }}</a-divider>

    <a-form-item
      v-if="model.ipv4"
      :label="t('page.settings.tab.config.banlistRemapping.ipv4.enabled')"
      field="banlist_remapping.ipv4.enabled"
    >
      <a-switch v-model="model.ipv4.enabled" />
      <template #extra>
        {{ t('page.settings.tab.config.banlistRemapping.ipv4.enabled.tips') }}
      </template>
    </a-form-item>

    <a-form-item
      v-if="model.ipv4"
      :label="t('page.settings.tab.config.banlistRemapping.ipv4.remap_range')"
      field="banlist_remapping.ipv4.remap_range"
    >
      <a-input-number
        v-model="model.ipv4.remap_range"
        style="width: 120px"
        :min="0"
        :max="32"
        :precision="0"
      />
      <template #extra>
        {{ t('page.settings.tab.config.banlistRemapping.ipv4.remap_range.tips') }}
      </template>
    </a-form-item>

    <a-divider orientation="left">{{
      t('page.settings.tab.config.banlistRemapping.ipv6.title')
    }}</a-divider>

    <a-form-item
      v-if="model.ipv6"
      :label="t('page.settings.tab.config.banlistRemapping.ipv6.enabled')"
      field="banlist_remapping.ipv6.enabled"
    >
      <a-switch v-model="model.ipv6.enabled" />
      <template #extra>
        {{ t('page.settings.tab.config.banlistRemapping.ipv6.enabled.tips') }}
      </template>
    </a-form-item>

    <a-form-item
      v-if="model.ipv6"
      :label="t('page.settings.tab.config.banlistRemapping.ipv6.remap_range')"
      field="banlist_remapping.ipv6.remap_range"
    >
      <a-input-number
        v-model="model.ipv6.remap_range"
        style="width: 120px"
        :min="0"
        :max="128"
        :precision="0"
      />
      <template #extra>
        {{ t('page.settings.tab.config.banlistRemapping.ipv6.remap_range.tips') }}
      </template>
    </a-form-item>
  </a-space>
</template>
<script setup lang="ts">
import { type BanlistRemapping } from '@/api/model/config'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const model = defineModel<BanlistRemapping>({ required: true })
</script>
