<template>
  <a-modal
    v-model:visible="visiable"
    :title="'BTN ' + t('page.settings.tab.info.btn.abilities')"
    unmount-on-close
    hide-cancel
    width="auto"
  >
    <a-space direction="vertical" fill>
      <a-typography-text>
        {{ t('page.settings.tab.info.btn.abilities.tips') }}
      </a-typography-text>
      <a-list
        :data="abilities"
        :virtual-list-props="{
          height: 500,
          threshold: 5
        }"
        style="max-width: 60rem"
      >
        <template #item="{ item }">
          <a-list-item action-layout="vertical">
            <a-list-item-meta :title="item.displayName">
              <template #description>
                <!-- eslint-disable-next-line vue/no-v-html-->
                <div v-html="md.render(item.description)"></div>
              </template>
            </a-list-item-meta>
            <template #actions>
              <a-space style="cursor: default">
                <a-typography-text>
                  {{ t('page.settings.tab.info.btn.abilities.list.lastSuccess') }}:&nbsp;
                  <a-tooltip :content="item.lastMessage">
                    <a-tag :color="item.lastSuccess ? 'green' : 'red'">
                      {{
                        item.lastSuccess
                          ? t('page.settings.tab.info.btn.abilities.list.lastSuccess.success')
                          : t('page.settings.tab.info.btn.abilities.list.lastSuccess.failed')
                      }}
                    </a-tag>
                  </a-tooltip>
                </a-typography-text>
                <br />
                <a-typography-text>
                  {{
                    t('page.settings.tab.info.btn.abilities.list.lastSuccessTime')
                  }}:&nbsp;<icon-clock-circle />
                  {{ d(item.lastUpdateAt, 'long') }}
                </a-typography-text>
              </a-space>
            </template>
          </a-list-item>
        </template>
      </a-list>
    </a-space>
  </a-modal>
</template>
<script setup lang="ts">
import type { Ability } from '@/api/model/status'
import markdownit from 'markdown-it'
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'

const md = new markdownit()

const { t, d } = useI18n()
const visiable = ref(false)
const abilities = ref([] as Ability[])

defineExpose({
  showModal: (abilityList: Ability[]) => {
    abilities.value = abilityList
    visiable.value = true
  }
})
</script>
