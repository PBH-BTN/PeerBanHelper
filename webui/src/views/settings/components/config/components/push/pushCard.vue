<template>
  <a-card hoverable :style="{ width: '15rem', marginBottom: '20px' }">
    <div
      :style="{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between'
      }"
    >
      <span :style="{ display: 'flex', alignItems: 'center', color: '#1D2129' }">
        <a-avatar
          :style="{ marginRight: '8px', backgroundColor: colorList[channel.type] }"
          :size="28"
        >
          {{ avatarList[channel.type] }}
        </a-avatar>
        <a-typography-text>{{ channel.name }}</a-typography-text>
      </span>
      <a-space warp size="mini">
        <a-tooltip :content="t('page.settings.tab.config.push.edit')" position="top" mini>
          <a-button class="edit-btn" shape="circle" type="text" @click="() => handleEdit()">
            <template #icon>
              <icon-edit />
            </template>
          </a-button>
        </a-tooltip>
        <a-popconfirm
          :content="t('page.settings.tab.config.push.deleteConfirm')"
          type="warning"
          @before-ok="() => handleDelete(channel.name)"
        >
          <a-button class="edit-btn" status="danger" shape="circle" type="text">
            <template #icon>
              <icon-delete />
            </template>
          </a-button>
        </a-popconfirm>
      </a-space>
    </div>
  </a-card>
</template>
<script setup lang="ts">
import { PushType, type PushConfig } from '@/api/model/push'
import { DeletePushChannel } from '@/service/push'
import { Message } from '@arco-design/web-vue'
import type { CSSProperties } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const { channel } = defineProps<{
  channel: PushConfig
}>()
const colorList: Record<PushType, CSSProperties['color']> = {
  [PushType.Email]: '#ffb400',
  [PushType.Telegram]: '#165DFF',
  [PushType.ServerChan]: '#b71de8',
  [PushType.PushPlus]: '#f53f3f',
  [PushType.Bark]: '#a4a6ab'
}
const avatarList: Record<PushType, string> = {
  [PushType.Email]: 'M',
  [PushType.Telegram]: 'T',
  [PushType.ServerChan]: 'S',
  [PushType.PushPlus]: 'P',
  [PushType.Bark]: 'B'
}
const emits = defineEmits<{
  (e: 'deleted'): void
  (e: 'edit'): void
}>()
const handleDelete = async (name: string) => {
  try {
    const res = await DeletePushChannel(name)
    if (res.success) {
      Message.success(res.message)
      emits('deleted')
      return true
    } else {
      throw new Error(res.message)
    }
  } catch (e: unknown) {
    if (e instanceof Error) Message.error({ content: e.message, resetOnHover: true })
    return false
  }
}
const handleEdit = () => {
  emits('edit')
}
</script>
<style scoped>
.edit-btn {
  color: rgb(var(--gray-8));
  font-size: 16px;
}

.align-right {
  display: flex;
  justify-content: flex-end;
}
</style>
