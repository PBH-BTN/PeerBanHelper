import tsParser from '@typescript-eslint/parser'
import eslintConfigPrettier from 'eslint-config-prettier'
import pluginVue from 'eslint-plugin-vue'
import ts from 'typescript-eslint'

export default [
  ...ts.configs.recommended,
  ...pluginVue.configs['flat/recommended'],
  eslintConfigPrettier,
  {
    files: ['**/*.ts', '**/*.js', '**/*.vue'],
    languageOptions: {
      parserOptions: {
        parser: tsParser
      }
    },
    ignores: ['dist/*', 'node_modules/*'],
    rules: {
      'vue/multi-word-component-names': 0,
      'vue/no-unused-vars': ['error', { ignorePattern: '^_' }],
      '@typescript-eslint/no-unused-vars': [
        'error',
        {
          args: 'all',
          argsIgnorePattern: '^_',
          caughtErrors: 'all',
          caughtErrorsIgnorePattern: '^_',
          destructuredArrayIgnorePattern: '^_',
          varsIgnorePattern: '^_',
          ignoreRestSiblings: true
        }
      ]
    }
  }
]
