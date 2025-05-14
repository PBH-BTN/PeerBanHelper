<template>
  <a-form-item field="config.backend_url" label="Backend Url" required>
    <a-input v-model="model.backend_url" />
  </a-form-item>
  <a-form-item field="config.device_key" label="Device Key" required>
    <a-input-password v-model="model.device_key" />
  </a-form-item>
</template>
<script setup lang="ts">
import { type BarkConfig } from '@/api/model/push'

const model = defineModel<BarkConfig>({ required: true })
if (model.value.backend_url==="") model.value.backend_url = "https://api.day.app/push"
</script>
