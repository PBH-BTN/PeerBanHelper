<template>
  <a-form-item field="config.token" label="Token" required>
    <a-input-password v-model="model.token" />
  </a-form-item>
  <a-form-item field="config.chatId" label="Chat ID" required>
    <a-input v-model="model.chatId" />
  </a-form-item>
</template>
<script setup lang="ts">
import { type TelegramConfig } from '@/api/model/push'

const model = defineModel<TelegramConfig>({ required: true })
</script>
