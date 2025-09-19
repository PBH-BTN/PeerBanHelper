<template>
  <a-space direction="vertical" fill size="large">
    <a-space style="display: flex; justify-content: space-between; padding: 0 4rem 2rem 4rem" fill>
      <a-typography-text style="font-size: 1.2em">
        {{
          t('page.rule_management.generic.description', {
            type: t('page.rule_management.' + type)
          })
        }}
      </a-typography-text>
      <AsyncMethod v-slot="{ run, loading: saveLoading }" once :async-fn="handleSave">
        <a-button type="primary" :loading="saveLoading" @click="run">
          <template #icon>
            <icon-save />
          </template>
          {{ t('page.rule_management.generic.save') }}
        </a-button>
      </AsyncMethod>
    </a-space>

    <a-transfer
      v-model="selectedKeys"
      :data="transferData"
      :loading="loading"
      :title="[
        t('page.rule_management.generic.available'),
        t('page.rule_management.generic.selected')
      ]"
      style="justify-content: center"
    >
    </a-transfer>
  </a-space>
</template>
<script lang="ts" setup>
import { netTypeArray, type netType } from '@/api/model/blacklist'
import AsyncMethod from '@/components/asyncMethod.vue'
import { getBlackList, updateRuleTypeBackList } from '@/service/blacklist'
import { Message } from '@arco-design/web-vue'
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRequest } from 'vue-request'

const { t } = useI18n()
const type = 'netType'

const selectedKeys = ref<netType[]>([])

const transferData = computed(() =>
  netTypeArray.map((item) => ({
    label: t('page.rule_management.netType.' + item),
    value: item,
    key: item,
    disabled: false
  }))
)

const { loading, refresh } = useRequest(getBlackList, {
  defaultParams: ['netType'],
  onSuccess: (res) => {
    if (res.success) {
      selectedKeys.value = res.data.netType
    } else {
      Message.error({ content: res.message, resetOnHover: true })
    }
  }
})

const handleSave = async () => {
  try {
    const resp = await updateRuleTypeBackList(selectedKeys.value)
    if (!resp.success) {
      throw new Error(resp.message)
    }
    Message.success({ content: resp.message, resetOnHover: true })
    refresh()
  } catch (e: unknown) {
    if (e instanceof Error) {
      Message.error({ content: e.message, resetOnHover: true })
    }
  }
}
</script>
<style scoped>
.edit-btn {
  color: rgb(var(--gray-8));
  font-size: 16px;
}
</style>
