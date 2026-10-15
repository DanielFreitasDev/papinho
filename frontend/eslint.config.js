import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import tseslint from 'typescript-eslint'
import { defineConfig, globalIgnores } from 'eslint/config'

export default defineConfig([
  globalIgnores(['dist', 'playwright-report', 'test-results']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      js.configs.recommended,
      tseslint.configs.recommended,
      reactHooks.configs.flat.recommended,
      reactRefresh.configs.vite,
    ],
    languageOptions: {
      globals: globals.browser,
    },
    rules: {
      'react-refresh/only-export-components': 'off',
      'no-restricted-imports': [
        'error',
        {
          paths: [
            {
              name: 'zod',
              message: 'Importe { z } de "@/lib/zod" (modo jitless, exigido pela CSP).',
            },
          ],
        },
      ],
    },
  },
  {
    files: ['src/lib/zod.ts'],
    rules: { 'no-restricted-imports': 'off' },
  },
  {
    // Fixtures do Playwright recebem um parâmetro chamado `use`, que não é o hook do React.
    files: ['e2e/**/*.ts'],
    rules: {
      'react-hooks/rules-of-hooks': 'off',
    },
  },
])
