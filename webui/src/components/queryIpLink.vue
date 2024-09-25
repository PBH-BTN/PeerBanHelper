<template>
  <a
    style="text-decoration: none; cursor: pointer"
    :style="style"
    :href="`ipHistory?ip=${ip}`"
  >
    <slot></slot>
  </a>
</template>
<script lang="ts" setup>
import type { StyleValue } from 'vue';

const { ip } = defineProps<{
  ip: string
  style?: StyleValue
}>()
</script>
