<template>
  <a-modal
    v-model:visible="visible"
    :title="t('page.rule_management.enhancedRuleSubscribe.settings')"
    :ok-text="t('global.save')"
    :cancel-text="t('global.cancel')"
    :ok-loading="loading"
    @ok="handleOk"
    @cancel="handleCancel"
  >
    <a-form ref="formRef" :model="form" :rules="rules" layout="vertical">
      <a-form-item field="checkInterval" :label="t('page.rule_management.enhancedRuleSubscribe.settings.checkInterval')">
        <a-input-number 
          v-model="form.checkInterval" 
          :min="60000"
          :max="86400000 * 7"
          :step="60000"
          :formatter="(value) => `${Math.floor(value / 60000)} min`"
          :parser="(value) => parseInt(value) * 60000"
          :placeholder="t('page.rule_management.enhancedRuleSubscribe.settings.checkIntervalPlaceholder')"
        />
        <template #help>
          {{ t('page.rule_management.enhancedRuleSubscribe.settings.checkIntervalHelp') }}
        </template>
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script lang="ts" setup>
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { Message } from '@arco-design/web-vue'
import { GetEnhancedRuleCheckInterval, UpdateEnhancedRuleCheckInterval } from '@/api/enhancedRuleSubscription'

const { t } = useI18n()

const emit = defineEmits(['refresh'])

const visible = ref(false)
const loading = ref(false)
const formRef = ref()

const form = reactive({
  checkInterval: 86400000 // Default 24 hours
})

const rules = {
  checkInterval: [
    { required: true, message: t('page.rule_management.enhancedRuleSubscribe.settings.checkIntervalRequired') },
    { 
      validator: (value: number, callback: (error?: string) => void) => {
        if (value < 60000) {
          callback(t('page.rule_management.enhancedRuleSubscribe.settings.checkIntervalTooSmall'))
        } else {
          callback()
        }
      }
    }
  ]
}

const showModal = async () => {
  try {
    const result = await GetEnhancedRuleCheckInterval()
    if (result.success) {
      form.checkInterval = result.data || 86400000
    }
  } catch (error) {
    console.error('Failed to load check interval:', error)
  }
  
  visible.value = true
}

const handleOk = async () => {
  try {
    const valid = await formRef.value?.validate()
    if (!valid) return
    
    loading.value = true
    
    const result = await UpdateEnhancedRuleCheckInterval(form.checkInterval)
    
    if (result.success) {
      Message.success({ content: result.message, resetOnHover: true })
      visible.value = false
      emit('refresh')
    } else {
      Message.error({ content: result.message, resetOnHover: true })
    }
  } catch (error) {
    Message.error({ content: 'Update failed', resetOnHover: true })
  } finally {
    loading.value = false
  }
}

const handleCancel = () => {
  visible.value = false
}

defineExpose({
  showModal
})
</script>