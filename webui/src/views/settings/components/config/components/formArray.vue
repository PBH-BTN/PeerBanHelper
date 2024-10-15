<template>
  <a-form-item
    :label="props.label"
    :required="props.required"
    field="model"
    :show-header="false"
    :tooltip="props.tooltip"
  >
    <a-space direction="vertical">
      <a-button @click="model.push('')">
        <template #icon>
          <icon-plus />
        </template>
      </a-button>
      <a-list style="min-width: 200px">
        <a-list-item v-for="(_, i) in model" :key="i">
          <a-space>
            <a-input v-model="model[i]" />
            <br />
          </a-space>
          <template #actions>
            <a-button
              class="edit-btn"
              status="danger"
              shape="circle"
              type="text"
              @click="model.splice(i, 1)"
            >
              <template #icon>
                <icon-delete />
              </template>
            </a-button>
          </template>
        </a-list-item>
      </a-list>
    </a-space>
  </a-form-item>
</template>
<script setup lang="ts">
type Value = string
const model = defineModel<Value[]>({ required: true })
const props = defineProps<{
  label: string
  required?: boolean
  tooltip?: string
}>()
</script>
<style scoped>
.edit-btn {
  color: rgb(var(--gray-8));
  font-size: 16px;
}
</style>
