<template>
  <a-trigger
    v-model:popup-visible="visable"
    trigger="click"
    unmount-on-close
    show-arrow
    :popup-translate="[0, 10]"
  >
    <a-tooltip
      :content="
        shouldHaveDot
          ? t('alert.tooltips.non-empty', { count: list.length })
          : t('alert.tooltips.empty')
      "
    >
      <a-badge :count="shouldHaveDot" dot>
        <a-button class="nav-btn" type="outline" shape="circle" status="normal">
          <template #icon><icon-notification /></template>
        </a-button>
      </a-badge>
    </a-tooltip>
    <template #content>
      <a-card :title="t('alert.title')" class="alert-card">
        <template #extra>
          <a-button
            type="text"
            :disabled="!list.some((alert) => alert.readAt === null)"
            @click="markAllAsRead()"
            >{{ t('alert.action.dismissAll') }}</a-button
          >
        </template>
        <a-list
          :bordered="false"
          :data="list"
          :virtual-list-props="{
            height: 500,
            threshold: 4
          }"
        >
          <template #item="{ item, index }">
            <a-list-item class="list-item">
              <a-list-item-meta>
                <template #title>
                  <a-space>
                    <a-typography-text
                      :disabled="item.readAt !== null"
                      :type="getColor(item.level)"
                    >
                      <icon-info-circle-fill v-if="item.level === Level.Info" />
                      <icon-info-circle-fill v-else-if="item.level === Level.Tip" />
                      <icon-close-circle-fill v-else-if="item.level === Level.Fatal" />
                      <icon-exclamation-circle-fill v-else-if="item.level === Level.Warn" />
                      <icon-close-circle-fill v-else-if="item.level === Level.Error" />
                    </a-typography-text>
                    <a-typography-text :disabled="item.readAt !== null">
                      {{ item.title }}
                    </a-typography-text>
                  </a-space>
                </template>
                <template #description>
                  <a-space direction="vertical" size="mini">
                    <a-typography-text type="secondary"
                      ><icon-clock-circle /> {{ d(item.createAt, 'long') }}</a-typography-text
                    >
                    <Markdown
                      v-if="item.readAt === null"
                      class="md-container"
                      style="max-width: 25rem"
                      :content="item.content"
                    />
                    <del v-else>
                      <Markdown
                        class="md-container"
                        style="max-width: 25rem"
                        :content="item.content"
                      />
                    </del>
                  </a-space>
                </template>
              </a-list-item-meta>
              <template #actions>
                <a-space>
                  <a-tooltip v-if="item.readAt === null" :content="t('alert.action.markAsRead')">
                    <a-button
                      class="edit-btn"
                      status="danger"
                      shape="circle"
                      type="text"
                      @click="markAsRead(index)"
                    >
                      <template #icon>
                        <icon-notification-close />
                      </template>
                    </a-button>
                  </a-tooltip>
                </a-space>
              </template>
            </a-list-item>
          </template>
        </a-list>
      </a-card>
    </template>
  </a-trigger>
</template>
<script setup lang="ts">
import { Level, type Alert } from '@/api/model/alert'
import { DismissAlert, DismissAll, GetUnreadAlerts } from '@/service/alert'
import { useAutoUpdatePlugin } from '@/stores/autoUpdate'
import { Button, Message, Notification } from '@arco-design/web-vue'
import { computed, h, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRequest } from 'vue-request'
import Markdown from './markdown.vue'
const { t, d } = useI18n()
const visable = ref(false)
useRequest(
  GetUnreadAlerts,
  {
    onSuccess: (data) => {
      // 打开时不刷新，避免突然消失
      if (!visable.value) {
        if (
          // 当前没有通知
          !shouldHaveDot.value &&
          // 有新的高级别通知
          data.data.some(
            (alert) =>
              alert.readAt === null &&
              (alert.level == Level.Error ||
                alert.level == Level.Warn ||
                alert.level == Level.Fatal)
          )
        ) {
          const closeHandler = Notification.warning({
            title: t('alert.newAlert'),
            content: t('alert.newAlert.tips'),
            footer: () =>
              h(
                Button,
                {
                  onClick: () => {
                    closeHandler.close()
                    visable.value = true
                  },
                  type: 'primary'
                },
                () => t('alert.newAlert.action')
              )
          })
        }
        list.value = data.data
      } else {
        const newAlerts = data.data.filter((i) => !list.value.some((j) => j.id === i.id))
        list.value.push(...newAlerts)
      }
    }
  },
  [useAutoUpdatePlugin]
)

const shouldHaveDot = computed(() => list.value.filter((i) => i.readAt === null)?.length ?? 0)

const list = ref([] as Alert[])
const getColor = (level: Level) => {
  switch (level) {
    case Level.Info:
      return 'primary'
    case Level.Tip:
      return 'success'
    case Level.Warn:
      return 'warning'
    case Level.Error:
      return 'danger'
    case Level.Fatal:
      return 'danger'
    default:
      return 'primary'
  }
}

const markAsRead = (index: number) => {
  list.value[index].readAt = Date.now()
  DismissAlert(list.value[index].id).catch((e) => {
    if (e instanceof Error) Message.error({ content: e.message, resetOnHover: true })
  })
}
const markAllAsRead = () => {
  list.value.forEach((item) => {
    item.readAt = Date.now()
  })
  DismissAll().catch((e) => {
    if (e instanceof Error) Message.error({ content: e.message, resetOnHover: true })
  })
}
</script>
<style scoped lang="less">
.navbar {
  display: flex;
  justify-content: space-between;
  height: 100%;
  background-color: var(--color-bg-2);
  border-bottom: 1px solid var(--color-border);
}
.right-side {
  .nav-btn {
    border-color: rgb(var(--gray-2));
    color: rgb(var(--gray-8));
    font-size: 16px;
  }
}
.list-item {
  padding: 0;
}
.edit-btn {
  color: rgb(var(--gray-8));
  font-size: 16px;
}
</style>
<style lang="less">
.alert-card {
  width: 30rem;
  box-shadow: #ccc 0px 0px 10px;
  .arco-card-body {
    padding: 0;
  }
}
body[arco-theme='dark'] .alert-card {
  box-shadow: #555 0px 0px 3px;
}
.md-container {
  p {
    margin-top: 0;
  }
}
</style>
