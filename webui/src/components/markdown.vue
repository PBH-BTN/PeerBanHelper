<!-- eslint-disable vue/no-v-html -->
<template>
  <article
    v-if="useGithubMarkdown"
    class="markdown-body"
    :data-theme="darkStore.isDark ? 'dark' : 'light'"
    v-html="md.render(content)"
  ></article>
  <div v-else v-html="md.render(content)"></div>
</template>
<script lang="ts" setup>
import '@/assets/github-markdown-css.css'
import { useDarkStore } from '@/stores/dark'
import md from '@/utils/markdown'

const darkStore = useDarkStore()
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
