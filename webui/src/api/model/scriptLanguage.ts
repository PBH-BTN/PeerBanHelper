export const scriptLanguages = {
  '.av': { label: 'AviatorScript', monaco: 'aviatorscript' },
  '.js': { label: 'JavaScript', monaco: 'javascript' },
  '.lua': { label: 'Lua', monaco: 'lua' }
} as const

export type ScriptExtension = keyof typeof scriptLanguages

export function scriptExtension(filename: string): ScriptExtension | undefined {
  return extensions.find((extension) => filename.endsWith(extension))
}

const extensions = Object.keys(scriptLanguages) as ScriptExtension[]

export async function readScriptEngines(response: Response): Promise<ScriptExtension[]> {
  // Legacy servers match /engines as a filename and reject its missing extension.
  if (response.status === 400 || response.status === 404) return ['.av']
  if (!response.ok) throw new Error(response.statusText || String(response.status))
  const result = await response.json()
  if (!result.success || !Array.isArray(result.data)) throw new Error(result.message)
  return extensions.filter((extension) => result.data.includes(extension))
}

export function canEditScript(filename: string, loaded?: boolean, engines?: ScriptExtension[]) {
  const extension = scriptExtension(filename)
  return !!extension && (loaded === false || engines?.includes(extension) === true)
}

export function scriptFilename(input: string, extension: ScriptExtension) {
  const name = input.trim()
  const suffix = scriptExtension(name)
  const base = suffix ? name.slice(0, -suffix.length) : name
  if (!base || /^\.+$/.test(base) || /[/\\]/.test(base) || (suffix && suffix !== extension)) {
    return undefined
  }
  return base + extension
}
