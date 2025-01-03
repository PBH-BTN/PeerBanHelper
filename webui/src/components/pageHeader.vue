<template>
  <a-page-header class="header" :class="mobileLayout === 0 ? 'mobile' : ''" :show-back="false">
    <template #title>
      <a v-if="mobileLayout === 0" href="https://github.com/PBH-BTN/PeerBanHelper">
        <a-space size="mini">
          <img v-if="isDark" src="@/assets/logo-dark.png" alt="logo" class="logo" />
          <img v-else src="@/assets/logo.png" alt="logo" class="logo" />
          <a-typography-title style="margin-top: 0%; margin-bottom: 0%"
            >PeerBanHelper</a-typography-title
          >
        </a-space>
      </a>
      <a-menu
        v-else
        class="header-menu"
        mode="horizontal"
        :selected-keys="selectedKeys"
        @menu-item-click="goto"
      >
        <a-menu-item :style="{ padding: 0, marginLeft: 0 }" disabled>
          <a href="https://github.com/PBH-BTN/PeerBanHelper">
            <a-space size="mini">
              <img v-if="isDark" src="@/assets/logo-dark.png" alt="logo" class="logo" />
              <img v-else src="@/assets/logo.png" alt="logo" class="logo" />
              <a-typography-title style="margin-top: 0%; margin-bottom: 0%"
                >PeerBanHelper</a-typography-title
              >
            </a-space>
          </a>
        </a-menu-item>
        <template v-if="!disableMenu">
          <template v-for="router in routers.filter((r) => !r!.meta?.hide)" :key="router.name">
            <a-sub-menu v-if="router.children">
              <template v-if="router.meta?.icon" #icon>
                <component :is="router.meta?.icon" />
              </template>
              <template #title>{{ t(String(router.meta?.label)) }}</template>
              <a-menu-item v-for="child in router.children" :key="child.name">
                <template v-if="child.meta?.icon" #icon>
                  <component :is="child.meta?.icon" />
                </template>
                {{ t(String(child.meta?.label)) }}
              </a-menu-item>
            </a-sub-menu>
            <a-menu-item v-else :key="router.name">
              <template v-if="router.meta?.icon" #icon>
                <component :is="router.meta?.icon" />
              </template>
              {{ t(String(router.meta?.label)) }}
            </a-menu-item>
          </template>
        </template>
      </a-menu>
    </template>
    <template #extra>
      <div v-if="!disableMenu" style="display: flex; gap: 12px; margin-top: 5px">
        <a-dropdown
          v-if="mobileLayout === 0"
          position="bl"
          :popup-max-height="false"
          @select="(router: unknown) => goto(String((router as (typeof routers)[number]).name))"
        >
          <a-button style="flex-grow: 1; gap: 12px">
            <template v-if="route.meta?.icon" #icon>
              <component :is="route.meta?.icon" />
            </template>
            {{ t(String(route.meta?.label)) }}
            <icon-down />
          </a-button>
          <template #content>
            <template v-for="router in routers.filter((r) => !r.meta?.hide)" :key="router.name">
              <a-dsubmenu v-if="router.children">
                <template v-if="router.meta?.icon" #icon>
                  <component :is="router.meta?.icon" />
                </template>
                {{ t(String(router.meta?.label)) }}
                <template #content>
                  <template v-for="child in router.children" :key="child.name">
                    <a-doption :value="child">
                      <template v-if="child.meta?.icon" #icon>
                        <component :is="child.meta?.icon" />
                      </template>
                      {{ t(String(child.meta?.label)) }}
                    </a-doption>
                  </template>
                </template>
              </a-dsubmenu>
              <a-doption v-else :value="router">
                <template v-if="router.meta?.icon" #icon>
                  <component :is="router.meta?.icon" />
                </template>
                {{ t(String(router.meta?.label)) }}
              </a-doption>
            </template>
          </template>
        </a-dropdown>
        <a-space class="right-side" wrap>
          <a-tooltip :content="t('settings.globalPause')">
            <global-pause-btn />
          </a-tooltip>
          <template v-if="!disableAutoUpdate">
            <auto-update-btn />
          </template>
          <div class="lang-selector">
            <a-dropdown
              trigger="click"
              @select="
                (lang: string | number | Record<string, any> | undefined) =>
                  changeLocale(lang as string)
              "
            >
              <a-tooltip :content="t('settings.language')">
                <a-button class="nav-btn" type="outline" :shape="'circle'">
                  <template #icon>
                    <icon-language />
                  </template>
                </a-button>
              </a-tooltip>
              <template #content>
                <a-doption v-for="item in locales" :key="item.value" :value="item.value">
                  <template #icon>
                    <icon-check v-show="item.value === locale" />
                  </template>
                  {{ item.label }}
                </a-doption>
              </template>
            </a-dropdown>
          </div>
          <a-tooltip
            :content="
              isDark ? t('settings.navbar.theme.toLight') : t('settings.navbar.theme.toDark')
            "
          >
            <a-button class="nav-btn" type="outline" :shape="'circle'" @click="handleToggleTheme">
              <template #icon>
                <icon-moon-fill v-if="isDark" />
                <icon-sun-fill v-else />
              </template>
            </a-button>
          </a-tooltip>
          <alert />
          <a-button
            class="nav-btn"
            type="outline"
            shape="circle"
            status="normal"
            @click="settingsModalRef?.showModal"
          >
            <template #icon><icon-settings /></template>
          </a-button>
        </a-space>
      </div>
    </template>
  </a-page-header>
  <settings-modal ref="settingsModalRef" />
