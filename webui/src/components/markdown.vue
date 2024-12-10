<template>
  <iframe
    v-if="useGithubMarkdown"
    title="changelog"
    style="border: none; width: 80vh; height: 60vh"
    :srcdoc="srcDoc"
    sandbox=""
  />
  <!-- eslint-disable vue/no-v-html -->
  <div v-else v-html="md.render(content)"></div>
</template>
<script lang="ts" setup>
import { useDarkStore } from '@/stores/dark';
import md from '@/utils/markdown';
import { computed } from 'vue';
const darkStore = useDarkStore()

const srcDoc = computed(() => {
  return `
    <link rel="stylesheet" href="./style/github-markdown-css.css"/>
    <div class="markdown-body" data-theme="${darkStore.isDark ? 'dark' : 'light'}">${md.render(content)}</div>
    `
})
const { content, useGithubMarkdown = false } = defineProps<{
  content: string
  useGithubMarkdown?: boolean
}>()
</script>
<style>
.markdown-body {
  box-sizing: border-box;
  min-width: 200px;
  max-width: 980px;
  margin: 0 auto;
  padding: 20px;
}

@media (max-width: 767px) {
  .markdown-body {
    padding: 15px;
  }
}
</style>
