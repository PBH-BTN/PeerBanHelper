import { languages } from 'monaco-editor'

const suggestions = [
  {
    label: 'math',
    kind: languages.CompletionItemKind.Text,
    insertText: 'math.',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'println',
    kind: languages.CompletionItemKind.Text,
    insertText: 'println(${0:text})',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'for in',
    kind: languages.CompletionItemKind.Snippet,
    insertText: ['for ${1:x} in ${2:object} {', '\t${3:statement};', '}'].join('\n'),
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'for in range',
    kind: languages.CompletionItemKind.Snippet,
    insertText: ['for ${1:i} in range(0, ${2:N})  {', '\t${3:statement};', '}'].join('\n'),
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'while',
    kind: languages.CompletionItemKind.Snippet,
    insertText: ['while (${1:condition}) {', '\t$0', '}'].join('\n'),
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'fn',
    kind: languages.CompletionItemKind.Snippet,
    insertText: ['fn ${1:name}(${2:paramList}) {', '\t${3}', '}'].join('\n'),
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'try-catch',
    kind: languages.CompletionItemKind.Snippet,
    insertText: ['try {', '${1:statement1};', '} catch(e){', '\t$0', '}'].join('\n'),
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'try-catch-finally',
    kind: languages.CompletionItemKind.Snippet,
    insertText: [
      'try {',
      '\t${1:statement1};',
      '} catch(e){',
      '\t$0',
      '} finally {',
      '\t$0',
      '}'
    ].join('\n'),
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'let name',
    kind: languages.CompletionItemKind.Keyword,
    insertText: 'let ${1:name};',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'let name = value',
    kind: languages.CompletionItemKind.Keyword,
    insertText: 'let ${1:name}=$0;',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'true',
    kind: languages.CompletionItemKind.Keyword,
    insertText: 'true',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'false',
    kind: languages.CompletionItemKind.Keyword,
    insertText: 'false',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'use',
    kind: languages.CompletionItemKind.Keyword,
    insertText: 'use',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'break',
    kind: languages.CompletionItemKind.Keyword,
    insertText: 'break;',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'continue',
    kind: languages.CompletionItemKind.Keyword,
    insertText: 'continue;',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'throw',
    kind: languages.CompletionItemKind.Keyword,
    insertText: 'throw;',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'return',
    kind: languages.CompletionItemKind.Keyword,
    insertText: 'return',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'lambda',
    kind: languages.CompletionItemKind.Keyword,
    insertText: 'lambda',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'end',
    kind: languages.CompletionItemKind.Keyword,
    insertText: 'end;',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'new',
    kind: languages.CompletionItemKind.Keyword,
    insertText: 'new ',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'nil',
    kind: languages.CompletionItemKind.Keyword,
    insertText: 'nil',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'end',
    kind: languages.CompletionItemKind.Keyword,
    insertText: 'end;',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'assert',
    kind: languages.CompletionItemKind.Keyword,
    insertText: 'assert(${1:condition})',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'if',
    kind: languages.CompletionItemKind.Snippet,
    insertText: ['if (${1:condition}) {', '\t$0', '}'].join('\n'),
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet,
    documentation: 'If-Else Statement'
  },
  {
    label: 'if-else',
    kind: languages.CompletionItemKind.Snippet,
    insertText: ['if (${1:condition}) {', '\t$0', '} else {', '\t', '}'].join('\n'),
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet,
    documentation: 'If-Else Statement'
  },
  {
    label: 'if-elseif-else',
    kind: languages.CompletionItemKind.Snippet,
    insertText: ['if (${1:condition}) {', '\t$0', '} elsif {', '\t', '} else {', '\t', '}'].join(
      '\n'
    ),
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet,
    documentation: 'If-Else Statement'
  },
  {
    label: 'let-lambda',
    kind: languages.CompletionItemKind.Snippet,
    insertText: ['let ${1:name} = lambda (${2:x}) -> ', '\t$0', 'end;'].join('\n'),
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet,
    documentation: 'If-Else Statement'
  },
  {
    label: 'lambda',
    kind: languages.CompletionItemKind.Snippet,
    insertText: ['lambda (${1:x}) -> ', '\t$0', 'end;'].join('\n'),
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet,
    documentation: 'If-Else Statement'
  },
  {
    label: 'assert(predicate, [msg])',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'assert($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'sysdate()',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'sysdate($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'rand()',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'rand($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'rand(n)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'rand($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'cmp(x, y)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'cmp($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'print([out],obj)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'print($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'println([out],obj)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'println($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'p([out], obj)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'p($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'pst([out], e);',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'pst($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'now()',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'now($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'long(v)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'long($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'double(v)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'double($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'boolean(v)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'boolean($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'str(v)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'str($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'bigint(x)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'bigint($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'decimal(x)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'decimal($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'identity(v)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'identity($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'type(x)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'type($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'is_a(x, class)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'is_a($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'is_def(x)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'is_def($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'undef(x)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'undef($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'range(start, end, [step])',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'range($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'tuple(x1, x2, ...)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'tuple($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'eval(script, [bindings], [cached])',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'eval($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'comparator(pred)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'comparator($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'max(x1, x2, x3, ...)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'max($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'min(x1, x2, x3, ...)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'min($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'constantly(x)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'constantly($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'date_to_string(date,format)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'date_to_string($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'string_to_date(source,format)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'string_to_date($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'string.contains(s1,s2)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'string.contains($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'string.length(s)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'string.length($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'string.startsWith(s1,s2)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'string.startsWith($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'string.endsWith(s1,s2)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'string.endsWith($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'string.substring(s,begin[,end])',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'string.substring($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'string.indexOf(s1,s2)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'string.indexOf($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'string.split(target,regex,[limit])',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'string.split($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'string.join(seq,seperator)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'string.join($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'string.replace_first(s,regex,replacement)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'string.replace_first($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'string.replace_all(s,regex,replacement)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'string.replace_all($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'math.abs(d)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'math.abs($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'math.round(d)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'math.round($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'math.floor(d)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'math.floor($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'math.ceil(d)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'math.ceil($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'math.sqrt(d)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'math.sqrt($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'math.pow(d1,d2)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'math.pow($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'math.log(d)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'math.log($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'math.log10(d)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'math.log10($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'math.sin(d)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'math.sin($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'math.cos(d)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'math.cos($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'math.tan(d)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'math.tan($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'math.atan(d)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'math.atan($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'math.acos(d)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'math.acos($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'math.asin(d)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'math.asin($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'repeat(n, x)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'repeat($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'repeatedly(n, f)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'repeatedly($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'seq.array(clazz, e1, e2,e3, ...)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'seq.array($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'seq.array_of(clazz, size1, size2, ...sizes)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'seq.array_of($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'seq.list(p1, p2, p3, ...)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'seq.list($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'seq.set(p1, p2, p3, ...)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'seq.set($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'seq.map(k1, v1, k2, v2, ...)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'seq.map($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'seq.entry(key, value)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'seq.entry($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'seq.keys(m)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'seq.keys($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'seq.vals(m)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'seq.vals($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'into(to_seq, from_seq)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'into($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'seq.contains_key(map, key)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'seq.contains_key($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'seq.add(coll, element)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'seq.add($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'seq.add(m, key, value)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'seq.add($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'seq.put(coll, key, value)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'seq.put($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'seq.remove(coll, element)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'seq.remove($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'seq.get(coll, element)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'seq.get($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'map(seq,fun)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'map($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'filter(seq,predicate)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'filter($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'count(seq)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'count($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'is_empty(seq)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'is_empty($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'distinct(seq)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'distinct($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'is_distinct(seq)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'is_distinct($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'concat(seq1, seq2)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'concat($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'include(seq,element)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'include($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'sort(seq, [comparator])',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'sort($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'reverse(seq)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'reverse($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'reduce(seq,fun,init)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'reduce($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'take_while(seq, pred)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'take_while($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'drop_while(seq, pred)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'drop_while($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'group_by(seq, keyfn)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'group_by($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'zipmap(keys, values)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'zipmap($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'seq.every(seq, fun)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'seq.every($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'seq.not_any(seq, fun)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'seq.not_any($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'seq.some(seq, fun)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'seq.some($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'seq.eq(value)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'seq.eq($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'seq.neq(value)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'seq.neq($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'seq.gt(value)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'seq.gt($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'seq.ge(value)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'seq.ge($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'seq.lt(value)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'seq.lt($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'seq.le(value)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'seq.le($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'seq.nil()',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'seq.nil($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'seq.exists()',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'seq.exists($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'seq.and(p1, p2, p3, ...)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'seq.and($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'seq.or(p1, p2, p3, ...)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'seq.or($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'seq.min(coll)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'seq.min($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'seq.max(coll)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'seq.max($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'load(path)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'load($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  },
  {
    label: 'require(path)',
    kind: languages.CompletionItemKind.Snippet,
    insertText: 'require($0)',
    insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet
  }
]

export default suggestions
