import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import tseslint from 'typescript-eslint'
import { defineConfig, globalIgnores } from 'eslint/config'

/**
 * UI-05: nenhum controle com a aparência padrão do navegador fora de src/components/ui/ (onde ficam os
 * componentes do shadcn, que escondem e estilizam o elemento nativo). Cada item aponta o substituto.
 */
const NATIVE_CONTROLS = [
  {
    selector: "JSXOpeningElement[name.name='select']",
    message: 'UI-05: use Select ou Combobox de @/components/ui, não <select>.',
  },
  {
    selector:
      "JSXOpeningElement[name.name=/^(input|Input|InputGroupInput)$/] > JSXAttribute[name.name='type'][value.value=/^(checkbox|radio)$/]",
    message: 'UI-05: use Checkbox, RadioGroup ou Switch de @/components/ui.',
  },
  {
    selector:
      "JSXOpeningElement[name.name=/^(input|Input|InputGroupInput)$/] > JSXAttribute[name.name='type'][value.value='range']",
    message: 'UI-05: use Slider de @/components/ui.',
  },
  {
    selector:
      "JSXOpeningElement[name.name=/^(input|Input|InputGroupInput)$/] > JSXAttribute[name.name='type'][value.value=/^(date|time|datetime-local|month|week)$/]",
    message: 'UI-05: use DatePicker ou DateTimePicker de @/components/date-picker.',
  },
  {
    selector:
      "JSXOpeningElement[name.name=/^(input|Input|InputGroupInput)$/] > JSXAttribute[name.name='type'][value.value='color']",
    message: 'UI-05: seletor de cor próprio; nada de <input type="color">.',
  },
  {
    selector:
      "JSXOpeningElement[name.name=/^(input|Input|InputGroupInput)$/] > JSXAttribute[name.name='type'][value.value='password']",
    message: 'UI-05: use PasswordInput de @/components/password-input (botão de mostrar a senha próprio).',
  },
  {
    selector:
      "JSXOpeningElement[name.name='input']:has(JSXAttribute[name.name='type'][value.value='file']):not(:has(JSXAttribute[name.name='hidden'])):not(:has(JSXAttribute[name.name='className'][value.value=/\\bsr-only\\b/]))",
    message: 'UI-05: <input type="file"> fica oculto (hidden) e é acionado por um botão do projeto.',
  },
  {
    selector: "JSXOpeningElement[name.name=/^(progress|meter)$/]",
    message: 'UI-05: use Progress de @/components/ui.',
  },
  {
    selector: "JSXOpeningElement[name.name=/^(audio|video)$/] > JSXAttribute[name.name='controls']",
    message: 'UI-05: sem controls nativos; use VideoPlayer, LocalAudioPlayer ou AudioControls de @/features/media.',
  },
  {
    selector: "JSXOpeningElement[name.name=/^(details|summary)$/]",
    message: 'UI-05: use Accordion ou Collapsible de @/components/ui.',
  },
  {
    selector: "JSXOpeningElement[name.name=/^(?!iframe$)[a-z][a-z0-9]*$/] > JSXAttribute[name.name='title']",
    message: 'UI-05: title="" mostra a dica nativa; use Tooltip (o iframe pode, é o nome acessível dele).',
  },
  {
    selector: "JSXOpeningElement[name.name='form']:not(:has(JSXAttribute[name.name='noValidate']))",
    message: 'UI-05: <form noValidate>; os erros aparecem com FieldError ou toast, não no balão do navegador.',
  },
]

/**
 * MS-01: texto do usuário nunca vira HTML. Nada de dangerouslySetInnerHTML, innerHTML/outerHTML nem
 * insertAdjacentHTML em lugar nenhum do app (também em src/components/ui/).
 */
