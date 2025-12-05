<template>
  <div class="btn-query-container">
    <a-spin v-if="loading" :tip="t('page.ipList.btnQuery.loading')" class="loading-container">
      <div class="loading-placeholder"></div>
    </a-spin>
    <a-empty
      v-else-if="error"
      :description="error.message || t('page.ipList.btnQuery.error')"
      class="error-container"
    >
      <template #image>
        <icon-close-circle style="font-size: 64px; color: var(--color-text-3)" />
      </template>
    </a-empty>
    <iframe
      v-else-if="iframeUrl"
      :src="iframeUrl"
      class="btn-iframe"
      frameborder="0"
      allowfullscreen
      sandbox="allow-scripts allow-same-origin allow-forms allow-popups allow-popups-to-escape-sandbox"
    />
  </div>
</template>

<script setup lang="ts">
import {GetBtnQueryIframe} from '@/service/data'
import {computed, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRequest} from 'vue-request'

const props = defineProps<{
  ip: string
}>()

const { t } = useI18n()

const { data, loading, error, run } = useRequest(() => GetBtnQueryIframe(props.ip), {
  manual: false
})

// Re-run the request when IP changes
watch(
  () => props.ip,
  () => {
    run()
  }
)

const iframeUrl = computed(() => {
  return data.value?.data || null
})
</script>

<style scoped>
.btn-query-container {
  width: 100%;
  min-height: 600px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.loading-container {
  width: 100%;
  height: 600px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.loading-placeholder {
  width: 100%;
  height: 100%;
}

.error-container {
  padding: 60px 20px;
}

.btn-iframe {
  width: 100%;
  height: 800px;
  border: 1px solid var(--color-border-2);
  border-radius: 4px;
  background-color: var(--color-bg-1);
  transition: all 0.2s;
}
</style>
