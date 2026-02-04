<template>
  <div class="oobe-container">
    <div class="corner-button-wrapper">
      <a-button class="corner-button" @click="toogleAdvanceSwitch">
        {{ showHidden ? t('page.oobe.advance.advance.hide') : t('page.oobe.advance.advance.show') }}
        <template #icon>
          <icon-face-frown-fill />
        </template>
      </a-button>
    </div>
    <a-steps :current="current" direction="vertical" class="oobe-steps">
      <a-step v-for="step of steps" :key="step.title" :description="step.description">{{
        step.title
      }}</a-step>
    </a-steps>
    <div class="oobe-main">
      <div class="oobe-content">
        <Suspense>
          <component :is="componentList[current - 1]" v-model="initConfig" />
        </Suspense>
      </div>
      <a-space size="large" class="oobe-footer">
        <a-button v-if="current > 1" type="secondary" @click="onPrev">
          <IconLeft /> {{ t('page.oobe.action.back') }}
        </a-button>
        <a-button
          v-if="current < oobeSteps.length"
          type="primary"
          :disabled="!canNext"
          @click="onNext"
        >
          {{ t('page.oobe.action.next') }}
          <IconRight />
        </a-button>
      </a-space>
    </div>
  </div>
</template>

<script lang="ts" setup>
import type { downloaderConfig } from '@/api/model/downloader'
import { type InitConfig } from '@/api/model/oobe'
import { computed, defineAsyncComponent, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { oobeSteps } from './steps'

const { t } = useI18n()
const current = ref(1)
const showHidden = ref(false)

const steps = computed(() =>
  oobeSteps
    .filter((step) => showHidden.value || !step.hidden)
    .map((step) => ({
      title: t(step.titleKey),
      description: step.descriptionKey ? t(step.descriptionKey) : ''
    }))
)

const toogleAdvanceSwitch = () => {
  showHidden.value = !showHidden.value
  if (!showHidden.value) {
    initConfig.value.database = { type: 'sqlite' }
  }
}

const initConfig = ref<InitConfig>({
  acceptPrivacy: false,
  token: '',
  downloader: {
    id: '',
    config: {
      basicAuth: {},
      verifySsl: true,
      httpVersion: 'HTTP_1_1',
      incrementBan: true
    } as downloaderConfig
  },
  btn: {
    enabled: true,
    submit: true,
    app_id: null,
    app_secret: null
  },
  database: {
    type: 'sqlite'
  },
  downloaderValid: false,
  databaseValid: false
})

const componentList = oobeSteps.map((step) =>
  defineAsyncComponent(
    step.component as () => Promise<{
      default: ReturnType<(typeof import('vue'))['defineComponent']>
    }>
  )
)

const canNext = computed(() => {
  const stepConfig = oobeSteps[current.value - 1]
  if (stepConfig?.canNext) {
    return stepConfig.canNext(initConfig.value)
  }
  return true
})

const onPrev = () => {
  current.value = Math.max(1, current.value - 1)
}

const onNext = () => {
  current.value = Math.min(oobeSteps.length, current.value + 1)
}
</script>

<style scoped>
.oobe-container {
  position: relative;
  min-height: calc(100vh - 220px);
  width: 80%;
  padding: 2% 0;
}

.oobe-steps {
  position: absolute;
  left: 0;
  top: 2%;
  width: 200px;
}

.oobe-main {
  display: flex;
  flex-direction: column;
  min-height: calc(100vh - 220px);
  margin-left: 220px;
}

.oobe-content {
  flex: 1;
  text-align: center;
}

.oobe-footer {
  margin-top: auto;
  display: flex;
  justify-content: center;
  padding-top: 24px;
}

.corner-button-wrapper {
  position: absolute;
  top: 0;
  right: 0;
  padding: 8px;
  z-index: 100;
}

.corner-button {
  opacity: 0;
  transition: opacity 0.3s ease;
}

.corner-button-wrapper:hover .corner-button {
  opacity: 1;
}
</style>
