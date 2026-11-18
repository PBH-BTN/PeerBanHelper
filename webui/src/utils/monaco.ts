import { loader } from '@guolao/vue-monaco-editor'
import * as monaco from 'monaco-editor/editor'
// Keep editor features without loading every built-in language and its worker.
import 'monaco-editor/features/register.all'

loader.config({ monaco })
