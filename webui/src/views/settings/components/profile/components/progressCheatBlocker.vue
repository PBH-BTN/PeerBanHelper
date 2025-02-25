<template>
  <a-space direction="vertical" fill>
    <a-alert>{{ t('page.settings.tab.profile.module.progressCheatBlocker.tips') }}</a-alert>
    <a-form-item :label="t('page.settings.tab.profile.module.enable')" field="model.enabled">
      <a-switch v-model="model.enabled" />
    </a-form-item>
    <a-form-item
      :label="t('page.settings.tab.profile.module.progressCheatBlocker.minSize')"
      field="model.minimum_size"
      :tooltip="t('page.settings.tab.profile.module.progressCheatBlocker.minSize.tips')"
    >
      <a-input-number v-model="model.minimum_size" style="width: 200px">
        <template #suffix> Bytes </template>
      </a-input-number>
      <template #extra> ≈ {{ formatFileSize(model.minimum_size) }} </template>
    </a-form-item>
    <a-form-item
      :label="t('page.settings.tab.profile.module.progressCheatBlocker.maxDifference')"
      field="model.maximum_difference"
      :tooltip="t('page.settings.tab.profile.module.progressCheatBlocker.maxDifference.tips')"
    >
      <a-space>
        <a-slider
          v-model="maximum_difference"
          style="width: 250px"
          :step="1"
          :min="0"
          :max="100"
          :format-tooltip="(value: number) => `${value}%`"
        />
        <br />{{ maximum_difference }}%
      </a-space>
    </a-form-item>
    <a-form-item
      :label="t('page.settings.tab.profile.module.progressCheatBlocker.progressRewindDetection')"
    >
      <a-switch v-model="rewindDetectionEnabled" />
    </a-form-item>
    <a-form-item
      v-if="model.rewind_maximum_difference !== -1"
      :label="t('page.settings.tab.profile.module.progressCheatBlocker.rewindMaxDifference')"
      field="model.rewind_maximum_difference"
      :tooltip="t('page.settings.tab.profile.module.progressCheatBlocker.rewindMaxDifference.tips')"
    >
      <a-space>
        <a-slider
          v-model="rewind_maximum_difference"
          style="width: 250px"
          :step="1"
          :min="0"
          :max="100"
          :format-tooltip="(value: number) => `${value}%`"
        />
        <br />{{ rewind_maximum_difference }}%
      </a-space>
    </a-form-item>
    <a-form-item
      :label="t('page.settings.tab.profile.module.progressCheatBlocker.block_excessive_clients')"
      field="model.block_excessive_clients"
      :tooltip="
        t('page.settings.tab.profile.module.progressCheatBlocker.block_excessive_clients.tips')
      "
    >
      <a-switch v-model="model.block_excessive_clients"></a-switch>
    </a-form-item>
    <a-form-item
      :label="t('page.settings.tab.profile.module.progressCheatBlocker.excessive_threshold')"
      field="model.excessive_threshold"
      :tooltip="t('page.settings.tab.profile.module.progressCheatBlocker.excessive_threshold.tips')"
    >
      <a-input-number v-model="model.excessive_threshold" style="width: 100px"></a-input-number>
    </a-form-item>
    <a-form-item
      :label="t('page.settings.tab.profile.module.progressCheatBlocker.ipv4prefixlength')"
      field="model.ipv4_prefix_length"
      :tooltip="t('page.settings.tab.profile.module.progressCheatBlocker.ipprefixLength.tips')"
    >
      <a-input-number
        v-model="model.ipv4_prefix_length"
        style="width: 100px"
        :min="0"
        :max="32"
      ></a-input-number>
    </a-form-item>
    <a-form-item
      :label="t('page.settings.tab.profile.module.progressCheatBlocker.ipv6prefixlength')"
      field="model.ipv6_prefix_length"
      :tooltip="t('page.settings.tab.profile.module.progressCheatBlocker.ipprefixLength.tips')"
    >
      <a-input-number
        v-model="model.ipv6_prefix_length"
        style="width: 100px"
        :min="0"
        :max="128"
      ></a-input-number>
    </a-form-item>
    <a-form-item
      :label="t('page.settings.tab.profile.module.progressCheatBlocker.useGlobalBanTime')"
      field="model.ban_duration"
    >
      <a-space>
        <a-switch v-model="useGlobalBanTime" />
        <a-input-number
          v-if="!useGlobalBanTime"
          v-model.number="model.ban_duration as number"
          :min="1"
        >
          <template #suffix> {{ t('page.settings.tab.profile.unit.ms') }} </template>
        </a-input-number>
      </a-space>
      <template v-if="model.ban_duration !== 'default'" #extra>
        ={{ formatMilliseconds(model.ban_duration) }}
      </template>
    </a-form-item>
    <a-form-item
      :label="t('page.settings.tab.profile.module.progressCheatBlocker.enablePersist')"
      field="model.enable_persist"
    >
      <a-switch v-model="model.enable_persist"></a-switch>
      <template v-if="model.enable_persist" #extra>
        <a-typography-text type="danger">{{
          t('page.settings.tab.profile.module.progressCheatBlocker.enablePersist.tips')
        }}</a-typography-text></template
      >
    </a-form-item>
    <a-form-item
      v-if="model.enable_persist"
      :label="t('page.settings.tab.profile.module.progressCheatBlocker.persistDuration')"
      field="model.persist_duration"
    >
      <a-input-number v-model="model.persist_duration" style="width: 200px">
        <template #suffix> {{ t('page.settings.tab.profile.unit.ms') }} </template>
      </a-input-number>
      <template #extra> ={{ formatMilliseconds(model.persist_duration) }} </template>
    </a-form-item>
    <a-form-item
      :label="t('page.settings.tab.profile.module.progressCheatBlocker.maxWaitDuration')"
      :tooltip="t('page.settings.tab.profile.module.progressCheatBlocker.maxWaitDuration.tips')"
      field="model.max_wait_duration"
    >
      <a-input-number v-model="model.max_wait_duration" style="width: 200px">
        <template #suffix> {{ t('page.settings.tab.profile.unit.ms') }} </template>
      </a-input-number>
      <template #extra> ={{ formatMilliseconds(model.max_wait_duration) }} </template>
    </a-form-item>
    <a-form-item
      :label="t('page.settings.tab.profile.module.progressCheatBlocker.enableFastPCBTest')"
      :tooltip="t('page.settings.tab.profile.module.progressCheatBlocker.enableFastPCBTest.tips')"
    >
      <a-switch v-model="fastPCBTestEnabled" />
    </a-form-item>
    <a-form-item
      v-if="model.fast_pcb_test_percentage !== -1"
      :label="t('page.settings.tab.profile.module.progressCheatBlocker.fastPCBTestPercentage')"
      field="model.fast_pcb_test_percentage"
    >
      <a-space>
        <a-slider
          v-model="fast_pcb_test_percentage"
          style="width: 250px"
          :step="1"
          :min="0"
          :max="100"
          :format-tooltip="(value: number) => `${value}%`"
        />
        <br />{{ fast_pcb_test_percentage }}%
      </a-space>
    </a-form-item>
  </a-space>
