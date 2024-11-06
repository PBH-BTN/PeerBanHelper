import type { languages } from 'monaco-editor'
const monarch = {
  // Set defaultToken to invalid to see what you do not tokenize yet
  // defaultToken: 'invalid',

  keywords: [
    'continue',
    'for',
    'switch',
    'goto',
    'do',
    'if',
    'this',
    'break',
    'throw',
    'else',
    'elsif',
    'enum',
    'return',
    'catch',
    'try',
    'in',
    'finally',
    'const',
    'super',
    'while',
    'true',
    'false',
    'fn',
    'let',
    'lambda',
    'exports',
    'new',
    'end',
    'nil',
    'use'
  ],

  typeKeywords: [
    // 'boolean', 'double', 'byte', 'int', 'short', 'char', 'void', 'long', 'float'
  ],

  operators: [
    '=',
    '>',
    '<',
    '!',
    '~',
    '?',
    ':',
    '==',
    '<=',
    '>=',
    '!=',
    '&&',
    '||',
    '+',
    '-',
    '*',
    '/',
    '&',
    '|',
    '^',
    '%',
    '<<',
    '>>',
    '>>>'
  ],

  // we include these common regular expressions
  symbols: /[=><!~?:&|+\-*\/\^%\u4e00-\u9fa5]+/,

  // style strings
  escapes: /\\(?:[abfnrtv\\"']|x[0-9A-Fa-f]{1,4}|u[0-9A-Fa-f]{4}|U[0-9A-Fa-f]{8})/,

  // The main tokenizer for our languages
  tokenizer: {
    root: [
      // numbers
      [/\d*\.\d+([eE][\-+]?\d+)?/, 'number.float'],
      [/0[xX][0-9a-fA-F]+/, 'number.hex'],

      // identifiers and keywords
      [
        /[a-z_\u4e00-\u9fa5][\w\u4e00-\u9fa5$]*/,
        {
          cases: {
            '@typeKeywords': 'keyword',
            '@keywords': 'keyword',
            '@default': 'identifier'
          }
        }
      ],
      [/[A-Z][\w\$]*/, 'type.identifier'], // to show class names nicely

      // whitespace
      {
        include: '@whitespace'
      },

      // delimiters and operators
      [/[{}()\[\]]/, '@brackets'],
      [/[<>](?!@symbols)/, '@brackets'],
      [
        /@symbols/,
        {
          cases: {
            '@operators': 'operator',
            '@default': ''
          }
        }
      ],

      // @ annotations.
      // As an example, we emit a debugging log message on these tokens.
      // Note: message are supressed during the first load -- change some lines to see them.
      [
        /@\s*[a-zA-Z_\$][\w\$]*/,
        {
          token: 'annotation',
          log: 'annotation token: $0'
        }
      ],

      // delimiter: after number because of .\d floats
      [/[;,.]/, 'delimiter'],

      // strings
      [/"([^"\\]|\\.)*$|'([^'\\]|\\.)*$/, 'string.invalid'], // non-teminated string
      [
        /"|'/,
        {
          token: 'string.quote',
          bracket: '@open',
          next: '@string'
        }
      ],

      // characters
      [/'[^\\']'/, 'string'],
      [/(')(@escapes)(')/, ['string', 'string.escape', 'string']],
      [/'/, 'string.invalid']
    ],

    comment: [
      [/[^\/*]+/, 'comment'],
      [/\/\*/, 'comment', '@push'], // nested comment
      ['\\*/', 'comment', '@pop'],
      [/[\/*]/, 'comment']
    ],

    string: [
      [/[^\\"']+/, 'string'],
      [/@escapes/, 'string.escape'],
      [/\\./, 'string.escape.invalid'],
      [
        /"|'/,
        {
          token: 'string.quote',
          bracket: '@close',
          next: '@pop'
        }
      ]
    ],

    whitespace: [
      [/[ \t\r\n]+/, 'white'],
      [/\/\*/, 'comment', '@comment'],
      [/\/\/.*$/, 'comment'],
      [/##.*$/, 'comment']
    ]
  }
} as languages.IMonarchLanguage
export default monarch
