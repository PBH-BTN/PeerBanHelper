<template>
  <a-space direction="vertical" fill>
    <a-space class="list-header" wrap>
      <a-typography-text>{{ t('page.banlist.banlist.description') }}</a-typography-text>
      <a-input-search
        :style="{ width: '250px' }"
        :placeholder="t('page.banlist.banlist.searchPlaceHolder')"
        @search="handleSearch"
        allow-clear
        search-button
      />
    </a-space>
    <a-list
      :virtualListProps="{
        height: virtualListHeight
      }"
      ref="banlist"
      @reach-bottom="loadMore"
      :scrollbar="false"
      :data="list"
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
          v-if="loading"
          :style="{ height: `${virtualListHeight}px`, display: 'flex', alignItems: 'center' }"
        />
        <a-empty v-else-if="list.length === 0" :style="{ height: `${virtualListHeight}px` }" />
        <div style="position: absolute; transform: translateY(-50%)" v-if="loadingMore">
          <a-typography-text v-if="bottom">{{
            t('page.banlist.banlist.bottomReached')
          }}</a-typography-text>
          <a-spin v-else />
        </div>
      </template>
    </a-list>
  </a-space>
</template>

<script setup lang="ts">
import { useRequest } from 'vue-request'
import { computed, onMounted, ref, watch } from 'vue'
import { useAutoUpdatePlugin } from '@/stores/autoUpdate'
import { useEndpointStore } from '@/stores/endpoint'
import { getBanList } from '@/service/banList'
import type { BanList } from '@/api/model/banlist'
import { useI18n } from 'vue-i18n'
import { useResponsiveState } from '@arco-design/web-vue/es/grid/hook/use-responsive-state'
import { useWindowSize } from '@vueuse/core'
import banListItem from './banListItem.vue'

const { height } = useWindowSize()
const banlist = ref()
const endpointState = useEndpointStore()
const bottom = ref(false)
const limit = ref(5)
const step = 5
const loadingMore = ref(false)
const { t } = useI18n()

let firstGet = true
async function getMoreBanList(): Promise<BanList[]> {
  if (firstGet || !data.value) {
    firstGet = false
    return (await getBanList(step)).data
  }
  if (data.value.length > limit.value - step) {
    // refresh the new data
    const newData: BanList[] = []
    let match = false
    // load more data until the limit or get the same data with the top one
    while (newData.length < limit.value && !match) {
      const moreData = await (
        await getBanList(step, newData[newData.length - 1]?.banMetadata.banAt)
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
const handleSearch = (value: string) => {
  if (value) {
    const index = data.value?.map((item) => item.address).findIndex((item) => item.includes(value))
    if (index !== -1) {
      banlist.value?.scrollIntoView({ index: index, align: 'auto' })
    }
  }
}

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
  setTimeout(
    () => {
      loadingMore.value = false
    },
    bottom.value ? 1000 : 0
  )
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
