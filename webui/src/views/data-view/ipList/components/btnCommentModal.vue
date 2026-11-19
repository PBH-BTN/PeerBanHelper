<template>
  <a-modal
    v-model:visible="visible"
    :title="title"
    :ok-text="t('page.ipList.btn.publish')"
    :ok-button-props="{ disabled: !canPublish || !draft.content.trim() }"
    :cancel-button-props="{ disabled: submitting }"
    :on-before-cancel="() => !submitting"
    @before-ok="submitComment"
  >
    <a-form :model="draft" layout="vertical" :disabled="submitting">
      <a-form-item field="scope" :label="t('page.ipList.btn.commentScope')">
        <a-select v-model="draft.scope" :disabled="!!parent">
          <a-option v-for="item in scopes" :key="item.scope" :value="item.scope">{{
            item.scope
          }}</a-option>
        </a-select>
      </a-form-item>
      <a-form-item field="content" :label="t('page.ipList.btn.commentContent')">
        <a-textarea
          v-model="draft.content"
          :auto-size="{ minRows: 4, maxRows: 10 }"
          :max-length="maxLength"
          show-word-limit
        />
      </a-form-item>
      <a-alert v-if="submitError" type="error">{{ submitError }}</a-alert>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import type { BTNIPComment } from '@/api/model/btn'
import { createBtnComment } from '@/service/btnIpQuery'
import { computed, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'

const props = defineProps<{
  scope: string
  scopes: { scope: string; prefix: number }[]
  canPublish: boolean
  maxLength?: number
}>()
const emit = defineEmits<{ published: [scope: string] }>()
const { t } = useI18n()
const visible = ref(false)
const submitting = ref(false)
const submitError = ref('')
const parent = ref<BTNIPComment>()
const draft = reactive({ scope: '', content: '' })
const title = computed(() =>
  parent.value
    ? t('page.ipList.btn.replyTo', {
        author: parent.value.authorNickname || t('page.ipList.btn.anonymous')
      })
    : t('page.ipList.btn.addComment')
)

function open(comment?: BTNIPComment) {
  parent.value = comment
  draft.scope = comment?.network ?? props.scope
  draft.content = ''
  submitError.value = ''
  visible.value = true
}

function addComment() {
  open()
}

function replayComment(comment: BTNIPComment) {
  open(comment)
}

async function submitComment() {
  const content = draft.content.trim()
  if (submitting.value || !props.canPublish || !content) return false
  submitting.value = true
  submitError.value = ''
  try {
    await createBtnComment(draft.scope, content, parent.value?.id ?? null)
    emit('published', draft.scope)
    return true
  } catch (cause) {
    submitError.value = (cause as Error).message || t('page.ipList.btn.publishFailed')
    return false
  } finally {
    submitting.value = false
  }
}

defineExpose({ addComment, replayComment })
</script>
