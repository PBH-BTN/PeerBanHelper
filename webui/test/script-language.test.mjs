import assert from 'node:assert/strict'
import { before, after, test } from 'node:test'
import { createServer } from 'vite'

let language
let server
before(async () => {
  server = await createServer({
    appType: 'custom',
    configFile: false,
    optimizeDeps: { noDiscovery: true },
    server: { middlewareMode: true, ws: false }
  })
  language = await server.ssrLoadModule('/src/api/model/scriptLanguage.ts')
})
after(async () => {
  await server?.close()
})

test('offers only advertised and recognized languages', async () => {
  for (const [advertised, expected] of [
    [
      ['.lua', '.js', '.av'],
      ['.av', '.js', '.lua']
    ],
    [['.lua', '.py', '.lua'], ['.lua']],
    [[], []]
  ]) {
    const response = Response.json({ success: true, data: advertised })
    assert.deepEqual(await language.readScriptEngines(response), expected)
  }
})

test('uses Aviator for legacy routes and surfaces service failures', async () => {
  for (const status of [400, 404]) {
    assert.deepEqual(await language.readScriptEngines(new Response(null, { status })), ['.av'])
  }
  for (const status of [401, 403, 500]) {
    await assert.rejects(language.readScriptEngines(new Response(null, { status })))
  }
  await assert.rejects(
    language.readScriptEngines(Response.json({ success: false, message: 'unavailable' })),
    /unavailable/
  )
})

test('new and existing filenames resolve to the same editor language', () => {
  for (const [suffix, mode] of [
    ['.av', 'aviatorscript'],
    ['.js', 'javascript'],
    ['.lua', 'lua']
  ]) {
    const id = '规则.v2' + suffix
    assert.equal(language.scriptFilename(' 规则.v2 ', suffix), id)
    assert.equal(language.scriptFilename(id, suffix), id)
    assert.equal(language.scriptLanguages[language.scriptExtension(id)].monaco, mode)
  }
  assert.equal(language.scriptExtension('other.py'), undefined)
})

test('rejects empty names, paths, and a conflicting language suffix', () => {
  for (const name of ['', '.av', '..', 'dir/test', 'dir\\test', 'rule.lua']) {
    assert.equal(language.scriptFilename(name, '.av'), undefined)
  }
})

test('allows repairing unloaded files when their engine is unavailable', () => {
  assert.equal(language.canEditScript('rule.lua', false, ['.av']), true)
  assert.equal(language.canEditScript('rule.av', undefined, ['.av']), true)
  assert.equal(language.canEditScript('rule.lua', undefined, ['.av']), false)
  assert.equal(language.canEditScript('rule.lua', true, ['.av']), false)
  assert.equal(language.canEditScript('rule.py', false, []), false)
})
