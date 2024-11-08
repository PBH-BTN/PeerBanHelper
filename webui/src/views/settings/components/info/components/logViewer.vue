<template>
  <a-space direction="vertical" class="container" size="medium">
    <a-space v-if="!loading" size="large">
      <a-space>
        <a-switch v-model="enableAutoRefresh" :before-change="changeAutoRefresh" />{{
          t('page.settings.tab.info.log.enableAutoRefresh')
        }}
      </a-space>
      <a-space>
        <a-switch v-model="hideBanWave" />{{ t('page.settings.tab.info.log.hideBanWave') }}
      </a-space>
      <a-space>
        <a-switch v-model="showThreadName" />{{ t('page.settings.tab.info.log.showThread') }}
      </a-space>
      <a-space v-if="enableAutoRefresh">
        <a-switch v-model="autoScroll" />{{ t('page.settings.tab.info.log.autoScorll') }}
      </a-space>
    </a-space>
    <a-list
      ref="logList"
      size="small"
      :loading="loading"
      scrollbar
      :virtual-list-props="{
        height: 700,
        buffer: 20
      }"
      :data="list"
    >
      <template #item="{ item, index }">
        <a-list-item :key="index">
          <a-space class="log-line" fill>
            <a-tag class="level-tag" :color="getColorByLogLevel(item.level)">{{
              item.level
            }}</a-tag>
            <a-tag>{{ d(item.time, 'log') }}</a-tag>
            <a-tag v-if="showThreadName" :color="getThreadColor(item.thread)">
              {{ item.thread }}
            </a-tag>
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
import { GetHistoryLogs, StreamLogger } from '@/service/logger'
import { getColor } from '@/utils/color'
import { List, Message } from '@arco-design/web-vue'
import { computed, onBeforeUnmount, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRequest } from 'vue-request'
const { t, d } = useI18n()
const hideBanWave = ref(false)
const autoScroll = ref(false)
const logBuffer = reactive([] as Log[])
const { loading } = useRequest(GetHistoryLogs, {
  onSuccess: (data) => {
    logBuffer.splice(0, logBuffer.length)
    logBuffer.push(...data.data)
  }
})

const list = computed(() =>
  hideBanWave.value ? logBuffer.filter((log) => log.thread !== 'Ban Wave') : logBuffer
)

const logList = ref<typeof List>()
const ws = new StreamLogger()
const enableAutoRefresh = ref(false)
const changeAutoRefresh = async (enable: boolean | string | number) => {
  try {
    if (enable) {
      console.log('open auto refresh')
      return ws.open(
        logBuffer.length > 0 ? logBuffer[logBuffer.length - 1].offset : 0,
        (newLog) => {
          logBuffer.push(newLog)
          console.log('scroll to', logBuffer.length - 1)
          if (autoScroll.value) {
            logList.value?.scrollIntoView({
              index: logBuffer.length - 1,
              align: 'bottom'
            } as ScrollIntoViewOptions)
          }
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

const getThreadColor = (thread: string) => {
  if (thread.startsWith('virtual-') || thread.startsWith('pool') || /Thread-[0-9]+/.test(thread)) {
    return 'gray'
  } else {
    return getColor(thread, ['orange', 'orangered', 'red', 'blue'])
  }
}

onBeforeUnmount(() => {
  console.log('close ws')
  ws.close()
})

const showThreadName = ref(true)
</script>

<style scoped>
.container {
  width: 70rem;
}
.level-tag {
  width: 4.5em;
  text-align: center;
  display: flex;
  justify-content: center;
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
