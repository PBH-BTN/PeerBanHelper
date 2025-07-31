<template>
  <a-form-item field="config.server_url" label="Server URL" required>
    <a-input v-model="model.server_url" placeholder="https://gotify.example.com" />
  </a-form-item>
  <a-form-item field="config.token" label="Application Token" required>
    <a-input-password v-model="model.token" />
  </a-form-item>
  <a-form-item field="config.priority" label="Priority">
    <a-input-number v-model="model.priority" :min="1" :max="10" />
    <template #extra> Message priority level (1-10, default: 5) </template>
  </a-form-item>
</template>
<script setup lang="ts">
import { type GotifyConfig } from '@/api/model/push'

const model = defineModel<GotifyConfig>({ required: true })
if (model.value.priority === undefined) model.value.priority = 5
</script>
