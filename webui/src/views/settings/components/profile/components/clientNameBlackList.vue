<template>
  <a-space direction="vertical" fill>
    <a-form-item :label="t('page.settings.tab.profile.module.enable')" field="model.enabled">
      <a-switch v-model="model.enabled" />
    </a-form-item>
    <a-form-item
      :label="t('page.settings.tab.profile.module.peerIdBlackList.individualBanTime')"
      field="model.ban_duration"
    >
      <a-space>
        <a-switch v-model="individualBanTime" @change="changeIndividualBanTime" />
        <a-input-number v-if="!individualBanTime" v-model="model.ban_duration as number">
          <template #suffix> {{ t('page.settings.tab.profile.unit.ms') }} </template>
        </a-input-number>
      </a-space>
      <template v-if="model.ban_duration !== 'default'" #extra>
        ={{ formatMilliseconds(model.ban_duration) }}
      </template>
    </a-form-item>
    <a-form-item
      :label="t('page.settings.tab.profile.module.peerIdBlackList.banPeerId')"
      field="model.ban_duration"
    >
      <a-space direction="vertical">
        <a-button @click="model.banned_client_name.push()">
          <template #icon>
            <icon-plus />
          </template>
        </a-button>
        <a-list
          style="min-width: 800px"
          :virtual-list-props="{ threshold: 8, height: 500, fixedSize: true, buffer: 4 }"
          :data="model.banned_client_name"
        >
          <template #item="{ index: i }">
            <a-list-item style="min-width: 250px">
              <a-space>
                <banRuleListItem v-model="model.banned_client_name[i]" />
                <br />
              </a-space>
              <template #actions>
                <a-button
                  class="edit-btn"
                  status="danger"
                  shape="circle"
                  type="text"
                  @click="model.banned_client_name.splice(i, 1)"
                >
                  <template #icon>
                    <icon-delete />
                  </template>
                </a-button>
              </template>
            </a-list-item>
          </template>
        </a-list>
      </a-space>
    </a-form-item>
  </a-space>
</template>
<script setup lang="ts">
import { type ClientNameBlacklist } from '@/api/model/profile'
import { formatMilliseconds } from '@/utils/time'
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import banRuleListItem from './banRuleListItem.vue'
const { t } = useI18n()
const model = defineModel<ClientNameBlacklist>({ required: true })
const individualBanTime = ref(model.value.ban_duration === 'default')
const changeIndividualBanTime = (value: string | number | boolean) => {
  if (value) {
    model.value.ban_duration = 'default'
  } else {
    model.value.ban_duration = 259200000
  }
}
</script>
