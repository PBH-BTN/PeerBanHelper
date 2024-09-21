<template>
  <a-tabs v-model:activeKey="activeTab" @change="loadTabContent">
    <!-- Dynamically generate tabs -->
    <a-tab-pane
        v-for="configId in configYamlList"
        :key="configId"
        :title="configId"
    >
      <Codemirror
          v-model:value="yamlContents[configId]"
          :options="cmOptions"
          border
          height="600"
          @change="onChange(configId)"
          @ready="onReady(configId)"
          @focus="onFocus"
      />
      <!-- 这里的 t 引用报错 -->
      <a-button type="primary" @click="saveConfig(configId)">{{ t('page.config.save') }}</a-button>
    </a-tab-pane>
  </a-tabs>
</template>
<script lang="ts">
import { defineComponent, onMounted, ref } from 'vue';
import { getConfigYamlContent, getConfigYamlList, setConfigYamlContent } from '@/service/config';
import Codemirror from 'codemirror-editor-vue3';
import type { Editor, EditorConfiguration } from 'codemirror';
import { Result } from '@/api/model/config';
import { Message } from '@arco-design/web-vue';
import "codemirror/mode/yaml/yaml.js";
import { useI18n } from 'vue-i18n'; // 无需导入 UseI18n
type TFunction = (key: string, values?: Record<string, unknown>) => string;

export default defineComponent({
  components: { Codemirror },
  setup() {
    const { t } = useI18n(); // 直接从 useI18n 获取 t 函数
    const typedT: TFunction = t; // 手动添加类型注释

    const configYamlList = ref<string[]>([]); // Holds list of YAML config files
    const yamlContents = ref<Record<string, string>>({}); // Holds content of each YAML file
    const activeTab = ref<string>(''); // Current active tab
    const cmOptions: EditorConfiguration = {
      mode: 'yaml',
      lineWrapping: true,
    };

    // Load list of YAML files on component mount
    onMounted(async () => {
      const response = await getConfigYamlList();
      if (response.success) {
        configYamlList.value = response.data;
        if (configYamlList.value.length > 0) {
          activeTab.value = configYamlList.value[0];
          await loadTabContent(activeTab.value); // Load content of the first tab
        }
      }
    });

    // Load YAML content for the selected tab
    const loadTabContent = async (configId: string) => {
      if (!yamlContents.value[configId]) { // Lazy load content if not already loaded
        const response = await getConfigYamlContent(configId);
        if (response.success) {
          yamlContents.value[configId] = response.data;
        }
      }
    };

    const saveConfig = async (configId: string) => {
      const content = yamlContents.value[configId];
      try {
        const response = await setConfigYamlContent(configId, content);
        if (!response.success) {
          Message.error({ content: response.message, resetOnHover: true });
          return true;
        }

        const results = response.data;
        let requiresRestart = false;

        results.forEach((result) => {
          if (result.result === Result.Exception) {
            Message.error({
              content: t('page.config.saveFailed', { name: result.name, errorMsg: result.errorMsg }),
              resetOnHover: true
            });
            return true;
          }
          if (result.result === Result.RequireRestart) requiresRestart = true;
        });

        const allSuccessful = results.every(
            (res) => res.result === Result.Success || res.result === Result.Outdated
        );

        if (allSuccessful) {
          Message.success({
            content: t('page.config.saveSuccess'),
            resetOnHover: true,
          });
        }

        if (requiresRestart) {
          Message.warning({
            content: t('page.config.saveRequireRestart'),
            resetOnHover: true,
          });
        }

        return true;
      } catch (error: unknown) {
        if (error instanceof Error) {
          Message.error({ content: error.message, resetOnHover: true });
        }
        return true;
      }
    };


    return {
      t: typedT,
      configYamlList,
      yamlContents,
      activeTab,
      cmOptions,
      loadTabContent,
      saveConfig,
      onReady: (configId: string) => (cm: Editor) => {
        console.log(`Codemirror instance ready for ${configId}`);
      },
      onChange: (configId: string) => (value: string) => {
        yamlContents.value[configId] = value; // Update the content in memory
      },
      onFocus(cm: Editor, event: FocusEvent) {
        console.log('Codemirror focused', cm, event);
      }
    };
  },
});
</script>
