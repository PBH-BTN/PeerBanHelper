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
    <a-textarea
      v-model="model.body_template"
      :auto-size="{ minRows: 4, maxRows: 12 }"
      :placeholder="
        t('page.settings.tab.config.push.form.webhook.body_template.placeholder', {
          l: '{',
          r: '}'
        })
      "
    />
  </a-form-item>

  <a-form-item :label="t('page.settings.tab.config.push.form.webhook.variables')">
    <a-alert>
      <template #title>{{
        t('page.settings.tab.config.push.form.webhook.variables.tip')
      }}</template>
      <div v-pre>{title} {content} {level} {date} {time} {datetime} {channelName}</div>
      <div v-pre>{level}: TIP, *INFO*, WARN, ERROR, FATAL</div>
    </a-alert>
  </a-form-item>

  <a-form-item :label="t('page.settings.tab.config.push.form.webhook.headers')">
    <a-space direction="vertical" style="width: 100%">
      <div v-for="(header, index) in headerRows" :key="header.id" style="display: flex; gap: 8px">
        <a-input
          v-model="header.key"
          style="flex: 1"
          :placeholder="t('page.settings.tab.config.push.form.webhook.headers.key')"
          allow-clear
        />
        <a-input
          v-model="header.value"
          style="flex: 1"
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
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

type HeaderRow = {
  id: number
  key: string
  value: string
}

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

const nextRowId = ref(0)
const createRow = (key = '', value = ''): HeaderRow => ({
  id: nextRowId.value++,
  key,
  value
})

const headerRows = ref<HeaderRow[]>([])

const headersToRows = (headers: Record<string, string>): HeaderRow[] =>
  Object.entries(headers).map(([key, value]) => createRow(key, value))

const rowsToHeaders = (rows: HeaderRow[]): Record<string, string> => {
  const headers: Record<string, string> = {}
  rows.forEach((row) => {
    const key = row.key.trim()
    if (!key) return
    headers[key] = row.value
  })
  return headers
}

headerRows.value = headersToRows(model.value.headers ?? {})

// 仅在外部替换 model.headers 时回填 rows，避免与本地编辑互相覆盖
watch(
  () => model.value.headers,
  (headers) => {
    const fromRows = rowsToHeaders(headerRows.value)
    const next = headers ?? {}
    if (JSON.stringify(fromRows) === JSON.stringify(next)) {
      return
    }
    headerRows.value = headersToRows(next)
  }
)

// rows 作为编辑真源，深度监听后回写 model（过滤空 key 仅影响提交数据，不影响 UI 行）
watch(
  headerRows,
  (rows) => {
    model.value.headers = rowsToHeaders(rows)
  },
  { deep: true }
)

const addHeader = () => {
  headerRows.value.push(createRow())
}

const removeHeader = (index: number) => {
  headerRows.value.splice(index, 1)
}
</script>
