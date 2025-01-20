<template>
  <iframe
    v-if="useGithubMarkdown"
    title="changelog"
    style="border: none; width: 100%; height: 100%"
    :srcdoc="srcDoc"
    sandbox=""
  />
  <!-- eslint-disable vue/no-v-html -->
  <div v-else v-html="md.render(content)"></div>
</template>
<script lang="ts" setup>
import { useDarkStore } from '@/stores/dark'
import md from '@/utils/markdown'
import { computed } from 'vue'
const darkStore = useDarkStore()

const srcDoc = computed(() => {
  return `
    <html data-theme="${darkStore.isDark ? 'dark' : 'light'}">
    <link rel="stylesheet" href="./style/github-markdown-css.css"/>
    <div class="markdown-body" data-theme="${darkStore.isDark ? 'dark' : 'light'}">${md.render(content)}</div>
    </html>
    `
})
const { content, useGithubMarkdown = false } = defineProps<{
  content: string
  useGithubMarkdown?: boolean
}>()
</script>
