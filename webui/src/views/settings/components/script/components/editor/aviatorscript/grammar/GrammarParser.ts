import { CharStreams, CommonTokenStream, ErrorListener, ParserRuleContext } from 'antlr4'

import DSLLexer from './DSLLexer'
import DSLParser from './DSLParser'

export default class GrammarParser {
  parse(code: string) {
    const chars = CharStreams.fromString(code)
    const lexer = new DSLLexer(chars)
    const tokens = new CommonTokenStream(lexer)
    const parser = new DSLParser(tokens)

    const errors: { line: number; column: number; message: string }[] = []
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
    const tree = parser.root()

    class Visitor {
      visitChildren(ctx: ParserRuleContext) {
        if (!ctx) {
          return
        }
        if (ctx.children) {
          return ctx.children.map((child) => {
            // @ts-expect-error 类型定义有问题
            if (child.children && child.children.length != 0) {
              // @ts-expect-error 类型定义有问题
              return child.accept(this)
            } else {
              return child.getText()
            }
          })
        }
      }
    }
    // @ts-expect-error 类型定义有问题
    const result = tree.accept(new Visitor())
    return {
      ast: {
        rule: 'root',
        children: result
      },
      errors: errors
    }
  }
}
