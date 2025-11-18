<template>
  <a-space direction="vertical" fill>
    <a-typography-title :heading="5">{{
      t('page.settings.tab.profile.module.peerAnalyseService.sessionAnalyse.title')
    }}</a-typography-title>
    <sessionAnalyse v-model="model.session_analyse" />
    <a-divider />
    <a-typography-title :heading="5">{{
      t('page.settings.tab.profile.module.peerAnalyseService.swarmTracking.title')
    }}</a-typography-title>
    <swarmTracking v-model="model.swarm_tracking" />
    <a-divider />
    <a-typography-title :heading="5">{{
      t('page.settings.tab.profile.module.peerAnalyseService.peerRecording.title')
    }}</a-typography-title>
    <peerRecording v-model="model.peer_recording" />
  </a-space>
</template>
<script setup lang="ts">
import type { PeerAnalyseService } from '@/api/model/profile'
import { useI18n } from 'vue-i18n'
import peerRecording from './peerRecording.vue'
import sessionAnalyse from './sessionAnalyse.vue'
import swarmTracking from './swarmTracking.vue'

const { t } = useI18n()
const model = defineModel<PeerAnalyseService>({ required: true })
</script>
