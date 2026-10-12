import { CharStreams, CommonTokenStream, ErrorListener } from 'antlr4'

import DSLLexer from './DSLLexer'
import DSLParser from './DSLParser'

export interface SyntaxError {
  line: number
  column: number
  message: string
}

export default class GrammarParser {
  parse(code: string) {
    const chars = CharStreams.fromString(code)
    const lexer = new DSLLexer(chars)
    const tokens = new CommonTokenStream(lexer)
    const parser = new DSLParser(tokens)

    const errors: SyntaxError[] = []
    class MyErrorListener extends ErrorListener<unknown> {
      constructor() {
        super()
      }
      syntaxError(
        _recognizer: unknown,
        _offendingSymbol: unknown,
        line: number,
        column: number,
        message: string,
        _e: unknown
      ) {
        errors.push({ line, column, message })
      }
    }

    lexer.removeErrorListeners()
    lexer.addErrorListener(new MyErrorListener())
    parser.removeErrorListeners()
    parser.addErrorListener(new MyErrorListener())

    parser.buildParseTrees = true
    parser.root()

    return { errors }
  }
}
