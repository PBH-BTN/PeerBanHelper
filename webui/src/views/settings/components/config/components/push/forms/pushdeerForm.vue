<template>
  <a-form-item field="config.endpoint" label="Endpoint" required>
    <a-input v-model="model.endpoint" />
  </a-form-item>
  <a-form-item field="config.pushkey" label="Push Key" required>
    <a-input-password v-model="model.pushkey" />
  </a-form-item>
</template>
<script setup lang="ts">
import { type PushDeerConfig } from '@/api/model/push'

const model = defineModel<PushDeerConfig>({ required: true })
if (model.value.endpoint === undefined) model.value.endpoint = 'https://api2.pushdeer.com/message/push'
</script>