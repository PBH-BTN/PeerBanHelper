<template>
  <a-form-item
    field="config.url"
    :label="t('page.settings.tab.config.push.form.webhook.url')"
    required
  >
    <a-input
      v-model="model.url"
      :placeholder="t('page.settings.tab.config.push.form.webhook.url.placeholder')"
      allow-clear
    />
  </a-form-item>

  <a-form-item
    field="config.method"
    :label="t('page.settings.tab.config.push.form.webhook.method')"
    required
  >
    <a-select v-model="model.method" :options="Object.values(WebhookMethod)" />
  </a-form-item>

  <a-form-item
    field="config.content_type"
    :label="t('page.settings.tab.config.push.form.webhook.content_type')"
    required
  >
    <a-select v-model="model.content_type" :options="Object.values(WebhookContentType)" />
  </a-form-item>

  <a-form-item
    v-if="model.method != 'GET'"
    field="config.body_template"
    :label="t('page.settings.tab.config.push.form.webhook.body_template')"
    required
  >
    <div class="textarea-wrapper">
      <div class="editor-container">
        <VueMonacoEditor
          v-model:value="model.body_template"
          :theme="darkStore.isDark ? 'vs-dark' : 'vs'"
          :language="contentTypeLanguage"
          :options="editorOptions"
        />
      </div>
      <a-tooltip position="right">
        <template #content>
          <div class="tooltip-content">
            {{
              t('page.settings.tab.config.push.form.webhook.body_template.tooltip', {
                l: '{',
                r: '}'
              })
            }}
          </div>
        </template>
        <span class="textarea-tooltip-icon">
          <icon-exclamation-circle />
        </span>
      </a-tooltip>
    </div>
  </a-form-item>

  <a-form-item :label="t('page.settings.tab.config.push.form.webhook.headers')">
    <a-space direction="vertical" style="width: 100%">
      <div v-for="(header, index) in headerRows" :key="index" class="header-row">
        <a-input
          v-model="header.key"
          class="header-input"
          :placeholder="t('page.settings.tab.config.push.form.webhook.headers.key')"
          allow-clear
        />
        <a-input
          v-model="header.value"
          class="header-input"
          :placeholder="t('page.settings.tab.config.push.form.webhook.headers.value')"
          allow-clear
        />
        <a-button status="danger" @click="removeHeader(index)">
          <template #icon>
            <icon-delete />
          </template>
        </a-button>
        <a-button @click="addHeader">
          <template #icon>
            <icon-plus />
          </template>
        </a-button>
      </div>
      <a-button v-if="headerRows.length === 0" long @click="addHeader">
        <template #icon>
          <icon-plus />
        </template>
        {{ t('page.settings.tab.config.push.form.webhook.headers.add') }}
      </a-button>
    </a-space>
  </a-form-item>
</template>

<script setup lang="ts">
import { WebhookContentType, WebhookMethod, type WebhookConfig } from '@/api/model/push'
import { VueMonacoEditor, loader } from '@guolao/vue-monaco-editor'
import * as monaco from 'monaco-editor'
import { useDarkStore } from '@/stores/dark'
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

loader.config({ monaco })

const { t } = useI18n()
const model = defineModel<WebhookConfig>({ required: true })

if (model.value.url === undefined) model.value.url = ''
if (model.value.method === undefined) model.value.method = WebhookMethod.POST
if (model.value.content_type === undefined) model.value.content_type = WebhookContentType.JSON
if (model.value.body_template === undefined) {
  model.value.body_template = `
{
    "title":"{title}",
    "content":"{content}",
    "level":"{level}",
    "date":"{date}",
    "time":"{time}",
    "datetime":"{datetime}",
    "channelName":"{channelName}"
}
`
}
if (model.value.headers === undefined) model.value.headers = {}

const darkStore = useDarkStore()

const contentTypeLanguage = computed(() =>
  model.value.content_type === WebhookContentType.JSON ? 'json' : 'plaintext'
)

const editorOptions = {
  automaticLayout: true,
  minimap: { enabled: false },
  lineNumbers: 'off',
  folding: false,
  lineDecorationsWidth: 0,
  lineNumbersMinChars: 0,
  scrollBeyondLastLine: false,
  wordWrap: 'on' as const,
  tabSize: 2
}

const headerRows = ref<{ key: string; value: string }[]>(
  Object.entries(model.value.headers ?? {}).map(([key, value]) => ({ key, value }))
)

watch(
  headerRows,
  (rows) => {
    const headers: Record<string, string> = {}
    rows.forEach((row) => {
      const key = row.key.trim()
      if (key) headers[key] = row.value
    })
    model.value.headers = headers
  },
  { deep: true }
)

const addHeader = () => {
  headerRows.value.push({ key: '', value: '' })
}

const removeHeader = (index: number) => {
  headerRows.value.splice(index, 1)
}
</script>

<style scoped>
.textarea-wrapper {
  position: relative;
  width: 100%;
}

.editor-container {
  height: 240px;
}

.tooltip-content {
  max-width: 340px;
  white-space: pre-line;
}

.textarea-tooltip-icon {
  position: absolute;
  top: 8px;
  right: 24px;
  color: var(--color-text-3);
  cursor: pointer;
  z-index: 10;
}

.header-row {
  display: flex;
  gap: 8px;
}

.header-input {
  flex: 1;
}
</style>
