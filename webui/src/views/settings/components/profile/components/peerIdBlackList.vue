<template>
  <a-space direction="vertical" fill>
    <a-form-item :label="t('page.settings.tab.profile.module.enable')" field="model.enabled">
      <a-switch v-model="model.enabled" />
    </a-form-item>
    <a-form-item
      :label="t('page.settings.tab.profile.module.peerIdBlackList.useGlobalBanTime')"
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
      :label="t('page.settings.tab.profile.module.peerIdBlackList.banPeerId')"
      field="model.banned_peer_id"
      :rules="[{ validator: nonEmptyValidator }]"
    >
      <a-space direction="vertical">
        <a-button @click="addNewItem">
          <template #icon>
            <icon-plus />
          </template>
        </a-button>
        <a-list
          ref="list"
          style="min-width: 800px"
          :pagination-props="controlledPaginationProps"
          :data="dataWithIndex"
        >
          <template #item="{ item }">
            <a-list-item>
              <a-space>
                <banRuleListItem v-model="model.banned_peer_id[item.index]" placeholder="Peer ID" />
                <br />
              </a-space>
              <template #actions>
                <a-button
                  class="edit-btn"
                  status="danger"
                  shape="circle"
                  type="text"
                  @click="model.banned_peer_id.splice(item.index, 1)"
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
import { type PeerIdBlacklist } from '@/api/model/profile'
import { formatMilliseconds } from '@/utils/time'
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import banRuleListItem from './banRuleListItem.vue'

const { t } = useI18n()
const model = defineModel<PeerIdBlacklist>({ required: true })

// Add pagination state management
const currentPage = ref(1)
const pageSize = computed(() => 5)

// Create computed pagination props that we can control
const controlledPaginationProps = computed(() => ({
  pageSize: pageSize.value,
  total: model.value.banned_peer_id.length,
  current: currentPage.value,
  onChange: (page: number) => {
    currentPage.value = page
  }
}))

const dataWithIndex = computed(() => {
  return model.value.banned_peer_id.map((item, index) => ({ ...item, index }))
})
const useGlobalBanTime = computed({
  get: () => model.value.ban_duration === 'default',
  set: (value: boolean) => {
    model.value.ban_duration = value ? 'default' : 259200000
  }
})

const addNewItem = () => {
  model.value.banned_peer_id.push({ method: 'STARTS_WITH', content: '' })
  // Calculate which page the new item will be on and navigate there
  const totalItems = model.value.banned_peer_id.length
  const lastPage = Math.ceil(totalItems / pageSize.value)
  currentPage.value = lastPage
}

const nonEmptyValidator = (_: unknown, cb: (error?: string) => void) => {
  if (model.value.banned_peer_id.some((item) => item.content === ''))
    cb(t('page.settings.tab.profile.module.banRuleTips.empty'))
  else cb()
}
</script>