</template>
<script setup lang="ts">
import type { ProgressCheatBlocker } from '@/api/model/profile'
import { formatFileSize } from '@/utils/file'
import { formatMilliseconds } from '@/utils/time'
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const model = defineModel<ProgressCheatBlocker>({ required: true })

const useGlobalBanTime = computed({
  get: () => model.value.ban_duration === 'default',
  set: (value: boolean) => {
    model.value.ban_duration = value ? 'default' : 259200000
  }
})
const fastPCBTestEnabled = computed({
  get: () => model.value.fast_pcb_test_percentage !== -1,
  set: (value) => {
    model.value.fast_pcb_test_percentage = value ? 10 : -1
  }
})
const rewindDetectionEnabled = computed({
  get: () => model.value.rewind_maximum_difference !== -1,
  set: (value) => {
    model.value.rewind_maximum_difference = value ? 7 : -1
  }
})

const maximum_difference = computed({
  get: () => Math.round(model.value.maximum_difference * 100),
  set: (value) => {
    model.value.maximum_difference = value / 100
  }
})
const rewind_maximum_difference = computed({
  get: () => Math.round(model.value.rewind_maximum_difference * 100),
  set: (value) => {
    model.value.rewind_maximum_difference = value / 100
  }
})
const fast_pcb_test_percentage = computed({
  get: () => Math.round(model.value.fast_pcb_test_percentage * 100),
  set: (value) => {
    model.value.fast_pcb_test_percentage = value / 100
  }
})
</script>
