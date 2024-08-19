<template>
  <slot v-if="plusStatus?.activated"></slot>
  <a-card v-else hoverable :title="title">
    <a-result class="overlay" status="warning" :title="t('page.charts.locked')">
      <template #icon>
        <icon-lock />
      </template>
      <template #subtitle> {{ t('page.charts.locked.tips') }} </template>
      <template #extra>
        <a-button type="primary" @click="activeSubscription">{{
          t('page.charts.locked.active')
        }}</a-button>
      </template>
    </a-result>
    <dummyChart />
  </a-card>
</template>
<script setup lang="ts">
import { useEndpointStore } from '@/stores/endpoint'
import { useI18n } from 'vue-i18n'
import { computed, defineAsyncComponent } from 'vue'
const dummyChart = defineAsyncComponent(() => import('./dummyChart.vue'))
const { t } = useI18n()
const endpointStore = useEndpointStore()

const plusStatus = computed(() => endpointStore.plusStatus)
const { title } = defineProps<{
  title: string
}>()
const activeSubscription = () => {
  endpointStore.emmitter.emit('open-plus-modal')
}
</script>
<style scoped>
.overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background-color: rgba(255, 255, 255, 0.6);
  backdrop-filter: blur(10px);
  color: white;
  font-size: 1.5em;
  font-weight: bold;
  z-index: 1;

  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
}
body[arco-theme='dark'] .overlay {
  background: rgba(0, 0, 0, 0.5); /* 深色模式下的半透明背景 */
  color: #fff; /* 深色模式下的文本颜色 */
}
</style>
