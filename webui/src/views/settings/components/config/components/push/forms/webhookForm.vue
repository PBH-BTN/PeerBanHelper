<template>
  <a-form-item
    field="config.url"
    :label="t('page.settings.tab.config.push.form.webhook.url')"
    required
    validate-trigger="blur"
    :rules="urlRules"
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
    v-if="!isGetMethod"
    field="config.content_type"
    :label="t('page.settings.tab.config.push.form.webhook.content_type')"
    required
  >
    <a-select v-model="model.content_type" :options="Object.values(WebhookContentType)" />
  </a-form-item>

  <a-form-item
    v-if="!isGetMethod"
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

  <a-form-item
    field="config.headers"
    :label="t('page.settings.tab.config.push.form.webhook.headers')"
    :rules="[{ validator: headersValidator }]"
  >
    <a-space direction="vertical" style="width: 100%">
      <a-button @click="addHeader">
        <template #icon>
          <icon-plus />
        </template>
        {{ t('page.settings.tab.config.push.form.webhook.headers.add') }}
      </a-button>
      <a-list
        v-if="headerRows.length > 0"
        class="header-list"
        :pagination-props="controlledPaginationProps"
        :data="dataWithIndex"
      >
        <template #item="{ item }">
          <a-list-item>
            <div class="header-row">
              <a-input
                v-model="headerRows[item.index]!.key"
                class="header-input"
                :placeholder="t('page.settings.tab.config.push.form.webhook.headers.key')"
                allow-clear
              />
              <a-input
                v-model="headerRows[item.index]!.value"
                class="header-input"
                :placeholder="t('page.settings.tab.config.push.form.webhook.headers.value')"
                allow-clear
              />
            </div>
            <template #actions>
              <a-button
                status="danger"
                shape="circle"
                type="text"
                @click="removeHeader(item.index)"
              >
                <template #icon>
                  <icon-delete />
                </template>
              </a-button>
            </template>
          </a-list-item>
        </template>
      </a-list>
    </a-space>
  </a-form-item>
</template>

<script setup lang="ts">
import { WebhookContentType, WebhookMethod, type WebhookConfig } from '@/api/model/push'
import type { FieldRule } from '@arco-design/web-vue'
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

type HeaderRow = { key: string; value: string }

const isGetMethod = computed(() => model.value.method === WebhookMethod.GET)

const contentTypeLanguage = computed(() =>
  model.value.content_type === WebhookContentType.JSON ? 'json' : 'plaintext'
)

const isValidUrl = (value: string) => {
  return URL.canParse(value)
}

const urlRules: FieldRule<string> = {
  type: 'string',
  required: true,
  validator: (value, callback) => {
    if (!value) return callback(t('page.settings.tab.config.push.form.webhook.url.error.required'))
    if (!value.startsWith('http://') && !value.startsWith('https://')) {
      return callback(t('page.settings.tab.config.push.form.webhook.url.error.invalidSchema'))
    }
    if (!isValidUrl(value)) {
      return callback(t('page.settings.tab.config.push.form.webhook.url.error.invalidUrl'))
    }
    callback()
  }
}

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

const createHeaderRows = (headers: Record<string, string> = {}) =>
  Object.entries(headers).map(([key, value]) => ({ key, value }))

const headerRows = ref<HeaderRow[]>(createHeaderRows(model.value.headers))
const currentPage = ref(1)
const pageSize = computed(() => 5)

const controlledPaginationProps = computed(() => ({
  pageSize: pageSize.value,
  total: headerRows.value.length,
  current: currentPage.value,
  onChange: (page: number) => {
    currentPage.value = page
  }
}))

const dataWithIndex = computed(() => {
  return headerRows.value.map((item, index) => ({ ...item, index }))
})

const syncHeadersToModel = () => {
  const headers: Record<string, string> = {}
  headerRows.value.forEach((row) => {
    const key = row.key.trim()
    if (key) headers[key] = row.value
  })
  model.value.headers = headers
}

watch(headerRows, () => syncHeadersToModel(), { deep: true })

watch(model, (value) => {
  if (value.headers === undefined) value.headers = {}
  headerRows.value = createHeaderRows(value.headers)
  currentPage.value = 1
})

const addHeader = () => {
  headerRows.value.push({ key: '', value: '' })
  currentPage.value = Math.ceil(headerRows.value.length / pageSize.value)
}

const removeHeader = (index: number) => {
  headerRows.value.splice(index, 1)
  const lastPage = Math.max(1, Math.ceil(headerRows.value.length / pageSize.value))
  if (currentPage.value > lastPage) currentPage.value = lastPage
}

const headersValidator = (_: unknown, callback: (error?: string) => void) => {
  const names = new Set<string>()
  for (const row of headerRows.value) {
    const key = row.key.trim()
    if (!key && row.value.trim()) {
      return callback(t('page.settings.tab.config.push.form.webhook.headers.error.emptyKey'))
    }
    if (!key) continue
    const normalizedKey = key.toLowerCase()
    if (names.has(normalizedKey)) {
      return callback(t('page.settings.tab.config.push.form.webhook.headers.error.duplicateKey'))
    }
    names.add(normalizedKey)
  }
  callback()
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
