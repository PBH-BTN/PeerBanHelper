<template>
  <div id="#top" style="position: relative">
    <a-form :model="form">
      <a-space direction="vertical" fill style="width: 100%">
        <a-typography-title id="id" :heading="3">{{
          t('page.settings.tab.config.base.title')
        }}</a-typography-title>
        <a-form-item
          :label="t('page.settings.tab.config.form.checkInterval')"
          field="form.check_interval"
          required
        >
          <a-input-number v-model="form.check_interval" style="width: 200px">
            <template #suffix> {{ t('page.settings.tab.config.unit.ms') }} </template>
          </a-input-number>
        </a-form-item>
        <a-form-item
          :label="t('page.settings.tab.config.form.banDuration')"
          field="form.ban_duration"
          required
        >
          <a-input-number v-model="form.ban_duration" style="width: 200px">
            <template #suffix> {{ t('page.settings.tab.config.unit.ms') }} </template>
          </a-input-number>
          <template #extra> ={{ formatMilliseconds(form.ban_duration) }} </template>
        </a-form-item>
        <formArray
          v-model="form.ignore_peers_from_addresses"
          :label="t('page.settings.tab.config.form.ingoreAddress')"
          :tooltip="t('page.settings.tab.config.form.ingoreAddress.tooltip')"
          :virtual-list-props="{ threshold: 8, height: 500 }"
        />
      </a-space>
      <a-divider />
      <a-space v-if="form.module" direction="vertical" fill style="width: 90%">
        <a-typography-title id="module" :heading="3">{{
          t('page.settings.tab.config.module.title')
        }}</a-typography-title>
        <a-collapse destroy-on-hide>
          <a-collapse-item key="1" :header="t('page.settings.tab.config.module.peerIdBlackList')">
            <peerIdBlackList v-model="form.module.peer_id_blacklist" />
          </a-collapse-item>
          <a-collapse-item
            key="2"
            :header="t('page.settings.tab.config.module.clientNameBlackList')"
          >
            <clientNameBlackList v-model="form.module.client_name_blacklist" />
          </a-collapse-item>
          <a-collapse-item
            key="3"
            :header="t('page.settings.tab.config.module.progressCheatBlocker')"
          >
            <progressCheatBlocker v-model="form.module.progress_cheat_blocker" />
          </a-collapse-item>
          <a-collapse-item
            key="4"
            :header="t('page.settings.tab.config.module.ipAddressBlocker.title')"
          >
            <ipAddressBlocker v-model="form.module.ip_address_blocker" />
          </a-collapse-item>
          <a-collapse-item
            key="5"
            :header="t('page.settings.tab.config.module.autoRangeBan.title')"
          >
            <autoRangeBan v-model="form.module.auto_range_ban" />
          </a-collapse-item>
          <a-collapse-item
            key="6"
            :header="t('page.settings.tab.config.module.multiDialingBlocker.title')"
          >
            <multiDialogBlocker v-model="form.module.multi_dialing_blocker" />
          </a-collapse-item>
          <a-collapse-item
            key="7"
            :header="t('page.settings.tab.config.module.expressionEngine.title')"
          >
            <expressionEngine v-model="form.module.expression_engine" />
          </a-collapse-item>
          <a-collapse-item
            key="8"
            :header="t('page.settings.tab.config.module.ruleSubscribe.title')"
          >
            <RuleSubscribe v-model="form.module.ip_address_blocker_rules" />
          </a-collapse-item>
        </a-collapse>
      </a-space>
    </a-form>
  </div>
  <a-back-top target-container="#top" :style="{ position: 'absolute' }" />
</template>
<script setup lang="ts">
import { type Config } from '@/api/model/settings'
import { GetSettings } from '@/service/settings'
import { formatMilliseconds } from '@/utils/time'
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import autoRangeBan from './components/autoRangeBan.vue'
import clientNameBlackList from './components/clientNameBlackList.vue'
import expressionEngine from './components/expressionEngine.vue'
import formArray from './components/formArray.vue'
import ipAddressBlocker from './components/ipAddressBlocker.vue'
import multiDialogBlocker from './components/multiDialogBlocker.vue'
import peerIdBlackList from './components/peerIdBlackList.vue'
import progressCheatBlocker from './components/progressCheatBlocker.vue'
import RuleSubscribe from './components/ruleSubscribe.vue'

const { t } = useI18n()
const form = ref({
  config_version: 0,
  check_interval: 0,
  ban_duration: 0,
  ignore_peers_from_addresses: [] as string[]
} as Config)
GetSettings().then((data) => (form.value = data.data))
</script>
<style scoped></style>
