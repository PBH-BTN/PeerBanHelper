<template>
  <div style="height: 80vh">
    <VueMonacoEditor
      theme="vs-dark"
      class="editor"
      :options="MONACO_EDITOR_OPTIONS"
      :default-language="AV"
      @mount="handleMount"
      @change="handleChange"
    />
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
import GrammarParser from './aviatorscript/grammar/GrammarParser'
import monarch from './aviatorscript/monarch'
import getSuggestion from './aviatorscript/suggestions'
loader.config({
  paths: {
    vs: 'https://cdn.jsdelivr.net/npm/monaco-editor@0.43.0/min/vs'
  }
})
const AV = 'aviatorscript'
const MONACO_EDITOR_OPTIONS = {
  automaticLayout: true,
  formatOnType: true,
  formatOnPaste: true
}
type onMountF = VueMonacoEditorEmitsOptions['mount']
type editor = Parameters<onMountF>[0] // monacoEditor.editor.IStandaloneCodeEditor
const editorRef = shallowRef<editor>()
const monacoRef = shallowRef<MonacoEditor>()
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
}
const grammarParser = new GrammarParser()
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
