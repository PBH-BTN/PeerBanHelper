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
import { type MonacoEditor, VueMonacoEditor, loader } from '@guolao/vue-monaco-editor'
import { MarkerSeverity, editor, type languages } from 'monaco-editor/esm/vs/editor/editor.api'
import { shallowRef } from 'vue'
import GrammarParser from './aviatorscript/grammar/GrammarParser'
import monarch from './aviatorscript/monarch'
import suggestions from './aviatorscript/suggestions'
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
const editorRef = shallowRef<editor.IStandaloneCodeEditor>()
const handleMount = (editor: editor.IStandaloneCodeEditor, monaco: MonacoEditor) => {
  editorRef.value = editor
  monaco.languages.register({ id: AV })
  monaco.languages.setMonarchTokensProvider(AV, monarch)
  monaco.languages.registerCompletionItemProvider(AV, {
    provideCompletionItems: () => ({
      suggestions: suggestions as languages.CompletionItem[]
    })
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
      severity: MarkerSeverity.Error,
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
      severity: MarkerSeverity.Error,
      startLineNumber: child.line,
      startColumn: child.column,
      endLineNumber: child.line,
      endColumn: child.column,
      message: child.text
    })
  }
  const model = editorRef.value?.getModel()
  if (model) editor.setModelMarkers(model, AV, markers)
}
</script>
