<template>
  <a-modal
    v-model:visible="visible"
    :title="modalTitle"
    :ok-text="t('global.save')"
    :cancel-text="t('global.cancel')"
    :ok-loading="loading"
    @ok="handleOk"
    @cancel="handleCancel"
  >
    <a-form ref="formRef" :model="form" :rules="rules" layout="vertical">
      <a-form-item field="ruleId" :label="t('page.rule_management.enhancedRuleSubscribe.form.ruleId')">
        <a-input v-model="form.ruleId" :disabled="mode === 'edit'" :placeholder="t('page.rule_management.enhancedRuleSubscribe.form.ruleIdPlaceholder')" />
      </a-form-item>
      
      <a-form-item field="ruleName" :label="t('page.rule_management.enhancedRuleSubscribe.form.ruleName')">
        <a-input v-model="form.ruleName" :placeholder="t('page.rule_management.enhancedRuleSubscribe.form.ruleNamePlaceholder')" />
      </a-form-item>
      
      <a-form-item field="ruleType" :label="t('page.rule_management.enhancedRuleSubscribe.form.ruleType')">
        <a-select v-model="form.ruleType" :placeholder="t('page.rule_management.enhancedRuleSubscribe.form.ruleTypePlaceholder')">
          <a-option v-for="ruleType in ruleTypes" :key="ruleType.code" :value="ruleType.code">
            <a-tag :color="getRuleTypeColor(ruleType.code)">
              {{ getRuleTypeLabel(ruleType.code) }}
            </a-tag>
            <span style="margin-left: 8px">{{ getRuleTypeDescription(ruleType.code) }}</span>
          </a-option>
        </a-select>
      </a-form-item>
      
      <a-form-item field="subUrl" :label="t('page.rule_management.enhancedRuleSubscribe.form.subUrl')">
        <a-input v-model="form.subUrl" :placeholder="t('page.rule_management.enhancedRuleSubscribe.form.subUrlPlaceholder')" />
      </a-form-item>
      
      <a-form-item field="description" :label="t('page.rule_management.enhancedRuleSubscribe.form.description')">
        <a-textarea 
          v-model="form.description" 
          :placeholder="t('page.rule_management.enhancedRuleSubscribe.form.descriptionPlaceholder')"
          :auto-size="{ minRows: 2, maxRows: 4 }"
        />
      </a-form-item>
      
      <a-form-item field="enabled" label="">
        <a-checkbox v-model="form.enabled">
          {{ t('page.rule_management.enhancedRuleSubscribe.form.enabled') }}
        </a-checkbox>
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script lang="ts" setup>
import { computed, ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { Message } from '@arco-design/web-vue'
import { CreateEnhancedRule, UpdateEnhancedRule, GetEnhancedRuleTypes } from '@/api/enhancedRuleSubscription'

const { t } = useI18n()

const emit = defineEmits(['refresh'])

const visible = ref(false)
const loading = ref(false)
const mode = ref<'add' | 'edit'>('add')
const formRef = ref()
const ruleTypes = ref([])

const form = reactive({
  ruleId: '',
  ruleName: '',
  ruleType: '',
  subUrl: '',
  description: '',
  enabled: true
})

const modalTitle = computed(() => {
  return mode.value === 'add' 
    ? t('page.rule_management.enhancedRuleSubscribe.addRule')
    : t('page.rule_management.enhancedRuleSubscribe.editRule')
})

const rules = {
  ruleId: [
    { required: true, message: t('page.rule_management.enhancedRuleSubscribe.form.ruleIdRequired') }
  ],
  ruleName: [
    { required: true, message: t('page.rule_management.enhancedRuleSubscribe.form.ruleNameRequired') }
  ],
  ruleType: [
    { required: true, message: t('page.rule_management.enhancedRuleSubscribe.form.ruleTypeRequired') }
  ],
  subUrl: [
    { required: true, message: t('page.rule_management.enhancedRuleSubscribe.form.subUrlRequired') },
    { 
      validator: (value: string, callback: (error?: string) => void) => {
        try {
          new URL(value)
          callback()
        } catch {
          callback(t('page.rule_management.enhancedRuleSubscribe.form.subUrlInvalid'))
        }
      }
    }
  ]
}

// Rule type color mapping
const ruleTypeColors = {
  'ip_blacklist': 'red',
  'peer_id': 'blue',
  'client_name': 'green', 
  'substring_match': 'orange',
  'prefix_match': 'purple',
  'exception_list': 'cyan',
  'script_engine': 'magenta'
}

// Rule type label mapping
const ruleTypeLabels = {
  'ip_blacklist': 'IP Blacklist',
  'peer_id': 'PeerID',
  'client_name': 'Client Name',
  'substring_match': 'Substring Match',
  'prefix_match': 'Prefix Match',
  'exception_list': 'Exception List',
  'script_engine': 'Script Engine'
}

// Rule type description mapping
const ruleTypeDescriptions = {
  'ip_blacklist': 'Block IP addresses',
  'peer_id': 'Block specific PeerIDs',
  'client_name': 'Block client names',
  'substring_match': 'Block if contains substring',
  'prefix_match': 'Block if starts with prefix',
  'exception_list': 'Allow listed items (whitelist)',
  'script_engine': 'Custom script evaluation'
}

const showModal = (modalMode: 'add' | 'edit', record?: any) => {
  mode.value = modalMode
  
  if (modalMode === 'add') {
    resetForm()
  } else if (record) {
    form.ruleId = record.ruleId
    form.ruleName = record.ruleName
    form.ruleType = record.ruleType
    form.subUrl = record.subUrl
    form.description = record.description || ''
    form.enabled = record.enabled
  }
  
  visible.value = true
}

const resetForm = () => {
  form.ruleId = ''
  form.ruleName = ''
  form.ruleType = ''
  form.subUrl = ''
  form.description = ''
  form.enabled = true
}

const handleOk = async () => {
  try {
    const valid = await formRef.value?.validate()
    if (!valid) return
    
    loading.value = true
    
    const ruleData = {
      ruleId: form.ruleId,
      ruleName: form.ruleName,
      ruleType: form.ruleType,
      subUrl: form.subUrl,
      description: form.description,
      enabled: form.enabled
    }
    
    let result
    if (mode.value === 'add') {
      result = await CreateEnhancedRule(ruleData)
    } else {
      result = await UpdateEnhancedRule(form.ruleId, ruleData)
    }
    
    if (result.success) {
      Message.success({ content: result.message, resetOnHover: true })
      visible.value = false
      emit('refresh')
    } else {
      Message.error({ content: result.message, resetOnHover: true })
    }
  } catch (error) {
    Message.error({ content: 'Operation failed', resetOnHover: true })
  } finally {
    loading.value = false
  }
}

const handleCancel = () => {
  visible.value = false
  resetForm()
}

const getRuleTypeColor = (ruleType: string): string => {
  return ruleTypeColors[ruleType as keyof typeof ruleTypeColors] || 'gray'
}

const getRuleTypeLabel = (ruleType: string): string => {
  return ruleTypeLabels[ruleType as keyof typeof ruleTypeLabels] || ruleType
}

const getRuleTypeDescription = (ruleType: string): string => {
  return ruleTypeDescriptions[ruleType as keyof typeof ruleTypeDescriptions] || ''
}

const loadRuleTypes = async () => {
  try {
    const result = await GetEnhancedRuleTypes()
    if (result.success) {
      ruleTypes.value = result.data || []
    }
  } catch (error) {
    console.error('Failed to load rule types:', error)
  }
}

onMounted(() => {
  loadRuleTypes()
})

defineExpose({
  showModal
})
</script>