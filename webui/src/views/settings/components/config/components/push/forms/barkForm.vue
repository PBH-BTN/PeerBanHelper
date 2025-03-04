<template>
  <a-form-item field="config.device_key" label="Device Key" required>
    <a-input-password v-model="model.device_key" />
  </a-form-item>
</template>
<script setup lang="ts">
import { type BarkConfig } from '@/api/model/push'

const model = defineModel<BarkConfig>({ required: true })
</script>
