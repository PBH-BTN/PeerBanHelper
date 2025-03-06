<template>
  <a-descriptions
    :column="{ xs: 3, md: 6, xl: 12 }"
    size="medium"
    class="banlist-item"
    :layout="(['inline-vertical', 'horizontal'] as const)[descriptionLayout]"
    table-layout="fixed"
  >
    <template #title>
      <a-space fill style="display: flex; justify-content: space-between">
        <a-space wrap>
          <a-typography-text bold copyable>
            {{
              formatIPAddressPort(
                item.banMetadata.peer.address.ip,
                item.banMetadata.peer.address.port
              )
            }}
          </a-typography-text>
          <a-tooltip
            :content="
              item.banMetadata.peer.id
                ? item.banMetadata.peer.id
                : t('page.banlist.banlist.listItem.empty')
            "
          >
            <a-tag>
              {{
                item.banMetadata.peer.clientName
                  ? item.banMetadata.peer.clientName
                  : t('page.banlist.banlist.listItem.empty')
              }}
            </a-tag>
          </a-tooltip>
        </a-space>
        <div>
          <a-button-group class="hover-display-btn">
            <AsyncMethod
              v-slot="{ run, loading }"
              once
              :async-fn="() => handleUnban(item.banMetadata.peer.address.ip)"
            >
              <a-button type="outline" :loading="loading" @click="run">
                {{ t('page.banlist.banlist.listItem.unban') }}
              </a-button>
            </AsyncMethod>
            <a-tooltip :content="t('page.banlist.banlist.listItem.threatAnalyse')">
              <a-button
                type="outline"
                target="_blank"
                :href="'ipHistory?ip=' + item.banMetadata.peer.address.ip"
              >
                <template #icon><icon-search /></template>
              </a-button>
            </a-tooltip>
          </a-button-group>
        </div>
      </a-space>
    </template>
    <a-descriptions-item :label="t('page.banlist.banlist.listItem.banTime')" :span="6">
      {{ d(item.banMetadata.banAt, 'long') }}
    </a-descriptions-item>
    <a-descriptions-item :label="t('page.banlist.banlist.listItem.expireTime')" :span="6">
      {{ d(item.banMetadata.unbanAt, 'long') }}
    </a-descriptions-item>
    <a-descriptions-item :label="t('page.banlist.banlist.listItem.snapshot')" :span="6">
      <icon-arrow-up class="green" />
      {{ formatFileSize(item.banMetadata.peer.uploaded) }}
      <icon-arrow-down class="red" />
      {{ formatFileSize(item.banMetadata.peer.downloaded) }}
      - {{ (item.banMetadata.peer.progress * 100).toFixed(2) }}%
    </a-descriptions-item>

    <a-descriptions-item
      v-if="item.banMetadata.geo"
      :label="t('page.banlist.banlist.listItem.geo')"
      :span="6"
    >
      <CountryFlag
        :iso="item.banMetadata.geo?.country?.iso ?? t('page.banlist.banlist.listItem.empty')"
      />
      {{
        `${item.banMetadata.geo?.country?.name} ${
          item.banMetadata.geo?.city?.name ?? t('page.banlist.banlist.listItem.empty')
        }`
      }}
      <!-- <a-link
        :href="`https://uri.amap.com/marker?position=${item.banMetadata.geo?.city?.location?.longitude},${item.banMetadata.geo?.city?.location?.latitude}`"
        :hoverable="false">
        <icon-location />
      </a-link> -->
    </a-descriptions-item>
    <a-descriptions-item
      v-if="item.banMetadata.geo?.as"
      :label="t('page.banlist.banlist.listItem.asn')"
      :span="6"
    >
      <a-space>
        <a-typography-text> {{ item.banMetadata.geo?.as?.organization }}</a-typography-text>
        <a-tag :color="getColor((item.banMetadata.geo?.as?.number ?? 0).toString())">{{
          item.banMetadata.geo?.as?.number
        }}</a-tag>
        <a-tooltip
          :content="
            t('page.banlist.banlist.listItem.asn.subnet') +
            item.banMetadata.geo?.as?.network?.ipAddress +
            '/' +
            item.banMetadata.geo?.as?.network?.prefixLength
          "
        >
          <a-link
            :href="`https://2ip.io/analytics/asn-list/?asnId=${item.banMetadata.geo?.as?.number}`"
            :hoverable="false"
          >
            <icon-info-circle />
          </a-link>
        </a-tooltip>
      </a-space>
    </a-descriptions-item>
    <a-descriptions-item
      v-if="item.banMetadata.reverseLookup != 'N/A'"
      :label="t('page.banlist.banlist.listItem.reserveDNSLookup')"
      :span="6"
    >
      {{ item.banMetadata.reverseLookup }}
    </a-descriptions-item>
    <a-descriptions-item
      v-if="item.banMetadata.geo?.network?.isp"
      :label="t('page.banlist.banlist.listItem.network.isp')"
      :span="6"
    >
      {{ item.banMetadata.geo?.network?.isp }}
    </a-descriptions-item>
    <a-descriptions-item
      v-if="item.banMetadata.geo?.network?.netType"
      :label="t('page.banlist.banlist.listItem.network.netType')"
      :span="6"
    >
      {{ item.banMetadata.geo?.network?.netType }}
    </a-descriptions-item>
    <a-descriptions-item :label="t('page.banlist.banlist.listItem.location')" :span="12">
      <!-- 这里非常离奇，只要用了a-typography-text就会被下面一行覆盖，怀疑框架有毛病 -->
      <a-tooltip :content="item.banMetadata.torrent.hash" position="bottom">
        <a-typography-text style="margin-bottom: 0" :ellipsis="{ showTooltip: true }">
          {{ item.banMetadata.torrent.name }}
        </a-typography-text>
      </a-tooltip>
    </a-descriptions-item>
    <a-descriptions-item :label="t('page.banlist.banlist.listItem.rule')" :span="12">
      <a-typography-text style="margin-bottom: 0" :ellipsis="{ showTooltip: true }">
        {{ item.banMetadata.rule }}
      </a-typography-text>
    </a-descriptions-item>
    <a-descriptions-item :label="t('page.banlist.banlist.listItem.reason')" :span="12">
      <div>
        <a-typography-text style="margin-bottom: 0" :ellipsis="{ showTooltip: true }">
          {{ item.banMetadata.description }}
        </a-typography-text>
      </div>
    </a-descriptions-item>
  </a-descriptions>