</template>
<script setup lang="ts">
import { LOCALE_OPTIONS } from '@/locale'
import { useViewRoute } from '@/router'
import { useDarkStore } from '@/stores/dark'
import { useEndpointStore } from '@/stores/endpoint'
import useLocale from '@/stores/locale'
import { useResponsiveState } from '@arco-design/web-vue/es/grid/hook/use-responsive-state'
import { useDark, useToggle } from '@vueuse/core'
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import alert from './alert.vue'
import autoUpdateBtn from './autoUpdateBtn.vue'
import globalPauseBtn from './globalPauseBtn.vue'
import settingsModal from './settingsModal.vue'

const { t, locale } = useI18n()
const { changeLocale } = useLocale()
const locales = [...LOCALE_OPTIONS]
const darkStore = useDarkStore()
const isDark = useDark({
  selector: 'body',
  attribute: 'arco-theme',
  valueDark: 'dark',
  valueLight: 'light',
  storageKey: 'dark-theme-config'
})
darkStore.setDark(isDark.value)
const settingsModalRef = ref<InstanceType<typeof settingsModal>>()
const toggleTheme = useToggle(isDark)
const handleToggleTheme = () => {
  toggleTheme()
  darkStore.setDark(isDark.value)
}

const props = withDefaults(
  defineProps<{
    disableAutoUpdate?: boolean
    disableMenu?: boolean
  }>(),
  {
    disableAutoUpdate: false,
    disableMenu: false
  }
)
const endpointStore = useEndpointStore()

endpointStore.emitter.on('open-settings-modal', () => {
  settingsModalRef.value?.showModal()
})

const [routers, currentName, goto] = useViewRoute()
const route = useRoute()
const disableAutoUpdate = computed(() => props.disableAutoUpdate || !!route.meta.disableAutoUpdate)
const disableMenu = computed(() => props.disableMenu || !!route.meta.disableMenu)
const selectedKeys = computed(() => [currentName.value])

const mobileLayout = useResponsiveState(
  ref({
    md: 1
  }),
  0
)
</script>
<style scoped lang="less">
.navbar {
  display: flex;
  justify-content: space-between;
  height: 100%;
  background-color: var(--color-bg-2);
  border-bottom: 1px solid var(--color-border);
}

.left-side {
  display: flex;
  align-items: center;
  padding-left: 20px;
}

.logo {
  width: 50px;
  height: 50px;
  margin-right: 0px;
}

.center-side {
  flex: 1;
}

.right-side {
  display: flex;
  list-style: none;
  :deep(.locale-select) {
    border-radius: 20px;
  }

  a {
    color: var(--color-text-1);
    text-decoration: none;
  }
  .nav-btn {
    border-color: rgb(var(--gray-2));
    color: rgb(var(--gray-8));
    font-size: 16px;
  }
  .trigger-btn,
  .ref-btn {
    position: absolute;
    bottom: 14px;
  }
  .trigger-btn {
    margin-left: 14px;
  }
}
</style>

<style lang="less">
.message-popover {
  .arco-popover-content {
    margin-top: 0;
  }
}
.arco-layout {
  padding: 0 24px;
  .arco-page-header {
    &.mobile {
      .arco-page-header-main {
        width: 100%;
      }
      .arco-page-header-extra {
        width: 100%;
      }
      .arco-page-header-header {
        flex-wrap: wrap;
      }
    }
  }
  .arco-layout-header {
    .arco-page-header-header {
      flex-wrap: nowrap;
    }
    .arco-menu-inner {
      padding-left: 0;
      padding-right: 0;
    }
    .arco-page-header-main {
      flex-grow: 1;
      .arco-page-header-title {
        flex-grow: 1;
      }
    }
    .arco-page-header-wrapper {
      padding: 0;
    }
    .arco-menu-light,
    .arco-menu-pop,
    .arco-menu-item {
      background-color: unset;
    }
  }
}
.arco-trigger-menu {
  max-height: unset;
}
</style>