const RAW_HTML = [
  {
    selector: "JSXAttribute[name.name='dangerouslySetInnerHTML']",
    message: 'MS-01: sem dangerouslySetInnerHTML; renderize nós do React (o texto do usuário fica texto).',
  },
  {
    selector: "AssignmentExpression > MemberExpression.left[property.name=/^(innerHTML|outerHTML)$/]",
    message: 'MS-01: sem innerHTML/outerHTML; use textContent ou nós do React.',
  },
  {
    selector: "CallExpression[callee.property.name='insertAdjacentHTML']",
    message: 'MS-01: sem insertAdjacentHTML; use nós do React.',
  },
]

/**
 * Ação com falha visível (AGENTS, UI-03): todo `.mutate(...)` passa um `onError` (useErrorToast ou o tratamento da
 * tela); quem mostra o erro no próprio estado (`isError`) desliga a regra na linha com o motivo. `mutateAsync`
 * fica com try/catch.
 */
const SILENT_MUTATION = [
  {
    selector: "CallExpression[callee.property.name='mutate']:not(:has(Property[key.name='onError']))",
    message: 'UI-03: .mutate(...) sem onError deixa a falha silenciosa; passe { onError } (useErrorToast).',
  },
]

/**
 * IN-01: texto da interface só pelo i18next. Nada de letras soltas no JSX nem em atributos que o leitor de tela
 * ou a pessoa leem (aria-label, placeholder, alt, title); números, símbolos e pontuação podem.
 */
const READ_ATTRIBUTES =
  'aria-label|aria-description|aria-placeholder|aria-roledescription|aria-valuetext|placeholder|alt|title|label'
const LITERAL_TEXT = [
  {
    selector: "JSXText[value=/[A-Za-zÀ-ÿ]/]",
    message: 'IN-01: texto fixo na interface; use t("chave") com a chave nos dois idiomas.',
  },
  {
    selector: [
      ':matches(JSXElement, JSXFragment) > JSXExpressionContainer > Literal[value=/[A-Za-zÀ-ÿ]/]',
      ':matches(JSXElement, JSXFragment) > JSXExpressionContainer > TemplateLiteral > TemplateElement[value.raw=/[A-Za-zÀ-ÿ]/]',
    ].join(', '),
    message: 'IN-01: texto fixo na interface; use t("chave") com a chave nos dois idiomas.',
  },
  {
    selector: [
      `JSXAttribute[name.name=/^(${READ_ATTRIBUTES})$/] > Literal[value=/[A-Za-zÀ-ÿ]/]`,
      `JSXAttribute[name.name=/^(${READ_ATTRIBUTES})$/] > JSXExpressionContainer > Literal[value=/[A-Za-zÀ-ÿ]/]`,
      `JSXAttribute[name.name=/^(${READ_ATTRIBUTES})$/] > JSXExpressionContainer > TemplateLiteral > TemplateElement[value.raw=/[A-Za-zÀ-ÿ]/]`,
    ].join(', '),
    message: 'IN-01: texto fixo num atributo lido pela pessoa; use t("chave").',
  },
]

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
      // alert/confirm/prompt são diálogos do navegador (UI-05): AlertDialog, Dialog ou toast.
      'no-alert': 'error',
      'no-restricted-imports': [
        'error',
        {
          paths: [
            {
              name: 'zod',
              message: 'Importe { z } de "@/lib/zod" (modo jitless, exigido pela CSP).',
            },
            {
              name: '@/components/ui/native-select',
              message: 'UI-05: NativeSelect é proibido; use Select ou Combobox.',
            },
          ],
        },
      ],
    },
  },
  {
    files: ['src/**/*.{ts,tsx}'],
    rules: {
      'no-restricted-syntax': ['error', ...RAW_HTML, ...SILENT_MUTATION],
    },
  },
  {
    files: ['src/**/*.tsx'],
    ignores: ['src/components/ui/**'],
    rules: {
      'no-restricted-syntax': ['error', ...NATIVE_CONTROLS, ...RAW_HTML, ...SILENT_MUTATION, ...LITERAL_TEXT],
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
