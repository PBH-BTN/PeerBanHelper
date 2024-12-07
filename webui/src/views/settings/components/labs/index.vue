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
    <a-typography-title :heading="5">
      {{ t('page.settings.tab.labs.list') }}
    </a-typography-title>
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
    <div v-else style="width: 100%; display: flex; justify-content: center">
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
                <!--eslint-disable-next-line vue/no-v-html-->
                <div v-html="md.render(item.description)"></div>
              </template>
            </a-list-item-meta>
          </a-list-item>
        </template>
      </a-list>
    </div>
  </a-space>
</template>
<script setup lang="ts">
import { GetExperimentList, SetExperimentStatus } from '@/service/labs'
import { Message } from '@arco-design/web-vue'
import markdownit from 'markdown-it'
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRequest } from 'vue-request'
const { t } = useI18n()
const firstLoading = ref(true)
const { data, loading, refresh } = useRequest(GetExperimentList, {
  onSuccess: () => (firstLoading.value = false)
})
const md = new markdownit()
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
</script>
