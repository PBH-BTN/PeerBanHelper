<template>
  <a-space direction="vertical" fill>
    <a-typography-text style="font-size: 1.2em">
      {{ t('page.torrentList.description') }}
    </a-typography-text>
    <TorrentList />
  </a-space>
</template>
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import TorrentList from './components/torrentTable.vue'

const { t } = useI18n()
</script>
