<template>
  <a-modal
    v-model:visible="visiable"
    :title="'BTN' + t('page.settings.tab.info.btn.abilities')"
    unmount-on-close
    width="auto"
  >
    <a-list
      :data="abilities"
      :pagination-props="{ defaultPageSize: 5, total: abilities.length }"
      style="width: 35vw"
    >
      <template #item="{ item }">
        <a-list-item action-layout="vertical">
          <a-list-item-meta :title="item.displayName" :description="item.description" />
          <template #actions>
            <a-space>
              <a-typography-text>
                {{ t('page.settings.tab.info.btn.abilities.list.lastSuccess') }}:&nbsp;
                <a-popover :content="item.lastMessage">
                  <a-tag :color="item.lastSuccess ? 'green' : 'red'">
                    {{
                      item.lastSuccess
                        ? t('page.settings.tab.info.btn.abilities.list.lastSuccess.success')
                        : t('page.settings.tab.info.btn.abilities.list.lastSuccess.failed')
                    }}
                  </a-tag>
                </a-popover>
              </a-typography-text>
              <br />
              <a-typography-text>
                {{ t('page.settings.tab.info.btn.abilities.list.lastSuccessTime') }}:{{
                  d(item.lastUpdateAt, 'long')
                }}
              </a-typography-text>
            </a-space>
          </template>
        </a-list-item>
      </template>
    </a-list>
  </a-modal>
</template>
<script setup lang="ts">
import type { Ability } from '@/api/model/status'
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'

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
