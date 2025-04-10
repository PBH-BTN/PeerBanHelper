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
      :default-language="AV"
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
  type VueMonacoEditorEmitsOptions,
  loader
} from '@guolao/vue-monaco-editor'
import { shallowRef } from 'vue'
import { useI18n } from 'vue-i18n'
import GrammarParser from './aviatorscript/grammar/GrammarParser'
import monarch from './aviatorscript/monarch'
import getSuggestion from './aviatorscript/suggestions'

const { t } = useI18n()

const { viewOnly = false } = defineProps<{
  viewOnly?: boolean
}>()
const model = defineModel<string | undefined>({ required: true })

loader.config({
  paths: {
    vs: 'https://cdn.jsdelivr.net/npm/monaco-editor@0.43.0/min/vs'
  }
})
const AV = 'aviatorscript'
type onMountF = VueMonacoEditorEmitsOptions['mount']
type editor = Parameters<onMountF>[0] // monacoEditor.editor.IStandaloneCodeEditor
const editorRef = shallowRef<editor>()
const monacoRef = shallowRef<MonacoEditor>()
const grammarParser = new GrammarParser()

const handleMount: onMountF = (editor, monaco) => {
  editorRef.value = editor
  monacoRef.value = monaco
  monaco.languages.register({ id: AV })
  monaco.languages.setMonarchTokensProvider(AV, monarch)
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
const handleChange = (value: string | undefined) => {
  if (!value) return
  const ast = grammarParser.parse(value + '\n')
  const markers = []
  for (let i = 0; i < ast.errors.length; i++) {
    const error = ast.errors[i]
    markers.push({
      severity: monacoRef.value!.MarkerSeverity.Error,
      startLineNumber: error.line,
      startColumn: error.column,
      endLineNumber: error.line,
      endColumn: error.column,
      message: error.message
    })
  }
  for (let i = 1; i < ast.ast.children.length - 1; i++) {
    const child = ast.ast.children[i]
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
