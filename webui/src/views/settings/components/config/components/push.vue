<template>
  <a-space direction="vertical" fill>
    <a-typography-title :heading="3">{{
      t('page.settings.tab.config.push.title')
    }}</a-typography-title>
    <a-space style="display: flex; justify-content: space-between" fill>
      <a-typography-text>
        {{ t('page.settings.tab.config.push.description') }}
      </a-typography-text>
      <a-button type="primary" @click="handleAdd">
        <template #icon>
          <icon-plus-circle />
        </template>
        {{ t('page.settings.tab.config.push.add') }}
      </a-button>
    </a-space>
    <br />
    <a-row
      justify="start"
      align="stretch"
      :wrap="true"
      :gutter="[
        { xs: 8, sm: 8, md: 8, lg: 24, xl: 32 },
        { xs: 8, sm: 8, md: 8, lg: 24, xl: 32 }
      ]"
    >
      <!--骨架屏-->
      <a-col v-if="loading" :xs="24" :sm="12" :md="8" :lg="6">
        <a-card hoverable :style="{ width: '15rem', marginBottom: '20px' }">
          <div
            :style="{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between'
            }"
          >
            <a-space>
              <a-skeleton-shape shape="circle" animation /><a-skeleton-line animation />
            </a-space>
          </div>
        </a-card>
      </a-col>
      <!--骨架屏-->
      <a-col v-for="push in data?.data" v-else :key="push.name" :xs="24" :sm="12" :md="8" :lg="6">
        <pushCard :channel="push" @deleted="refresh" @edit="handleEdit(push)" />
      </a-col>
    </a-row>
  </a-space>
  <editPushModal ref="editModal" @changed="refresh" />
</template>
<script setup lang="ts">
import type { PushConfig } from '@/api/model/push'
import { GetPushChannelList } from '@/service/push'
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRequest } from 'vue-request'
import editPushModal from './push/editPush.vue'
import pushCard from './push/pushCard.vue'
const { t } = useI18n()
const { data, refresh, loading } = useRequest(GetPushChannelList)
const editModal = ref<InstanceType<typeof editPushModal>>()
const handleAdd = () => {
  editModal.value?.showModal(true)
}
const handleEdit = (config: PushConfig) => {
  editModal.value?.showModal(false, config)
}
</script>
