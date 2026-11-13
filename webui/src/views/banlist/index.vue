<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import BanList from './components/banList.vue'

const { t } = useI18n()
</script>

<template>
  <a-space direction="vertical" fill>
    <BanList source="primary" />
    <a-collapse class="prefilled-banlist" :bordered="false">
      <a-collapse-item key="prefilled">
        <template #title>
          <a-space>
            <a-typography-text bold>{{ t('page.banlist.prefilled.title') }}</a-typography-text>
            <a-tag>{{ t('page.banlist.prefilled.collapsed') }}</a-tag>
          </a-space>
        </template>
        <BanList source="prefilled" />
      </a-collapse-item>
    </a-collapse>
  </a-space>
</template>

<style scoped lang="less">
.prefilled-banlist {
  width: 100%;
}
</style>
