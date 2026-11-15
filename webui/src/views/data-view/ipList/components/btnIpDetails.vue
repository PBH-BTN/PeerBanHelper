<template>
  <dl class="network-details">
    <div v-for="[label, value] in fields" :key="label" class="detail">
      <dt>{{ t('page.ipList.btn.' + label) }}</dt>
      <dd>
        <a-typography-text code copyable class="address-value">{{ value }}</a-typography-text>
      </dd>
    </div>
    <div class="detail">
      <dt>{{ t('page.ipList.btn.flags') }}</dt>
      <dd>
        <a-space wrap>
          <a-tag v-for="flag in lookup.flags" :key="flag">{{ flag }}</a-tag>
        </a-space>
      </dd>
    </div>
    <div class="detail">
      <dt>{{ t('page.ipList.btn.routable') }}</dt>
      <dd>{{ t(lookup.globallyRoutable ? 'page.ipList.btn.yes' : 'page.ipList.btn.no') }}</dd>
    </div>
    <div v-if="lookup.geoStart" class="detail">
      <dt>{{ t('page.ipList.btn.geoStart') }}</dt>
      <dd>{{ formatGeo(lookup.geoStart) }}</dd>
    </div>
    <div v-if="lookup.geoEnd && lookup.rangeStart !== lookup.rangeEnd" class="detail">
      <dt>{{ t('page.ipList.btn.geoEnd') }}</dt>
      <dd>{{ formatGeo(lookup.geoEnd) }}</dd>
    </div>
  </dl>
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
.network-details {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 28px;
  margin: 0;
}

.detail {
  display: grid;
  align-content: start;
  gap: 6px;
  min-width: 0;
  padding: 14px 0;
  border-bottom: 1px solid var(--color-border-1);
}

dt {
  color: var(--color-text-3);
  font-size: 13px;
}

dd {
  margin: 0;
  overflow-wrap: anywhere;
}

@media (max-width: 640px) {
  .network-details {
    grid-template-columns: minmax(0, 1fr);
  }
}

.address-value {
  overflow-wrap: anywhere;
}
</style>
