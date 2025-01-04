<template>
  <a-space>
    {{ t('page.settings.tab.profile.module.peerIdBlackList.if') }}
    <a-select
      v-model="model.method"
      style="width: 150px"
      :placeholder="t('page.settings.tab.profile.module.peerIdBlackList.rule')"
    >
      <a-option v-for="method in ruleMethod" :key="method" :value="method">
        <a-space
          >{{ method
          }}<a-tooltip :content="t('page.settings.tab.profile.module.banRuleTips.' + method)"
            ><icon-question-circle /></a-tooltip
        ></a-space>
      </a-option>
    </a-select>
    <a-input v-model="model.content" :placeholder="placeholder"></a-input>
    {{ t('page.settings.tab.profile.module.peerIdBlackList.hit') }}
    <a-select v-model="model.hit" style="width: 100px">
      <a-option v-for="action in actions" :key="action" :value="action">{{
        t('page.settings.tab.profile.module.banRuleTips.' + action)
      }}</a-option>
    </a-select>
    {{ t('page.settings.tab.profile.module.peerIdBlackList.miss') }}
    <a-select v-model="model.miss" style="width: 100px">
      <a-option v-for="action in actions" :key="action" :value="action">{{
        t('page.settings.tab.profile.module.banRuleTips.' + action)
      }}</a-option>
    </a-select>
  </a-space>
</template>
<script setup lang="ts">
import type { PeerRule } from '@/api/model/profile'
import { useI18n } from 'vue-i18n'
const { placeholder } = defineProps<{ placeholder: string }>()
const { t } = useI18n()
const ruleMethod = ['STARTS_WITH', 'ENDS_WITH', 'LENGTH', 'CONTAINS', 'EQUALS', 'REGEX']
const actions = ['TRUE', 'FALSE', 'DEFAULT']
const model = defineModel<PeerRule>({ required: true })
</script>
