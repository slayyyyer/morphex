import { fileURLToPath } from 'node:url';
import js from '@eslint/js';
import prettier from 'eslint-config-prettier';
import tseslint from 'typescript-eslint';

const tsconfigRootDir = fileURLToPath(new URL('.', import.meta.url));

export default tseslint.config(
  {
    ignores: ['dist/**', 'node_modules/**', 'src/generated/**', 'coverage/**', 'eslint.config.js'],
  },
  js.configs.recommended,
  ...tseslint.configs.strictTypeChecked,
  {
    languageOptions: {
      parserOptions: {
        projectService: true,
        tsconfigRootDir,
      },
    },
    rules: {
      '@typescript-eslint/no-confusing-void-expression': 'off',
      '@typescript-eslint/restrict-template-expressions': [
        'error',
        { allowNumber: true, allowBoolean: true },
      ],
    },
  },
  {
    files: ['tests/**/*.ts'],
    rules: {
      '@typescript-eslint/no-non-null-assertion': 'off',
    },
  },
  prettier,
);
