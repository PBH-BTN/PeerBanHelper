<template>
  <a-space direction="vertical" fill>
    <a-space class="list-header" wrap>
      <a-typography-text>{{ t('page.banlist.banlist.description') }}</a-typography-text>
      <a-space class="list-header-right-group" wrap>
        <AsyncMethod
          v-slot="{ run: unban, loading: unbaning }"
          once
          :async-fn="() => handleUnban('*')"
        >
          <a-button
            type="secondary"
            :disabled="(list?.length ?? 0) === 0"
            :loading="unbaning"
            @click="unban"
          >
            {{ t('page.banlist.banlist.listItem.unbanall') }}
          </a-button>
        </AsyncMethod>
        <a-input-search
          :style="{ width: '250px' }"
          :placeholder="t('page.banlist.banlist.searchPlaceHolder')"
          allow-clear
          search-button
          @search="handleSearch"
        />
      </a-space>
    </a-space>
    <a-list
      ref="banlist"
      :virtual-list-props="{
        height: virtualListHeight,
        fixedSize: true,
        buffer: 10
      }"
      :scrollbar="true"
      :data="list"
      @reach-bottom="loadMore"
    >
      <template #item="{ item, index }">
        <a-list-item
          :style="{ marginBottom: index === list.length - 1 && loadingMore ? '50px' : undefined }"
        >
          <banListItem :item="item" @unban="refresh()" />
        </a-list-item>
      </template>
      <template #scroll-loading>
        <a-spin
          v-if="loading && list.length === 0"
          :style="{ height: `${virtualListHeight}px`, display: 'flex', alignItems: 'center' }"
        />
        <a-empty v-else-if="list.length === 0" :style="{ height: `${virtualListHeight}px` }" />
        <div v-if="loadingMore" style="position: absolute; transform: translateY(-50%)">
          <a-typography-text v-if="bottom"
            >{{ t('page.banlist.banlist.bottomReached') }}
          </a-typography-text>
          <a-spin v-else />
        </div>
      </template>
    </a-list>
  </a-space>
</template>

<script setup lang="ts">
import type { BanList } from '@/api/model/banlist'
import AsyncMethod from '@/components/asyncMethod.vue'
import { getBanList, unbanIP } from '@/service/banList'
import { useAutoUpdatePlugin } from '@/stores/autoUpdate'
import { useEndpointStore } from '@/stores/endpoint'
import { Message } from '@arco-design/web-vue'
import { useResponsiveState } from '@arco-design/web-vue/es/grid/hook/use-responsive-state'
import { useWindowSize } from '@vueuse/core'
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRequest } from 'vue-request'
import banListItem from './banListItem.vue'

const { height } = useWindowSize()
const banlist = ref()
const endpointState = useEndpointStore()
const bottom = ref(false)
const limit = ref(5)
const step = 5
const loadingMore = ref(false)
const searchString = ref('')
const { t } = useI18n()

const firstGet = ref(true)
async function getMoreBanList(): Promise<BanList[]> {
  if (firstGet.value || !data.value) {
    firstGet.value = false
    return (await getBanList(step, searchString.value)).data
  }
  if (data.value.length > limit.value - step) {
    // refresh the new data
    const newData: BanList[] = []
    let match = false
    // load more data until the limit or get the same data with the top one
    while (newData.length < limit.value && !match) {
      const moreData = await (
        await getBanList(step, searchString.value, newData[newData.length - 1]?.banMetadata.banAt)
      ).data
      for (const item of moreData) {
        if (item.banMetadata.randomId !== data.value[0].banMetadata.randomId) {
          newData.push(item)
        } else {
          match = true
          break
        }
      }
    }
    if (match) {
      limit.value = data.value.length + newData.length
      return newData.concat(data.value)
    } else {
      return newData
    }
  }
  return data.value
}

const { data, refresh, run, loading } = useRequest(
  getMoreBanList,
  {
    manual: true
  },
  [useAutoUpdatePlugin]
)

const loadMore = async () => {
  if (!data.value) return
  limit.value = data.value.length + step
  if (loadingMore.value) return
  loadingMore.value = true
  bottom.value = false
  if (data.value.length <= limit.value) {
    const newData: BanList[] = []
    while (newData.length + data.value.length < limit.value && !bottom.value) {
      const moreData = (
        await getBanList(
          step,
          searchString.value,
          (newData[newData.length - 1] || data.value[data.value.length - 1])?.banMetadata.banAt
        )
      ).data
      if (moreData.length < step) {
        bottom.value = true
      }
      newData.push(...moreData)
    }
    data.value = data.value.concat(newData)
  }
  loadingMore.value = false
}

const handleUnban = async (address: string) => {
  const { count } = await (await unbanIP(address)).data
  if (!count || count < 1) {
    Message.error({
      content: t('page.banlist.banlist.listItem.unbanUnexcepted'),
      resetOnHover: true
    })
    return false
  } else {
    Message.success({
      content: t('page.banlist.banlist.listItem.unbanSuccess', { count: count }),
      resetOnHover: true
    })
    refresh()
    return true
  }
}

watch(
  () => endpointState.endpoint,
  () => {
    limit.value = step
    data.value = undefined
    refresh()
  }
)

onMounted(run)

const list = computed(() => data.value ?? [])

const virtualListMaxHeight = useResponsiveState(
  ref({
    xs: 1500,
    md: 1000,
    xl: 800
  }),
  800
)
const virtualListHeight = computed(() => Math.min(virtualListMaxHeight.value, height.value - 200))

const handleSearch = (v: string) => {
  if (searchString.value === v) return
  searchString.value = v
  firstGet.value = true
  refresh()
}
</script>

<style scoped lang="less">
.list-header {
  display: flex;
  justify-content: space-between;
}

a {
  color: var(--color-text-1);
  text-decoration: none;
}
</style>
