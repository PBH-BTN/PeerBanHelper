<template>
  <a-space direction="vertical" fill>
    <a-form-item :label="t('page.settings.tab.profile.module.enable')" field="model.enabled">
      <a-switch v-model="model.enabled" />
    </a-form-item>
    <a-form-item
      :label="t('page.settings.tab.profile.module.ptrBlackList.useGlobalBanTime')"
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
      :label="t('page.settings.tab.profile.module.ptrBlackList.reserveName')"
      field="model.banned_client_name"
      :tooltip="t('page.settings.tab.profile.module.ptrBlackList.tooltip')"
      :rules="[{ validator: nonEmptyValidator }]"
    >
      <a-space direction="vertical">
        <a-button @click="model.ptr_rules.unshift({ method: 'STARTS_WITH', content: '' })">
          <template #icon>
            <icon-plus />
          </template>
        </a-button>
        <a-list
          style="min-width: 800px"
          :pagination-props="{ pageSize: 5, total: model.ptr_rules.length }"
          :data="dataWithIndex"
        >
          <template #item="{ item }">
            <a-list-item style="min-width: 250px">
              <a-space>
                <banRuleListItem
                  v-model="model.ptr_rules[item.index]"
                  :placeholder="t('page.settings.tab.profile.module.ptrBlackList.placeholder')"
                />
                <br />
              </a-space>
              <template #actions>
                <a-button
                  class="edit-btn"
                  status="danger"
                  shape="circle"
                  type="text"
                  @click="model.ptr_rules.splice(item.index, 1)"
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
import { type PtrBlacklist } from '@/api/model/profile'
import { formatMilliseconds } from '@/utils/time'
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import banRuleListItem from './banRuleListItem.vue'
const { t } = useI18n()
const model = defineModel<PtrBlacklist>({ required: true })
const useGlobalBanTime = computed({
  get: () => model.value.ban_duration === 'default',
  set: (value: boolean) => {
    model.value.ban_duration = value ? 'default' : 259200000
  }
})
const dataWithIndex = computed(() => {
  return model.value.ptr_rules.map((item, index) => ({ ...item, index }))
})
const nonEmptyValidator = (_: unknown, cb: (error?: string) => void) => {
  if (model.value.ptr_rules.some((item) => item.content === ''))
    cb(t('page.settings.tab.profile.module.banRuleTips.empty'))
  else cb()
}
</script>
