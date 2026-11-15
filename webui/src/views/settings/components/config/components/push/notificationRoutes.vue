<template>
  <a-spin :loading="loading" style="display: block">
    <a-alert v-if="loadFailed" type="error">{{ t(prefix + 'loadFailed') }}</a-alert>
    <a-space v-else direction="vertical" fill>
      <a-typography-text>{{ t(prefix + 'description') }}</a-typography-text>
      <a-card v-for="[group, rules] in groups" :key="group" :title="label('group.' + group, group)">
        <a-form :model="{ rules }" layout="vertical">
          <a-form-item
            v-for="rule in rules"
            :key="rule.category.id"
            :label="label('category.' + rule.category.id, rule.category.name)"
          >
            <a-space direction="vertical" fill>
              <a-checkbox v-model="rule.useDefault" :disabled="!!saving">
                {{ t(prefix + 'default') }}
              </a-checkbox>
              <a-select
                v-model="rule.channelIds"
                multiple
                :disabled="rule.useDefault || !!saving"
                :options="options(rule)"
                :placeholder="t(prefix + 'none')"
              />
              <a-button
                size="small"
                :loading="saving === rule.category.id"
                :disabled="!!saving || !isChanged(rule)"
                @click="save(rule)"
              >
                {{ t(prefix + 'save') }}
              </a-button>
            </a-space>
          </a-form-item>
        </a-form>
      </a-card>
    </a-space>
  </a-spin>
</template>

<script setup lang="ts">
import {
  getNotificationSettings,
  saveNotificationRule,
  type NotificationRule,
  type NotificationSettings
} from '@/service/notifications'
import { Message } from '@arco-design/web-vue'
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'

const prefix = 'page.settings.tab.config.push.routing.'
const { t, te } = useI18n()
const label = (key: string, fallback: string) => (te(prefix + key) ? t(prefix + key) : fallback)
const settings = ref<NotificationSettings>()
const drafts = ref<NotificationRule[]>([])
const loading = ref(false)
const loadFailed = ref(false)
const saving = ref<string>()
let loadVersion = 0

const groups = computed(() => {
  const result = new Map<string, NotificationRule[]>()
  for (const rule of drafts.value) {
    const group = result.get(rule.category.group)
    if (group) group.push(rule)
    else result.set(rule.category.group, [rule])
  }
  return result
})

function options(rule: NotificationRule) {
  const channels = settings.value?.channels ?? []
  return [
    ...channels.map((channel) => ({
      value: channel.id,
      label: channel.id === 'desktop' ? t(prefix + 'desktop') : channel.name
    })),
    ...rule.channelIds
      .filter((id) => !channels.some((channel) => channel.id === id))
      .map((id) => ({ value: id, label: t(prefix + 'unavailable', { id }) }))
  ]
}

function isChanged(rule: NotificationRule) {
  const saved = settings.value?.rules.find((item) => item.category.id === rule.category.id)
  return (
    saved?.useDefault !== rule.useDefault ||
    (!rule.useDefault && JSON.stringify(saved?.channelIds) !== JSON.stringify(rule.channelIds))
  )
}

async function refresh() {
  const version = ++loadVersion
  loading.value = true
  loadFailed.value = false
  try {
    const response = await getNotificationSettings()
    if (!response.success) throw new Error(response.message)
    if (version !== loadVersion) return
    const modified = new Map(drafts.value.filter(isChanged).map((rule) => [rule.category.id, rule]))
    settings.value = response.data
    drafts.value = response.data.rules.map(
      (rule) => modified.get(rule.category.id) ?? { ...rule, channelIds: [...rule.channelIds] }
    )
  } catch {
    if (version === loadVersion) loadFailed.value = true
  } finally {
    if (version === loadVersion) loading.value = false
  }
}

async function save(rule: NotificationRule) {
  saving.value = rule.category.id
  try {
    const response = await saveNotificationRule(rule)
    if (!response.success) throw new Error(response.message)
    drafts.value = drafts.value.filter((item) => item.category.id !== rule.category.id)
    Message.success(t(prefix + 'saved'))
    await refresh()
  } catch (error) {
    Message.error(
      error instanceof Error && error.message ? error.message : t(prefix + 'saveFailed')
    )
  } finally {
    saving.value = undefined
  }
}

onMounted(refresh)
defineExpose({ refresh })
</script>
