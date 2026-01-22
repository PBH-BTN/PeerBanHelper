<template>
  <div style="height: 80vh">
    <VueMonacoEditor
      v-model:value="model"
      theme="vs-dark"
      class="editor"
      :options="{
        automaticLayout: true,
        formatOnType: true,
        formatOnPaste: true,
        foldingStrategy: 'indentation',
        autoIndent: 'brackets'
      }"
      :language="currentLanguage"
      @mount="handleMount"
      @change="handleChange"
    >
      <template #default>
        <a-spin :tip="t('page.rule.custom-script.detail.loading')" />
      </template>
      <template #failure>
        <a-result status="error" :title="t('page.rule.custom-script.detail.failed')">
          <template #subtitle>
            {{ t('page.rule.custom-script.detail.failed.tips') }}
          </template>
        </a-result>
      </template>
    </VueMonacoEditor>
  </div>
</template>
<script setup lang="ts">
import {
  type MonacoEditor,
  VueMonacoEditor,
  type VueMonacoEditorEmitsOptions
} from '@guolao/vue-monaco-editor'
import { computed, shallowRef, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import GrammarParser from './aviatorscript/grammar/GrammarParser'
import monarch from './aviatorscript/monarch'
import getSuggestion from './aviatorscript/suggestions'

const { t } = useI18n()

const { viewOnly = false, fileType = 'av' } = defineProps<{
  viewOnly?: boolean
  fileType?: string
}>()
const model = defineModel<string | undefined>({ required: true })

const AV = 'aviatorscript'
const PYTHON = 'python'

// 根据 fileType 计算当前语言
const currentLanguage = computed(() => {
  return fileType === 'py' ? PYTHON : AV
})

type onMountF = VueMonacoEditorEmitsOptions['mount']
type editor = Parameters<onMountF>[0] // monacoEditor.editor.IStandaloneCodeEditor
const editorRef = shallowRef<editor>()
const monacoRef = shallowRef<MonacoEditor>()
const grammarParser = new GrammarParser()

const handleMount: onMountF = (editor, monaco) => {
  editorRef.value = editor
  monacoRef.value = monaco

  // 注册 AviatorScript 语言
  monaco.languages.register({ id: AV })
  monaco.languages.setMonarchTokensProvider(AV, monarch)

  // 为 AviatorScript 设置自动补全
  monaco.languages.registerCompletionItemProvider(AV, {
    provideCompletionItems: () =>
      ({
        suggestions: getSuggestion(monaco)
      }) as ReturnType<
        Parameters<
          typeof monaco.languages.registerCompletionItemProvider
        >[1]['provideCompletionItems']
      >
  })
  editor.updateOptions({ readOnly: viewOnly })
}

// 监听 fileType 变化，清除错误标记
watch(
  () => fileType,
  () => {
    // 清除之前的错误标记
    const model = editorRef.value?.getModel()
    if (model && monacoRef.value) {
      monacoRef.value.editor.setModelMarkers(model, AV, [])
    }
  }
)

const handleChange = (value: string | undefined) => {
  if (!value) return

  // 只对 AviatorScript 进行语法检查
  if (currentLanguage.value !== AV) {
    // 清除标记
    const model = editorRef.value?.getModel()
    if (model && monacoRef.value) {
      monacoRef.value.editor.setModelMarkers(model, AV, [])
    }
    return
  }

  const ast = grammarParser.parse(value + '\n')
  const markers = []
  for (const error of ast.errors) {
    markers.push({
      severity: monacoRef.value!.MarkerSeverity.Error,
      startLineNumber: error.line,
      startColumn: error.column,
      endLineNumber: error.line,
      endColumn: error.column,
      message: error.message
    })
  }
  for (const child of ast.ast.children) {
    markers.push({
      severity: monacoRef.value!.MarkerSeverity.Error,
      startLineNumber: child.line,
      startColumn: child.column,
      endLineNumber: child.line,
      endColumn: child.column,
      message: child.text
    })
  }
  const model = editorRef.value?.getModel()
  if (model) monacoRef.value?.editor.setModelMarkers(model, AV, markers)
}
</script>
