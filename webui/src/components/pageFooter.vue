<template>
  <a-row align="center" justify="center">
    <a-col class="footer">
      <a-descriptions :column="{ xs: 1, md: 3, lg: 4 }">
        <a-descriptions-item label="Backend Version">
          <a-space>
            <div>{{ serverVersion?.version }}</div>
            <div v-if="serverVersion?.commit">
              (<a-link
                :href="`https://github.com/Ghost-chu/PeerBanHelper/commit/${serverVersion?.commit}`"
                >{{ serverVersion?.commit.substring(0, 8) }} </a-link
              >)
            </div>
          </a-space>
        </a-descriptions-item>
        <a-descriptions-item v-if="endpointStore.status === 'pass'">
          <ChangelogViewer />
        </a-descriptions-item>
        <a-descriptions-item label="WebUI Version">
          <a-space>
            {{ version }}
            <div v-if="hash">
              (<a-link :href="`https://github.com/PBH-BTN/PeerBanHelper/tree/${hash}/webui`">
                {{ hash.substring(0, 8) }} </a-link
              >)
            </div></a-space
          >
        </a-descriptions-item>
        <a-descriptions-item
          v-if="endpointStore.plusStatus?.enabledFeatures.includes('paid')"
          :span="2"
        >
          <a-button class="plus-button" type="outline" size="mini" @click="plusInfo?.showModal()">
            <icon-heart-fill />
            &nbsp;PBH Plus
          </a-button>
        </a-descriptions-item>
      </a-descriptions>
    </a-col>
  </a-row>
  <PlusModal ref="plusInfo" />
</template>

<script setup lang="ts">
import { useEndpointStore } from '@/stores/endpoint'
import { computed, ref } from 'vue'
import ChangelogViewer from './changelogViewer.vue'
import PlusModal from './plus/plusModalContainer.vue'

const version = __APP_VERSION__
const hash = __APP_HASH__
const endpointStore = useEndpointStore()
const serverVersion = computed(() => endpointStore.serverManifest?.version)
const plusInfo = ref<InstanceType<typeof PlusModal>>()
endpointStore.emitter.on('open-plus-modal', () => {
  plusInfo.value?.showModal()
})
</script>

<style scoped>
.footer {
  display: flex;
  justify-content: center;
}

.plus-button {
  color: #d9659b;
  border: #d9659b solid 1px;
}

.plus-button:hover {
  color: #d9659b;
  border: #d9659b solid 1px;
  box-shadow: 0 0 10px #d9659b;
}
body[arco-theme='dark'] .plus-button {
  color: #d0b15e;
  border: #c7aa68 solid 1px;
}
body[arco-theme='dark'] .plus-button:hover {
  color: #d0b15e;
  border: #c7aa68 solid 1px;
  box-shadow: 0 0 10px #c7aa68;
}
</style>
