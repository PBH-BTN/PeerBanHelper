/// <reference types="vite/client" />
interface ImportMetaEnv {
  readonly VITE_APP_BASE_URL: string
  // more env variables...
}

// add by define
declare const __APP_VERSION__: string
declare const __APP_HASH__: string
