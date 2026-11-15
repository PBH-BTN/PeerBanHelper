<template>
  <a-space direction="vertical" :size="16" fill>
    <a-select
      v-model="scope"
      class="comment-scope"
      :aria-label="t('page.ipList.btn.commentScope')"
      :options="details.lookup.commentScopes.map((item) => item.scope)"
      @change="resetPage"
    />
    <a-alert v-if="!canPublish && !loading" type="info">{{
      t('page.ipList.btn.commentsReadOnly')
    }}</a-alert>
    <a-alert v-if="error" type="error">{{ error }}</a-alert>
    <a-space direction="vertical" :size="16" fill>
      <a-space v-if="replyTo || editing">
        <a-tag
          >{{ editing ? t('page.ipList.btn.edit') : t('page.ipList.btn.reply') }} #{{
            (editing ?? replyTo)?.id
          }}</a-tag
        >
        <a-button size="mini" @click="clearDraft">{{ t('page.ipList.btn.cancel') }}</a-button>
      </a-space>
      <a-textarea
        v-model="draft"
        :disabled="!canPublish || saving"
        :max-length="access.maxLength"
        :placeholder="t('page.ipList.btn.comments')"
        show-word-limit
      />
      <a-button
        type="primary"
        :disabled="!canPublish || !draft.trim()"
        :loading="saving"
        @click="save"
      >
        {{ t('page.ipList.btn.publish') }}
      </a-button>
    </a-space>
    <a-spin :loading="loading" style="width: 100%">
      <a-space direction="vertical" :size="16" fill>
        <a-empty v-if="!records.length && !loading" />
        <a-card v-for="comment in records" :key="comment.id" size="small">
          <template #title>
            {{ comment.authorNickname || t('page.ipList.btn.anonymous') }}
            <a-tag>{{ comment.network }}</a-tag>
          </template>
          <a-space wrap>
            <a-typography-text type="secondary">{{
              d(new Date(comment.createdAt), 'long')
            }}</a-typography-text>
            <a-tag v-if="comment.parentId">↪ #{{ comment.parentId }}</a-tag>
            <a-tag v-if="comment.moderationStatus !== 'APPROVED'">
              {{ t('page.ipList.btn.moderation.' + comment.moderationStatus) }}
            </a-tag>
          </a-space>
          <p class="comment-content">{{ comment.content }}</p>
          <p v-if="comment.moderationReason">{{ comment.moderationReason }}</p>
          <a-space wrap>
            <a-button
              v-if="canPublish && comment.moderationStatus === 'APPROVED'"
              size="mini"
              @click="reply(comment)"
              >{{ t('page.ipList.btn.reply') }}</a-button
            >
            <a-button v-if="canPublish && comment.canEdit" size="mini" @click="edit(comment)">
              {{ t('page.ipList.btn.edit') }}
            </a-button>
            <a-popconfirm
              v-if="comment.canDelete"
              :content="t('page.ipList.btn.deleteConfirm')"
              @ok="remove(comment.id)"
            >
              <a-button size="mini" status="danger">{{ t('page.ipList.btn.delete') }}</a-button>
            </a-popconfirm>
          </a-space>
          <details v-if="comment.history.length">
            <summary>{{ t('page.ipList.btn.commentHistory') }}</summary>
            <div v-for="item in comment.history" :key="item.revision">
              <strong>#{{ item.revision }}</strong> {{ d(new Date(item.createdAt), 'long') }}
              <p class="comment-content">{{ item.content }}</p>
            </div>
          </details>
        </a-card>
        <a-pagination
          v-if="total > pageSize"
          :current="page"
          :total="total"
          :page-size="pageSize"
          @change="page = $event"
        />
      </a-space>
    </a-spin>
  </a-space>
</template>

<script setup lang="ts">
import type { BTNIPComment, BTNIPLookup } from '@/api/model/btn'
import { deleteBtnComment, getBtnComments, saveBtnComment } from '@/service/btnIpQuery'
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

const { t, d } = useI18n()
const { details } = defineProps<{ details: BTNIPLookup }>()
const scope = ref(details.lookup.defaultCommentScope)
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const records = ref<BTNIPComment[]>([])
const access = ref(details.lookup.commentAccess)
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const draft = ref('')
const editing = ref<BTNIPComment | null>(null)
const replyTo = ref<BTNIPComment | null>(null)
const canPublish = computed(
  () =>
    details.canComment && access.value.visible && access.value.publishingEnabled && !loading.value
)
let loadGeneration = 0

function clearDraft() {
  draft.value = ''
  editing.value = null
  replyTo.value = null
}

function resetPage() {
  page.value = 1
  clearDraft()
}

async function load() {
  const generation = ++loadGeneration
  loading.value = true
  error.value = ''
  records.value = []
  try {
    const response = await getBtnComments(scope.value, page.value)
    if (generation !== loadGeneration) return
    if (!response?.success) throw new Error(response?.message)
    records.value = response.data.records
    access.value = response.data.access
    total.value = response.data.total
    pageSize.value = response.data.size
  } catch (cause) {
    if (generation !== loadGeneration) return
    access.value = { ...access.value, publishingEnabled: false }
    error.value = (cause as Error).message || t('page.ipList.btn.error')
  } finally {
    if (generation === loadGeneration) loading.value = false
  }
}

function reply(comment: BTNIPComment) {
  replyTo.value = comment
  editing.value = null
  draft.value = ''
}

function edit(comment: BTNIPComment) {
  replyTo.value = null
  editing.value = comment
  draft.value = comment.content
}

async function save() {
  if (!canPublish.value || saving.value || !draft.value.trim()) return
  saving.value = true
  error.value = ''
  try {
    const response = await saveBtnComment(
      draft.value.trim(),
      scope.value,
      replyTo.value?.id ?? null,
      editing.value
    )
    if (!response?.success) throw new Error(response?.message)
    clearDraft()
    if (page.value === 1) await load()
    else page.value = 1
  } catch (cause) {
    const message = (cause as Error).message || t('page.ipList.btn.error')
    await load()
    error.value = message
  } finally {
    saving.value = false
  }
}

async function remove(id: number) {
  try {
    const response = await deleteBtnComment(id)
    if (!response?.success) throw new Error(response?.message)
    if (records.value.length === 1 && page.value > 1) page.value--
    else await load()
  } catch (cause) {
    error.value = (cause as Error).message || t('page.ipList.btn.error')
  }
}

watch([scope, page], load, { immediate: true })
</script>

<style scoped>
.comment-scope {
  width: min(100%, 22rem);
}

.comment-content {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
</style>
