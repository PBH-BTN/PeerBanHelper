<template>
  <a-form-item field="config.sendKey" label="Send Key" required>
    <a-input-password v-model="model.sendKey" />
  </a-form-item>
  <a-form-item field="config.channel" label="Channel">
    <a-input v-model="model.channel" />
  </a-form-item>
  <a-form-item field="config.openId" label="Open ID">
    <a-input v-model="model.openId" />
  </a-form-item>
</template>
<script setup lang="ts">
import { type ServerChanConfig } from '@/api/model/push'

const model = defineModel<ServerChanConfig>({ required: true })
</script>
