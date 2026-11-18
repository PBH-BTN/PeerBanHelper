<template>
  <a-space direction="vertical" fill>
    <div class="comment-header">
      <a-space wrap>
        <a-typography-text bold>{{ scope }}</a-typography-text>
        <a-typography-text
          v-if="!loading && !error && result && result.access?.visible !== false"
          type="secondary"
        >
          {{ t('page.ipList.btn.commentTotal', { count: result.total }, result.total) }}
        </a-typography-text>
      </a-space>
      <a-radio-group
        v-model="scope"
        type="button"
        size="small"
        class="scope-switch"
        :aria-label="t('page.ipList.btn.commentScope')"
        @change="run({ scope, page: 1, pageSize })"
      >
        <a-radio v-for="item in scopes" :key="item.scope" :value="item.scope"
          >/{{ item.prefix }}</a-radio
        >
      </a-radio-group>
    </div>
    <a-skeleton v-if="loading" animation aria-busy="true">
      <a-space direction="vertical" fill aria-hidden="true">
        <a-card v-for="index in 2" :key="index" size="small">
          <template #title>
            <a-skeleton-line :rows="1" :widths="['35%']" />
          </template>
          <a-skeleton-line :rows="3" :widths="['22%', '85%', '60%']" />
        </a-card>
      </a-space>
    </a-skeleton>
    <a-alert v-else-if="error" type="error">
      {{ error.message || t('page.ipList.btn.error') }}
      <template #action
        ><a-link @click="refresh()">{{ t('page.ipList.btn.retry') }}</a-link></template
      >
    </a-alert>
    <a-alert v-else-if="result?.access?.visible === false" type="info">{{
      t('page.ipList.btn.commentsUnavailable')
    }}</a-alert>
    <a-space v-else-if="result" direction="vertical" fill>
      <a-empty v-if="!result.records.length" :description="t('page.ipList.btn.noComments')" />
      <a-card v-for="comment in result.records" :key="comment.id" size="small" class="comment-card">
        <template #title>
          <span class="comment-byline">
            #{{ comment.id }}
            <strong>{{ comment.authorNickname || t('page.ipList.btn.anonymous') }}</strong>
            <i18n-t
              :keypath="
                comment.parentId != null ? 'page.ipList.btn.repliedAt' : 'page.ipList.btn.wroteAt'
              "
            >
              <template #parentId>{{ comment.parentId }}</template>
              <template #time>
                <time :datetime="comment.createdAt">{{
                  dayjs(comment.createdAt).format('YYYY-MM-DD HH:mm:ss')
                }}</time>
              </template>
            </i18n-t>
          </span>
        </template>
        <template #extra>
          <a-space wrap>
            <a-tag
              v-if="comment.moderationStatus !== 'APPROVED'"
              :color="comment.moderationStatus === 'REJECTED' ? 'red' : 'gold'"
            >
              {{ t('page.ipList.btn.moderation.' + comment.moderationStatus) }}
            </a-tag>
            <a-tag>{{ comment.network }}</a-tag>
            <a-popconfirm
              v-if="comment.canDelete"
              :content="t('page.ipList.btn.deleteConfirm')"
              @ok="removeComment(comment.id)"
            >
              <a-button
                size="small"
                type="text"
                status="danger"
                :aria-label="t('page.ipList.btn.deleteComment')"
                :title="t('page.ipList.btn.deleteComment')"
                :loading="deletingId === comment.id"
                :disabled="deletingId !== undefined || loading"
              >
                <template #icon><icon-delete /></template>
              </a-button>
            </a-popconfirm>
            <span v-else class="delete-button-placeholder" aria-hidden="true"></span>
          </a-space>
        </template>
        <p class="comment-content">{{ comment.content }}</p>
        <a-alert
          v-if="comment.moderationStatus === 'REJECTED' && comment.moderationReason"
          type="error"
        >
          <template #title>{{ t('page.ipList.btn.rejectionTitle') }}</template>
          {{ comment.moderationReason }}
        </a-alert>
        <p v-else-if="comment.moderationReason">{{ comment.moderationReason }}</p>
      </a-card>
      <a-pagination
        :current="current"
        :total="total"
        :page-size="pageSize"
        :page-size-options="[5, 10, 20, 50]"
        show-page-size
        @change="changeCurrent"
        @page-size-change="changePagination(1, $event)"
      />
    </a-space>
  </a-space>
</template>

<script setup lang="ts">
import { deleteBtnComment, getBtnComments } from '@/service/btnIpQuery'
import { Message } from '@arco-design/web-vue'
import dayjs from 'dayjs'
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { usePagination } from 'vue-request'

const { t } = useI18n()
const props = defineProps<{ scopes: { scope: string; prefix: number }[] }>()
const scope = ref(props.scopes[0]!.scope)
const {
  data: result,
  current,
  pageSize,
  total,
  changeCurrent,
  changePagination,
  loading,
  error,
  run,
  refresh
} = usePagination(getBtnComments, {
  defaultParams: [{ scope: scope.value, page: 1, pageSize: 5 }],
  pagination: { currentKey: 'page', pageSizeKey: 'pageSize', totalKey: 'total' },
  loadingDelay: 0,
  loadingKeep: 0
})

const deletingId = ref<number>()

async function removeComment(id: number) {
  if (deletingId.value !== undefined) return
  const network = scope.value
  deletingId.value = id
  try {
    await deleteBtnComment(id)
    if (scope.value !== network) return
    const lastOnPage =
      !loading.value &&
      current.value > 1 &&
      result.value?.records.length === 1 &&
      result.value.records[0]?.id === id
    if (lastOnPage) changeCurrent(current.value - 1)
    else refresh()
  } catch (cause) {
    Message.error((cause as Error).message || t('page.ipList.btn.deleteFailed'))
  } finally {
    deletingId.value = undefined
  }
}
</script>

<style scoped>
.comment-header {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 16px;
}
.scope-switch {
  display: inline-flex;
  flex-wrap: wrap;
  margin-inline-start: auto;
}
.comment-card :deep(.arco-card-header) {
  height: auto;
  min-height: 40px;
  gap: 4px 12px;
  flex-wrap: wrap;
}
.comment-card :deep(.arco-card-header-title) {
  flex: 1 1 auto;
  white-space: normal;
  overflow-wrap: anywhere;
}
.comment-card :deep(.arco-card-header-extra) {
  margin-inline-start: auto;
}
.delete-button-placeholder {
  display: inline-block;
  width: 28px;
  height: 28px;
  vertical-align: middle;
}
.comment-byline {
  font-weight: normal;
}
.comment-byline strong {
  margin-inline: 0.25em;
}
.comment-byline time {
  color: var(--color-text-3);
  font-size: 12px;
  white-space: nowrap;
}
.comment-content {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
</style>
