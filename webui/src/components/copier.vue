<template>
  <AsyncMethod v-slot="{ run, loading: copied }" once :async-fn="handleCopy">
    <a-tooltip :content="t(copied ? 'copier.copied' : 'copier.copy')">
      <span
        :class="copied ? 'arco-typography-operation-copied' : 'arco-typography-operation-copy'"
        @click="run"
      >
        <icon-check-circle-fill v-if="copied" />
        <icon-copy v-else />
      </span>
    </a-tooltip>
  </AsyncMethod>
</template>
<script setup lang="ts">
import copy from 'copy-to-clipboard'
import { useI18n } from 'vue-i18n'
import AsyncMethod from './asyncMethod.vue'
const { t } = useI18n()
const { text } = defineProps<{
  text: string
}>()
const handleCopy = async () => {
  copy(text)
  await new Promise((resolve) => setTimeout(resolve, 3000))
}
</script>
