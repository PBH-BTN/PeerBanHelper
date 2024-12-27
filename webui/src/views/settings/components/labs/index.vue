<template>
  <a-space direction="vertical" fill>
    <a-typography-title :heading="3">
      {{ t('page.settings.tab.labs') }}
    </a-typography-title>
    <a-alert :title="t('page.settings.tab.labs.welcome')">
      <i18n-t keypath="page.settings.tab.labs.welcome.content">
        <template #github>
          <a-link href="https://github.com/PBH-BTN/PeerBanHelper/issues">Github</a-link>
        </template>
      </i18n-t>
    </a-alert>
    <br />
    <a-spin
      v-if="firstLoading"
      style="
        width: 100%;
        height: 30rem;
        display: flex;
        justify-content: center;
        align-items: center;
      "
      dot
    />
    <a-space v-else direction="vertical" fill size="large">
      <a-form :model="config">
        <a-form-item field="enabled" :label="t('page.settings.tab.labs.enable')">
          <a-switch
            v-model="config.enabled"
            :before-change="(v) => switchLabConfig(v as boolean)"
          />
          <template #extra>
            {{ t('page.settings.tab.labs.enable.tips') }}
          </template>
        </a-form-item>
      </a-form>
      <a-typography-title v-if="config.enabled" :heading="5" style="text-indent: 2em">
        {{ t('page.settings.tab.labs.list') }}
      </a-typography-title>
      <br />
      <div v-if="config.enabled" style="width: 100%; display: flex; justify-content: center">
        <a-list :data="data?.data.experiments" style="max-width: 60rem" :loading="loading">
          <template #item="{ item }">
            <a-list-item action-layout="vertical">
              <template #actions>
                <a-space
                  >{{ t('page.settings.tab.labs.action.enable') }}
                  <a-switch
                    v-model="item.activated"
                    size="small"
                    :before-change="(value) => switchExperimentStatus(item.id, value as boolean)"
                  />
                </a-space>
              </template>
              <a-list-item-meta>
                <template #title>
                  {{ item.title }} &nbsp;
                  <a-tag v-if="item.activated" color="green">{{
                    t('page.settings.tab.labs.enabled')
                  }}</a-tag>
                </template>
                <template #description>
                  <Markdown :content="item.description" />
                </template>
              </a-list-item-meta>
            </a-list-item>
          </template>
        </a-list>
      </div>
    </a-space>
  </a-space>
</template>
<script setup lang="ts">
import Markdown from '@/components/markdown.vue'
import { GetExperimentList, SetExperimentStatus, SetLabConfig } from '@/service/labs'
import { Message } from '@arco-design/web-vue'
import { reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRequest } from 'vue-request'
const { t } = useI18n()
const firstLoading = ref(true)
const config = reactive({
  enabled: false
})
const { data, loading, refresh } = useRequest(GetExperimentList, {
  onSuccess: (data) => {
    firstLoading.value = false
    config.enabled = data.data.labEnabled
  }
})

const switchExperimentStatus = async (id: string, activated: boolean) => {
  try {
    const res = await SetExperimentStatus(id, activated)
    if (res.success) {
      refresh()
      return true
    } else throw new Error(res.message)
  } catch (e) {
    if (e instanceof Error) Message.error(e.message)
    return false
  }
}
const switchLabConfig = async (value: boolean) => {
  try {
    const res = await SetLabConfig({ enabled: value })
    if (res.success) {
      refresh()
      config.enabled = value
      return true
    } else throw new Error(res.message)
  } catch (e) {
    if (e instanceof Error) Message.error(e.message)
    return false
  }
}
</script>
