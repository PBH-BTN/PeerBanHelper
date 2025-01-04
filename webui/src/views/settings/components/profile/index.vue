<template>
  <a-spin style="width: 100%" :loading="loading" dot>
    <div id="top" style="position: relative">
      <a-form :model="form">
        <a-space direction="vertical" fill>
          <a-space direction="vertical" fill style="width: 100%">
            <a-typography-title :heading="3">{{
              t('page.settings.tab.profile.base.title')
            }}</a-typography-title>
            <a-form-item
              :label="t('page.settings.tab.profile.form.checkInterval')"
              field="check_interval"
              required
            >
              <a-input-number v-model="form.check_interval" style="width: 200px">
                <template #suffix> {{ t('page.settings.tab.profile.unit.ms') }} </template>
              </a-input-number>
            </a-form-item>
            <a-form-item
              :label="t('page.settings.tab.profile.form.banDuration')"
              field="ban_duration"
              required
            >
              <a-input-number v-model="form.ban_duration" style="width: 200px">
                <template #suffix> {{ t('page.settings.tab.profile.unit.ms') }} </template>
              </a-input-number>
              <template #extra> ={{ formatMilliseconds(form.ban_duration) }} </template>
            </a-form-item>
            <formArray
              v-model="form.ignore_peers_from_addresses"
              :label="t('page.settings.tab.profile.form.ignoreAddress')"
              :tooltip="t('page.settings.tab.profile.form.ignoreAddress.tooltip')"
              :pagination-props="{ pageSize: 10, total: form.ignore_peers_from_addresses.length }"
            />
          </a-space>
          <a-divider />
          <a-space v-if="form.module" direction="vertical" fill style="width: 90%">
            <a-typography-title id="module" :heading="3">{{
              t('page.settings.tab.profile.module.title')
            }}</a-typography-title>
            <a-collapse destroy-on-hide>
              <a-collapse-item
                key="1"
                :header="t('page.settings.tab.profile.module.peerIdBlackList')"
              >
                <peerIdBlackList v-model="form.module.peer_id_blacklist" />
              </a-collapse-item>
              <a-collapse-item
                key="2"
                :header="t('page.settings.tab.profile.module.clientNameBlackList')"
              >
                <clientNameBlackList v-model="form.module.client_name_blacklist" />
              </a-collapse-item>
              <a-collapse-item
                key="3"
                :header="t('page.settings.tab.profile.module.progressCheatBlocker')"
              >
                <progressCheatBlocker v-model="form.module.progress_cheat_blocker" />
              </a-collapse-item>
              <a-collapse-item
                key="4"
                :header="t('page.settings.tab.profile.module.ipAddressBlocker.title')"
              >
                <ipAddressBlocker v-model="form.module.ip_address_blocker" />
              </a-collapse-item>
              <a-collapse-item
                key="5"
                :header="t('page.settings.tab.profile.module.autoRangeBan.title')"
              >
                <autoRangeBan v-model="form.module.auto_range_ban" />
              </a-collapse-item>
              <a-collapse-item key="6" header="BTN">
                <btn v-model="form.module.btn" />
              </a-collapse-item>
              <a-collapse-item
                key="7"
                :header="t('page.settings.tab.profile.module.multiDialingBlocker.title')"
              >
                <multiDialingBlocker v-model="form.module.multi_dialing_blocker" />
              </a-collapse-item>
              <a-collapse-item
                key="8"
                :header="t('page.settings.tab.profile.module.expressionEngine.title')"
              >
                <expressionEngine v-model="form.module.expression_engine" />
              </a-collapse-item>
              <a-collapse-item
                key="9"
                :header="t('page.settings.tab.profile.module.ruleSubscribe.title')"
              >
                <ruleSubscribe v-model="form.module.ip_address_blocker_rules" />
              </a-collapse-item>
              <a-collapse-item
                key="10"
                :header="t('page.settings.tab.profile.module.activeMonitor.title')"
              >
                <activeMonitoring v-model="form.module.active_monitoring" />
              </a-collapse-item>
              <a-collapse-item
                key="11"
                :header="t('page.settings.tab.profile.module.ptrBlackList.title')"
              >
                <ptrBlocker v-model="form.module.ptr_blacklist" />
              </a-collapse-item>
            </a-collapse>
          </a-space>
          <br />
          <a-form-item label-col-flex="0">
            <a-button html-type="submit" type="primary" :loading="saving" @click="submitConfig()">
              <template #icon><icon-save /></template>
              {{ t('page.settings.tab.profile.save') }}
            </a-button>
          </a-form-item>
        </a-space>
      </a-form>
    </div>
  </a-spin>
  <a-back-top target-container="#top" :style="{ position: 'absolute' }" />
</template>
<script setup lang="ts">
import { type Profile } from '@/api/model/profile'
import { GetProfile, SaveProfile } from '@/service/settings'
import { formatMilliseconds } from '@/utils/time'
import { Message } from '@arco-design/web-vue'
import { reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRequest } from 'vue-request'
import activeMonitoring from './components/activeMonitoring.vue'
import autoRangeBan from './components/autoRangeBan.vue'
import btn from './components/btn.vue'
import clientNameBlackList from './components/clientNameBlackList.vue'
import expressionEngine from './components/expressionEngine.vue'
import formArray from './components/formArray.vue'
import ipAddressBlocker from './components/ipAddressBlocker.vue'
import multiDialingBlocker from './components/multiDialingBlocker.vue'
import peerIdBlackList from './components/peerIdBlackList.vue'
import progressCheatBlocker from './components/progressCheatBlocker.vue'
import ruleSubscribe from './components/ruleSubscribe.vue'
import ptrBlocker from './components/ptrBlocker.vue'

const { t } = useI18n()
const form = reactive({
  config_version: 0,
  check_interval: 0,
  ban_duration: 0,
  ignore_peers_from_addresses: [] as string[],
  module: {
    peer_id_blacklist: {},
    client_name_blacklist: {},
    progress_cheat_blocker: {},
    ip_address_blocker: {},
    auto_range_ban: {},
    btn: {},
    multi_dialing_blocker: {},
    expression_engine: {},
    ip_address_blocker_rules: {},
    active_monitoring: {
      traffic_monitoring: {}
    }
  }
} as Profile)
const { loading } = useRequest(GetProfile, {
  onSuccess: (data) => {
    Object.assign(form, data.data)
  }
})
const saving = ref(false)
const submitConfig = async () => {
  saving.value = true
  try {
    const data = await SaveProfile(form)
    if (data.success) {
      Message.success(data.message)
    } else {
      Message.error(data.message)
    }
  } catch (error) {
    Message.error((error as Error).message)
  } finally {
    saving.value = false
  }
}
</script>
<style scoped>
.align-right {
  display: flex;
  justify-content: flex-end;
}
</style>
