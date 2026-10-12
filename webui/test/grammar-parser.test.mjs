import assert from 'node:assert/strict'
import { after, before, test } from 'node:test'
import { createServer } from 'vite'

let server
let GrammarParser

before(async () => {
  server = await createServer({
    appType: 'custom',
    configFile: false,
    optimizeDeps: { noDiscovery: true },
    server: { middlewareMode: true }
  })
  ;({ default: GrammarParser } = await server.ssrLoadModule(
    '/src/views/custom-script/components/editor/aviatorscript/grammar/GrammarParser.ts'
  ))
})

after(async () => {
  await server?.close()
})

test('reports an unclosed parenthesis in an incomplete if condition', () => {
  const code = `## @NAME IPV6 ::1 Transmission 2.94 恶意多拨
## @AUTHOR PBH-BTN Community
## @CACHEABLE true
## @VERSION 1.1
## @THREADSAFE true

if(isBlank(peer.clientName)) {
    return false; ## 下载器必须提供 ClientName
}

ipAddress = peer.peerAddress.address;
strIp = toString(ipAddress);

if(!string.contains(peer.clientName, 'Transmission 2.94')){
    return false; ## 不管非 Transmission 2.94 的
}

if(string.endsWith(strIp, "::1")){
    return "Transmission 2.94 (IPV6 ::1) 多拨伪装吸血";
}

if(true

return false;
`

  const result = new GrammarParser().parse(code)

  assert.ok(
    result.errors.some(
      ({ line, column, message }) => line === 22 && column === 7 && message === "missing ')'"
    )
  )
})

test('accepts the same script after the if condition is completed', () => {
  const code = `if(true) {
    return true;
}
return false;
`

  assert.deepEqual(new GrammarParser().parse(code).errors, [])
})

test('reports an expression statement without a semicolon', () => {
  const result = new GrammarParser().parse(`sa
return false;
`)

  assert.ok(
    result.errors.some(
      ({ line, column, message }) => line === 1 && column === 2 && message === "missing ';'"
    )
  )
})

test('reports a return statement without a semicolon', () => {
  const result = new GrammarParser().parse(`if(a){
    return 1
}
return false;
`)

  assert.ok(
    result.errors.some(
      ({ line, column, message }) => line === 2 && column === 12 && message === "missing ';'"
    )
  )
})

test('accepts expression and return statements terminated by semicolons', () => {
  const code = `sa;
if(a){
    return 1;
}
return false;
`

  assert.deepEqual(new GrammarParser().parse(code).errors, [])
})
