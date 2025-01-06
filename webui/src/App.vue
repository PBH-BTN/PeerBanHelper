<template>
  <a-alert v-if="endPointStore.globalConfig?.globalPaused" type="warning" banner center>
    {{ t('global.pause.alert') }}
    <template #action>
      <AsyncMethod
        v-slot="{ run, loading }"
        once
        :async-fn="
          async () => {
            await endPointStore.updateGlobalConfig({ globalPaused: false })
          }
        "
      >
        <a-button size="small" :loading="loading" status="warning" type="text" @click="run">{{
          t('global.pause.alert.disable')
        }}</a-button>
      </AsyncMethod>
    </template>
  </a-alert>
  <a-config-provider :locale="ArcoI18nMessages[locale]">
    <a-layout>
      <a-layout-header>
        <pageHeader :disable-auto-update="specialStatus" :disable-menu="specialStatus" />
      </a-layout-header>
      <a-layout-content v-if="status === 'needLogin'" class="login-page">
        <Login style="width: 100%" />
      </a-layout-content>
      <a-layout-content v-else-if="status === 'needInit'">
        <OOBE />
      </a-layout-content>
      <a-layout-content v-else>
        <div style="width: 100%; position: relative">
          <router-view v-slot="{ Component, route }">
            <transition
              :name="String(route.meta.transition)"
              @before-enter="onBeforeEnter"
              @after-enter="onAfterEnter"
            >
              <a-result
                v-if="
                  route.meta?.moduleRequire &&
                  !isModuleEnable(endPointStore.serverManifest, String(route.meta?.moduleRequire))
                "
                status="403"
                :title="t('router.moduleNotEnable', { moduleName: t(String(route.meta?.label)) })"
              >
                <template #subtitle>
                  <a-typography-text style="font-size: 0.8rem">{{
                    t('router.moduleNotEnable.tips')
                  }}</a-typography-text>
                </template>

                <template #extra>
                  <a-space>
                    <a-button :href="String(route.meta?.documentation)" type="primary">
                      {{ t('router.moduleNotEnable.viewDoc') }}
                    </a-button>
                  </a-space>
                </template>
              </a-result>
              <component :is="Component" v-else :key="route.fullPath" />
            </transition>
          </router-view>
          <a-divider />
        </div>
      </a-layout-content>

      <a-layout-footer>
        <pageFooter />
      </a-layout-footer>
    </a-layout>
  </a-config-provider>
</template>
<script setup lang="ts">
import { computed, defineAsyncComponent } from 'vue'
import { useI18n } from 'vue-i18n'
import AsyncMethod from './components/asyncMethod.vue'
import pageFooter from './components/pageFooter.vue'
import pageHeader from './components/pageHeader.vue'
import { ArcoI18nMessages } from './locale'
import { isModuleEnable, useEndpointStore } from './stores/endpoint'
import './transition.less'

const endPointStore = useEndpointStore()
const status = computed(() => endPointStore.status)

const OOBE = defineAsyncComponent(() => import('@/views/oobe/index.vue'))
const Login = defineAsyncComponent(() => import('@/views/login/index.vue'))

const { t, locale } = useI18n()

const specialStatus = computed(() => status.value === 'needLogin' || status.value === 'needInit')
const onBeforeEnter = () => {
  window.document.body.style.overflowX = 'hidden'
}
const onAfterEnter = () => {
  window.document.body.style.overflowX = 'unset'
}
</script>

<style scoped lang="less">
.login-page {
  display: flex;
  flex-direction: column;

  &::after,
  &::before {
    content: '';
    display: block;
    flex: 1;
  }

  &::before {
    flex: 0.4;
  }
}
</style>
