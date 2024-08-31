<template>
  <a-row justify="center" style="margin: 2% auto 0; width: 100%">
    <a-col :xl="16" :md="18" :sm="24">
      <a-space direction="vertical" style="display: flex; justify-content: center">
        <a-steps :current="current">
          <a-step v-for="step of steps" :key="step.title" :description="step.description">{{
            step.title
          }}</a-step>
        </a-steps>
        <div
          :style="{
            width: '100%',
            minHeight: '50vh',
            textAlign: 'center',
            position: 'relative'
          }"
        >
          <Suspense>
            <component :is="componentList[current - 1]" v-model="initConfig" />
          </Suspense>
        </div>
        <a-space size="large" style="display: flex; justify-content: center">
          <a-button v-if="current > 1" type="secondary" @click="onPrev">
            <IconLeft /> {{ t('page.oobe.action.back') }}
          </a-button>
          <a-button v-if="current < 4" type="primary" :disabled="!canNext()" @click="onNext">
            {{ t('page.oobe.action.next') }}
            <IconRight />
          </a-button>
        </a-space>
      </a-space>
    </a-col>
  </a-row>
</template>

<script lang="ts" setup>
import {computed, defineAsyncComponent, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {type InitConfig} from '@/api/model/oobe'
import type {downloaderConfig} from '@/api/model/downloader'

const { t } = useI18n()
const current = ref(1)
const steps = computed(() => [
  {
    title: t('page.oobe.steps.welcome'),
    description: ''
  },
  {
    title: t('page.oobe.steps.setToken.title'),
    description: t('page.oobe.steps.setToken.description')
  },
  {
    title: t('page.oobe.steps.addDownloader.title'),
    description: ''
  },
  {
    title: t('page.oobe.steps.success.title'),
    description: t('page.oobe.steps.success.description')
  }
])

const initConfig = ref<InitConfig>({
  token: '',
  downloaderConfig: {
    name: '',
    config: {
      basicAuth: {},
      verifySsl: true,
      httpVersion: 'HTTP_1_1',
      incrementBan: true
    } as downloaderConfig
  },
  valid: false
})

const componentList = [
  defineAsyncComponent(() => import('./components/welcome.vue')),
  defineAsyncComponent(() => import('./components/setToken.vue')),
  defineAsyncComponent(() => import('./components/addDownloader.vue')),
  defineAsyncComponent(() => import('./components/result.vue'))
]

const onPrev = () => {
  current.value = Math.max(1, current.value - 1)
}

const canNext = () => {
  switch (current.value) {
    case 1:
      return true
    case 2:
      return initConfig.value.token.length > 0
    case 3:
      return initConfig.value.valid
    case 4:
      return false
  }
}

const onNext = () => {
  current.value = Math.min(4, current.value + 1)
}
</script>