</template>
<script setup lang="ts">
import type { BanList } from '@/api/model/banlist'
import AsyncMethod from '@/components/asyncMethod.vue'
import CountryFlag from '@/components/countryFlag.vue'
import { unbanIP } from '@/service/banList'
import { getColor } from '@/utils/color'
import { formatFileSize } from '@/utils/file'
import { formatIPAddressPort } from '@/utils/string'
import { Message } from '@arco-design/web-vue'
import { useResponsiveState } from '@arco-design/web-vue/es/grid/hook/use-responsive-state'
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'

const { t, d } = useI18n()
defineProps<{
  item: BanList
}>()
const descriptionLayout = useResponsiveState(
  ref({
    md: 1
  }),
  0
)
const emits = defineEmits<{
  (e: 'unban', address: string): void
}>()
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
    emits('unban', address)
    return true
  }
}
</script>

<style lang="less" scoped>
.red {
  color: red;
}

.green {
  color: green;
}

a {
  color: var(--color-text-1);
  text-decoration: none;
}

.hover-display-btn {
  transition: opacity 0.15s ease-in-out;
  opacity: 0;
}

.banlist-item:hover .hover-display-btn {
  transition: opacity 0.15s ease-in-out;
  opacity: 1;
}
</style>
<style lang="less">
.banlist-item {
  div {
    table {
      tbody {
        tr {
          td.arco-descriptions-item-label {
            width: 100px;
          }
        }
      }
    }
  }
}
</style>
