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
      @change="setMarkers"
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
import { shallowRef, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import GrammarParser from './aviatorscript/grammar/GrammarParser'
import monarch from './aviatorscript/monarch'
import getSuggestion from './aviatorscript/suggestions'

const { t } = useI18n()

const { viewOnly = false } = defineProps<{
  viewOnly?: boolean
}>()
const model = defineModel<string | undefined>({ required: true })

const AV = 'aviatorscript'
type onMountF = VueMonacoEditorEmitsOptions['mount']
type editor = Parameters<onMountF>[0] // monacoEditor.editor.IStandaloneCodeEditor
const editorRef = shallowRef<editor>()
const monacoRef = shallowRef<MonacoEditor>()
const grammarParser = new GrammarParser()
let lastValidatedValue: string | undefined
let hasCachedValidation = false
let lastValidationResult = true

const setMarkers = (value: string | undefined) => {
  const editorModel = editorRef.value?.getModel()
  const monaco = monacoRef.value
  if (!editorModel || !monaco) return true
  if (hasCachedValidation && value === lastValidatedValue) return lastValidationResult

  if (!value) {
    monaco.editor.setModelMarkers(editorModel, AV, [])
    lastValidatedValue = value
    hasCachedValidation = true
    lastValidationResult = true
    return lastValidationResult
  }

  const code = value.endsWith('\n') ? value : `${value}\n`
  const { errors } = grammarParser.parse(code)
  const markers = errors.map((error) => {
    const lineNumber = Math.min(Math.max(error.line, 1), editorModel.getLineCount())
    const maxColumn = editorModel.getLineMaxColumn(lineNumber)
    const startColumn = Math.min(Math.max(error.column + 1, 1), maxColumn)

    return {
      severity: monaco.MarkerSeverity.Error,
      startLineNumber: lineNumber,
      startColumn,
      endLineNumber: lineNumber,
      endColumn: Math.min(startColumn + 1, maxColumn),
      message: error.message
    }
  })

  monaco.editor.setModelMarkers(editorModel, AV, markers)
  lastValidatedValue = value
  hasCachedValidation = true
  lastValidationResult = markers.length === 0
  return lastValidationResult
}

const validate = () => setMarkers(model.value)

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
  setMarkers(editor.getValue())
}

watch(model, setMarkers, { flush: 'post' })

defineExpose({ validate })
</script>
