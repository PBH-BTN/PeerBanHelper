<template>
  <a-descriptions :column="{ xs: 1, lg: 2 }" bordered>
    <a-descriptions-item
      v-for="[label, value] in fields"
      :key="label"
      :label="t('page.ipList.btn.' + label)"
    >
      <a-typography-text code copyable class="address-value">{{ value }}</a-typography-text>
    </a-descriptions-item>
    <a-descriptions-item :label="t('page.ipList.btn.flags')">
      <a-space wrap>
        <a-tag v-for="flag in lookup.flags" :key="flag">{{ flag }}</a-tag>
      </a-space>
    </a-descriptions-item>
    <a-descriptions-item :label="t('page.ipList.btn.routable')">
      {{ t(lookup.globallyRoutable ? 'page.ipList.btn.yes' : 'page.ipList.btn.no') }}
    </a-descriptions-item>
    <a-descriptions-item v-if="lookup.geoStart" :label="t('page.ipList.btn.geoStart')">
      {{ formatGeo(lookup.geoStart) }}
    </a-descriptions-item>
    <a-descriptions-item
      v-if="lookup.geoEnd && lookup.rangeStart !== lookup.rangeEnd"
      :label="t('page.ipList.btn.geoEnd')"
    >
      {{ formatGeo(lookup.geoEnd) }}
    </a-descriptions-item>
  </a-descriptions>
</template>

<script setup lang="ts">
import type { BTNIPGeo, BTNIPLookup } from '@/api/model/btn'
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

const { lookup } = defineProps<{ lookup: BTNIPLookup['lookup'] }>()
const { t } = useI18n()

const fields = computed(() =>
  Object.entries({
    normalizedAddress: lookup.normalizedAddress,
    canonicalCidr: lookup.canonicalCidr,
    range: lookup.rangeStart + ' – ' + lookup.rangeEnd,
    totalAddresses: lookup.totalAddresses,
    reverseDnsName: lookup.reverseDnsName,
    expandedAddress: lookup.expandedAddress,
    binaryAddress: lookup.binaryAddress,
    decimalAddress: lookup.decimalAddress
  }).filter(([, value]) => value)
)

function formatGeo(geo: BTNIPGeo) {
  return [
    [geo.country, geo.province, geo.city, geo.district].filter(Boolean).join(' / '),
    [geo.asn, geo.asnOrganization, geo.asnNetwork].filter(Boolean).join(' '),
    geo.isp,
    geo.networkType
  ]
    .filter(Boolean)
    .join(' · ')
}
</script>

<style scoped>
.address-value {
  overflow-wrap: anywhere;
}
</style>
