<template>
  <a-alert
    >由于后端接口还未完成，此页面仅能体验，点<a-link
      href="https://github.com/PBH-BTN/PeerBanHelper/issues/527#issuecomment-2416689571"
      >我</a-link
    >给后端上压力。</a-alert
  >
  <a-tabs default-active-key="1" lazy-load>
    <a-tab-pane key="1" :title="t('page.settings.tab.config')">
      <ConfigForm />
    </a-tab-pane>
    <a-tab-pane key="2" :title="t('page.settings.tab.profile')">
      <ProfileForm />
    </a-tab-pane>
    <a-tab-pane key="3">
      <template #title
        ><icon-code-square />
        {{ t('page.settings.tab.script') }}
      </template>
      敬请期待
    </a-tab-pane>
  </a-tabs>
</template>
<script setup lang="ts">
import { useI18n } from 'vue-i18n';
import ConfigForm from './components/config/index.vue';
import ProfileForm from './components/profile/index.vue';

const { t } = useI18n()
</script>
