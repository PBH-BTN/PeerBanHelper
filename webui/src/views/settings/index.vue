<template>
  <a-tabs default-active-key="1" lazy-load animation>
    <a-tab-pane key="1" :title="t('page.settings.tab.config')">
      <ConfigForm />
    </a-tab-pane>
    <a-tab-pane key="2" :title="t('page.settings.tab.profile')">
      <ProfileForm />
    </a-tab-pane>
    <!-- This is unsafe, remove -->
    <!-- <a-tab-pane key="3" disabled>
      <template #title
        ><icon-code-square />
        {{ t('page.settings.tab.script') }}
      </template>
    </a-tab-pane> -->
  </a-tabs>
</template>
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import ConfigForm from './components/config/index.vue'
import ProfileForm from './components/profile/index.vue'

const { t } = useI18n()
</script>
