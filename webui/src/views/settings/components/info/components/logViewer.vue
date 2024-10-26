<template>
  <a-space direction="vertical" class="container" size="medium">
    <a-space v-if="!loading">
      <a-switch v-model="enableAutoRefresh" :before-change="changeAutoRefresh" />{{
        t('page.settings.tab.info.log.enableAutoRefresh')
      }}
    </a-space>
    <a-list
      ref="logList"
      size="small"
      :loading="loading"
      scrollbar
      :virtual-list-props="{
        height: 600,
        buffer: 20
      }"
      :data="logBuffer"
    >
      <template #item="{ item, index }">
        <a-list-item :key="index">
          <a-space class="log-line" fill>
            <a-tag>{{ d(item.time, 'log') }}</a-tag>
            <a-tag :color="getColor(item.thread, ['orange', 'orangered', 'red', 'blue'])">
              {{ item.thread }}
            </a-tag>
            <a-tag :color="getColorByLogLevel(item.level)">{{ item.level }}</a-tag>
            <a-typography-text
              :ellipsis="{
                rows: 1,
                showTooltip: true
              }"
              >{{ item.content }}</a-typography-text
            >
          </a-space>
        </a-list-item>
      </template>
    </a-list>
  </a-space>
</template>
<script setup lang="ts">
import { LogLevel, type Log } from '@/api/model/log'
import { GetHistoryLogs } from '@/service/logger'
import { onBeforeUnmount, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRequest } from 'vue-request'
import { StreamLogger } from '@/service/logger'
import { List, Message } from '@arco-design/web-vue'
import { getColor } from '@/utils/color'
const { t, d } = useI18n()
const logBuffer = ref([] as Log[])
const { loading } = useRequest(GetHistoryLogs, {
  onSuccess: (data) => {
    logBuffer.value = data.data
  }
})
const logList = ref<typeof List>()
const ws = new StreamLogger()
const enableAutoRefresh = ref(false)
const changeAutoRefresh = async (enable: boolean | string | number) => {
  try {
    if (enable) {
      console.log('open auto refresh')
      return ws.open(
        logBuffer.value.length > 0 ? logBuffer.value[logBuffer.value.length - 1].offset : 0,
        (newLog) => {
          logBuffer.value.push(newLog)
          console.log('scroll to', logBuffer.value.length - 1)
          logList.value?.scrollIntoView({
            index: logBuffer.value.length - 1,
            align: 'bottom'
          } as ScrollIntoViewOptions)
        },
        (err) => {
          Message.error(err.message)
          enableAutoRefresh.value = false
        }
      )
    } else {
      ws.close()
      return true
    }
  } catch (e) {
    if (e instanceof Error) Message.error(e.message)
    return false
  }
}

const getColorByLogLevel = (level: LogLevel) => {
  switch (level) {
    case LogLevel.TRACE:
      return 'blue'
    case LogLevel.WARN:
      return 'orange'
    case LogLevel.ERROR:
      return 'red'
    case LogLevel.DEBUG:
    case LogLevel.INFO:
    default:
      return 'gray'
  }
}
onBeforeUnmount(() => {
  console.log('close ws')
  ws.close()
})
</script>

<style scoped>
.container {
  width: 70rem;
}
</style>

<style lang="less">
.log-line {
  .arco-space-item {
    display: flex;
    align-items: center;
    .arco-typography {
      width: 50rem;
      margin-bottom: 0;
    }
  }
}
</style>
