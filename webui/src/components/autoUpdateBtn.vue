<template>
  <a-popover :popup-container="container">
    <a-button
      ref="autoUpdateBtn"
      class="auto-update-btn"
      :type="autoUpdate.autoUpdate ? 'primary' : 'outline'"
      :shape="'circle'"
      @click="() => autoUpdate.refresh()"
    >
      <icon-sync
        id="spin"
        :class="{
          loading: loadingStatus === 'loading' || loadingHolding,
          'loading-holding': loadingStatus === 'idle' && loadingHolding
        }"
      />
    </a-button>
    <template #title>
      <a-space>
        <div>{{ t('navbar.action.autoUpdate') }}</div>
        <a-switch v-model="autoUpdate.autoUpdate" />
      </a-space>
    </template>
    <template #content>
      <p>{{ t('navbar.action.autoUpdate.lastUpdate') }}</p>
      <p>{{ d(autoUpdate.lastUpdate, 'longlong') }}</p>
    </template>
  </a-popover>
</template>

<script setup lang="ts">
import { useAutoUpdate } from '@/stores/autoUpdate'
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
const { t, d } = useI18n()
const autoUpdate = useAutoUpdate()
const autoUpdateBtn = ref()
const loadingHolding = ref(false)

let eventAbortController: AbortController
const container = ref<HTMLElement>()

onMounted(() => {
  container.value = window.document.querySelector('body') as HTMLElement
  eventAbortController = new AbortController()
  autoUpdateBtn.value.$el.addEventListener(
    'animationstart',
    () => {
      loadingHolding.value = true
    },
    { signal: eventAbortController.signal }
  )
  autoUpdateBtn.value.$el.addEventListener(
    'animationend',
    () => {
      loadingHolding.value = false
    },
    { signal: eventAbortController.signal }
  )
})

onUnmounted(() => {
  eventAbortController.abort()
})

const loadingStatus = computed(() => autoUpdate.status)
</script>

<style lang="less" scoped>
.auto-update-btn:not(.arco-btn-primary) {
  border-color: rgb(var(--gray-2));
  color: rgb(var(--gray-8));
}

.auto-update-btn,
.auto-update-btn:hover {
  font-size: 16px;
  #spin {
    &.loading {
      animation: whirl 0.25s linear infinite;
      &.loading-holding {
        animation-iteration-count: 1;
      }
    }
  }
}

@keyframes whirl {
  0% {
    transform: rotate(0deg);
  }
  100% {
    transform: rotate(180deg);
  }
}
</style>
