// Generated from workspace/2022-02-16-003239-023436000/DSL.g4 by ANTLR 4.9.3
// jshint ignore: start
import {
  ATN,
  ATNDeserializer,
  CommonTokenStream,
  DFA,
  FailedPredicateException,
  NoViableAltException,
  Parser,
  ParserATNSimulator,
  ParserRuleContext,
  PredictionContextCache,
  RecognitionException,
  Token
} from 'antlr4'

const serializedATN = [
  '\u0003\u608b\ua72a\u8133\ub9ed\u417c\u3be7\u7786',
  '\u5964\u0003H\u0262\u0004\u0002\t\u0002\u0004\u0003\t\u0003\u0004\u0004',
  '\t\u0004\u0004\u0005\t\u0005\u0004\u0006\t\u0006\u0004\u0007\t\u0007',
  '\u0004\b\t\b\u0004\t\t\t\u0004\n\t\n\u0004\u000b\t\u000b\u0004\f\t\f',
  '\u0004\r\t\r\u0004\u000e\t\u000e\u0004\u000f\t\u000f\u0004\u0010\t\u0010',
  '\u0004\u0011\t\u0011\u0004\u0012\t\u0012\u0004\u0013\t\u0013\u0004\u0014',
  '\t\u0014\u0004\u0015\t\u0015\u0004\u0016\t\u0016\u0004\u0017\t\u0017',
  '\u0004\u0018\t\u0018\u0004\u0019\t\u0019\u0004\u001a\t\u001a\u0004\u001b',
  '\t\u001b\u0004\u001c\t\u001c\u0004\u001d\t\u001d\u0004\u001e\t\u001e',
  '\u0004\u001f\t\u001f\u0004 \t \u0004!\t!\u0004"\t"\u0004#\t#\u0004',
  "$\t$\u0004%\t%\u0004&\t&\u0004'\t'\u0004(\t(\u0004)\t)\u0004*\t*\u0004",
  '+\t+\u0004,\t,\u0004-\t-\u0004.\t.\u0004/\t/\u00040\t0\u0003\u0002\u0003',
  '\u0002\u0003\u0002\u0003\u0003\u0006\u0003e\n\u0003\r\u0003\u000e\u0003',
  'f\u0003\u0004\u0003\u0004\u0003\u0004\u0005\u0004l\n\u0004\u0003\u0004',
  '\u0003\u0004\u0003\u0004\u0003\u0004\u0003\u0004\u0003\u0004\u0005\u0004',
  't\n\u0004\u0003\u0004\u0003\u0004\u0005\u0004x\n\u0004\u0003\u0004\u0003',
  '\u0004\u0003\u0004\u0003\u0004\u0003\u0004\u0005\u0004\u007f\n\u0004',
  '\u0003\u0004\u0003\u0004\u0005\u0004\u0083\n\u0004\u0003\u0004\u0003',
  '\u0004\u0003\u0004\u0005\u0004\u0088\n\u0004\u0003\u0004\u0003\u0004',
  '\u0003\u0004\u0006\u0004\u008d\n\u0004\r\u0004\u000e\u0004\u008e\u0005',
  '\u0004\u0091\n\u0004\u0003\u0004\u0003\u0004\u0007\u0004\u0095\n\u0004',
  '\f\u0004\u000e\u0004\u0098\u000b\u0004\u0003\u0005\u0003\u0005\u0003',
  '\u0005\u0003\u0005\u0003\u0005\u0003\u0005\u0003\u0005\u0007\u0005\u00a1',
  '\n\u0005\f\u0005\u000e\u0005\u00a4\u000b\u0005\u0003\u0005\u0005\u0005',
  '\u00a7\n\u0005\u0003\u0005\u0003\u0005\u0005\u0005\u00ab\n\u0005\u0003',
  '\u0005\u0003\u0005\u0003\u0006\u0003\u0006\u0005\u0006\u00b1\n\u0006',
  '\u0003\u0006\u0006\u0006\u00b4\n\u0006\r\u0006\u000e\u0006\u00b5\u0003',
  '\u0006\u0007\u0006\u00b9\n\u0006\f\u0006\u000e\u0006\u00bc\u000b\u0006',
  '\u0003\u0006\u0003\u0006\u0003\u0007\u0003\u0007\u0005\u0007\u00c2\n',
  '\u0007\u0003\u0007\u0003\u0007\u0003\u0007\u0003\u0007\u0003\u0007\u0003',
  '\u0007\u0003\u0007\u0003\b\u0005\b\u00cc\n\b\u0003\b\u0003\b\u0003\b',
  '\u0005\b\u00d1\n\b\u0003\b\u0007\b\u00d4\n\b\f\b\u000e\b\u00d7\u000b',
  '\b\u0005\b\u00d9\n\b\u0003\t\u0005\t\u00dc\n\t\u0003\t\u0007\t\u00df',
  '\n\t\f\t\u000e\t\u00e2\u000b\t\u0003\t\u0005\t\u00e5\n\t\u0003\n\u0003',
  '\n\u0003\n\u0003\u000b\u0003\u000b\u0003\u000b\u0007\u000b\u00ed\n\u000b',
  '\f\u000b\u000e\u000b\u00f0\u000b\u000b\u0003\f\u0003\f\u0003\f\u0003',
  '\f\u0003\f\u0003\f\u0005\f\u00f8\n\f\u0003\f\u0003\f\u0003\f\u0003\f',
  '\u0005\f\u00fe\n\f\u0003\r\u0003\r\u0003\r\u0007\r\u0103\n\r\f\r\u000e',
  '\r\u0106\u000b\r\u0003\u000e\u0003\u000e\u0003\u000e\u0003\u000e\u0003',
  '\u000e\u0003\u000e\u0003\u000e\u0005\u000e\u010f\n\u000e\u0003\u000e',
  '\u0003\u000e\u0003\u000e\u0003\u000e\u0005\u000e\u0115\n\u000e\u0003',
  '\u000f\u0003\u000f\u0005\u000f\u0119\n\u000f\u0003\u000f\u0003\u000f',
  '\u0005\u000f\u011d\n\u000f\u0003\u000f\u0003\u000f\u0007\u000f\u0121',
  '\n\u000f\f\u000f\u000e\u000f\u0124\u000b\u000f\u0003\u000f\u0005\u000f',
  '\u0127\n\u000f\u0003\u0010\u0003\u0010\u0003\u0010\u0005\u0010\u012c',
  '\n\u0010\u0003\u0010\u0005\u0010\u012f\n\u0010\u0003\u0010\u0003\u0010',
  '\u0005\u0010\u0133\n\u0010\u0003\u0010\u0003\u0010\u0003\u0011\u0003',
  '\u0011\u0003\u0011\u0003\u0012\u0003\u0012\u0003\u0012\u0005\u0012\u013d',
  '\n\u0012\u0003\u0013\u0003\u0013\u0003\u0013\u0003\u0013\u0003\u0013',
  '\u0003\u0013\u0003\u0014\u0003\u0014\u0005\u0014\u0147\n\u0014\u0003',
  '\u0014\u0003\u0014\u0005\u0014\u014b\n\u0014\u0003\u0014\u0003\u0014',
  '\u0005\u0014\u014f\n\u0014\u0003\u0015\u0003\u0015\u0003\u0016\u0003',
  '\u0016\u0003\u0016\u0003\u0016\u0005\u0016\u0157\n\u0016\u0003\u0016',
  '\u0003\u0016\u0003\u0016\u0003\u0016\u0003\u0017\u0003\u0017\u0003\u0017',
  '\u0003\u0017\u0003\u0017\u0003\u0017\u0003\u0018\u0003\u0018\u0003\u0018',
  '\u0005\u0018\u0166\n\u0018\u0003\u0018\u0003\u0018\u0003\u0018\u0003',
  '\u0018\u0005\u0018\u016c\n\u0018\u0003\u0018\u0003\u0018\u0005\u0018',
  '\u0170\n\u0018\u0003\u0018\u0003\u0018\u0003\u0019\u0003\u0019\u0005',
  '\u0019\u0176\n\u0019\u0003\u0019\u0003\u0019\u0005\u0019\u017a\n\u0019',
  '\u0003\u0019\u0003\u0019\u0003\u001a\u0003\u001a\u0003\u001b\u0003\u001b',
  '\u0003\u001c\u0003\u001c\u0003\u001c\u0003\u001c\u0003\u001c\u0003\u001c',
  '\u0005\u001c\u0188\n\u001c\u0003\u001c\u0003\u001c\u0003\u001c\u0005',
  '\u001c\u018d\n\u001c\u0007\u001c\u018f\n\u001c\f\u001c\u000e\u001c\u0192',
  '\u000b\u001c\u0003\u001c\u0003\u001c\u0007\u001c\u0196\n\u001c\f\u001c',
  '\u000e\u001c\u0199\u000b\u001c\u0003\u001d\u0003\u001d\u0003\u001d\u0005',
  '\u001d\u019e\n\u001d\u0003\u001d\u0007\u001d\u01a1\n\u001d\f\u001d\u000e',
  '\u001d\u01a4\u000b\u001d\u0003\u001e\u0003\u001e\u0003\u001e\u0003\u001e',
  '\u0007\u001e\u01aa\n\u001e\f\u001e\u000e\u001e\u01ad\u000b\u001e\u0003',
  '\u001e\u0003\u001e\u0003\u001f\u0003\u001f\u0003\u001f\u0003\u001f\u0003',
  '\u001f\u0003\u001f\u0003\u001f\u0003\u001f\u0003\u001f\u0003\u001f\u0003',
  '\u001f\u0003\u001f\u0003\u001f\u0003\u001f\u0003\u001f\u0003\u001f\u0003',
  '\u001f\u0003\u001f\u0003\u001f\u0003\u001f\u0003\u001f\u0005\u001f\u01c6',
  '\n\u001f\u0003\u001f\u0003\u001f\u0003\u001f\u0003\u001f\u0003\u001f',
  '\u0003\u001f\u0003\u001f\u0003\u001f\u0003\u001f\u0005\u001f\u01d1\n',
  '\u001f\u0003\u001f\u0003\u001f\u0005\u001f\u01d5\n\u001f\u0003\u001f',
  '\u0003\u001f\u0003\u001f\u0003\u001f\u0003\u001f\u0003\u001f\u0003\u001f',
  '\u0003\u001f\u0003\u001f\u0003\u001f\u0003\u001f\u0003\u001f\u0003\u001f',
  '\u0003\u001f\u0003\u001f\u0003\u001f\u0003\u001f\u0003\u001f\u0003\u001f',
  '\u0003\u001f\u0003\u001f\u0003\u001f\u0003\u001f\u0003\u001f\u0003\u001f',
  '\u0003\u001f\u0003\u001f\u0003\u001f\u0003\u001f\u0003\u001f\u0003\u001f',
  '\u0003\u001f\u0003\u001f\u0003\u001f\u0003\u001f\u0003\u001f\u0007\u001f',
  '\u01fb\n\u001f\f\u001f\u000e\u001f\u01fe\u000b\u001f\u0003 \u0003 \u0003',
  ' \u0003 \u0003 \u0003 \u0007 \u0206\n \f \u000e \u0209\u000b \u0003',
  ' \u0003 \u0005 \u020d\n \u0003!\u0003!\u0003!\u0003!\u0003!\u0003!\u0003',
  '!\u0007!\u0216\n!\f!\u000e!\u0219\u000b!\u0003"\u0003"\u0003"\u0003',
  '#\u0003#\u0003#\u0003$\u0003$\u0003$\u0003%\u0003%\u0003%\u0003&\u0003',
  "&\u0003&\u0003'\u0003'\u0003'\u0003(\u0003(\u0003)\u0003)\u0003*",
  '\u0003*\u0003+\u0003+\u0003,\u0003,\u0003-\u0003-\u0003-\u0003-\u0003',
  '.\u0003.\u0003.\u0003.\u0003.\u0005.\u0240\n.\u0003.\u0003.\u0005.\u0244',
  '\n.\u0003.\u0003.\u0005.\u0248\n.\u0003.\u0003.\u0005.\u024c\n.\u0003',
  '.\u0005.\u024f\n.\u0003.\u0003.\u0005.\u0253\n.\u0007.\u0255\n.\f.\u000e',
  '.\u0258\u000b.\u0005.\u025a\n.\u0003.\u0003.\u0003/\u0003/\u00030\u0003',
  '0\u00030\u0002\u0004\u0006<1\u0002\u0004\u0006\b\n\f\u000e\u0010\u0012',
  '\u0014\u0016\u0018\u001a\u001c\u001e "$&(*,.02468:<>@BDFHJLNPRTVXZ',
  "\\^\u0002\t\u0003\u0002%'\u0003\u0002\u001a\u001f\u0003\u0002\u0014",
  '\u0016\u0003\u0002CD\u0003\u0002!"\u0004\u0002\u0011\u0011??\u0003',
  '\u0002(,\u0002\u02a9\u0002`\u0003\u0002\u0002\u0002\u0004d\u0003\u0002',
  '\u0002\u0002\u0006\u0090\u0003\u0002\u0002\u0002\b\u0099\u0003\u0002',
  '\u0002\u0002\n\u00ae\u0003\u0002\u0002\u0002\f\u00bf\u0003\u0002\u0002',
  '\u0002\u000e\u00d8\u0003\u0002\u0002\u0002\u0010\u00db\u0003\u0002\u0002',
  '\u0002\u0012\u00e6\u0003\u0002\u0002\u0002\u0014\u00e9\u0003\u0002\u0002',
  '\u0002\u0016\u00fd\u0003\u0002\u0002\u0002\u0018\u00ff\u0003\u0002\u0002',
  '\u0002\u001a\u0114\u0003\u0002\u0002\u0002\u001c\u0116\u0003\u0002\u0002',
  '\u0002\u001e\u012b\u0003\u0002\u0002\u0002 \u0136\u0003\u0002\u0002',
  '\u0002"\u013c\u0003\u0002\u0002\u0002$\u013e\u0003\u0002\u0002\u0002',
  '&\u0146\u0003\u0002\u0002\u0002(\u0150\u0003\u0002\u0002\u0002*\u0152',
  '\u0003\u0002\u0002\u0002,\u015c\u0003\u0002\u0002\u0002.\u0162\u0003',
  '\u0002\u0002\u00020\u0173\u0003\u0002\u0002\u00022\u017d\u0003\u0002',
  '\u0002\u00024\u017f\u0003\u0002\u0002\u00026\u0181\u0003\u0002\u0002',
  '\u00028\u019a\u0003\u0002\u0002\u0002:\u01a5\u0003\u0002\u0002\u0002',
  '<\u01c5\u0003\u0002\u0002\u0002>\u01ff\u0003\u0002\u0002\u0002@\u020e',
  '\u0003\u0002\u0002\u0002B\u021a\u0003\u0002\u0002\u0002D\u021d\u0003',
  '\u0002\u0002\u0002F\u0220\u0003\u0002\u0002\u0002H\u0223\u0003\u0002',
  '\u0002\u0002J\u0226\u0003\u0002\u0002\u0002L\u0229\u0003\u0002\u0002',
  '\u0002N\u022c\u0003\u0002\u0002\u0002P\u022e\u0003\u0002\u0002\u0002',
  'R\u0230\u0003\u0002\u0002\u0002T\u0232\u0003\u0002\u0002\u0002V\u0234',
  '\u0003\u0002\u0002\u0002X\u0236\u0003\u0002\u0002\u0002Z\u023f\u0003',
  '\u0002\u0002\u0002\\\u025d\u0003\u0002\u0002\u0002^\u025f\u0003\u0002',
  '\u0002\u0002`a\u0005\u0004\u0003\u0002ab\u0007\u0002\u0002\u0003b\u0003',
  '\u0003\u0002\u0002\u0002ce\u0005\u0006\u0004\u0002dc\u0003\u0002\u0002',
  '\u0002ef\u0003\u0002\u0002\u0002fd\u0003\u0002\u0002\u0002fg\u0003\u0002',
  '\u0002\u0002g\u0005\u0003\u0002\u0002\u0002hi\b\u0004\u0001\u0002ik',
  '\u0005\n\u0006\u0002jl\u0005\\/\u0002kj\u0003\u0002\u0002\u0002kl\u0003',
  '\u0002\u0002\u0002l\u0091\u0003\u0002\u0002\u0002m\u0091\u0005\b\u0005',
  '\u0002no\u0005\f\u0007\u0002op\u0005\\/\u0002p\u0091\u0003\u0002\u0002',
  '\u0002qs\u0005\u0012\n\u0002rt\u0005\\/\u0002sr\u0003\u0002\u0002\u0002',
  'st\u0003\u0002\u0002\u0002t\u0091\u0003\u0002\u0002\u0002uw\u0005\u0018',
  '\r\u0002vx\u0005\\/\u0002wv\u0003\u0002\u0002\u0002wx\u0003\u0002\u0002',
  '\u0002x\u0091\u0003\u0002\u0002\u0002y\u0091\u0005\u001c\u000f\u0002',
  'z\u0091\u0005"\u0012\u0002{\u0091\u00050\u0019\u0002|~\u00074\u0002',
  '\u0002}\u007f\u0005\\/\u0002~}\u0003\u0002\u0002\u0002~\u007f\u0003',
  '\u0002\u0002\u0002\u007f\u0091\u0003\u0002\u0002\u0002\u0080\u0082\u0007',
  '5\u0002\u0002\u0081\u0083\u0005\\/\u0002\u0082\u0081\u0003\u0002\u0002',
  '\u0002\u0082\u0083\u0003\u0002\u0002\u0002\u0083\u0091\u0003\u0002\u0002',
  '\u0002\u0084\u0091\u00058\u001d\u0002\u0085\u0087\u0005<\u001f\u0002',
  '\u0086\u0088\u0005\\/\u0002\u0087\u0086\u0003\u0002\u0002\u0002\u0087',
  '\u0088\u0003\u0002\u0002\u0002\u0088\u0091\u0003\u0002\u0002\u0002\u0089',
  '\u0091\u00056\u001c\u0002\u008a\u0091\u0007\u0012\u0002\u0002\u008b',
  '\u008d\u0005\\/\u0002\u008c\u008b\u0003\u0002\u0002\u0002\u008d\u008e',
  '\u0003\u0002\u0002\u0002\u008e\u008c\u0003\u0002\u0002\u0002\u008e\u008f',
  '\u0003\u0002\u0002\u0002\u008f\u0091\u0003\u0002\u0002\u0002\u0090h',
  '\u0003\u0002\u0002\u0002\u0090m\u0003\u0002\u0002\u0002\u0090n\u0003',
  '\u0002\u0002\u0002\u0090q\u0003\u0002\u0002\u0002\u0090u\u0003\u0002',
  '\u0002\u0002\u0090y\u0003\u0002\u0002\u0002\u0090z\u0003\u0002\u0002',
  '\u0002\u0090{\u0003\u0002\u0002\u0002\u0090|\u0003\u0002\u0002\u0002',
  '\u0090\u0080\u0003\u0002\u0002\u0002\u0090\u0084\u0003\u0002\u0002\u0002',
  '\u0090\u0085\u0003\u0002\u0002\u0002\u0090\u0089\u0003\u0002\u0002\u0002',
  '\u0090\u008a\u0003\u0002\u0002\u0002\u0090\u008c\u0003\u0002\u0002\u0002',
  '\u0091\u0096\u0003\u0002\u0002\u0002\u0092\u0093\f\u0005\u0002\u0002',
  '\u0093\u0095\u0007\u0012\u0002\u0002\u0094\u0092\u0003\u0002\u0002\u0002',
  '\u0095\u0098\u0003\u0002\u0002\u0002\u0096\u0094\u0003\u0002\u0002\u0002',
  '\u0096\u0097\u0003\u0002\u0002\u0002\u0097\u0007\u0003\u0002\u0002\u0002',
  '\u0098\u0096\u0003\u0002\u0002\u0002\u0099\u009a\u0007\u0003\u0002\u0002',
  '\u009a\u00a6\u0005@!\u0002\u009b\u009c\u0007B\u0002\u0002\u009c\u009d',
  '\u0007=\u0002\u0002\u009d\u00a2\u0007G\u0002\u0002\u009e\u009f\u0007',
  '\u0004\u0002\u0002\u009f\u00a1\u0007G\u0002\u0002\u00a0\u009e\u0003',
  '\u0002\u0002\u0002\u00a1\u00a4\u0003\u0002\u0002\u0002\u00a2\u00a0\u0003',
  '\u0002\u0002\u0002\u00a2\u00a3\u0003\u0002\u0002\u0002\u00a3\u00a5\u0003',
  '\u0002\u0002\u0002\u00a4\u00a2\u0003\u0002\u0002\u0002\u00a5\u00a7\u0007',
  '>\u0002\u0002\u00a6\u009b\u0003\u0002\u0002\u0002\u00a6\u00a7\u0003',
  '\u0002\u0002\u0002\u00a7\u00aa\u0003\u0002\u0002\u0002\u00a8\u00a9\u0007',
  'B\u0002\u0002\u00a9\u00ab\u0007\u0014\u0002\u0002\u00aa\u00a8\u0003',
  '\u0002\u0002\u0002\u00aa\u00ab\u0003\u0002\u0002\u0002\u00ab\u00ac\u0003',
  '\u0002\u0002\u0002\u00ac\u00ad\u0007?\u0002\u0002\u00ad\t\u0003\u0002',
  '\u0002\u0002\u00ae\u00b0\u0007=\u0002\u0002\u00af\u00b1\u0005\\/\u0002',
  '\u00b0\u00af\u0003\u0002\u0002\u0002\u00b0\u00b1\u0003\u0002\u0002\u0002',
  '\u00b1\u00b3\u0003\u0002\u0002\u0002\u00b2\u00b4\u0005\u0006\u0004\u0002',
  '\u00b3\u00b2\u0003\u0002\u0002\u0002\u00b4\u00b5\u0003\u0002\u0002\u0002',
  '\u00b5\u00b3\u0003\u0002\u0002\u0002\u00b5\u00b6\u0003\u0002\u0002\u0002',
  '\u00b6\u00ba\u0003\u0002\u0002\u0002\u00b7\u00b9\u0005\\/\u0002\u00b8',
  '\u00b7\u0003\u0002\u0002\u0002\u00b9\u00bc\u0003\u0002\u0002\u0002\u00ba',
  '\u00b8\u0003\u0002\u0002\u0002\u00ba\u00bb\u0003\u0002\u0002\u0002\u00bb',
  '\u00bd\u0003\u0002\u0002\u0002\u00bc\u00ba\u0003\u0002\u0002\u0002\u00bd',
  '\u00be\u0007>\u0002\u0002\u00be\u000b\u0003\u0002\u0002\u0002\u00bf',
  '\u00c1\u0007/\u0002\u0002\u00c0\u00c2\u0007G\u0002\u0002\u00c1\u00c0',
  '\u0003\u0002\u0002\u0002\u00c1\u00c2\u0003\u0002\u0002\u0002\u00c2\u00c3',
  '\u0003\u0002\u0002\u0002\u00c3\u00c4\u0007;\u0002\u0002\u00c4\u00c5',
  '\u0005\u000e\b\u0002\u00c5\u00c6\u0007<\u0002\u0002\u00c6\u00c7\u0007',
  '=\u0002\u0002\u00c7\u00c8\u0005\u0010\t\u0002\u00c8\u00c9\u0007>\u0002',
  '\u0002\u00c9\r\u0003\u0002\u0002\u0002\u00ca\u00cc\u0007!\u0002\u0002',
  '\u00cb\u00ca\u0003\u0002\u0002\u0002\u00cb\u00cc\u0003\u0002\u0002\u0002',
  '\u00cc\u00cd\u0003\u0002\u0002\u0002\u00cd\u00d5\u0007G\u0002\u0002',
  '\u00ce\u00d0\u0007\u0004\u0002\u0002\u00cf\u00d1\u0007!\u0002\u0002',
  '\u00d0\u00cf\u0003\u0002\u0002\u0002\u00d0\u00d1\u0003\u0002\u0002\u0002',
  '\u00d1\u00d2\u0003\u0002\u0002\u0002\u00d2\u00d4\u0007G\u0002\u0002',
  '\u00d3\u00ce\u0003\u0002\u0002\u0002\u00d4\u00d7\u0003\u0002\u0002\u0002',
  '\u00d5\u00d3\u0003\u0002\u0002\u0002\u00d5\u00d6\u0003\u0002\u0002\u0002',
  '\u00d6\u00d9\u0003\u0002\u0002\u0002\u00d7\u00d5\u0003\u0002\u0002\u0002',
  '\u00d8\u00cb\u0003\u0002\u0002\u0002\u00d8\u00d9\u0003\u0002\u0002\u0002',
  '\u00d9\u000f\u0003\u0002\u0002\u0002\u00da\u00dc\u0005\\/\u0002\u00db',
  '\u00da\u0003\u0002\u0002\u0002\u00db\u00dc\u0003\u0002\u0002\u0002\u00dc',
  '\u00e0\u0003\u0002\u0002\u0002\u00dd\u00df\u0005\u0006\u0004\u0002\u00de',
  '\u00dd\u0003\u0002\u0002\u0002\u00df\u00e2\u0003\u0002\u0002\u0002\u00e0',
  '\u00de\u0003\u0002\u0002\u0002\u00e0\u00e1\u0003\u0002\u0002\u0002\u00e1',
  '\u00e4\u0003\u0002\u0002\u0002\u00e2\u00e0\u0003\u0002\u0002\u0002\u00e3',
  '\u00e5\u00058\u001d\u0002\u00e4\u00e3\u0003\u0002\u0002\u0002\u00e4',
  '\u00e5\u0003\u0002\u0002\u0002\u00e5\u0011\u0003\u0002\u0002\u0002\u00e6',
  '\u00e7\u0007.\u0002\u0002\u00e7\u00e8\u0005\u0014\u000b\u0002\u00e8',
  '\u0013\u0003\u0002\u0002\u0002\u00e9\u00ee\u0005\u0016\f\u0002\u00ea',
  '\u00eb\u0007\u0004\u0002\u0002\u00eb\u00ed\u0005\u0016\f\u0002\u00ec',
  '\u00ea\u0003\u0002\u0002\u0002\u00ed\u00f0\u0003\u0002\u0002\u0002\u00ee',
  '\u00ec\u0003\u0002\u0002\u0002\u00ee\u00ef\u0003\u0002\u0002\u0002\u00ef',
  '\u0015\u0003\u0002\u0002\u0002\u00f0\u00ee\u0003\u0002\u0002\u0002\u00f1',
  '\u00f2\u0005@!\u0002\u00f2\u00f3\u0007\f\u0002\u0002\u00f3\u00fe\u0003',
  '\u0002\u0002\u0002\u00f4\u00f7\u0005@!\u0002\u00f5\u00f6\u0007-\u0002',
  '\u0002\u00f6\u00f8\u0005<\u001f\u0002\u00f7\u00f5\u0003\u0002\u0002',
  '\u0002\u00f7\u00f8\u0003\u0002\u0002\u0002\u00f8\u00fe\u0003\u0002\u0002',
  '\u0002\u00f9\u00fa\u0005@!\u0002\u00fa\u00fb\u0007-\u0002\u0002\u00fb',
  '\u00fc\u0005\u0006\u0004\u0002\u00fc\u00fe\u0003\u0002\u0002\u0002\u00fd',
  '\u00f1\u0003\u0002\u0002\u0002\u00fd\u00f4\u0003\u0002\u0002\u0002\u00fd',
  '\u00f9\u0003\u0002\u0002\u0002\u00fe\u0017\u0003\u0002\u0002\u0002\u00ff',
  '\u0104\u0005\u001a\u000e\u0002\u0100\u0101\u0007\u0004\u0002\u0002\u0101',
  '\u0103\u0005\u001a\u000e\u0002\u0102\u0100\u0003\u0002\u0002\u0002\u0103',
  '\u0106\u0003\u0002\u0002\u0002\u0104\u0102\u0003\u0002\u0002\u0002\u0104',
  '\u0105\u0003\u0002\u0002\u0002\u0105\u0019\u0003\u0002\u0002\u0002\u0106',
  '\u0104\u0003\u0002\u0002\u0002\u0107\u0108\u0005@!\u0002\u0108\u0109',
  '\u0007\f\u0002\u0002\u0109\u0115\u0003\u0002\u0002\u0002\u010a\u010b',
  '\u0005@!\u0002\u010b\u010e\u0007-\u0002\u0002\u010c\u010f\u0005<\u001f',
  '\u0002\u010d\u010f\u0005\f\u0007\u0002\u010e\u010c\u0003\u0002\u0002',
  '\u0002\u010e\u010d\u0003\u0002\u0002\u0002\u010f\u0115\u0003\u0002\u0002',
  '\u0002\u0110\u0115\u0005F$\u0002\u0111\u0115\u0005H%\u0002\u0112\u0115',
  "\u0005J&\u0002\u0113\u0115\u0005L'\u0002\u0114\u0107\u0003\u0002\u0002",
  '\u0002\u0114\u010a\u0003\u0002\u0002\u0002\u0114\u0110\u0003\u0002\u0002',
  '\u0002\u0114\u0111\u0003\u0002\u0002\u0002\u0114\u0112\u0003\u0002\u0002',
  '\u0002\u0114\u0113\u0003\u0002\u0002\u0002\u0115\u001b\u0003\u0002\u0002',
  '\u0002\u0116\u0118\u00071\u0002\u0002\u0117\u0119\u0007;\u0002\u0002',
  '\u0118\u0117\u0003\u0002\u0002\u0002\u0118\u0119\u0003\u0002\u0002\u0002',
  '\u0119\u011a\u0003\u0002\u0002\u0002\u011a\u011c\u0005<\u001f\u0002',
  '\u011b\u011d\u0007<\u0002\u0002\u011c\u011b\u0003\u0002\u0002\u0002',
  '\u011c\u011d\u0003\u0002\u0002\u0002\u011d\u011e\u0003\u0002\u0002\u0002',
  '\u011e\u0122\u0005\u0006\u0004\u0002\u011f\u0121\u0005\u001e\u0010\u0002',
  '\u0120\u011f\u0003\u0002\u0002\u0002\u0121\u0124\u0003\u0002\u0002\u0002',
  '\u0122\u0120\u0003\u0002\u0002\u0002\u0122\u0123\u0003\u0002\u0002\u0002',
  '\u0123\u0126\u0003\u0002\u0002\u0002\u0124\u0122\u0003\u0002\u0002\u0002',
  '\u0125\u0127\u0005 \u0011\u0002\u0126\u0125\u0003\u0002\u0002\u0002',
  '\u0126\u0127\u0003\u0002\u0002\u0002\u0127\u001d\u0003\u0002\u0002\u0002',
  '\u0128\u012c\u00073\u0002\u0002\u0129\u012a\u00072\u0002\u0002\u012a',
  '\u012c\u00071\u0002\u0002\u012b\u0128\u0003\u0002\u0002\u0002\u012b',
  '\u0129\u0003\u0002\u0002\u0002\u012c\u012e\u0003\u0002\u0002\u0002\u012d',
  '\u012f\u0007;\u0002\u0002\u012e\u012d\u0003\u0002\u0002\u0002\u012e',
  '\u012f\u0003\u0002\u0002\u0002\u012f\u0130\u0003\u0002\u0002\u0002\u0130',
  '\u0132\u0005<\u001f\u0002\u0131\u0133\u0007<\u0002\u0002\u0132\u0131',
  '\u0003\u0002\u0002\u0002\u0132\u0133\u0003\u0002\u0002\u0002\u0133\u0134',
  '\u0003\u0002\u0002\u0002\u0134\u0135\u0005\u0006\u0004\u0002\u0135\u001f',
  '\u0003\u0002\u0002\u0002\u0136\u0137\u00072\u0002\u0002\u0137\u0138',
  '\u0005\u0006\u0004\u0002\u0138!\u0003\u0002\u0002\u0002\u0139\u013d',
  '\u0005$\u0013\u0002\u013a\u013d\u0005*\u0016\u0002\u013b\u013d\u0005',
  ',\u0017\u0002\u013c\u0139\u0003\u0002\u0002\u0002\u013c\u013a\u0003',
  '\u0002\u0002\u0002\u013c\u013b\u0003\u0002\u0002\u0002\u013d#\u0003',
  '\u0002\u0002\u0002\u013e\u013f\u00076\u0002\u0002\u013f\u0140\u0007',
  ';\u0002\u0002\u0140\u0141\u0005&\u0014\u0002\u0141\u0142\u0007<\u0002',
  '\u0002\u0142\u0143\u0005(\u0015\u0002\u0143%\u0003\u0002\u0002\u0002',
  '\u0144\u0147\u0005\u0012\n\u0002\u0145\u0147\u0005\u0018\r\u0002\u0146',
  '\u0144\u0003\u0002\u0002\u0002\u0146\u0145\u0003\u0002\u0002\u0002\u0146',
  '\u0147\u0003\u0002\u0002\u0002\u0147\u0148\u0003\u0002\u0002\u0002\u0148',
  '\u014a\u0007?\u0002\u0002\u0149\u014b\u0005<\u001f\u0002\u014a\u0149',
  '\u0003\u0002\u0002\u0002\u014a\u014b\u0003\u0002\u0002\u0002\u014b\u014c',
  '\u0003\u0002\u0002\u0002\u014c\u014e\u0007?\u0002\u0002\u014d\u014f',
  '\u0005\u0018\r\u0002\u014e\u014d\u0003\u0002\u0002\u0002\u014e\u014f',
  "\u0003\u0002\u0002\u0002\u014f'\u0003\u0002\u0002\u0002\u0150\u0151",
  '\u0005\u0006\u0004\u0002\u0151)\u0003\u0002\u0002\u0002\u0152\u0153',
  '\u00076\u0002\u0002\u0153\u0156\u0007G\u0002\u0002\u0154\u0155\u0007',
  '\u0004\u0002\u0002\u0155\u0157\u0007G\u0002\u0002\u0156\u0154\u0003',
  '\u0002\u0002\u0002\u0156\u0157\u0003\u0002\u0002\u0002\u0157\u0158\u0003',
  '\u0002\u0002\u0002\u0158\u0159\u0007\u0005\u0002\u0002\u0159\u015a\u0005',
  '@!\u0002\u015a\u015b\u0005(\u0015\u0002\u015b+\u0003\u0002\u0002\u0002',
  '\u015c\u015d\u00076\u0002\u0002\u015d\u015e\u0007G\u0002\u0002\u015e',
  '\u015f\u0007\u0005\u0002\u0002\u015f\u0160\u0005.\u0018\u0002\u0160',
  '\u0161\u0005(\u0015\u0002\u0161-\u0003\u0002\u0002\u0002\u0162\u0163',
  '\u0007\u0006\u0002\u0002\u0163\u0165\u0007;\u0002\u0002\u0164\u0166',
  '\u0007D\u0002\u0002\u0165\u0164\u0003\u0002\u0002\u0002\u0165\u0166',
  '\u0003\u0002\u0002\u0002\u0166\u0167\u0003\u0002\u0002\u0002\u0167\u0168',
  '\u0007(\u0002\u0002\u0168\u016b\u0007\u0004\u0002\u0002\u0169\u016c',
  '\u0007(\u0002\u0002\u016a\u016c\u0005@!\u0002\u016b\u0169\u0003\u0002',
  '\u0002\u0002\u016b\u016a\u0003\u0002\u0002\u0002\u016c\u016f\u0003\u0002',
  '\u0002\u0002\u016d\u016e\u0007\u0004\u0002\u0002\u016e\u0170\u0007(',
  '\u0002\u0002\u016f\u016d\u0003\u0002\u0002\u0002\u016f\u0170\u0003\u0002',
  '\u0002\u0002\u0170\u0171\u0003\u0002\u0002\u0002\u0171\u0172\u0007<',
  '\u0002\u0002\u0172/\u0003\u0002\u0002\u0002\u0173\u0175\u00078\u0002',
  '\u0002\u0174\u0176\u0007;\u0002\u0002\u0175\u0174\u0003\u0002\u0002',
  '\u0002\u0175\u0176\u0003\u0002\u0002\u0002\u0176\u0177\u0003\u0002\u0002',
  '\u0002\u0177\u0179\u00052\u001a\u0002\u0178\u017a\u0007<\u0002\u0002',
  '\u0179\u0178\u0003\u0002\u0002\u0002\u0179\u017a\u0003\u0002\u0002\u0002',
  '\u017a\u017b\u0003\u0002\u0002\u0002\u017b\u017c\u00054\u001b\u0002',
  '\u017c1\u0003\u0002\u0002\u0002\u017d\u017e\u0005<\u001f\u0002\u017e',
  '3\u0003\u0002\u0002\u0002\u017f\u0180\u0005\u0006\u0004\u0002\u0180',
  '5\u0003\u0002\u0002\u0002\u0181\u0182\u0007\u0007\u0002\u0002\u0182',
  '\u0197\u0005\u0006\u0004\u0002\u0183\u0184\u0007\b\u0002\u0002\u0184',
  '\u0185\u0007;\u0002\u0002\u0185\u0187\u0007G\u0002\u0002\u0186\u0188',
  '\u0007G\u0002\u0002\u0187\u0186\u0003\u0002\u0002\u0002\u0187\u0188',
  '\u0003\u0002\u0002\u0002\u0188\u0190\u0003\u0002\u0002\u0002\u0189\u018a',
  '\u0007"\u0002\u0002\u018a\u018c\u0007G\u0002\u0002\u018b\u018d\u0007',
  'G\u0002\u0002\u018c\u018b\u0003\u0002\u0002\u0002\u018c\u018d\u0003',
  '\u0002\u0002\u0002\u018d\u018f\u0003\u0002\u0002\u0002\u018e\u0189\u0003',
  '\u0002\u0002\u0002\u018f\u0192\u0003\u0002\u0002\u0002\u0190\u018e\u0003',
  '\u0002\u0002\u0002\u0190\u0191\u0003\u0002\u0002\u0002\u0191\u0193\u0003',
  '\u0002\u0002\u0002\u0192\u0190\u0003\u0002\u0002\u0002\u0193\u0194\u0007',
  '<\u0002\u0002\u0194\u0196\u0005\u0006\u0004\u0002\u0195\u0183\u0003',
  '\u0002\u0002\u0002\u0196\u0199\u0003\u0002\u0002\u0002\u0197\u0195\u0003',
  '\u0002\u0002\u0002\u0197\u0198\u0003\u0002\u0002\u0002\u01987\u0003',
  '\u0002\u0002\u0002\u0199\u0197\u0003\u0002\u0002\u0002\u019a\u019d\u0007',
  '0\u0002\u0002\u019b\u019e\u0005<\u001f\u0002\u019c\u019e\u0005\f\u0007',
  '\u0002\u019d\u019b\u0003\u0002\u0002\u0002\u019d\u019c\u0003\u0002\u0002',
  '\u0002\u019d\u019e\u0003\u0002\u0002\u0002\u019e\u01a2\u0003\u0002\u0002',
  '\u0002\u019f\u01a1\u0005\\/\u0002\u01a0\u019f\u0003\u0002\u0002\u0002',
  '\u01a1\u01a4\u0003\u0002\u0002\u0002\u01a2\u01a0\u0003\u0002\u0002\u0002',
  '\u01a2\u01a3\u0003\u0002\u0002\u0002\u01a39\u0003\u0002\u0002\u0002',
  '\u01a4\u01a2\u0003\u0002\u0002\u0002\u01a5\u01a6\u00079\u0002\u0002',
  '\u01a6\u01ab\u0005<\u001f\u0002\u01a7\u01a8\u0007\u0004\u0002\u0002',
  '\u01a8\u01aa\u0005<\u001f\u0002\u01a9\u01a7\u0003\u0002\u0002\u0002',
  '\u01aa\u01ad\u0003\u0002\u0002\u0002\u01ab\u01a9\u0003\u0002\u0002\u0002',
  '\u01ab\u01ac\u0003\u0002\u0002\u0002\u01ac\u01ae\u0003\u0002\u0002\u0002',
  '\u01ad\u01ab\u0003\u0002\u0002\u0002\u01ae\u01af\u0007:\u0002\u0002',
  '\u01af;\u0003\u0002\u0002\u0002\u01b0\u01b1\b\u001f\u0001\u0002\u01b1',
  '\u01c6\u0005^0\u0002\u01b2\u01c6\u0005> \u0002\u01b3\u01c6\u0005Z.\u0002',
  '\u01b4\u01c6\u0005B"\u0002\u01b5\u01c6\u0005D#\u0002\u01b6\u01c6\u0005',
  'F$\u0002\u01b7\u01c6\u0005H%\u0002\u01b8\u01c6\u0005J&\u0002\u01b9\u01c6',
  "\u0005L'\u0002\u01ba\u01bb\u0007\u0017\u0002\u0002\u01bb\u01c6\u0005",
  '<\u001f\u0018\u01bc\u01bd\u0007$\u0002\u0002\u01bd\u01c6\u0005<\u001f',
  '\u0017\u01be\u01c6\u0005X-\u0002\u01bf\u01c6\u0005@!\u0002\u01c0\u01c6',
  '\u0005:\u001e\u0002\u01c1\u01c6\u0005.\u0018\u0002\u01c2\u01c6\u0007',
  '\u000e\u0002\u0002\u01c3\u01c6\u0007\u000f\u0002\u0002\u01c4\u01c6\u0005',
  '\u001a\u000e\u0002\u01c5\u01b0\u0003\u0002\u0002\u0002\u01c5\u01b2\u0003',
  '\u0002\u0002\u0002\u01c5\u01b3\u0003\u0002\u0002\u0002\u01c5\u01b4\u0003',
  '\u0002\u0002\u0002\u01c5\u01b5\u0003\u0002\u0002\u0002\u01c5\u01b6\u0003',
  '\u0002\u0002\u0002\u01c5\u01b7\u0003\u0002\u0002\u0002\u01c5\u01b8\u0003',
  '\u0002\u0002\u0002\u01c5\u01b9\u0003\u0002\u0002\u0002\u01c5\u01ba\u0003',
  '\u0002\u0002\u0002\u01c5\u01bc\u0003\u0002\u0002\u0002\u01c5\u01be\u0003',
  '\u0002\u0002\u0002\u01c5\u01bf\u0003\u0002\u0002\u0002\u01c5\u01c0\u0003',
  '\u0002\u0002\u0002\u01c5\u01c1\u0003\u0002\u0002\u0002\u01c5\u01c2\u0003',
  '\u0002\u0002\u0002\u01c5\u01c3\u0003\u0002\u0002\u0002\u01c5\u01c4\u0003',
  '\u0002\u0002\u0002\u01c6\u01fc\u0003\u0002\u0002\u0002\u01c7\u01c8\f',
  '\u0016\u0002\u0002\u01c8\u01c9\u0007\u0013\u0002\u0002\u01c9\u01fb\u0005',
  '<\u001f\u0017\u01ca\u01cb\f\u0015\u0002\u0002\u01cb\u01cc\u0005P)\u0002',
  '\u01cc\u01cd\u0005<\u001f\u0016\u01cd\u01fb\u0003\u0002\u0002\u0002',
  '\u01ce\u01d0\f\u0014\u0002\u0002\u01cf\u01d1\u0007\u0011\u0002\u0002',
  '\u01d0\u01cf\u0003\u0002\u0002\u0002\u01d0\u01d1\u0003\u0002\u0002\u0002',
  '\u01d1\u01d2\u0003\u0002\u0002\u0002\u01d2\u01d4\u0005R*\u0002\u01d3',
  '\u01d5\u0007\u0011\u0002\u0002\u01d4\u01d3\u0003\u0002\u0002\u0002\u01d4',
  '\u01d5\u0003\u0002\u0002\u0002\u01d5\u01d6\u0003\u0002\u0002\u0002\u01d6',
  '\u01d7\u0005<\u001f\u0015\u01d7\u01fb\u0003\u0002\u0002\u0002\u01d8',
  '\u01d9\f\u0013\u0002\u0002\u01d9\u01da\t\u0002\u0002\u0002\u01da\u01fb',
  '\u0005<\u001f\u0014\u01db\u01dc\f\u0012\u0002\u0002\u01dc\u01dd\u0005',
  'N(\u0002\u01dd\u01de\u0005<\u001f\u0013\u01de\u01fb\u0003\u0002\u0002',
  '\u0002\u01df\u01e0\f\u0011\u0002\u0002\u01e0\u01e1\u0007!\u0002\u0002',
  '\u01e1\u01fb\u0005<\u001f\u0012\u01e2\u01e3\f\u0010\u0002\u0002\u01e3',
  '\u01e4\u0007"\u0002\u0002\u01e4\u01fb\u0005<\u001f\u0011\u01e5\u01e6',
  '\f\u000f\u0002\u0002\u01e6\u01e7\u0005N(\u0002\u01e7\u01e8\u0005<\u001f',
  '\u0010\u01e8\u01fb\u0003\u0002\u0002\u0002\u01e9\u01ea\f\u000e\u0002',
  '\u0002\u01ea\u01eb\u0007\u0018\u0002\u0002\u01eb\u01fb\u0005<\u001f',
  '\u000f\u01ec\u01ed\f\r\u0002\u0002\u01ed\u01ee\u0007\u0019\u0002\u0002',
  '\u01ee\u01fb\u0005<\u001f\u000e\u01ef\u01f0\f\f\u0002\u0002\u01f0\u01f1',
  '\u0007 \u0002\u0002\u01f1\u01fb\u0005<\u001f\r\u01f2\u01f3\f\n\u0002',
  '\u0002\u01f3\u01f4\u0007@\u0002\u0002\u01f4\u01f5\u0005<\u001f\u0002',
  '\u01f5\u01f6\u0007A\u0002\u0002\u01f6\u01f7\u0005<\u001f\u000b\u01f7',
  '\u01fb\u0003\u0002\u0002\u0002\u01f8\u01f9\f\u000b\u0002\u0002\u01f9',
  '\u01fb\u0007\r\u0002\u0002\u01fa\u01c7\u0003\u0002\u0002\u0002\u01fa',
  '\u01ca\u0003\u0002\u0002\u0002\u01fa\u01ce\u0003\u0002\u0002\u0002\u01fa',
  '\u01d8\u0003\u0002\u0002\u0002\u01fa\u01db\u0003\u0002\u0002\u0002\u01fa',
  '\u01df\u0003\u0002\u0002\u0002\u01fa\u01e2\u0003\u0002\u0002\u0002\u01fa',
  '\u01e5\u0003\u0002\u0002\u0002\u01fa\u01e9\u0003\u0002\u0002\u0002\u01fa',
  '\u01ec\u0003\u0002\u0002\u0002\u01fa\u01ef\u0003\u0002\u0002\u0002\u01fa',
  '\u01f2\u0003\u0002\u0002\u0002\u01fa\u01f8\u0003\u0002\u0002\u0002\u01fb',
  '\u01fe\u0003\u0002\u0002\u0002\u01fc\u01fa\u0003\u0002\u0002\u0002\u01fc',
  '\u01fd\u0003\u0002\u0002\u0002\u01fd=\u0003\u0002\u0002\u0002\u01fe',
  '\u01fc\u0003\u0002\u0002\u0002\u01ff\u0200\u0007\t\u0002\u0002\u0200',
  '\u0201\u0007;\u0002\u0002\u0201\u0202\u0005\u000e\b\u0002\u0202\u0203',
  '\u0007<\u0002\u0002\u0203\u0207\u0007\n\u0002\u0002\u0204\u0206\u0005',
  '\u0006\u0004\u0002\u0205\u0204\u0003\u0002\u0002\u0002\u0206\u0209\u0003',
  '\u0002\u0002\u0002\u0207\u0205\u0003\u0002\u0002\u0002\u0207\u0208\u0003',
  '\u0002\u0002\u0002\u0208\u020a\u0003\u0002\u0002\u0002\u0209\u0207\u0003',
  '\u0002\u0002\u0002\u020a\u020c\u0007\u000b\u0002\u0002\u020b\u020d\u0007',
  '?\u0002\u0002\u020c\u020b\u0003\u0002\u0002\u0002\u020c\u020d\u0003',
  '\u0002\u0002\u0002\u020d?\u0003\u0002\u0002\u0002\u020e\u0217\u0007',
  'G\u0002\u0002\u020f\u0210\u0007B\u0002\u0002\u0210\u0216\u0007G\u0002',
  '\u0002\u0211\u0212\u00079\u0002\u0002\u0212\u0213\u0005<\u001f\u0002',
  '\u0213\u0214\u0007:\u0002\u0002\u0214\u0216\u0003\u0002\u0002\u0002',
  '\u0215\u020f\u0003\u0002\u0002\u0002\u0215\u0211\u0003\u0002\u0002\u0002',
  '\u0216\u0219\u0003\u0002\u0002\u0002\u0217\u0215\u0003\u0002\u0002\u0002',
  '\u0217\u0218\u0003\u0002\u0002\u0002\u0218A\u0003\u0002\u0002\u0002',
  '\u0219\u0217\u0003\u0002\u0002\u0002\u021a\u021b\u0007C\u0002\u0002',
  '\u021b\u021c\u0005<\u001f\u0002\u021cC\u0003\u0002\u0002\u0002\u021d',
  '\u021e\u0007D\u0002\u0002\u021e\u021f\u0005<\u001f\u0002\u021fE\u0003',
  '\u0002\u0002\u0002\u0220\u0221\u0007G\u0002\u0002\u0221\u0222\u0007',
  'E\u0002\u0002\u0222G\u0003\u0002\u0002\u0002\u0223\u0224\u0007G\u0002',
  '\u0002\u0224\u0225\u0007F\u0002\u0002\u0225I\u0003\u0002\u0002\u0002',
  '\u0226\u0227\u0007E\u0002\u0002\u0227\u0228\u0007G\u0002\u0002\u0228',
  'K\u0003\u0002\u0002\u0002\u0229\u022a\u0007F\u0002\u0002\u022a\u022b',
  '\u0007G\u0002\u0002\u022bM\u0003\u0002\u0002\u0002\u022c\u022d\t\u0003',
  '\u0002\u0002\u022dO\u0003\u0002\u0002\u0002\u022e\u022f\t\u0004\u0002',
  '\u0002\u022fQ\u0003\u0002\u0002\u0002\u0230\u0231\t\u0005\u0002\u0002',
  '\u0231S\u0003\u0002\u0002\u0002\u0232\u0233\t\u0006\u0002\u0002\u0233',
  'U\u0003\u0002\u0002\u0002\u0234\u0235\t\u0004\u0002\u0002\u0235W\u0003',
  '\u0002\u0002\u0002\u0236\u0237\u0007;\u0002\u0002\u0237\u0238\u0005',
  '<\u001f\u0002\u0238\u0239\u0007<\u0002\u0002\u0239Y\u0003\u0002\u0002',
  '\u0002\u023a\u023b\u0007;\u0002\u0002\u023b\u023c\u0005> \u0002\u023c',
  '\u023d\u0007<\u0002\u0002\u023d\u0240\u0003\u0002\u0002\u0002\u023e',
  '\u0240\u0005@!\u0002\u023f\u023a\u0003\u0002\u0002\u0002\u023f\u023e',
  '\u0003\u0002\u0002\u0002\u0240\u0241\u0003\u0002\u0002\u0002\u0241\u0259',
  '\u0007;\u0002\u0002\u0242\u0244\u0007\u0014\u0002\u0002\u0243\u0242',
  '\u0003\u0002\u0002\u0002\u0243\u0244\u0003\u0002\u0002\u0002\u0244\u0245',
  '\u0003\u0002\u0002\u0002\u0245\u0256\u0005<\u001f\u0002\u0246\u0248',
  '\u0007\u0011\u0002\u0002\u0247\u0246\u0003\u0002\u0002\u0002\u0247\u0248',
  '\u0003\u0002\u0002\u0002\u0248\u0249\u0003\u0002\u0002\u0002\u0249\u024b',
  '\u0007\u0004\u0002\u0002\u024a\u024c\u0007\u0011\u0002\u0002\u024b\u024a',
  '\u0003\u0002\u0002\u0002\u024b\u024c\u0003\u0002\u0002\u0002\u024c\u024e',
  '\u0003\u0002\u0002\u0002\u024d\u024f\u0007\u0014\u0002\u0002\u024e\u024d',
  '\u0003\u0002\u0002\u0002\u024e\u024f\u0003\u0002\u0002\u0002\u024f\u0252',
  '\u0003\u0002\u0002\u0002\u0250\u0253\u0005<\u001f\u0002\u0251\u0253',
  '\u0007C\u0002\u0002\u0252\u0250\u0003\u0002\u0002\u0002\u0252\u0251',
  '\u0003\u0002\u0002\u0002\u0253\u0255\u0003\u0002\u0002\u0002\u0254\u0247',
  '\u0003\u0002\u0002\u0002\u0255\u0258\u0003\u0002\u0002\u0002\u0256\u0254',
  '\u0003\u0002\u0002\u0002\u0256\u0257\u0003\u0002\u0002\u0002\u0257\u025a',
  '\u0003\u0002\u0002\u0002\u0258\u0256\u0003\u0002\u0002\u0002\u0259\u0243',
  '\u0003\u0002\u0002\u0002\u0259\u025a\u0003\u0002\u0002\u0002\u025a\u025b',
  '\u0003\u0002\u0002\u0002\u025b\u025c\u0007<\u0002\u0002\u025c[\u0003',
  '\u0002\u0002\u0002\u025d\u025e\t\u0007\u0002\u0002\u025e]\u0003\u0002',
  '\u0002\u0002\u025f\u0260\t\b\u0002\u0002\u0260_\u0003\u0002\u0002\u0002',
  'Ifksw~\u0082\u0087\u008e\u0090\u0096\u00a2\u00a6\u00aa\u00b0\u00b5\u00ba',
  '\u00c1\u00cb\u00d0\u00d5\u00d8\u00db\u00e0\u00e4\u00ee\u00f7\u00fd\u0104',
  '\u010e\u0114\u0118\u011c\u0122\u0126\u012b\u012e\u0132\u013c\u0146\u014a',
  '\u014e\u0156\u0165\u016b\u016f\u0175\u0179\u0187\u018c\u0190\u0197\u019d',
  '\u01a2\u01ab\u01c5\u01d0\u01d4\u01fa\u01fc\u0207\u020c\u0215\u0217\u023f',
  '\u0243\u0247\u024b\u024e\u0252\u0256\u0259'
].join('')
const atn = new ATNDeserializer().deserialize(serializedATN as unknown as number[])

const decisionsToDFA = atn.decisionToState.map((ds, index) => new DFA(ds, index))

const sharedContextCache = new PredictionContextCache()

class RootContext extends ParserRuleContext {
  constructor(parser: Parser, parent?: ParserRuleContext, invokingState?: number) {
    if (invokingState === undefined || invokingState === null) {
      invokingState = -1
    }
    super(parent, invokingState)
    this.parser = parser
    this.ruleIndex = DSLParser.RULE_root
  }
  ruleIndex: number

  program() {
    return this.getTypedRuleContext(ProgramContext, 0)
  }

  EOF() {
    return this.getToken(DSLParser.EOF, 0)
  }
}

class ProgramContext extends ParserRuleContext {
  constructor(parser?: Parser, parent?: ParserRuleContext, invokingState?: number) {
    if (invokingState === undefined || invokingState === null) {
      invokingState = -1
    }
    super(parent, invokingState)
    this.parser = parser
    this.ruleIndex = DSLParser.RULE_program
  }
  ruleIndex: number

  statement(i?: number | null) {
    if (i === undefined) {
      i = null
    }
    if (i === null) {
      return this.getTypedRuleContexts(StatementContext)
    } else {
      return this.getTypedRuleContext(StatementContext, i)
    }
  }
}

class StatementContext extends ParserRuleContext {
  constructor(parser?: Parser, parent?: ParserRuleContext, invokingState?: number) {
    if (invokingState === undefined || invokingState === null) {
      invokingState = -1
    }
    super(parent, invokingState)
    this.parser = parser
    this.ruleIndex = DSLParser.RULE_statement
  }
  ruleIndex: number

  block() {
    return this.getTypedRuleContext(BlockContext, 0)
  }

  eos(i: number | null) {
    if (i === undefined) {
      i = null
    }
    if (i === null) {
      return this.getTypedRuleContexts(EosContext)
    } else {
      return this.getTypedRuleContext(EosContext, i)
    }
  }

  useStatement() {
    return this.getTypedRuleContext(UseStatementContext, 0)
  }

  functionDeclaration() {
    return this.getTypedRuleContext(FunctionDeclarationContext, 0)
  }

  variableStatement() {
    return this.getTypedRuleContext(VariableStatementContext, 0)
  }

  assignListStatement() {
    return this.getTypedRuleContext(AssignListStatementContext, 0)
  }

  ifStatement() {
    return this.getTypedRuleContext(IfStatementContext, 0)
  }

  forStatements() {
    return this.getTypedRuleContext(ForStatementsContext, 0)
  }

  whileStatement() {
    return this.getTypedRuleContext(WhileStatementContext, 0)
  }

  Break() {
    return this.getToken(DSLParser.Break, 0)
  }

  Continue() {
    return this.getToken(DSLParser.Continue, 0)
  }

  returnStatement() {
    return this.getTypedRuleContext(ReturnStatementContext, 0)
  }

  expression() {
    return this.getTypedRuleContext(ExpressionContext, 0)
  }

  tryStatement() {
    return this.getTypedRuleContext(TryStatementContext, 0)
  }

  Comment() {
    return this.getToken(DSLParser.Comment, 0)
  }

  statement() {
    return this.getTypedRuleContext(StatementContext, 0)
  }
}

class UseStatementContext extends ParserRuleContext {
  constructor(parser?: Parser, parent?: ParserRuleContext, invokingState?: number) {
    if (invokingState === undefined || invokingState === null) {
      invokingState = -1
    }
    super(parent, invokingState)
    this.parser = parser
    this.ruleIndex = DSLParser.RULE_useStatement
  }
  ruleIndex: number

  identifierExpress() {
    return this.getTypedRuleContext(IdentifierExpressContext, 0)
  }

  SemiColon() {
    return this.getToken(DSLParser.SemiColon, 0)
  }

  Dot(i: number | null) {
    if (i === undefined) {
      i = null
    }
    if (i === null) {
      return this.getTokens(DSLParser.Dot)
    } else {
      return this.getToken(DSLParser.Dot, i)
    }
  }

  OpenBrace() {
    return this.getToken(DSLParser.OpenBrace, 0)
  }

  Identifier(i: number | null) {
    if (i === undefined) {
      i = null
    }
    if (i === null) {
      return this.getTokens(DSLParser.Identifier)
    } else {
      return this.getToken(DSLParser.Identifier, i)
    }
  }

  CloseBrace() {
    return this.getToken(DSLParser.CloseBrace, 0)
  }

  Multiply() {
    return this.getToken(DSLParser.Multiply, 0)
  }
}

class BlockContext extends ParserRuleContext {
  constructor(parser?: Parser, parent?: ParserRuleContext, invokingState?: number) {
    if (invokingState === undefined || invokingState === null) {
      invokingState = -1
    }
    super(parent, invokingState)
    this.parser = parser
    this.ruleIndex = DSLParser.RULE_block
  }
  ruleIndex: number

  OpenBrace() {
    return this.getToken(DSLParser.OpenBrace, 0)
  }

  CloseBrace() {
    return this.getToken(DSLParser.CloseBrace, 0)
  }

  eos(i: number | null) {
    if (i === undefined) {
      i = null
    }
    if (i === null) {
      return this.getTypedRuleContexts(EosContext)
    } else {
      return this.getTypedRuleContext(EosContext, i)
    }
  }

  statement(i: number | null) {
    if (i === undefined) {
      i = null
    }
    if (i === null) {
      return this.getTypedRuleContexts(StatementContext)
    } else {
      return this.getTypedRuleContext(StatementContext, i)
    }
  }
}

class FunctionDeclarationContext extends ParserRuleContext {
  constructor(parser?: Parser, parent?: ParserRuleContext, invokingState?: number) {
    if (invokingState === undefined || invokingState === null) {
      invokingState = -1
    }
    super(parent, invokingState)
    this.parser = parser
    this.ruleIndex = DSLParser.RULE_functionDeclaration
  }
  ruleIndex: number

  FunctionKeyword() {
    return this.getToken(DSLParser.FunctionKeyword, 0)
  }

  OpenParen() {
    return this.getToken(DSLParser.OpenParen, 0)
  }

  functionParameterList() {
    return this.getTypedRuleContext(FunctionParameterListContext, 0)
  }

  CloseParen() {
    return this.getToken(DSLParser.CloseParen, 0)
  }

  OpenBrace() {
    return this.getToken(DSLParser.OpenBrace, 0)
  }

  functionBody() {
    return this.getTypedRuleContext(FunctionBodyContext, 0)
  }

  CloseBrace() {
    return this.getToken(DSLParser.CloseBrace, 0)
  }

  Identifier() {
    return this.getToken(DSLParser.Identifier, 0)
  }
}

class FunctionParameterListContext extends ParserRuleContext {
  constructor(parser?: Parser, parent?: ParserRuleContext, invokingState?: number) {
    if (invokingState === undefined || invokingState === null) {
      invokingState = -1
    }
    super(parent, invokingState)
    this.parser = parser
    this.ruleIndex = DSLParser.RULE_functionParameterList
  }
  ruleIndex: number

  Identifier(i: number | null) {
    if (i === undefined) {
      i = null
    }
    if (i === null) {
      return this.getTokens(DSLParser.Identifier)
    } else {
      return this.getToken(DSLParser.Identifier, i)
    }
  }

  BitAnd(i: number | null) {
    if (i === undefined) {
      i = null
    }
    if (i === null) {
      return this.getTokens(DSLParser.BitAnd)
    } else {
      return this.getToken(DSLParser.BitAnd, i)
    }
  }
}

class FunctionBodyContext extends ParserRuleContext {
  ruleIndex: number
  constructor(parser?: Parser, parent?: ParserRuleContext, invokingState?: number) {
    if (invokingState === undefined || invokingState === null) {
      invokingState = -1
    }
    super(parent, invokingState)
    this.parser = parser
    this.ruleIndex = DSLParser.RULE_functionBody
  }

  eos() {
    return this.getTypedRuleContext(EosContext, 0)
  }

  statement(i: number | null) {
    if (i === undefined) {
      i = null
    }
    if (i === null) {
      return this.getTypedRuleContexts(StatementContext)
    } else {
      return this.getTypedRuleContext(StatementContext, i)
    }
  }

  returnStatement() {
    return this.getTypedRuleContext(ReturnStatementContext, 0)
  }
}

class VariableStatementContext extends ParserRuleContext {
  ruleIndex: number
  constructor(parser?: Parser, parent?: ParserRuleContext, invokingState?: number) {
    if (invokingState === undefined || invokingState === null) {
      invokingState = -1
    }
    super(parent, invokingState)
    this.parser = parser
    this.ruleIndex = DSLParser.RULE_variableStatement
  }

  VariablePrefix() {
    return this.getToken(DSLParser.VariablePrefix, 0)
  }

  variableDeclarationList() {
    return this.getTypedRuleContext(VariableDeclarationListContext, 0)
  }
}

class VariableDeclarationListContext extends ParserRuleContext {
  constructor(parser?: Parser, parent?: ParserRuleContext, invokingState?: number) {
    if (invokingState === undefined || invokingState === null) {
      invokingState = -1
    }
    super(parent, invokingState)
    this.parser = parser
    this.ruleIndex = DSLParser.RULE_variableDeclarationList
  }
  ruleIndex: number

  variableDeclaration(i: number | null) {
    if (i === undefined) {
      i = null
    }
    if (i === null) {
      return this.getTypedRuleContexts(VariableDeclarationContext)
    } else {
      return this.getTypedRuleContext(VariableDeclarationContext, i)
    }
  }
}

class VariableDeclarationContext extends ParserRuleContext {
  constructor(parser?: Parser, parent?: ParserRuleContext, invokingState?: number) {
    if (invokingState === undefined || invokingState === null) {
      invokingState = -1
    }
    super(parent, invokingState)
    this.parser = parser
    this.ruleIndex = DSLParser.RULE_variableDeclaration
  }
  ruleIndex: number

  identifierExpress() {
    return this.getTypedRuleContext(IdentifierExpressContext, 0)
  }

  RegularAssign() {
    return this.getToken(DSLParser.RegularAssign, 0)
  }

  Assignment() {
    return this.getToken(DSLParser.Assignment, 0)
  }

  expression() {
    return this.getTypedRuleContext(ExpressionContext, 0)
  }

  statement() {
    return this.getTypedRuleContext(StatementContext, 0)
  }
}

class AssignListStatementContext extends ParserRuleContext {
  constructor(parser?: Parser, parent?: ParserRuleContext, invokingState?: number) {
    if (invokingState === undefined || invokingState === null) {
      invokingState = -1
    }
    super(parent, invokingState)
    this.parser = parser
    this.ruleIndex = DSLParser.RULE_assignListStatement
  }
  ruleIndex: number

  assignStatement(i: number | null) {
    if (i === undefined) {
      i = null
    }
    if (i === null) {
      return this.getTypedRuleContexts(AssignStatementContext)
    } else {
      return this.getTypedRuleContext(AssignStatementContext, i)
    }
  }
}

class AssignStatementContext extends ParserRuleContext {
  constructor(parser?: Parser, parent?: ParserRuleContext, invokingState?: number) {
    if (invokingState === undefined || invokingState === null) {
      invokingState = -1
    }
    super(parent, invokingState)
    this.parser = parser
    this.ruleIndex = DSLParser.RULE_assignStatement
  }
  ruleIndex: number

  identifierExpress() {
    return this.getTypedRuleContext(IdentifierExpressContext, 0)
  }

  RegularAssign() {
    return this.getToken(DSLParser.RegularAssign, 0)
  }

  Assignment() {
    return this.getToken(DSLParser.Assignment, 0)
  }

  expression() {
    return this.getTypedRuleContext(ExpressionContext, 0)
  }

  functionDeclaration() {
    return this.getTypedRuleContext(FunctionDeclarationContext, 0)
  }

  rightPlusPlus() {
    return this.getTypedRuleContext(RightPlusPlusContext, 0)
  }

  rightMinusMinus() {
    return this.getTypedRuleContext(RightMinusMinusContext, 0)
  }

  leftPlusPlus() {
    return this.getTypedRuleContext(LeftPlusPlusContext, 0)
  }

  leftMinusMinus() {
    return this.getTypedRuleContext(LeftMinusMinusContext, 0)
  }
}

class IfStatementContext extends ParserRuleContext {
  constructor(parser?: Parser, parent?: ParserRuleContext, invokingState?: number) {
    if (invokingState === undefined || invokingState === null) {
      invokingState = -1
    }
    super(parent, invokingState)
    this.parser = parser
    this.ruleIndex = DSLParser.RULE_ifStatement
  }
  ruleIndex: number

  IfKeyword() {
    return this.getToken(DSLParser.IfKeyword, 0)
  }

  expression() {
    return this.getTypedRuleContext(ExpressionContext, 0)
  }

  statement() {
    return this.getTypedRuleContext(StatementContext, 0)
  }

  OpenParen() {
    return this.getToken(DSLParser.OpenParen, 0)
  }

  CloseParen() {
    return this.getToken(DSLParser.CloseParen, 0)
  }

  elseIfStatement(i: number | null) {
    if (i === undefined) {
      i = null
    }
    if (i === null) {
      return this.getTypedRuleContexts(ElseIfStatementContext)
    } else {
      return this.getTypedRuleContext(ElseIfStatementContext, i)
    }
  }

  elseStatement() {
    return this.getTypedRuleContext(ElseStatementContext, 0)
  }
}

class ElseIfStatementContext extends ParserRuleContext {
  constructor(parser?: Parser, parent?: ParserRuleContext, invokingState?: number) {
    if (invokingState === undefined || invokingState === null) {
      invokingState = -1
    }
    super(parent, invokingState)
    this.parser = parser
    this.ruleIndex = DSLParser.RULE_elseIfStatement
  }
  ruleIndex: number

  expression() {
    return this.getTypedRuleContext(ExpressionContext, 0)
  }

  statement() {
    return this.getTypedRuleContext(StatementContext, 0)
  }

  ElseIfKeyword() {
    return this.getToken(DSLParser.ElseIfKeyword, 0)
  }

  ElseKeyword() {
    return this.getToken(DSLParser.ElseKeyword, 0)
  }

  IfKeyword() {
    return this.getToken(DSLParser.IfKeyword, 0)
  }

  OpenParen() {
    return this.getToken(DSLParser.OpenParen, 0)
  }

  CloseParen() {
    return this.getToken(DSLParser.CloseParen, 0)
  }
}

class ElseStatementContext extends ParserRuleContext {
  constructor(parser?: Parser, parent?: ParserRuleContext, invokingState?: number) {
    if (invokingState === undefined || invokingState === null) {
      invokingState = -1
    }
    super(parent, invokingState)
    this.parser = parser
    this.ruleIndex = DSLParser.RULE_elseStatement
  }
  ruleIndex: number

  ElseKeyword() {
    return this.getToken(DSLParser.ElseKeyword, 0)
  }

  statement() {
    return this.getTypedRuleContext(StatementContext, 0)
  }
}

class ForStatementsContext extends ParserRuleContext {
  constructor(parser?: Parser, parent?: ParserRuleContext, invokingState?: number) {
    if (invokingState === undefined || invokingState === null) {
      invokingState = -1
    }
    super(parent, invokingState)
    this.parser = parser
    this.ruleIndex = DSLParser.RULE_forStatements
  }
  ruleIndex: number

  forStatement() {
    return this.getTypedRuleContext(ForStatementContext, 0)
  }

  forInStatement() {
    return this.getTypedRuleContext(ForInStatementContext, 0)
  }

  forInRangeStatement() {
    return this.getTypedRuleContext(ForInRangeStatementContext, 0)
  }
}

class ForStatementContext extends ParserRuleContext {
  constructor(parser?: Parser, parent?: ParserRuleContext, invokingState?: number) {
    if (invokingState === undefined || invokingState === null) {
      invokingState = -1
    }
    super(parent, invokingState)
    this.parser = parser
    this.ruleIndex = DSLParser.RULE_forStatement
  }
  ruleIndex: number

  For() {
    return this.getToken(DSLParser.For, 0)
  }

  OpenParen() {
    return this.getToken(DSLParser.OpenParen, 0)
  }

  forStatementParametar() {
    return this.getTypedRuleContext(ForStatementParametarContext, 0)
  }

  CloseParen() {
    return this.getToken(DSLParser.CloseParen, 0)
  }

  forStatementBody() {
    return this.getTypedRuleContext(ForStatementBodyContext, 0)
  }
}

class ForStatementParametarContext extends ParserRuleContext {
  constructor(parser?: Parser, parent?: ParserRuleContext, invokingState?: number) {
    if (invokingState === undefined || invokingState === null) {
      invokingState = -1
    }
    super(parent, invokingState)
    this.parser = parser
    this.ruleIndex = DSLParser.RULE_forStatementParametar
  }
  ruleIndex: number

  SemiColon(i: number | null) {
    if (i === undefined) {
      i = null
    }
    if (i === null) {
      return this.getTokens(DSLParser.SemiColon)
    } else {
      return this.getToken(DSLParser.SemiColon, i)
    }
  }

  variableStatement() {
    return this.getTypedRuleContext(VariableStatementContext, 0)
  }

  assignListStatement(i: number | null) {
    if (i === undefined) {
      i = null
    }
    if (i === null) {
      return this.getTypedRuleContexts(AssignListStatementContext)
    } else {
      return this.getTypedRuleContext(AssignListStatementContext, i)
    }
  }

  expression() {
    return this.getTypedRuleContext(ExpressionContext, 0)
  }
}

class ForStatementBodyContext extends ParserRuleContext {
  constructor(parser?: Parser, parent?: ParserRuleContext, invokingState?: number) {
    if (invokingState === undefined || invokingState === null) {
      invokingState = -1
    }
    super(parent, invokingState)
    this.parser = parser
    this.ruleIndex = DSLParser.RULE_forStatementBody
  }
  ruleIndex: number

  statement() {
    return this.getTypedRuleContext(StatementContext, 0)
  }
}

class ForInStatementContext extends ParserRuleContext {
  constructor(parser?: Parser, parent?: ParserRuleContext, invokingState?: number) {
    if (invokingState === undefined || invokingState === null) {
      invokingState = -1
    }
    super(parent, invokingState)
    this.parser = parser
    this.ruleIndex = DSLParser.RULE_forInStatement
  }
  ruleIndex: number

  For() {
    return this.getToken(DSLParser.For, 0)
  }

  Identifier(i: number | null) {
    if (i === undefined) {
      i = null
    }
    if (i === null) {
      return this.getTokens(DSLParser.Identifier)
    } else {
      return this.getToken(DSLParser.Identifier, i)
    }
  }

  identifierExpress() {
    return this.getTypedRuleContext(IdentifierExpressContext, 0)
  }

  forStatementBody() {
    return this.getTypedRuleContext(ForStatementBodyContext, 0)
  }
}

class ForInRangeStatementContext extends ParserRuleContext {
  constructor(parser?: Parser, parent?: ParserRuleContext, invokingState?: number) {
    if (invokingState === undefined || invokingState === null) {
      invokingState = -1
    }
    super(parent, invokingState)
    this.parser = parser
    this.ruleIndex = DSLParser.RULE_forInRangeStatement
  }
  ruleIndex: number

  For() {
    return this.getToken(DSLParser.For, 0)
  }

  Identifier() {
    return this.getToken(DSLParser.Identifier, 0)
  }

  rangeFunction() {
    return this.getTypedRuleContext(RangeFunctionContext, 0)
  }

  forStatementBody() {
    return this.getTypedRuleContext(ForStatementBodyContext, 0)
  }
}

class RangeFunctionContext extends ParserRuleContext {
  constructor(parser?: Parser, parent?: ParserRuleContext, invokingState?: number) {
    if (invokingState === undefined || invokingState === null) {
      invokingState = -1
    }
    super(parent, invokingState)
    this.parser = parser
    this.ruleIndex = DSLParser.RULE_rangeFunction
  }
  ruleIndex: number

  OpenParen() {
    return this.getToken(DSLParser.OpenParen, 0)
  }

  Number(i: number | null) {
    if (i === undefined) {
      i = null
    }
    if (i === null) {
      return this.getTokens(DSLParser.Number)
    } else {
      return this.getToken(DSLParser.Number, i)
    }
  }

  CloseParen() {
    return this.getToken(DSLParser.CloseParen, 0)
  }

  identifierExpress() {
    return this.getTypedRuleContext(IdentifierExpressContext, 0)
  }

  Minus() {
    return this.getToken(DSLParser.Minus, 0)
  }
}

class WhileStatementContext extends ParserRuleContext {
  constructor(parser?: Parser, parent?: ParserRuleContext, invokingState?: number) {
    if (invokingState === undefined || invokingState === null) {
      invokingState = -1
    }
    super(parent, invokingState)
    this.parser = parser
    this.ruleIndex = DSLParser.RULE_whileStatement
  }
  ruleIndex: number

  While() {
    return this.getToken(DSLParser.While, 0)
  }

  whileStatementParametar() {
    return this.getTypedRuleContext(WhileStatementParametarContext, 0)
  }

  whileStatementBody() {
    return this.getTypedRuleContext(WhileStatementBodyContext, 0)
  }

  OpenParen() {
    return this.getToken(DSLParser.OpenParen, 0)
  }

  CloseParen() {
    return this.getToken(DSLParser.CloseParen, 0)
  }
}

class WhileStatementParametarContext extends ParserRuleContext {
  constructor(parser?: Parser, parent?: ParserRuleContext, invokingState?: number) {
    if (invokingState === undefined || invokingState === null) {
      invokingState = -1
    }
    super(parent, invokingState)
    this.parser = parser
    this.ruleIndex = DSLParser.RULE_whileStatementParametar
  }
  ruleIndex: number

  expression() {
    return this.getTypedRuleContext(ExpressionContext, 0)
  }
}

class WhileStatementBodyContext extends ParserRuleContext {
  constructor(parser?: Parser, parent?: ParserRuleContext, invokingState?: number) {
    if (invokingState === undefined || invokingState === null) {
      invokingState = -1
    }
    super(parent, invokingState)
    this.parser = parser
    this.ruleIndex = DSLParser.RULE_whileStatementBody
  }
  ruleIndex: number

  statement() {
    return this.getTypedRuleContext(StatementContext, 0)
  }
}

class TryStatementContext extends ParserRuleContext {
  constructor(parser?: Parser, parent?: ParserRuleContext, invokingState?: number) {
    if (invokingState === undefined || invokingState === null) {
      invokingState = -1
    }
    super(parent, invokingState)
    this.parser = parser
    this.ruleIndex = DSLParser.RULE_tryStatement
  }
  ruleIndex: number

  statement(i: number | null) {
    if (i === undefined) {
      i = null
    }
    if (i === null) {
      return this.getTypedRuleContexts(StatementContext)
    } else {
      return this.getTypedRuleContext(StatementContext, i)
    }
  }

  OpenParen(i: number | null) {
    if (i === undefined) {
      i = null
    }
    if (i === null) {
      return this.getTokens(DSLParser.OpenParen)
    } else {
      return this.getToken(DSLParser.OpenParen, i)
    }
  }

  Identifier(i: number | null) {
    if (i === undefined) {
      i = null
    }
    if (i === null) {
      return this.getTokens(DSLParser.Identifier)
    } else {
      return this.getToken(DSLParser.Identifier, i)
    }
  }

  CloseParen(i: number | null) {
    if (i === undefined) {
      i = null
    }
    if (i === null) {
      return this.getTokens(DSLParser.CloseParen)
    } else {
      return this.getToken(DSLParser.CloseParen, i)
    }
  }

  BitOr(i: number | null) {
    if (i === undefined) {
      i = null
    }
    if (i === null) {
      return this.getTokens(DSLParser.BitOr)
    } else {
      return this.getToken(DSLParser.BitOr, i)
    }
  }
}

class ReturnStatementContext extends ParserRuleContext {
  constructor(parser?: Parser, parent?: ParserRuleContext, invokingState?: number) {
    if (invokingState === undefined || invokingState === null) {
      invokingState = -1
    }
    super(parent, invokingState)
    this.parser = parser
    this.ruleIndex = DSLParser.RULE_returnStatement
  }
  ruleIndex: number

  ReturnKeyword() {
    return this.getToken(DSLParser.ReturnKeyword, 0)
  }

  expression() {
    return this.getTypedRuleContext(ExpressionContext, 0)
  }

  functionDeclaration() {
    return this.getTypedRuleContext(FunctionDeclarationContext, 0)
  }

  eos(i: number | null) {
    if (i === undefined) {
      i = null
    }
    if (i === null) {
      return this.getTypedRuleContexts(EosContext)
    } else {
      return this.getTypedRuleContext(EosContext, i)
    }
  }
}

class ArrayContext extends ParserRuleContext {
  constructor(parser?: Parser, parent?: ParserRuleContext, invokingState?: number) {
    if (invokingState === undefined || invokingState === null) {
      invokingState = -1
    }
    super(parent, invokingState)
    this.parser = parser
    this.ruleIndex = DSLParser.RULE_array
  }
  ruleIndex: number

  OpenBracket() {
    return this.getToken(DSLParser.OpenBracket, 0)
  }

  expression(i: number | null) {
    if (i === undefined) {
      i = null
    }
    if (i === null) {
      return this.getTypedRuleContexts(ExpressionContext)
    } else {
      return this.getTypedRuleContext(ExpressionContext, i)
    }
  }

  CloseBracket() {
    return this.getToken(DSLParser.CloseBracket, 0)
  }
}

class ExpressionContext extends ParserRuleContext {
  constructor(parser?: Parser, parent?: ParserRuleContext, invokingState?: number) {
    if (invokingState === undefined || invokingState === null) {
      invokingState = -1
    }
    super(parent, invokingState)
    this.parser = parser
    this.ruleIndex = DSLParser.RULE_expression
  }
  ruleIndex: number

  literal() {
    return this.getTypedRuleContext(LiteralContext, 0)
  }

  lambdaExpression() {
    return this.getTypedRuleContext(LambdaExpressionContext, 0)
  }

  functionInvoikeExpression() {
    return this.getTypedRuleContext(FunctionInvoikeExpressionContext, 0)
  }

  plusExpression() {
    return this.getTypedRuleContext(PlusExpressionContext, 0)
  }

  minusExpression() {
    return this.getTypedRuleContext(MinusExpressionContext, 0)
  }

  rightPlusPlus() {
    return this.getTypedRuleContext(RightPlusPlusContext, 0)
  }

  rightMinusMinus() {
    return this.getTypedRuleContext(RightMinusMinusContext, 0)
  }

  leftPlusPlus() {
    return this.getTypedRuleContext(LeftPlusPlusContext, 0)
  }

  leftMinusMinus() {
    return this.getTypedRuleContext(LeftMinusMinusContext, 0)
  }

  Not() {
    return this.getToken(DSLParser.Not, 0)
  }

  expression(i: number | null) {
    if (i === undefined) {
      i = null
    }
    if (i === null) {
      return this.getTypedRuleContexts(ExpressionContext)
    } else {
      return this.getTypedRuleContext(ExpressionContext, i)
    }
  }

  BitNot() {
    return this.getToken(DSLParser.BitNot, 0)
  }

  wrapperExpression() {
    return this.getTypedRuleContext(WrapperExpressionContext, 0)
  }

  identifierExpress() {
    return this.getTypedRuleContext(IdentifierExpressContext, 0)
  }

  array() {
    return this.getTypedRuleContext(ArrayContext, 0)
  }

  rangeFunction() {
    return this.getTypedRuleContext(RangeFunctionContext, 0)
  }

  RegularRelationLeftExpression() {
    return this.getToken(DSLParser.RegularRelationLeftExpression, 0)
  }

  RegularRelationRightExpression() {
    return this.getToken(DSLParser.RegularRelationRightExpression, 0)
  }

  assignStatement() {
    return this.getTypedRuleContext(AssignStatementContext, 0)
  }

  Power() {
    return this.getToken(DSLParser.Power, 0)
  }

  operator2() {
    return this.getTypedRuleContext(Operator2Context, 0)
  }

  operator1() {
    return this.getTypedRuleContext(Operator1Context, 0)
  }

  NewLine(i: number | null) {
    if (i === undefined) {
      i = null
    }
    if (i === null) {
      return this.getTokens(DSLParser.NewLine)
    } else {
      return this.getToken(DSLParser.NewLine, i)
    }
  }

  BitRightShiftNoSign() {
    return this.getToken(DSLParser.BitRightShiftNoSign, 0)
  }

  BitLeftShift() {
    return this.getToken(DSLParser.BitLeftShift, 0)
  }

  BitRightShift() {
    return this.getToken(DSLParser.BitRightShift, 0)
  }

  relationOperator() {
    return this.getTypedRuleContext(RelationOperatorContext, 0)
  }

  BitAnd() {
    return this.getToken(DSLParser.BitAnd, 0)
  }

  BitOr() {
    return this.getToken(DSLParser.BitOr, 0)
  }

  And() {
    return this.getToken(DSLParser.And, 0)
  }

  Or() {
    return this.getToken(DSLParser.Or, 0)
  }

  RegulaLike() {
    return this.getToken(DSLParser.RegulaLike, 0)
  }

  QuestionMark() {
    return this.getToken(DSLParser.QuestionMark, 0)
  }

  Colon() {
    return this.getToken(DSLParser.Colon, 0)
  }

  RegularLikeRight() {
    return this.getToken(DSLParser.RegularLikeRight, 0)
  }
}

class LambdaExpressionContext extends ParserRuleContext {
  ruleIndex: number
  constructor(parser?: Parser, parent?: ParserRuleContext, invokingState?: number) {
    if (invokingState === undefined || invokingState === null) {
      invokingState = -1
    }
    super(parent, invokingState)
    this.parser = parser
    this.ruleIndex = DSLParser.RULE_lambdaExpression
  }

  OpenParen() {
    return this.getToken(DSLParser.OpenParen, 0)
  }

  functionParameterList() {
    return this.getTypedRuleContext(FunctionParameterListContext, 0)
  }

  CloseParen() {
    return this.getToken(DSLParser.CloseParen, 0)
  }

  statement(i: number | null) {
    if (i === undefined) {
      i = null
    }
    if (i === null) {
      return this.getTypedRuleContexts(StatementContext)
    } else {
      return this.getTypedRuleContext(StatementContext, i)
    }
  }

  SemiColon() {
    return this.getToken(DSLParser.SemiColon, 0)
  }
}

class IdentifierExpressContext extends ParserRuleContext {
  constructor(parser?: Parser, parent?: ParserRuleContext, invokingState?: number) {
    if (invokingState === undefined || invokingState === null) {
      invokingState = -1
    }
    super(parent, invokingState)
    this.parser = parser
    this.ruleIndex = DSLParser.RULE_identifierExpress
  }
  ruleIndex: number

  Identifier(i: number | null) {
    if (i === undefined) {
      i = null
    }
    if (i === null) {
      return this.getTokens(DSLParser.Identifier)
    } else {
      return this.getToken(DSLParser.Identifier, i)
    }
  }

  Dot(i: number | null) {
    if (i === undefined) {
      i = null
    }
    if (i === null) {
      return this.getTokens(DSLParser.Dot)
    } else {
      return this.getToken(DSLParser.Dot, i)
    }
  }

  OpenBracket(i: number | null) {
    if (i === undefined) {
      i = null
    }
    if (i === null) {
      return this.getTokens(DSLParser.OpenBracket)
    } else {
      return this.getToken(DSLParser.OpenBracket, i)
    }
  }

  expression(i: number | null) {
    if (i === undefined) {
      i = null
    }
    if (i === null) {
      return this.getTypedRuleContexts(ExpressionContext)
    } else {
      return this.getTypedRuleContext(ExpressionContext, i)
    }
  }

  CloseBracket(i: number | null) {
    if (i === undefined) {
      i = null
    }
    if (i === null) {
      return this.getTokens(DSLParser.CloseBracket)
    } else {
      return this.getToken(DSLParser.CloseBracket, i)
    }
  }
}

class PlusExpressionContext extends ParserRuleContext {
  constructor(parser?: Parser, parent?: ParserRuleContext, invokingState?: number) {
    if (invokingState === undefined || invokingState === null) {
      invokingState = -1
    }
    super(parent, invokingState)
    this.parser = parser
    this.ruleIndex = DSLParser.RULE_plusExpression
  }
  ruleIndex: number

  Plus() {
    return this.getToken(DSLParser.Plus, 0)
  }

  expression() {
    return this.getTypedRuleContext(ExpressionContext, 0)
  }
}

class MinusExpressionContext extends ParserRuleContext {
  constructor(parser?: Parser, parent?: ParserRuleContext, invokingState?: number) {
    if (invokingState === undefined || invokingState === null) {
      invokingState = -1
    }
    super(parent, invokingState)
    this.parser = parser
    this.ruleIndex = DSLParser.RULE_minusExpression
  }

  Minus() {
    return this.getToken(DSLParser.Minus, 0)
  }
  ruleIndex: number

  expression() {
    return this.getTypedRuleContext(ExpressionContext, 0)
  }
}

class RightPlusPlusContext extends ParserRuleContext {
  constructor(parser?: Parser, parent?: ParserRuleContext, invokingState?: number) {
    if (invokingState === undefined || invokingState === null) {
      invokingState = -1
    }
    super(parent, invokingState)
    this.parser = parser
    this.ruleIndex = DSLParser.RULE_rightPlusPlus
  }
  ruleIndex: number

  Identifier() {
    return this.getToken(DSLParser.Identifier, 0)
  }

  PlusPlus() {
    return this.getToken(DSLParser.PlusPlus, 0)
  }
}

class RightMinusMinusContext extends ParserRuleContext {
  constructor(parser?: Parser, parent?: ParserRuleContext, invokingState?: number) {
    if (invokingState === undefined || invokingState === null) {
      invokingState = -1
    }
    super(parent, invokingState)
    this.parser = parser
    this.ruleIndex = DSLParser.RULE_rightMinusMinus
  }

  Identifier() {
    return this.getToken(DSLParser.Identifier, 0)
  }
  ruleIndex: number

  MinusMinus() {
    return this.getToken(DSLParser.MinusMinus, 0)
  }
}

class LeftPlusPlusContext extends ParserRuleContext {
  constructor(parser?: Parser, parent?: ParserRuleContext, invokingState?: number) {
    if (invokingState === undefined || invokingState === null) {
      invokingState = -1
    }
    super(parent, invokingState)
    this.parser = parser
    this.ruleIndex = DSLParser.RULE_leftPlusPlus
  }

  PlusPlus() {
    return this.getToken(DSLParser.PlusPlus, 0)
  }
  ruleIndex: number

  Identifier() {
    return this.getToken(DSLParser.Identifier, 0)
  }
}

class LeftMinusMinusContext extends ParserRuleContext {
  constructor(parser?: Parser, parent?: ParserRuleContext, invokingState?: number) {
    if (invokingState === undefined || invokingState === null) {
      invokingState = -1
    }
    super(parent, invokingState)
    this.parser = parser
    this.ruleIndex = DSLParser.RULE_leftMinusMinus
  }

  MinusMinus() {
    return this.getToken(DSLParser.MinusMinus, 0)
  }
  ruleIndex: number

  Identifier() {
    return this.getToken(DSLParser.Identifier, 0)
  }
}

class RelationOperatorContext extends ParserRuleContext {
  constructor(parser?: Parser, parent?: ParserRuleContext, invokingState?: number) {
    if (invokingState === undefined || invokingState === null) {
      invokingState = -1
    }
    super(parent, invokingState)
    this.parser = parser
    this.ruleIndex = DSLParser.RULE_relationOperator
  }

  GreatThan() {
    return this.getToken(DSLParser.GreatThan, 0)
  }
  ruleIndex: number

  GreatThanOrEqual() {
    return this.getToken(DSLParser.GreatThanOrEqual, 0)
  }

  LessThan() {
    return this.getToken(DSLParser.LessThan, 0)
  }

  LessThanOrEqual() {
    return this.getToken(DSLParser.LessThanOrEqual, 0)
  }

  Equal() {
    return this.getToken(DSLParser.Equal, 0)
  }

  NotEqual() {
    return this.getToken(DSLParser.NotEqual, 0)
  }
}

class Operator2Context extends ParserRuleContext {
  constructor(parser?: Parser, parent?: ParserRuleContext, invokingState?: number) {
    if (invokingState === undefined || invokingState === null) {
      invokingState = -1
    }
    super(parent, invokingState)
    this.parser = parser
    this.ruleIndex = DSLParser.RULE_operator2
  }

  Multiply() {
    return this.getToken(DSLParser.Multiply, 0)
  }
  ruleIndex: number

  Divide() {
    return this.getToken(DSLParser.Divide, 0)
  }

  Mode() {
    return this.getToken(DSLParser.Mode, 0)
  }
}

class Operator1Context extends ParserRuleContext {
  ruleIndex: number
  constructor(parser?: Parser, parent?: ParserRuleContext, invokingState?: number) {
    if (invokingState === undefined || invokingState === null) {
      invokingState = -1
    }
    super(parent, invokingState)
    this.parser = parser
    this.ruleIndex = DSLParser.RULE_operator1
  }

  Plus() {
    return this.getToken(DSLParser.Plus, 0)
  }

  Minus() {
    return this.getToken(DSLParser.Minus, 0)
  }
}

class BitOperatorContext extends ParserRuleContext {
  ruleIndex: number
  constructor(parser?: Parser, parent?: ParserRuleContext, invokingState?: number) {
    if (invokingState === undefined || invokingState === null) {
      invokingState = -1
    }
    super(parent, invokingState)
    this.parser = parser
    this.ruleIndex = DSLParser.RULE_bitOperator
  }

  BitAnd() {
    return this.getToken(DSLParser.BitAnd, 0)
  }

  BitOr() {
    return this.getToken(DSLParser.BitOr, 0)
  }
}

class SingleBitOperatorContext extends ParserRuleContext {
  ruleIndex: number
  constructor(parser?: Parser, parent?: ParserRuleContext, invokingState?: number) {
    if (invokingState === undefined || invokingState === null) {
      invokingState = -1
    }
    super(parent, invokingState)
    this.parser = parser
    this.ruleIndex = DSLParser.RULE_singleBitOperator
  }

  Multiply() {
    return this.getToken(DSLParser.Multiply, 0)
  }

  Divide() {
    return this.getToken(DSLParser.Divide, 0)
  }

  Mode() {
    return this.getToken(DSLParser.Mode, 0)
  }
}

class WrapperExpressionContext extends ParserRuleContext {
  ruleIndex: number
  constructor(parser?: Parser, parent?: ParserRuleContext, invokingState?: number) {
    if (invokingState === undefined || invokingState === null) {
      invokingState = -1
    }
    super(parent, invokingState)
    this.parser = parser
    this.ruleIndex = DSLParser.RULE_wrapperExpression
  }

  OpenParen() {
    return this.getToken(DSLParser.OpenParen, 0)
  }

  expression() {
    return this.getTypedRuleContext(ExpressionContext, 0)
  }

  CloseParen() {
    return this.getToken(DSLParser.CloseParen, 0)
  }
}

class FunctionInvoikeExpressionContext extends ParserRuleContext {
  ruleIndex: number
  constructor(parser?: Parser, parent?: ParserRuleContext, invokingState?: number) {
    if (invokingState === undefined || invokingState === null) {
      invokingState = -1
    }
    super(parent, invokingState)
    this.parser = parser
    this.ruleIndex = DSLParser.RULE_functionInvoikeExpression
  }

  OpenParen(i: number | null) {
    if (i === undefined) {
      i = null
    }
    if (i === null) {
      return this.getTokens(DSLParser.OpenParen)
    } else {
      return this.getToken(DSLParser.OpenParen, i)
    }
  }

  CloseParen(i: number | null) {
    if (i === undefined) {
      i = null
    }
    if (i === null) {
      return this.getTokens(DSLParser.CloseParen)
    } else {
      return this.getToken(DSLParser.CloseParen, i)
    }
  }

  lambdaExpression() {
    return this.getTypedRuleContext(LambdaExpressionContext, 0)
  }

  identifierExpress() {
    return this.getTypedRuleContext(IdentifierExpressContext, 0)
  }

  expression(i: number | null) {
    if (i === undefined) {
      i = null
    }
    if (i === null) {
      return this.getTypedRuleContexts(ExpressionContext)
    } else {
      return this.getTypedRuleContext(ExpressionContext, i)
    }
  }

  Multiply(i: number | null) {
    if (i === undefined) {
      i = null
    }
    if (i === null) {
      return this.getTokens(DSLParser.Multiply)
    } else {
      return this.getToken(DSLParser.Multiply, i)
    }
  }

  Plus(i: number | null) {
    if (i === undefined) {
      i = null
    }
    if (i === null) {
      return this.getTokens(DSLParser.Plus)
    } else {
      return this.getToken(DSLParser.Plus, i)
    }
  }

  NewLine(i: number | null) {
    if (i === undefined) {
      i = null
    }
    if (i === null) {
      return this.getTokens(DSLParser.NewLine)
    } else {
      return this.getToken(DSLParser.NewLine, i)
    }
  }
}

class EosContext extends ParserRuleContext {
  ruleIndex: number

  constructor(parser?: Parser, parent?: ParserRuleContext, invokingState?: number) {
    if (invokingState === undefined || invokingState === null) {
      invokingState = -1
    }
    super(parent, invokingState)
    this.parser = parser
    this.ruleIndex = DSLParser.RULE_eos
  }

  SemiColon() {
    return this.getToken(DSLParser.SemiColon, 0)
  }

  NewLine() {
    return this.getToken(DSLParser.NewLine, 0)
  }
}

class LiteralContext extends ParserRuleContext {
  ruleIndex: number
  constructor(parser?: Parser, parent?: ParserRuleContext, invokingState?: number) {
    if (invokingState === undefined || invokingState === null) {
      invokingState = -1
    }
    super(parent, invokingState)
    this.parser = parser
    this.ruleIndex = DSLParser.RULE_literal
  }

  Number() {
    return this.getToken(DSLParser.Number, 0)
  }

  NumberHex() {
    return this.getToken(DSLParser.NumberHex, 0)
  }

  BooleanTrue() {
    return this.getToken(DSLParser.BooleanTrue, 0)
  }

  BooleanFalse() {
    return this.getToken(DSLParser.BooleanFalse, 0)
  }

  String() {
    return this.getToken(DSLParser.String, 0)
  }
}

export default class DSLParser extends Parser {
  static grammarFileName = 'DSL.g4'
  static literalNames = [
    null,
    "'use'",
    "','",
    "'in'",
    "'range'",
    "'try'",
    "'catch'",
    "'lambda'",
    "'->'",
    "'end'",
    null,
    null,
    null,
    null,
    null,
    null,
    null,
    "'**'",
    "'*'",
    "'/'",
    "'%'",
    "'!'",
    "'&&'",
    "'||'",
    "'!='",
    "'>='",
    "'>'",
    "'<='",
    "'<'",
    "'=='",
    "'=~'",
    "'&'",
    "'|'",
    "'^'",
    "'~'",
    "'>>>'",
    "'<<'",
    "'>>'",
    null,
    null,
    "'true'",
    "'false'",
    null,
    "'='",
    "'let'",
    "'fn'",
    "'return'",
    "'if'",
    "'else'",
    "'elsif'",
    "'break'",
    "'continue'",
    "'for'",
    "'switch'",
    "'while'",
    "'['",
    "']'",
    "'('",
    "')'",
    "'{'",
    "'}'",
    "';'",
    "'?'",
    "':'",
    "'.'",
    "'+'",
    "'-'",
    "'++'",
    "'--'"
  ]
  static symbolicNames = [
    null,
    null,
    null,
    null,
    null,
    null,
    null,
    null,
    null,
    null,
    'RegularAssign',
    'RegularLikeRight',
    'RegularRelationLeftExpression',
    'RegularRelationRightExpression',
    'Space',
    'NewLine',
    'Comment',
    'Power',
    'Multiply',
    'Divide',
    'Mode',
    'Not',
    'And',
    'Or',
    'NotEqual',
    'GreatThanOrEqual',
    'GreatThan',
    'LessThanOrEqual',
    'LessThan',
    'Equal',
    'RegulaLike',
    'BitAnd',
    'BitOr',
    'BitXor',
    'BitNot',
    'BitRightShiftNoSign',
    'BitLeftShift',
    'BitRightShift',
    'Number',
    'NumberHex',
    'BooleanTrue',
    'BooleanFalse',
    'String',
    'Assignment',
    'VariablePrefix',
    'FunctionKeyword',
    'ReturnKeyword',
    'IfKeyword',
    'ElseKeyword',
    'ElseIfKeyword',
    'Break',
    'Continue',
    'For',
    'Switch',
    'While',
    'OpenBracket',
    'CloseBracket',
    'OpenParen',
    'CloseParen',
    'OpenBrace',
    'CloseBrace',
    'SemiColon',
    'QuestionMark',
    'Colon',
    'Dot',
    'Plus',
    'Minus',
    'PlusPlus',
    'MinusMinus',
    'Identifier',
    'UnexpectedCharacter'
  ]
  static ruleNames = [
    'root',
    'program',
    'statement',
    'useStatement',
    'block',
    'functionDeclaration',
    'functionParameterList',
    'functionBody',
    'variableStatement',
    'variableDeclarationList',
    'variableDeclaration',
    'assignListStatement',
    'assignStatement',
    'ifStatement',
    'elseIfStatement',
    'elseStatement',
    'forStatements',
    'forStatement',
    'forStatementParametar',
    'forStatementBody',
    'forInStatement',
    'forInRangeStatement',
    'rangeFunction',
    'whileStatement',
    'whileStatementParametar',
    'whileStatementBody',
    'tryStatement',
    'returnStatement',
    'array',
    'expression',
    'lambdaExpression',
    'identifierExpress',
    'plusExpression',
    'minusExpression',
    'rightPlusPlus',
    'rightMinusMinus',
    'leftPlusPlus',
    'leftMinusMinus',
    'relationOperator',
    'operator2',
    'operator1',
    'bitOperator',
    'singleBitOperator',
    'wrapperExpression',
    'functionInvoikeExpression',
    'eos',
    'literal'
  ]

  static EOF = Token.EOF
  static T__0 = 1
  static T__1 = 2
  static T__2 = 3
  static T__3 = 4
  static T__4 = 5
  static T__5 = 6
  static T__6 = 7
  static T__7 = 8
  static T__8 = 9
  static RegularAssign = 10
  static RegularLikeRight = 11
  static RegularRelationLeftExpression = 12
  static RegularRelationRightExpression = 13
  static Space = 14
  static NewLine = 15
  static Comment = 16
  static Power = 17
  static Multiply = 18
  static Divide = 19
  static Mode = 20
  static Not = 21
  static And = 22
  static Or = 23
  static NotEqual = 24
  static GreatThanOrEqual = 25
  static GreatThan = 26
  static LessThanOrEqual = 27
  static LessThan = 28
  static Equal = 29
  static RegulaLike = 30
  static BitAnd = 31
  static BitOr = 32
  static BitXor = 33
  static BitNot = 34
  static BitRightShiftNoSign = 35
  static BitLeftShift = 36
  static BitRightShift = 37
  static Number = 38
  static NumberHex = 39
  static BooleanTrue = 40
  static BooleanFalse = 41
  static String = 42
  static Assignment = 43
  static VariablePrefix = 44
  static FunctionKeyword = 45
  static ReturnKeyword = 46
  static IfKeyword = 47
  static ElseKeyword = 48
  static ElseIfKeyword = 49
  static Break = 50
  static Continue = 51
  static For = 52
  static Switch = 53
  static While = 54
  static OpenBracket = 55
  static CloseBracket = 56
  static OpenParen = 57
  static CloseParen = 58
  static OpenBrace = 59
  static CloseBrace = 60
  static SemiColon = 61
  static QuestionMark = 62
  static Colon = 63
  static Dot = 64
  static Plus = 65
  static Minus = 66
  static PlusPlus = 67
  static MinusMinus = 68
  static Identifier = 69
  static UnexpectedCharacter = 70
  static RULE_root = 0
  static RULE_program = 1
  static RULE_statement = 2
  static RULE_useStatement = 3
  static RULE_block = 4
  static RULE_functionDeclaration = 5
  static RULE_functionParameterList = 6
  static RULE_functionBody = 7
  static RULE_variableStatement = 8
  static RULE_variableDeclarationList = 9
  static RULE_variableDeclaration = 10
  static RULE_assignListStatement = 11
  static RULE_assignStatement = 12
  static RULE_ifStatement = 13
  static RULE_elseIfStatement = 14
  static RULE_elseStatement = 15
  static RULE_forStatements = 16
  static RULE_forStatement = 17
  static RULE_forStatementParametar = 18
  static RULE_forStatementBody = 19
  static RULE_forInStatement = 20
  static RULE_forInRangeStatement = 21
  static RULE_rangeFunction = 22
  static RULE_whileStatement = 23
  static RULE_whileStatementParametar = 24
  static RULE_whileStatementBody = 25
  static RULE_tryStatement = 26
  static RULE_returnStatement = 27
  static RULE_array = 28
  static RULE_expression = 29
  static RULE_lambdaExpression = 30
  static RULE_identifierExpress = 31
  static RULE_plusExpression = 32
  static RULE_minusExpression = 33
  static RULE_rightPlusPlus = 34
  static RULE_rightMinusMinus = 35
  static RULE_leftPlusPlus = 36
  static RULE_leftMinusMinus = 37
  static RULE_relationOperator = 38
  static RULE_operator2 = 39
  static RULE_operator1 = 40
  static RULE_bitOperator = 41
  static RULE_singleBitOperator = 42
  static RULE_wrapperExpression = 43
  static RULE_functionInvoikeExpression = 44
  static RULE_eos = 45
  static RULE_literal = 46
  static RootContext = RootContext
  static ProgramContext = ProgramContext
  static StatementContext = StatementContext
  static UseStatementContext = UseStatementContext
  static BlockContext = BlockContext
  static FunctionDeclarationContext = FunctionDeclarationContext
  static FunctionParameterListContext = FunctionParameterListContext
  static FunctionBodyContext = FunctionBodyContext
  static VariableStatementContext = VariableStatementContext
  static VariableDeclarationListContext = VariableDeclarationListContext
  static VariableDeclarationContext = VariableDeclarationContext
  static AssignListStatementContext = AssignListStatementContext
  static AssignStatementContext = AssignStatementContext
  static IfStatementContext = IfStatementContext
  static ElseIfStatementContext = ElseIfStatementContext
  static ElseStatementContext = ElseStatementContext
  static ForStatementsContext = ForStatementsContext
  static ForStatementContext = ForStatementContext
  static ForStatementParametarContext = ForStatementParametarContext
  static ForStatementBodyContext = ForStatementBodyContext
  static ForInStatementContext = ForInStatementContext
  static ForInRangeStatementContext = ForInRangeStatementContext
  static RangeFunctionContext = RangeFunctionContext
  static WhileStatementContext = WhileStatementContext
  static WhileStatementParametarContext = WhileStatementParametarContext
  static WhileStatementBodyContext = WhileStatementBodyContext
  static TryStatementContext = TryStatementContext
  static ReturnStatementContext = ReturnStatementContext
  static ArrayContext = ArrayContext
  static ExpressionContext = ExpressionContext
  static LambdaExpressionContext = LambdaExpressionContext
  static IdentifierExpressContext = IdentifierExpressContext
  static PlusExpressionContext = PlusExpressionContext
  static MinusExpressionContext = MinusExpressionContext
  static RightPlusPlusContext = RightPlusPlusContext
  static RightMinusMinusContext = RightMinusMinusContext
  static LeftPlusPlusContext = LeftPlusPlusContext
  static LeftMinusMinusContext = LeftMinusMinusContext
  static RelationOperatorContext = RelationOperatorContext
  static Operator2Context = Operator2Context
  static Operator1Context = Operator1Context
  static BitOperatorContext = BitOperatorContext
  static SingleBitOperatorContext = SingleBitOperatorContext
  static WrapperExpressionContext = WrapperExpressionContext
  static FunctionInvoikeExpressionContext = FunctionInvoikeExpressionContext
  static EosContext = EosContext
  static LiteralContext = LiteralContext
  constructor(input: CommonTokenStream) {
    super(input)
    this._interp = new ParserATNSimulator(this, atn, decisionsToDFA, sharedContextCache)
    DSLParser.ruleNames = DSLParser.ruleNames
    DSLParser.literalNames = DSLParser.literalNames
    DSLParser.symbolicNames = DSLParser.symbolicNames
  }

  get atn() {
    return atn
  }

  sempred(localctx: ParserRuleContext, ruleIndex: number, predIndex: number) {
    switch (ruleIndex) {
      case 2:
        return this.statement_sempred(localctx, predIndex)
      case 29:
        return this.expression_sempred(localctx, predIndex)
      default:
        throw 'No predicate with index:' + ruleIndex
    }
  }

  statement_sempred(_localctx: ParserRuleContext, predIndex: number) {
    switch (predIndex) {
      case 0:
        return this.precpred(this._ctx, 3)
      default:
        throw 'No predicate with index:' + predIndex
    }
  }

  expression_sempred(_localctx: ParserRuleContext, predIndex: number) {
    switch (predIndex) {
      case 1:
        return this.precpred(this._ctx, 20)
      case 2:
        return this.precpred(this._ctx, 19)
      case 3:
        return this.precpred(this._ctx, 18)
      case 4:
        return this.precpred(this._ctx, 17)
      case 5:
        return this.precpred(this._ctx, 16)
      case 6:
        return this.precpred(this._ctx, 15)
      case 7:
        return this.precpred(this._ctx, 14)
      case 8:
        return this.precpred(this._ctx, 13)
      case 9:
        return this.precpred(this._ctx, 12)
      case 10:
        return this.precpred(this._ctx, 11)
      case 11:
        return this.precpred(this._ctx, 10)
      case 12:
        return this.precpred(this._ctx, 8)
      case 13:
        return this.precpred(this._ctx, 9)
      default:
        throw 'No predicate with index:' + predIndex
    }
  }

  root() {
    const localctx = new RootContext(this, this._ctx, this.state)
    this.enterRule(localctx, 0, DSLParser.RULE_root)
    try {
      this.enterOuterAlt(localctx, 1)
      this.state = 94
      this.program()
      this.state = 95
      this.match(DSLParser.EOF)
    } catch (re) {
      if (re instanceof RecognitionException) {
        localctx.exception = re
        this._errHandler.reportError(this, re)
        this._errHandler.recover(this, re)
      } else {
        throw re
      }
    } finally {
      this.exitRule()
    }
    return localctx
  }

  program() {
    const localctx = new ProgramContext(this, this._ctx, this.state)
    this.enterRule(localctx, 2, DSLParser.RULE_program)
    let _la = 0 // Token type
    try {
      this.enterOuterAlt(localctx, 1)
      this.state = 98
      this._errHandler.sync(this)
      _la = this._input.LA(1)
      do {
        this.state = 97
        this.statement(0)
        this.state = 100
        this._errHandler.sync(this)
        _la = this._input.LA(1)
      } while (
        ((_la & ~0x1f) == 0 &&
          ((1 << _la) &
            ((1 << DSLParser.T__0) |
              (1 << DSLParser.T__3) |
              (1 << DSLParser.T__4) |
              (1 << DSLParser.T__6) |
              (1 << DSLParser.RegularRelationLeftExpression) |
              (1 << DSLParser.RegularRelationRightExpression) |
              (1 << DSLParser.NewLine) |
              (1 << DSLParser.Comment) |
              (1 << DSLParser.Not))) !==
            0) ||
        (((_la - 34) & ~0x1f) == 0 &&
          ((1 << (_la - 34)) &
            ((1 << (DSLParser.BitNot - 34)) |
              (1 << (DSLParser.Number - 34)) |
              (1 << (DSLParser.NumberHex - 34)) |
              (1 << (DSLParser.BooleanTrue - 34)) |
              (1 << (DSLParser.BooleanFalse - 34)) |
              (1 << (DSLParser.String - 34)) |
              (1 << (DSLParser.VariablePrefix - 34)) |
              (1 << (DSLParser.FunctionKeyword - 34)) |
              (1 << (DSLParser.ReturnKeyword - 34)) |
              (1 << (DSLParser.IfKeyword - 34)) |
              (1 << (DSLParser.Break - 34)) |
              (1 << (DSLParser.Continue - 34)) |
              (1 << (DSLParser.For - 34)) |
              (1 << (DSLParser.While - 34)) |
              (1 << (DSLParser.OpenBracket - 34)) |
              (1 << (DSLParser.OpenParen - 34)) |
              (1 << (DSLParser.OpenBrace - 34)) |
              (1 << (DSLParser.SemiColon - 34)) |
              (1 << (DSLParser.Plus - 34)))) !==
            0) ||
        (((_la - 66) & ~0x1f) == 0 &&
          ((1 << (_la - 66)) &
            ((1 << (DSLParser.Minus - 66)) |
              (1 << (DSLParser.PlusPlus - 66)) |
              (1 << (DSLParser.MinusMinus - 66)) |
              (1 << (DSLParser.Identifier - 66)))) !==
            0)
      )
    } catch (re) {
      if (re instanceof RecognitionException) {
        localctx.exception = re
        this._errHandler.reportError(this, re)
        this._errHandler.recover(this, re)
      } else {
        throw re
      }
    } finally {
      this.exitRule()
    }
    return localctx
  }

  statement(_p?: number) {
    if (_p === undefined) {
      _p = 0
    }
    const _parentctx = this._ctx
    const _parentState = this.state
    let localctx = new StatementContext(this, this._ctx, _parentState)
    let _prevctx = localctx
    const _startState = 4
    this.enterRecursionRule(localctx, 4, DSLParser.RULE_statement, _p)
    try {
      this.enterOuterAlt(localctx, 1)
      this.state = 142
      this._errHandler.sync(this)
      switch (this._interp.adaptivePredict(this._input, 8, this._ctx)) {
        case 1:
          this.state = 103
          this.block()
          this.state = 105
          this._errHandler.sync(this)
          const la_ = this._interp.adaptivePredict(this._input, 1, this._ctx)
          if (la_ === 1) {
            this.state = 104
            this.eos()
          }
          break

        case 2:
          this.state = 107
          this.useStatement()
          break

        case 3:
          this.state = 108
          this.functionDeclaration()
          this.state = 109
          this.eos()
          break

        case 4:
          this.state = 111
          this.variableStatement()
          this.state = 113
          this._errHandler.sync(this)
          const la_4 = this._interp.adaptivePredict(this._input, 2, this._ctx)
          if (la_4 === 1) {
            this.state = 112
            this.eos()
          }
          break

        case 5:
          this.state = 115
          this.assignListStatement()
          this.state = 117
          this._errHandler.sync(this)
          const la_5 = this._interp.adaptivePredict(this._input, 3, this._ctx)
          if (la_5 === 1) {
            this.state = 116
            this.eos()
          }
          break

        case 6:
          this.state = 119
          this.ifStatement()
          break

        case 7:
          this.state = 120
          this.forStatements()
          break

        case 8:
          this.state = 121
          this.whileStatement()
          break

        case 9:
          this.state = 122
          this.match(DSLParser.Break)
          this.state = 124
          this._errHandler.sync(this)
          const la_9 = this._interp.adaptivePredict(this._input, 4, this._ctx)
          if (la_9 === 1) {
            this.state = 123
            this.eos()
          }
          break

        case 10:
          this.state = 126
          this.match(DSLParser.Continue)
          this.state = 128
          this._errHandler.sync(this)
          const la_10 = this._interp.adaptivePredict(this._input, 5, this._ctx)
          if (la_10 === 1) {
            this.state = 127
            this.eos()
          }
          break

        case 11:
          this.state = 130
          this.returnStatement()
          break

        case 12:
          this.state = 131
          this.expression(0)
          this.state = 133
          this._errHandler.sync(this)
          if (this._interp.adaptivePredict(this._input, 6, this._ctx) === 1) {
            this.state = 132
            this.eos()
          }
          break

        case 13:
          this.state = 135
          this.tryStatement()
          break

        case 14:
          this.state = 136
          this.match(DSLParser.Comment)
          break

        case 15:
          this.state = 138
          this._errHandler.sync(this)
          let _alt = 1
          do {
            switch (_alt) {
              case 1:
                this.state = 137
                this.eos()
                break
              default:
                throw new NoViableAltException(this)
            }
            this.state = 140
            this._errHandler.sync(this)
            _alt = this._interp.adaptivePredict(this._input, 7, this._ctx)
          } while (_alt != 2 && _alt != ATN.INVALID_ALT_NUMBER)
          break
      }
      this._ctx.stop = this._input.LT(-1)
      this.state = 148
      this._errHandler.sync(this)
      let _alt = this._interp.adaptivePredict(this._input, 9, this._ctx)
      while (_alt != 2 && _alt != ATN.INVALID_ALT_NUMBER) {
        if (_alt === 1) {
          if (this._parseListeners !== null) {
            this.triggerExitRuleEvent()
          }
          _prevctx = localctx
          localctx = new StatementContext(this, _parentctx, _parentState)
          this.pushNewRecursionContext(localctx, _startState, DSLParser.RULE_statement)
          this.state = 144
          if (!this.precpred(this._ctx, 3)) {
            throw new FailedPredicateException(this, 'this.precpred(this._ctx, 3)', '')
          }
          this.state = 145
          this.match(DSLParser.Comment)
        }
        this.state = 150
        this._errHandler.sync(this)
        _alt = this._interp.adaptivePredict(this._input, 9, this._ctx)
      }
    } catch (error) {
      if (error instanceof RecognitionException) {
        localctx.exception = error
        this._errHandler.reportError(this, error)
        this._errHandler.recover(this, error)
      } else {
        throw error
      }
    } finally {
      this.unrollRecursionContexts(_parentctx)
    }
    return localctx
  }

  useStatement() {
    const localctx = new UseStatementContext(this, this._ctx, this.state)
    this.enterRule(localctx, 6, DSLParser.RULE_useStatement)
    let _la = 0 // Token type
    try {
      this.enterOuterAlt(localctx, 1)
      this.state = 151
      this.match(DSLParser.T__0)
      this.state = 152
      this.identifierExpress()
      this.state = 164
      this._errHandler.sync(this)
      const la_ = this._interp.adaptivePredict(this._input, 11, this._ctx)
      if (la_ === 1) {
        this.state = 153
        this.match(DSLParser.Dot)
        this.state = 154
        this.match(DSLParser.OpenBrace)
        this.state = 155
        this.match(DSLParser.Identifier)
        this.state = 160
        this._errHandler.sync(this)
        _la = this._input.LA(1)
        while (_la === DSLParser.T__1) {
          this.state = 156
          this.match(DSLParser.T__1)
          this.state = 157
          this.match(DSLParser.Identifier)
          this.state = 162
          this._errHandler.sync(this)
          _la = this._input.LA(1)
        }
        this.state = 163
        this.match(DSLParser.CloseBrace)
      }
      this.state = 168
      this._errHandler.sync(this)
      _la = this._input.LA(1)
      if (_la === DSLParser.Dot) {
        this.state = 166
        this.match(DSLParser.Dot)
        this.state = 167
        this.match(DSLParser.Multiply)
      }

      this.state = 170
      this.match(DSLParser.SemiColon)
    } catch (re) {
      if (re instanceof RecognitionException) {
        localctx.exception = re
        this._errHandler.reportError(this, re)
        this._errHandler.recover(this, re)
      } else {
        throw re
      }
    } finally {
      this.exitRule()
    }
    return localctx
  }

  block() {
    const localctx = new BlockContext(this, this._ctx, this.state)
    this.enterRule(localctx, 8, DSLParser.RULE_block)
    let _la = 0 // Token type
    try {
      this.enterOuterAlt(localctx, 1)
      this.state = 172
      this.match(DSLParser.OpenBrace)
      this.state = 174
      this._errHandler.sync(this)
      const la_ = this._interp.adaptivePredict(this._input, 13, this._ctx)
      if (la_ === 1) {
        this.state = 173
        this.eos()
      }
      this.state = 177
      this._errHandler.sync(this)
      let _alt = 1
      do {
        switch (_alt) {
          case 1:
            this.state = 176
            this.statement(0)
            break
          default:
            throw new NoViableAltException(this)
        }
        this.state = 179
        this._errHandler.sync(this)
        _alt = this._interp.adaptivePredict(this._input, 14, this._ctx)
      } while (_alt != 2 && _alt != ATN.INVALID_ALT_NUMBER)
      this.state = 184
      this._errHandler.sync(this)
      _la = this._input.LA(1)
      while (_la === DSLParser.NewLine || _la === DSLParser.SemiColon) {
        this.state = 181
        this.eos()
        this.state = 186
        this._errHandler.sync(this)
        _la = this._input.LA(1)
      }
      this.state = 187
      this.match(DSLParser.CloseBrace)
    } catch (re) {
      if (re instanceof RecognitionException) {
        localctx.exception = re
        this._errHandler.reportError(this, re)
        this._errHandler.recover(this, re)
      } else {
        throw re
      }
    } finally {
      this.exitRule()
    }
    return localctx
  }

  functionDeclaration() {
    const localctx = new FunctionDeclarationContext(this, this._ctx, this.state)
    this.enterRule(localctx, 10, DSLParser.RULE_functionDeclaration)
    let _la = 0 // Token type
    try {
      this.enterOuterAlt(localctx, 1)
      this.state = 189
      this.match(DSLParser.FunctionKeyword)
      this.state = 191
      this._errHandler.sync(this)
      _la = this._input.LA(1)
      if (_la === DSLParser.Identifier) {
        this.state = 190
        this.match(DSLParser.Identifier)
      }

      this.state = 193
      this.match(DSLParser.OpenParen)
      this.state = 194
      this.functionParameterList()
      this.state = 195
      this.match(DSLParser.CloseParen)
      this.state = 196
      this.match(DSLParser.OpenBrace)
      this.state = 197
      this.functionBody()
      this.state = 198
      this.match(DSLParser.CloseBrace)
    } catch (re) {
      if (re instanceof RecognitionException) {
        localctx.exception = re
        this._errHandler.reportError(this, re)
        this._errHandler.recover(this, re)
      } else {
        throw re
      }
    } finally {
      this.exitRule()
    }
    return localctx
  }

  functionParameterList() {
    const localctx = new FunctionParameterListContext(this, this._ctx, this.state)
    this.enterRule(localctx, 12, DSLParser.RULE_functionParameterList)
    let _la = 0 // Token type
    try {
      this.enterOuterAlt(localctx, 1)
      this.state = 214
      this._errHandler.sync(this)
      _la = this._input.LA(1)
      if (_la === DSLParser.BitAnd || _la === DSLParser.Identifier) {
        this.state = 201
        this._errHandler.sync(this)
        _la = this._input.LA(1)
        if (_la === DSLParser.BitAnd) {
          this.state = 200
          this.match(DSLParser.BitAnd)
        }

        this.state = 203
        this.match(DSLParser.Identifier)
        this.state = 211
        this._errHandler.sync(this)
        _la = this._input.LA(1)
        while (_la === DSLParser.T__1) {
          this.state = 204
          this.match(DSLParser.T__1)
          this.state = 206
          this._errHandler.sync(this)
          _la = this._input.LA(1)
          if (_la === DSLParser.BitAnd) {
            this.state = 205
            this.match(DSLParser.BitAnd)
          }

          this.state = 208
          this.match(DSLParser.Identifier)
          this.state = 213
          this._errHandler.sync(this)
          _la = this._input.LA(1)
        }
      }
    } catch (re) {
      if (re instanceof RecognitionException) {
        localctx.exception = re
        this._errHandler.reportError(this, re)
        this._errHandler.recover(this, re)
      } else {
        throw re
      }
    } finally {
      this.exitRule()
    }
    return localctx
  }

  functionBody() {
    const localctx = new FunctionBodyContext(this, this._ctx, this.state)
    this.enterRule(localctx, 14, DSLParser.RULE_functionBody)
    try {
      this.enterOuterAlt(localctx, 1)
      this.state = 217
      this._errHandler.sync(this)
      const la_ = this._interp.adaptivePredict(this._input, 21, this._ctx)
      if (la_ === 1) {
        this.state = 216
        this.eos()
      }
      this.state = 222
      this._errHandler.sync(this)
      let _alt = this._interp.adaptivePredict(this._input, 22, this._ctx)
      while (_alt != 2 && _alt != ATN.INVALID_ALT_NUMBER) {
        if (_alt === 1) {
          this.state = 219
          this.statement(0)
        }
        this.state = 224
        this._errHandler.sync(this)
        _alt = this._interp.adaptivePredict(this._input, 22, this._ctx)
      }

      this.state = 226
      this._errHandler.sync(this)
      const _la = this._input.LA(1)
      if (_la === DSLParser.ReturnKeyword) {
        this.state = 225
        this.returnStatement()
      }
    } catch (re) {
      if (re instanceof RecognitionException) {
        localctx.exception = re
        this._errHandler.reportError(this, re)
        this._errHandler.recover(this, re)
      } else {
        throw re
      }
    } finally {
      this.exitRule()
    }
    return localctx
  }

  variableStatement() {
    const localctx = new VariableStatementContext(this, this._ctx, this.state)
    this.enterRule(localctx, 16, DSLParser.RULE_variableStatement)
    try {
      this.enterOuterAlt(localctx, 1)
      this.state = 228
      this.match(DSLParser.VariablePrefix)
      this.state = 229
      this.variableDeclarationList()
    } catch (re) {
      if (re instanceof RecognitionException) {
        localctx.exception = re
        this._errHandler.reportError(this, re)
        this._errHandler.recover(this, re)
      } else {
        throw re
      }
    } finally {
      this.exitRule()
    }
    return localctx
  }

  variableDeclarationList() {
    const localctx = new VariableDeclarationListContext(this, this._ctx, this.state)
    this.enterRule(localctx, 18, DSLParser.RULE_variableDeclarationList)
    try {
      this.enterOuterAlt(localctx, 1)
      this.state = 231
      this.variableDeclaration()
      this.state = 236
      this._errHandler.sync(this)
      let _alt = this._interp.adaptivePredict(this._input, 24, this._ctx)
      while (_alt != 2 && _alt != ATN.INVALID_ALT_NUMBER) {
        if (_alt === 1) {
          this.state = 232
          this.match(DSLParser.T__1)
          this.state = 233
          this.variableDeclaration()
        }
        this.state = 238
        this._errHandler.sync(this)
        _alt = this._interp.adaptivePredict(this._input, 24, this._ctx)
      }
    } catch (re) {
      if (re instanceof RecognitionException) {
        localctx.exception = re
        this._errHandler.reportError(this, re)
        this._errHandler.recover(this, re)
      } else {
        throw re
      }
    } finally {
      this.exitRule()
    }
    return localctx
  }

  variableDeclaration() {
    const localctx = new VariableDeclarationContext(this, this._ctx, this.state)
    this.enterRule(localctx, 20, DSLParser.RULE_variableDeclaration)
    try {
      this.state = 251
      this._errHandler.sync(this)
      const la_ = this._interp.adaptivePredict(this._input, 26, this._ctx)
      switch (la_) {
        case 1:
          this.enterOuterAlt(localctx, 1)
          this.state = 239
          this.identifierExpress()
          this.state = 240
          this.match(DSLParser.RegularAssign)
          break

        case 2:
          this.enterOuterAlt(localctx, 2)
          this.state = 242
          this.identifierExpress()
          this.state = 245
          this._errHandler.sync(this)
          const la_ = this._interp.adaptivePredict(this._input, 25, this._ctx)
          if (la_ === 1) {
            this.state = 243
            this.match(DSLParser.Assignment)
            this.state = 244
            this.expression(0)
          }
          break

        case 3:
          this.enterOuterAlt(localctx, 3)
          this.state = 247
          this.identifierExpress()
          this.state = 248
          this.match(DSLParser.Assignment)
          this.state = 249
          this.statement(0)
          break
      }
    } catch (re) {
      if (re instanceof RecognitionException) {
        localctx.exception = re
        this._errHandler.reportError(this, re)
        this._errHandler.recover(this, re)
      } else {
        throw re
      }
    } finally {
      this.exitRule()
    }
    return localctx
  }

  assignListStatement() {
    const localctx = new AssignListStatementContext(this, this._ctx, this.state)
    this.enterRule(localctx, 22, DSLParser.RULE_assignListStatement)
    try {
      this.enterOuterAlt(localctx, 1)
      this.state = 253
      this.assignStatement()
      this.state = 258
      this._errHandler.sync(this)
      let _alt = this._interp.adaptivePredict(this._input, 27, this._ctx)
      while (_alt != 2 && _alt != ATN.INVALID_ALT_NUMBER) {
        if (_alt === 1) {
          this.state = 254
          this.match(DSLParser.T__1)
          this.state = 255
          this.assignStatement()
        }
        this.state = 260
        this._errHandler.sync(this)
        _alt = this._interp.adaptivePredict(this._input, 27, this._ctx)
      }
    } catch (re) {
      if (re instanceof RecognitionException) {
        localctx.exception = re
        this._errHandler.reportError(this, re)
        this._errHandler.recover(this, re)
      } else {
        throw re
      }
    } finally {
      this.exitRule()
    }
    return localctx
  }

  assignStatement() {
    const localctx = new AssignStatementContext(this, this._ctx, this.state)
    this.enterRule(localctx, 24, DSLParser.RULE_assignStatement)
    try {
      this.state = 274
      this._errHandler.sync(this)
      const la_ = this._interp.adaptivePredict(this._input, 29, this._ctx)
      switch (la_) {
        case 1:
          this.enterOuterAlt(localctx, 1)
          this.state = 261
          this.identifierExpress()
          this.state = 262
          this.match(DSLParser.RegularAssign)
          break

        case 2:
          this.enterOuterAlt(localctx, 2)
          this.state = 264
          this.identifierExpress()
          this.state = 265
          this.match(DSLParser.Assignment)
          this.state = 268
          this._errHandler.sync(this)
          switch (this._input.LA(1)) {
            case DSLParser.T__3:
            case DSLParser.T__6:
            case DSLParser.RegularRelationLeftExpression:
            case DSLParser.RegularRelationRightExpression:
            case DSLParser.Not:
            case DSLParser.BitNot:
            case DSLParser.Number:
            case DSLParser.NumberHex:
            case DSLParser.BooleanTrue:
            case DSLParser.BooleanFalse:
            case DSLParser.String:
            case DSLParser.OpenBracket:
            case DSLParser.OpenParen:
            case DSLParser.Plus:
            case DSLParser.Minus:
            case DSLParser.PlusPlus:
            case DSLParser.MinusMinus:
            case DSLParser.Identifier:
              this.state = 266
              this.expression(0)
              break
            case DSLParser.FunctionKeyword:
              this.state = 267
              this.functionDeclaration()
              break
            default:
              throw new NoViableAltException(this)
          }
          break

        case 3:
          this.enterOuterAlt(localctx, 3)
          this.state = 270
          this.rightPlusPlus()
          break

        case 4:
          this.enterOuterAlt(localctx, 4)
          this.state = 271
          this.rightMinusMinus()
          break

        case 5:
          this.enterOuterAlt(localctx, 5)
          this.state = 272
          this.leftPlusPlus()
          break

        case 6:
          this.enterOuterAlt(localctx, 6)
          this.state = 273
          this.leftMinusMinus()
          break
      }
    } catch (re) {
      if (re instanceof RecognitionException) {
        localctx.exception = re
        this._errHandler.reportError(this, re)
        this._errHandler.recover(this, re)
      } else {
        throw re
      }
    } finally {
      this.exitRule()
    }
    return localctx
  }

  ifStatement() {
    const localctx = new IfStatementContext(this, this._ctx, this.state)
    this.enterRule(localctx, 26, DSLParser.RULE_ifStatement)
    let _la = 0 // Token type
    try {
      this.enterOuterAlt(localctx, 1)
      this.state = 276
      this.match(DSLParser.IfKeyword)
      this.state = 278
      this._errHandler.sync(this)
      let la_ = this._interp.adaptivePredict(this._input, 30, this._ctx)
      if (la_ === 1) {
        this.state = 277
        this.match(DSLParser.OpenParen)
      }
      this.state = 280
      this.expression(0)
      this.state = 282
      this._errHandler.sync(this)
      _la = this._input.LA(1)
      if (_la === DSLParser.CloseParen) {
        this.state = 281
        this.match(DSLParser.CloseParen)
      }

      this.state = 284
      this.statement(0)
      this.state = 288
      this._errHandler.sync(this)
      let _alt = this._interp.adaptivePredict(this._input, 32, this._ctx)
      while (_alt != 2 && _alt != ATN.INVALID_ALT_NUMBER) {
        if (_alt === 1) {
          this.state = 285
          this.elseIfStatement()
        }
        this.state = 290
        this._errHandler.sync(this)
        _alt = this._interp.adaptivePredict(this._input, 32, this._ctx)
      }

      this.state = 292
      this._errHandler.sync(this)
      la_ = this._interp.adaptivePredict(this._input, 33, this._ctx)
      if (la_ === 1) {
        this.state = 291
        this.elseStatement()
      }
    } catch (re) {
      if (re instanceof RecognitionException) {
        localctx.exception = re
        this._errHandler.reportError(this, re)
        this._errHandler.recover(this, re)
      } else {
        throw re
      }
    } finally {
      this.exitRule()
    }
    return localctx
  }

  elseIfStatement() {
    const localctx = new ElseIfStatementContext(this, this._ctx, this.state)
    this.enterRule(localctx, 28, DSLParser.RULE_elseIfStatement)
    let _la = 0 // Token type
    try {
      this.enterOuterAlt(localctx, 1)
      this.state = 297
      this._errHandler.sync(this)
      switch (this._input.LA(1)) {
        case DSLParser.ElseIfKeyword:
          this.state = 294
          this.match(DSLParser.ElseIfKeyword)
          break
        case DSLParser.ElseKeyword:
          this.state = 295
          this.match(DSLParser.ElseKeyword)
          this.state = 296
          this.match(DSLParser.IfKeyword)
          break
        default:
          throw new NoViableAltException(this)
      }
      this.state = 300
      this._errHandler.sync(this)
      const la_ = this._interp.adaptivePredict(this._input, 35, this._ctx)
      if (la_ === 1) {
        this.state = 299
        this.match(DSLParser.OpenParen)
      }
      this.state = 302
      this.expression(0)
      this.state = 304
      this._errHandler.sync(this)
      _la = this._input.LA(1)
      if (_la === DSLParser.CloseParen) {
        this.state = 303
        this.match(DSLParser.CloseParen)
      }

      this.state = 306
      this.statement(0)
    } catch (re) {
      if (re instanceof RecognitionException) {
        localctx.exception = re
        this._errHandler.reportError(this, re)
        this._errHandler.recover(this, re)
      } else {
        throw re
      }
    } finally {
      this.exitRule()
    }
    return localctx
  }

  elseStatement() {
    const localctx = new ElseStatementContext(this, this._ctx, this.state)
    this.enterRule(localctx, 30, DSLParser.RULE_elseStatement)
    try {
      this.enterOuterAlt(localctx, 1)
      this.state = 308
      this.match(DSLParser.ElseKeyword)
      this.state = 309
      this.statement(0)
    } catch (re) {
      if (re instanceof RecognitionException) {
        localctx.exception = re
        this._errHandler.reportError(this, re)
        this._errHandler.recover(this, re)
      } else {
        throw re
      }
    } finally {
      this.exitRule()
    }
    return localctx
  }

  forStatements() {
    const localctx = new ForStatementsContext(this, this._ctx, this.state)
    this.enterRule(localctx, 32, DSLParser.RULE_forStatements)
    try {
      this.state = 314
      this._errHandler.sync(this)
      const la_ = this._interp.adaptivePredict(this._input, 37, this._ctx)
      switch (la_) {
        case 1:
          this.enterOuterAlt(localctx, 1)
          this.state = 311
          this.forStatement()
          break

        case 2:
          this.enterOuterAlt(localctx, 2)
          this.state = 312
          this.forInStatement()
          break

        case 3:
          this.enterOuterAlt(localctx, 3)
          this.state = 313
          this.forInRangeStatement()
          break
      }
    } catch (re) {
      if (re instanceof RecognitionException) {
        localctx.exception = re
        this._errHandler.reportError(this, re)
        this._errHandler.recover(this, re)
      } else {
        throw re
      }
    } finally {
      this.exitRule()
    }
    return localctx
  }

  forStatement() {
    const localctx = new ForStatementContext(this, this._ctx, this.state)
    this.enterRule(localctx, 34, DSLParser.RULE_forStatement)
    try {
      this.enterOuterAlt(localctx, 1)
      this.state = 316
      this.match(DSLParser.For)
      this.state = 317
      this.match(DSLParser.OpenParen)
      this.state = 318
      this.forStatementParametar()
      this.state = 319
      this.match(DSLParser.CloseParen)
      this.state = 320
      this.forStatementBody()
    } catch (re) {
      if (re instanceof RecognitionException) {
        localctx.exception = re
        this._errHandler.reportError(this, re)
        this._errHandler.recover(this, re)
      } else {
        throw re
      }
    } finally {
      this.exitRule()
    }
    return localctx
  }

  forStatementParametar() {
    const localctx = new ForStatementParametarContext(this, this._ctx, this.state)
    this.enterRule(localctx, 36, DSLParser.RULE_forStatementParametar)
    let _la = 0 // Token type
    try {
      this.enterOuterAlt(localctx, 1)
      this.state = 324
      this._errHandler.sync(this)
      switch (this._input.LA(1)) {
        case DSLParser.VariablePrefix:
          this.state = 322
          this.variableStatement()
          break
        case DSLParser.PlusPlus:
        case DSLParser.MinusMinus:
        case DSLParser.Identifier:
          this.state = 323
          this.assignListStatement()
          break
        case DSLParser.SemiColon:
          break
        default:
          break
      }
      this.state = 326
      this.match(DSLParser.SemiColon)
      this.state = 328
      this._errHandler.sync(this)
      _la = this._input.LA(1)
      if (
        (((_la - 4) & ~0x1f) == 0 &&
          ((1 << (_la - 4)) &
            ((1 << (DSLParser.T__3 - 4)) |
              (1 << (DSLParser.T__6 - 4)) |
              (1 << (DSLParser.RegularRelationLeftExpression - 4)) |
              (1 << (DSLParser.RegularRelationRightExpression - 4)) |
              (1 << (DSLParser.Not - 4)) |
              (1 << (DSLParser.BitNot - 4)))) !==
            0) ||
        (((_la - 38) & ~0x1f) == 0 &&
          ((1 << (_la - 38)) &
            ((1 << (DSLParser.Number - 38)) |
              (1 << (DSLParser.NumberHex - 38)) |
              (1 << (DSLParser.BooleanTrue - 38)) |
              (1 << (DSLParser.BooleanFalse - 38)) |
              (1 << (DSLParser.String - 38)) |
              (1 << (DSLParser.OpenBracket - 38)) |
              (1 << (DSLParser.OpenParen - 38)) |
              (1 << (DSLParser.Plus - 38)) |
              (1 << (DSLParser.Minus - 38)) |
              (1 << (DSLParser.PlusPlus - 38)) |
              (1 << (DSLParser.MinusMinus - 38)) |
              (1 << (DSLParser.Identifier - 38)))) !==
            0)
      ) {
        this.state = 327
        this.expression(0)
      }

      this.state = 330
      this.match(DSLParser.SemiColon)
      this.state = 332
      this._errHandler.sync(this)
      _la = this._input.LA(1)
      if (
        ((_la - 67) & ~0x1f) == 0 &&
        ((1 << (_la - 67)) &
          ((1 << (DSLParser.PlusPlus - 67)) |
            (1 << (DSLParser.MinusMinus - 67)) |
            (1 << (DSLParser.Identifier - 67)))) !==
          0
      ) {
        this.state = 331
        this.assignListStatement()
      }
    } catch (re) {
      if (re instanceof RecognitionException) {
        localctx.exception = re
        this._errHandler.reportError(this, re)
        this._errHandler.recover(this, re)
      } else {
        throw re
      }
    } finally {
      this.exitRule()
    }
    return localctx
  }

  forStatementBody() {
    const localctx = new ForStatementBodyContext(this, this._ctx, this.state)
    this.enterRule(localctx, 38, DSLParser.RULE_forStatementBody)
    try {
      this.enterOuterAlt(localctx, 1)
      this.state = 334
      this.statement(0)
    } catch (re) {
      if (re instanceof RecognitionException) {
        localctx.exception = re
        this._errHandler.reportError(this, re)
        this._errHandler.recover(this, re)
      } else {
        throw re
      }
    } finally {
      this.exitRule()
    }
    return localctx
  }

  forInStatement() {
    const localctx = new ForInStatementContext(this, this._ctx, this.state)
    this.enterRule(localctx, 40, DSLParser.RULE_forInStatement)
    let _la = 0 // Token type
    try {
      this.enterOuterAlt(localctx, 1)
      this.state = 336
      this.match(DSLParser.For)
      this.state = 337
      this.match(DSLParser.Identifier)
      this.state = 340
      this._errHandler.sync(this)
      _la = this._input.LA(1)
      if (_la === DSLParser.T__1) {
        this.state = 338
        this.match(DSLParser.T__1)
        this.state = 339
        this.match(DSLParser.Identifier)
      }

      this.state = 342
      this.match(DSLParser.T__2)
      this.state = 343
      this.identifierExpress()
      this.state = 344
      this.forStatementBody()
    } catch (re) {
      if (re instanceof RecognitionException) {
        localctx.exception = re
        this._errHandler.reportError(this, re)
        this._errHandler.recover(this, re)
      } else {
        throw re
      }
    } finally {
      this.exitRule()
    }
    return localctx
  }

  forInRangeStatement() {
    const localctx = new ForInRangeStatementContext(this, this._ctx, this.state)
    this.enterRule(localctx, 42, DSLParser.RULE_forInRangeStatement)
    try {
      this.enterOuterAlt(localctx, 1)
      this.state = 346
      this.match(DSLParser.For)
      this.state = 347
      this.match(DSLParser.Identifier)
      this.state = 348
      this.match(DSLParser.T__2)
      this.state = 349
      this.rangeFunction()
      this.state = 350
      this.forStatementBody()
    } catch (re) {
      if (re instanceof RecognitionException) {
        localctx.exception = re
        this._errHandler.reportError(this, re)
        this._errHandler.recover(this, re)
      } else {
        throw re
      }
    } finally {
      this.exitRule()
    }
    return localctx
  }

  rangeFunction() {
    const localctx = new RangeFunctionContext(this, this._ctx, this.state)
    this.enterRule(localctx, 44, DSLParser.RULE_rangeFunction)
    let _la = 0 // Token type
    try {
      this.enterOuterAlt(localctx, 1)
      this.state = 352
      this.match(DSLParser.T__3)
      this.state = 353
      this.match(DSLParser.OpenParen)
      this.state = 355
      this._errHandler.sync(this)
      _la = this._input.LA(1)
      if (_la === DSLParser.Minus) {
        this.state = 354
        this.match(DSLParser.Minus)
      }

      this.state = 357
      this.match(DSLParser.Number)
      this.state = 358
      this.match(DSLParser.T__1)
      this.state = 361
      this._errHandler.sync(this)
      switch (this._input.LA(1)) {
        case DSLParser.Number:
          this.state = 359
          this.match(DSLParser.Number)
          break
        case DSLParser.Identifier:
          this.state = 360
          this.identifierExpress()
          break
        default:
          throw new NoViableAltException(this)
      }
      this.state = 365
      this._errHandler.sync(this)
      _la = this._input.LA(1)
      if (_la === DSLParser.T__1) {
        this.state = 363
        this.match(DSLParser.T__1)
        this.state = 364
        this.match(DSLParser.Number)
      }

      this.state = 367
      this.match(DSLParser.CloseParen)
    } catch (re) {
      if (re instanceof RecognitionException) {
        localctx.exception = re
        this._errHandler.reportError(this, re)
        this._errHandler.recover(this, re)
      } else {
        throw re
      }
    } finally {
      this.exitRule()
    }
    return localctx
  }

  whileStatement() {
    const localctx = new WhileStatementContext(this, this._ctx, this.state)
    this.enterRule(localctx, 46, DSLParser.RULE_whileStatement)
    let _la = 0 // Token type
    try {
      this.enterOuterAlt(localctx, 1)
      this.state = 369
      this.match(DSLParser.While)
      this.state = 371
      this._errHandler.sync(this)
      const la_ = this._interp.adaptivePredict(this._input, 45, this._ctx)
      if (la_ === 1) {
        this.state = 370
        this.match(DSLParser.OpenParen)
      }
      this.state = 373
      this.whileStatementParametar()
      this.state = 375
      this._errHandler.sync(this)
      _la = this._input.LA(1)
      if (_la === DSLParser.CloseParen) {
        this.state = 374
        this.match(DSLParser.CloseParen)
      }

      this.state = 377
      this.whileStatementBody()
    } catch (re) {
      if (re instanceof RecognitionException) {
        localctx.exception = re
        this._errHandler.reportError(this, re)
        this._errHandler.recover(this, re)
      } else {
        throw re
      }
    } finally {
      this.exitRule()
    }
    return localctx
  }

  whileStatementParametar() {
    const localctx = new WhileStatementParametarContext(this, this._ctx, this.state)
    this.enterRule(localctx, 48, DSLParser.RULE_whileStatementParametar)
    try {
      this.enterOuterAlt(localctx, 1)
      this.state = 379
      this.expression(0)
    } catch (re) {
      if (re instanceof RecognitionException) {
        localctx.exception = re
        this._errHandler.reportError(this, re)
        this._errHandler.recover(this, re)
      } else {
        throw re
      }
    } finally {
      this.exitRule()
    }
    return localctx
  }

  whileStatementBody() {
    const localctx = new WhileStatementBodyContext(this, this._ctx, this.state)
    this.enterRule(localctx, 50, DSLParser.RULE_whileStatementBody)
    try {
      this.enterOuterAlt(localctx, 1)
      this.state = 381
      this.statement(0)
    } catch (re) {
      if (re instanceof RecognitionException) {
        localctx.exception = re
        this._errHandler.reportError(this, re)
        this._errHandler.recover(this, re)
      } else {
        throw re
      }
    } finally {
      this.exitRule()
    }
    return localctx
  }

  tryStatement() {
    const localctx = new TryStatementContext(this, this._ctx, this.state)
    this.enterRule(localctx, 52, DSLParser.RULE_tryStatement)
    let _la = 0 // Token type
    try {
      this.enterOuterAlt(localctx, 1)
      this.state = 383
      this.match(DSLParser.T__4)
      this.state = 384
      this.statement(0)
      this.state = 405
      this._errHandler.sync(this)
      let _alt = this._interp.adaptivePredict(this._input, 50, this._ctx)
      while (_alt != 2 && _alt != ATN.INVALID_ALT_NUMBER) {
        if (_alt === 1) {
          this.state = 385
          this.match(DSLParser.T__5)
          this.state = 386
          this.match(DSLParser.OpenParen)
          this.state = 387
          this.match(DSLParser.Identifier)
          this.state = 389
          this._errHandler.sync(this)
          _la = this._input.LA(1)
          if (_la === DSLParser.Identifier) {
            this.state = 388
            this.match(DSLParser.Identifier)
          }

          this.state = 398
          this._errHandler.sync(this)
          _la = this._input.LA(1)
          while (_la === DSLParser.BitOr) {
            this.state = 391
            this.match(DSLParser.BitOr)
            this.state = 392
            this.match(DSLParser.Identifier)
            this.state = 394
            this._errHandler.sync(this)
            _la = this._input.LA(1)
            if (_la === DSLParser.Identifier) {
              this.state = 393
              this.match(DSLParser.Identifier)
            }

            this.state = 400
            this._errHandler.sync(this)
            _la = this._input.LA(1)
          }
          this.state = 401
          this.match(DSLParser.CloseParen)
          this.state = 402
          this.statement(0)
        }
        this.state = 407
        this._errHandler.sync(this)
        _alt = this._interp.adaptivePredict(this._input, 50, this._ctx)
      }
    } catch (re) {
      if (re instanceof RecognitionException) {
        localctx.exception = re
        this._errHandler.reportError(this, re)
        this._errHandler.recover(this, re)
      } else {
        throw re
      }
    } finally {
      this.exitRule()
    }
    return localctx
  }

  returnStatement() {
    const localctx = new ReturnStatementContext(this, this._ctx, this.state)
    this.enterRule(localctx, 54, DSLParser.RULE_returnStatement)
    try {
      this.enterOuterAlt(localctx, 1)
      this.state = 408
      this.match(DSLParser.ReturnKeyword)
      this.state = 411
      this._errHandler.sync(this)
      const la_ = this._interp.adaptivePredict(this._input, 51, this._ctx)
      if (la_ === 1) {
        this.state = 409
        this.expression(0)
      } else if (la_ === 2) {
        this.state = 410
        this.functionDeclaration()
      }
      this.state = 416
      this._errHandler.sync(this)
      let _alt = this._interp.adaptivePredict(this._input, 52, this._ctx)
      while (_alt != 2 && _alt != ATN.INVALID_ALT_NUMBER) {
        if (_alt === 1) {
          this.state = 413
          this.eos()
        }
        this.state = 418
        this._errHandler.sync(this)
        _alt = this._interp.adaptivePredict(this._input, 52, this._ctx)
      }
    } catch (re) {
      if (re instanceof RecognitionException) {
        localctx.exception = re
        this._errHandler.reportError(this, re)
        this._errHandler.recover(this, re)
      } else {
        throw re
      }
    } finally {
      this.exitRule()
    }
    return localctx
  }

  array() {
    const localctx = new ArrayContext(this, this._ctx, this.state)
    this.enterRule(localctx, 56, DSLParser.RULE_array)
    let _la = 0 // Token type
    try {
      this.enterOuterAlt(localctx, 1)
      this.state = 419
      this.match(DSLParser.OpenBracket)
      this.state = 420
      this.expression(0)
      this.state = 425
      this._errHandler.sync(this)
      _la = this._input.LA(1)
      while (_la === DSLParser.T__1) {
        this.state = 421
        this.match(DSLParser.T__1)
        this.state = 422
        this.expression(0)
        this.state = 427
        this._errHandler.sync(this)
        _la = this._input.LA(1)
      }
      this.state = 428
      this.match(DSLParser.CloseBracket)
    } catch (re) {
      if (re instanceof RecognitionException) {
        localctx.exception = re
        this._errHandler.reportError(this, re)
        this._errHandler.recover(this, re)
      } else {
        throw re
      }
    } finally {
      this.exitRule()
    }
    return localctx
  }

  expression(_p: number) {
    if (_p === undefined) {
      _p = 0
    }
    const _parentctx = this._ctx
    const _parentState = this.state
    let localctx = new ExpressionContext(this, this._ctx, _parentState)
    const _startState = 58
    this.enterRecursionRule(localctx, 58, DSLParser.RULE_expression, _p)
    let _la = 0 // Token type
    try {
      this.enterOuterAlt(localctx, 1)
      this.state = 451
      this._errHandler.sync(this)
      const la_ = this._interp.adaptivePredict(this._input, 54, this._ctx)
      switch (la_) {
        case 1:
          this.state = 431
          this.literal()
          break

        case 2:
          this.state = 432
          this.lambdaExpression()
          break

        case 3:
          this.state = 433
          this.functionInvoikeExpression()
          break

        case 4:
          this.state = 434
          this.plusExpression()
          break

        case 5:
          this.state = 435
          this.minusExpression()
          break

        case 6:
          this.state = 436
          this.rightPlusPlus()
          break

        case 7:
          this.state = 437
          this.rightMinusMinus()
          break

        case 8:
          this.state = 438
          this.leftPlusPlus()
          break

        case 9:
          this.state = 439
          this.leftMinusMinus()
          break

        case 10:
          this.state = 440
          this.match(DSLParser.Not)
          this.state = 441
          this.expression(22)
          break

        case 11:
          this.state = 442
          this.match(DSLParser.BitNot)
          this.state = 443
          this.expression(21)
          break

        case 12:
          this.state = 444
          this.wrapperExpression()
          break

        case 13:
          this.state = 445
          this.identifierExpress()
          break

        case 14:
          this.state = 446
          this.array()
          break

        case 15:
          this.state = 447
          this.rangeFunction()
          break

        case 16:
          this.state = 448
          this.match(DSLParser.RegularRelationLeftExpression)
          break

        case 17:
          this.state = 449
          this.match(DSLParser.RegularRelationRightExpression)
          break

        case 18:
          this.state = 450
          this.assignStatement()
          break
      }
      this._ctx.stop = this._input.LT(-1)
      this.state = 506
      this._errHandler.sync(this)
      let _alt = this._interp.adaptivePredict(this._input, 58, this._ctx)
      while (_alt != 2 && _alt != ATN.INVALID_ALT_NUMBER) {
        if (_alt === 1) {
          if (this._parseListeners !== null) {
            this.triggerExitRuleEvent()
          }
          this.state = 504
          this._errHandler.sync(this)
          const la_ = this._interp.adaptivePredict(this._input, 57, this._ctx)
          switch (la_) {
            case 1:
              localctx = new ExpressionContext(this, _parentctx, _parentState)
              this.pushNewRecursionContext(localctx, _startState, DSLParser.RULE_expression)
              this.state = 453
              if (!this.precpred(this._ctx, 20)) {
                throw new FailedPredicateException(this, 'this.precpred(this._ctx, 20)', '')
              }
              this.state = 454
              this.match(DSLParser.Power)
              this.state = 455
              this.expression(21)
              break

            case 2:
              localctx = new ExpressionContext(this, _parentctx, _parentState)
              this.pushNewRecursionContext(localctx, _startState, DSLParser.RULE_expression)
              this.state = 456
              if (!this.precpred(this._ctx, 19)) {
                throw new FailedPredicateException(this, 'this.precpred(this._ctx, 19)', '')
              }
              this.state = 457
              this.operator2()
              this.state = 458
              this.expression(20)
              break

            case 3:
              localctx = new ExpressionContext(this, _parentctx, _parentState)
              this.pushNewRecursionContext(localctx, _startState, DSLParser.RULE_expression)
              this.state = 460
              if (!this.precpred(this._ctx, 18)) {
                throw new FailedPredicateException(this, 'this.precpred(this._ctx, 18)', '')
              }
              this.state = 462
              this._errHandler.sync(this)
              _la = this._input.LA(1)
              if (_la === DSLParser.NewLine) {
                this.state = 461
                this.match(DSLParser.NewLine)
              }

              this.state = 464
              this.operator1()
              this.state = 466
              this._errHandler.sync(this)
              _la = this._input.LA(1)
              if (_la === DSLParser.NewLine) {
                this.state = 465
                this.match(DSLParser.NewLine)
              }

              this.state = 468
              this.expression(19)
              break

            case 4:
              localctx = new ExpressionContext(this, _parentctx, _parentState)
              this.pushNewRecursionContext(localctx, _startState, DSLParser.RULE_expression)
              this.state = 470
              if (!this.precpred(this._ctx, 17)) {
                throw new FailedPredicateException(this, 'this.precpred(this._ctx, 17)', '')
              }
              this.state = 471
              _la = this._input.LA(1)
              if (
                !(
                  ((_la - 35) & ~0x1f) == 0 &&
                  ((1 << (_la - 35)) &
                    ((1 << (DSLParser.BitRightShiftNoSign - 35)) |
                      (1 << (DSLParser.BitLeftShift - 35)) |
                      (1 << (DSLParser.BitRightShift - 35)))) !==
                    0
                )
              ) {
                this._errHandler.recoverInline(this)
              } else {
                this._errHandler.reportMatch(this)
                this.consume()
              }
              this.state = 472
              this.expression(18)
              break

            case 5:
              localctx = new ExpressionContext(this, _parentctx, _parentState)
              this.pushNewRecursionContext(localctx, _startState, DSLParser.RULE_expression)
              this.state = 473
              if (!this.precpred(this._ctx, 16)) {
                throw new FailedPredicateException(this, 'this.precpred(this._ctx, 16)', '')
              }
              this.state = 474
              this.relationOperator()
              this.state = 475
              this.expression(17)
              break

            case 6:
              localctx = new ExpressionContext(this, _parentctx, _parentState)
              this.pushNewRecursionContext(localctx, _startState, DSLParser.RULE_expression)
              this.state = 477
              if (!this.precpred(this._ctx, 15)) {
                throw new FailedPredicateException(this, 'this.precpred(this._ctx, 15)', '')
              }
              this.state = 478
              this.match(DSLParser.BitAnd)
              this.state = 479
              this.expression(16)
              break

            case 7:
              localctx = new ExpressionContext(this, _parentctx, _parentState)
              this.pushNewRecursionContext(localctx, _startState, DSLParser.RULE_expression)
              this.state = 480
              if (!this.precpred(this._ctx, 14)) {
                throw new FailedPredicateException(this, 'this.precpred(this._ctx, 14)', '')
              }
              this.state = 481
              this.match(DSLParser.BitOr)
              this.state = 482
              this.expression(15)
              break

            case 8:
              localctx = new ExpressionContext(this, _parentctx, _parentState)
              this.pushNewRecursionContext(localctx, _startState, DSLParser.RULE_expression)
              this.state = 483
              if (!this.precpred(this._ctx, 13)) {
                throw new FailedPredicateException(this, 'this.precpred(this._ctx, 13)', '')
              }
              this.state = 484
              this.relationOperator()
              this.state = 485
              this.expression(14)
              break

            case 9:
              localctx = new ExpressionContext(this, _parentctx, _parentState)
              this.pushNewRecursionContext(localctx, _startState, DSLParser.RULE_expression)
              this.state = 487
              if (!this.precpred(this._ctx, 12)) {
                throw new FailedPredicateException(this, 'this.precpred(this._ctx, 12)', '')
              }
              this.state = 488
              this.match(DSLParser.And)
              this.state = 489
              this.expression(13)
              break

            case 10:
              localctx = new ExpressionContext(this, _parentctx, _parentState)
              this.pushNewRecursionContext(localctx, _startState, DSLParser.RULE_expression)
              this.state = 490
              if (!this.precpred(this._ctx, 11)) {
                throw new FailedPredicateException(this, 'this.precpred(this._ctx, 11)', '')
              }
              this.state = 491
              this.match(DSLParser.Or)
              this.state = 492
              this.expression(12)
              break

            case 11:
              localctx = new ExpressionContext(this, _parentctx, _parentState)
              this.pushNewRecursionContext(localctx, _startState, DSLParser.RULE_expression)
              this.state = 493
              if (!this.precpred(this._ctx, 10)) {
                throw new FailedPredicateException(this, 'this.precpred(this._ctx, 10)', '')
              }
              this.state = 494
              this.match(DSLParser.RegulaLike)
              this.state = 495
              this.expression(11)
              break

            case 12:
              localctx = new ExpressionContext(this, _parentctx, _parentState)
              this.pushNewRecursionContext(localctx, _startState, DSLParser.RULE_expression)
              this.state = 496
              if (!this.precpred(this._ctx, 8)) {
                throw new FailedPredicateException(this, 'this.precpred(this._ctx, 8)', '')
              }
              this.state = 497
              this.match(DSLParser.QuestionMark)
              this.state = 498
              this.expression(0)
              this.state = 499
              this.match(DSLParser.Colon)
              this.state = 500
              this.expression(9)
              break

            case 13:
              localctx = new ExpressionContext(this, _parentctx, _parentState)
              this.pushNewRecursionContext(localctx, _startState, DSLParser.RULE_expression)
              this.state = 502
              if (!this.precpred(this._ctx, 9)) {
                throw new FailedPredicateException(this, 'this.precpred(this._ctx, 9)', '')
              }
              this.state = 503
              this.match(DSLParser.RegularLikeRight)
              break
          }
        }
        this.state = 508
        this._errHandler.sync(this)
        _alt = this._interp.adaptivePredict(this._input, 58, this._ctx)
      }
    } catch (error) {
      if (error instanceof RecognitionException) {
        localctx.exception = error
        this._errHandler.reportError(this, error)
        this._errHandler.recover(this, error)
      } else {
        throw error
      }
    } finally {
      this.unrollRecursionContexts(_parentctx)
    }
    return localctx
  }

  lambdaExpression() {
    const localctx = new LambdaExpressionContext(this, this._ctx, this.state)
    this.enterRule(localctx, 60, DSLParser.RULE_lambdaExpression)
    let _la = 0 // Token type
    try {
      this.enterOuterAlt(localctx, 1)
      this.state = 509
      this.match(DSLParser.T__6)
      this.state = 510
      this.match(DSLParser.OpenParen)
      this.state = 511
      this.functionParameterList()
      this.state = 512
      this.match(DSLParser.CloseParen)
      this.state = 513
      this.match(DSLParser.T__7)
      this.state = 517
      this._errHandler.sync(this)
      _la = this._input.LA(1)
      while (
        ((_la & ~0x1f) == 0 &&
          ((1 << _la) &
            ((1 << DSLParser.T__0) |
              (1 << DSLParser.T__3) |
              (1 << DSLParser.T__4) |
              (1 << DSLParser.T__6) |
              (1 << DSLParser.RegularRelationLeftExpression) |
              (1 << DSLParser.RegularRelationRightExpression) |
              (1 << DSLParser.NewLine) |
              (1 << DSLParser.Comment) |
              (1 << DSLParser.Not))) !==
            0) ||
        (((_la - 34) & ~0x1f) == 0 &&
          ((1 << (_la - 34)) &
            ((1 << (DSLParser.BitNot - 34)) |
              (1 << (DSLParser.Number - 34)) |
              (1 << (DSLParser.NumberHex - 34)) |
              (1 << (DSLParser.BooleanTrue - 34)) |
              (1 << (DSLParser.BooleanFalse - 34)) |
              (1 << (DSLParser.String - 34)) |
              (1 << (DSLParser.VariablePrefix - 34)) |
              (1 << (DSLParser.FunctionKeyword - 34)) |
              (1 << (DSLParser.ReturnKeyword - 34)) |
              (1 << (DSLParser.IfKeyword - 34)) |
              (1 << (DSLParser.Break - 34)) |
              (1 << (DSLParser.Continue - 34)) |
              (1 << (DSLParser.For - 34)) |
              (1 << (DSLParser.While - 34)) |
              (1 << (DSLParser.OpenBracket - 34)) |
              (1 << (DSLParser.OpenParen - 34)) |
              (1 << (DSLParser.OpenBrace - 34)) |
              (1 << (DSLParser.SemiColon - 34)) |
              (1 << (DSLParser.Plus - 34)))) !==
            0) ||
        (((_la - 66) & ~0x1f) == 0 &&
          ((1 << (_la - 66)) &
            ((1 << (DSLParser.Minus - 66)) |
              (1 << (DSLParser.PlusPlus - 66)) |
              (1 << (DSLParser.MinusMinus - 66)) |
              (1 << (DSLParser.Identifier - 66)))) !==
            0)
      ) {
        this.state = 514
        this.statement(0)
        this.state = 519
        this._errHandler.sync(this)
        _la = this._input.LA(1)
      }
      this.state = 520
      this.match(DSLParser.T__8)
      this.state = 522
      this._errHandler.sync(this)
      const la_ = this._interp.adaptivePredict(this._input, 60, this._ctx)
      if (la_ === 1) {
        this.state = 521
        this.match(DSLParser.SemiColon)
      }
    } catch (re) {
      if (re instanceof RecognitionException) {
        localctx.exception = re
        this._errHandler.reportError(this, re)
        this._errHandler.recover(this, re)
      } else {
        throw re
      }
    } finally {
      this.exitRule()
    }
    return localctx
  }

  identifierExpress() {
    const localctx = new IdentifierExpressContext(this, this._ctx, this.state)
    this.enterRule(localctx, 62, DSLParser.RULE_identifierExpress)
    try {
      this.enterOuterAlt(localctx, 1)
      this.state = 524
      this.match(DSLParser.Identifier)
      this.state = 533
      this._errHandler.sync(this)
      let _alt = this._interp.adaptivePredict(this._input, 62, this._ctx)
      while (_alt != 2 && _alt != ATN.INVALID_ALT_NUMBER) {
        if (_alt === 1) {
          this.state = 531
          this._errHandler.sync(this)
          switch (this._input.LA(1)) {
            case DSLParser.Dot:
              this.state = 525
              this.match(DSLParser.Dot)
              this.state = 526
              this.match(DSLParser.Identifier)
              break
            case DSLParser.OpenBracket:
              this.state = 527
              this.match(DSLParser.OpenBracket)
              this.state = 528
              this.expression(0)
              this.state = 529
              this.match(DSLParser.CloseBracket)
              break
            default:
              throw new NoViableAltException(this)
          }
        }
        this.state = 535
        this._errHandler.sync(this)
        _alt = this._interp.adaptivePredict(this._input, 62, this._ctx)
      }
    } catch (re) {
      if (re instanceof RecognitionException) {
        localctx.exception = re
        this._errHandler.reportError(this, re)
        this._errHandler.recover(this, re)
      } else {
        throw re
      }
    } finally {
      this.exitRule()
    }
    return localctx
  }

  plusExpression() {
    const localctx = new PlusExpressionContext(this, this._ctx, this.state)
    this.enterRule(localctx, 64, DSLParser.RULE_plusExpression)
    try {
      this.enterOuterAlt(localctx, 1)
      this.state = 536
      this.match(DSLParser.Plus)
      this.state = 537
      this.expression(0)
    } catch (re) {
      if (re instanceof RecognitionException) {
        localctx.exception = re
        this._errHandler.reportError(this, re)
        this._errHandler.recover(this, re)
      } else {
        throw re
      }
    } finally {
      this.exitRule()
    }
    return localctx
  }

  minusExpression() {
    const localctx = new MinusExpressionContext(this, this._ctx, this.state)
    this.enterRule(localctx, 66, DSLParser.RULE_minusExpression)
    try {
      this.enterOuterAlt(localctx, 1)
      this.state = 539
      this.match(DSLParser.Minus)
      this.state = 540
      this.expression(0)
    } catch (re) {
      if (re instanceof RecognitionException) {
        localctx.exception = re
        this._errHandler.reportError(this, re)
        this._errHandler.recover(this, re)
      } else {
        throw re
      }
    } finally {
      this.exitRule()
    }
    return localctx
  }

  rightPlusPlus() {
    const localctx = new RightPlusPlusContext(this, this._ctx, this.state)
    this.enterRule(localctx, 68, DSLParser.RULE_rightPlusPlus)
    try {
      this.enterOuterAlt(localctx, 1)
      this.state = 542
      this.match(DSLParser.Identifier)
      this.state = 543
      this.match(DSLParser.PlusPlus)
    } catch (re) {
      if (re instanceof RecognitionException) {
        localctx.exception = re
        this._errHandler.reportError(this, re)
        this._errHandler.recover(this, re)
      } else {
        throw re
      }
    } finally {
      this.exitRule()
    }
    return localctx
  }

  rightMinusMinus() {
    const localctx = new RightMinusMinusContext(this, this._ctx, this.state)
    this.enterRule(localctx, 70, DSLParser.RULE_rightMinusMinus)
    try {
      this.enterOuterAlt(localctx, 1)
      this.state = 545
      this.match(DSLParser.Identifier)
      this.state = 546
      this.match(DSLParser.MinusMinus)
    } catch (re) {
      if (re instanceof RecognitionException) {
        localctx.exception = re
        this._errHandler.reportError(this, re)
        this._errHandler.recover(this, re)
      } else {
        throw re
      }
    } finally {
      this.exitRule()
    }
    return localctx
  }

  leftPlusPlus() {
    const localctx = new LeftPlusPlusContext(this, this._ctx, this.state)
    this.enterRule(localctx, 72, DSLParser.RULE_leftPlusPlus)
    try {
      this.enterOuterAlt(localctx, 1)
      this.state = 548
      this.match(DSLParser.PlusPlus)
      this.state = 549
      this.match(DSLParser.Identifier)
    } catch (re) {
      if (re instanceof RecognitionException) {
        localctx.exception = re
        this._errHandler.reportError(this, re)
        this._errHandler.recover(this, re)
      } else {
        throw re
      }
    } finally {
      this.exitRule()
    }
    return localctx
  }

  leftMinusMinus() {
    const localctx = new LeftMinusMinusContext(this, this._ctx, this.state)
    this.enterRule(localctx, 74, DSLParser.RULE_leftMinusMinus)
    try {
      this.enterOuterAlt(localctx, 1)
      this.state = 551
      this.match(DSLParser.MinusMinus)
      this.state = 552
      this.match(DSLParser.Identifier)
    } catch (re) {
      if (re instanceof RecognitionException) {
        localctx.exception = re
        this._errHandler.reportError(this, re)
        this._errHandler.recover(this, re)
      } else {
        throw re
      }
    } finally {
      this.exitRule()
    }
    return localctx
  }

  relationOperator() {
    const localctx = new RelationOperatorContext(this, this._ctx, this.state)
    this.enterRule(localctx, 76, DSLParser.RULE_relationOperator)
    let _la = 0 // Token type
    try {
      this.enterOuterAlt(localctx, 1)
      this.state = 554
      _la = this._input.LA(1)
      if (
        !(
          (_la & ~0x1f) == 0 &&
          ((1 << _la) &
            ((1 << DSLParser.NotEqual) |
              (1 << DSLParser.GreatThanOrEqual) |
              (1 << DSLParser.GreatThan) |
              (1 << DSLParser.LessThanOrEqual) |
              (1 << DSLParser.LessThan) |
              (1 << DSLParser.Equal))) !==
            0
        )
      ) {
        this._errHandler.recoverInline(this)
      } else {
        this._errHandler.reportMatch(this)
        this.consume()
      }
    } catch (re) {
      if (re instanceof RecognitionException) {
        localctx.exception = re
        this._errHandler.reportError(this, re)
        this._errHandler.recover(this, re)
      } else {
        throw re
      }
    } finally {
      this.exitRule()
    }
    return localctx
  }

  operator2() {
    const localctx = new Operator2Context(this, this._ctx, this.state)
    this.enterRule(localctx, 78, DSLParser.RULE_operator2)
    let _la = 0 // Token type
    try {
      this.enterOuterAlt(localctx, 1)
      this.state = 556
      _la = this._input.LA(1)
      if (
        !(
          (_la & ~0x1f) == 0 &&
          ((1 << _la) &
            ((1 << DSLParser.Multiply) | (1 << DSLParser.Divide) | (1 << DSLParser.Mode))) !==
            0
        )
      ) {
        this._errHandler.recoverInline(this)
      } else {
        this._errHandler.reportMatch(this)
        this.consume()
      }
    } catch (re) {
      if (re instanceof RecognitionException) {
        localctx.exception = re
        this._errHandler.reportError(this, re)
        this._errHandler.recover(this, re)
      } else {
        throw re
      }
    } finally {
      this.exitRule()
    }
    return localctx
  }

  operator1() {
    const localctx = new Operator1Context(this, this._ctx, this.state)
    this.enterRule(localctx, 80, DSLParser.RULE_operator1)
    let _la = 0 // Token type
    try {
      this.enterOuterAlt(localctx, 1)
      this.state = 558
      _la = this._input.LA(1)
      if (!(_la === DSLParser.Plus || _la === DSLParser.Minus)) {
        this._errHandler.recoverInline(this)
      } else {
        this._errHandler.reportMatch(this)
        this.consume()
      }
    } catch (re) {
      if (re instanceof RecognitionException) {
        localctx.exception = re
        this._errHandler.reportError(this, re)
        this._errHandler.recover(this, re)
      } else {
        throw re
      }
    } finally {
      this.exitRule()
    }
    return localctx
  }

  bitOperator() {
    const localctx = new BitOperatorContext(this, this._ctx, this.state)
    this.enterRule(localctx, 82, DSLParser.RULE_bitOperator)
    let _la = 0 // Token type
    try {
      this.enterOuterAlt(localctx, 1)
      this.state = 560
      _la = this._input.LA(1)
      if (!(_la === DSLParser.BitAnd || _la === DSLParser.BitOr)) {
        this._errHandler.recoverInline(this)
      } else {
        this._errHandler.reportMatch(this)
        this.consume()
      }
    } catch (re) {
      if (re instanceof RecognitionException) {
        localctx.exception = re
        this._errHandler.reportError(this, re)
        this._errHandler.recover(this, re)
      } else {
        throw re
      }
    } finally {
      this.exitRule()
    }
    return localctx
  }

  singleBitOperator() {
    const localctx = new SingleBitOperatorContext(this, this._ctx, this.state)
    this.enterRule(localctx, 84, DSLParser.RULE_singleBitOperator)
    let _la = 0 // Token type
    try {
      this.enterOuterAlt(localctx, 1)
      this.state = 562
      _la = this._input.LA(1)
      if (
        !(
          (_la & ~0x1f) == 0 &&
          ((1 << _la) &
            ((1 << DSLParser.Multiply) | (1 << DSLParser.Divide) | (1 << DSLParser.Mode))) !==
            0
        )
      ) {
        this._errHandler.recoverInline(this)
      } else {
        this._errHandler.reportMatch(this)
        this.consume()
      }
    } catch (re) {
      if (re instanceof RecognitionException) {
        localctx.exception = re
        this._errHandler.reportError(this, re)
        this._errHandler.recover(this, re)
      } else {
        throw re
      }
    } finally {
      this.exitRule()
    }
    return localctx
  }

  wrapperExpression() {
    const localctx = new WrapperExpressionContext(this, this._ctx, this.state)
    this.enterRule(localctx, 86, DSLParser.RULE_wrapperExpression)
    try {
      this.enterOuterAlt(localctx, 1)
      this.state = 564
      this.match(DSLParser.OpenParen)
      this.state = 565
      this.expression(0)
      this.state = 566
      this.match(DSLParser.CloseParen)
    } catch (re) {
      if (re instanceof RecognitionException) {
        localctx.exception = re
        this._errHandler.reportError(this, re)
        this._errHandler.recover(this, re)
      } else {
        throw re
      }
    } finally {
      this.exitRule()
    }
    return localctx
  }

  functionInvoikeExpression() {
    const localctx = new FunctionInvoikeExpressionContext(this, this._ctx, this.state)
    this.enterRule(localctx, 88, DSLParser.RULE_functionInvoikeExpression)
    let _la = 0 // Token type
    try {
      this.enterOuterAlt(localctx, 1)
      this.state = 573
      this._errHandler.sync(this)
      switch (this._input.LA(1)) {
        case DSLParser.OpenParen:
          this.state = 568
          this.match(DSLParser.OpenParen)
          this.state = 569
          this.lambdaExpression()
          this.state = 570
          this.match(DSLParser.CloseParen)
          break
        case DSLParser.Identifier:
          this.state = 572
          this.identifierExpress()
          break
        default:
          throw new NoViableAltException(this)
      }
      this.state = 575
      this.match(DSLParser.OpenParen)
      this.state = 599
      this._errHandler.sync(this)
      _la = this._input.LA(1)
      if (
        (((_la - 4) & ~0x1f) == 0 &&
          ((1 << (_la - 4)) &
            ((1 << (DSLParser.T__3 - 4)) |
              (1 << (DSLParser.T__6 - 4)) |
              (1 << (DSLParser.RegularRelationLeftExpression - 4)) |
              (1 << (DSLParser.RegularRelationRightExpression - 4)) |
              (1 << (DSLParser.Multiply - 4)) |
              (1 << (DSLParser.Not - 4)) |
              (1 << (DSLParser.BitNot - 4)))) !==
            0) ||
        (((_la - 38) & ~0x1f) == 0 &&
          ((1 << (_la - 38)) &
            ((1 << (DSLParser.Number - 38)) |
              (1 << (DSLParser.NumberHex - 38)) |
              (1 << (DSLParser.BooleanTrue - 38)) |
              (1 << (DSLParser.BooleanFalse - 38)) |
              (1 << (DSLParser.String - 38)) |
              (1 << (DSLParser.OpenBracket - 38)) |
              (1 << (DSLParser.OpenParen - 38)) |
              (1 << (DSLParser.Plus - 38)) |
              (1 << (DSLParser.Minus - 38)) |
              (1 << (DSLParser.PlusPlus - 38)) |
              (1 << (DSLParser.MinusMinus - 38)) |
              (1 << (DSLParser.Identifier - 38)))) !==
            0)
      ) {
        this.state = 577
        this._errHandler.sync(this)
        _la = this._input.LA(1)
        if (_la === DSLParser.Multiply) {
          this.state = 576
          this.match(DSLParser.Multiply)
        }

        this.state = 579
        this.expression(0)
        this.state = 596
        this._errHandler.sync(this)
        _la = this._input.LA(1)
        while (_la === DSLParser.T__1 || _la === DSLParser.NewLine) {
          this.state = 581
          this._errHandler.sync(this)
          _la = this._input.LA(1)
          if (_la === DSLParser.NewLine) {
            this.state = 580
            this.match(DSLParser.NewLine)
          }

          this.state = 583
          this.match(DSLParser.T__1)
          this.state = 585
          this._errHandler.sync(this)
          _la = this._input.LA(1)
          if (_la === DSLParser.NewLine) {
            this.state = 584
            this.match(DSLParser.NewLine)
          }

          this.state = 588
          this._errHandler.sync(this)
          _la = this._input.LA(1)
          if (_la === DSLParser.Multiply) {
            this.state = 587
            this.match(DSLParser.Multiply)
          }

          this.state = 592
          this._errHandler.sync(this)
          const la_ = this._interp.adaptivePredict(this._input, 68, this._ctx)
          switch (la_) {
            case 1:
              this.state = 590
              this.expression(0)
              break

            case 2:
              this.state = 591
              this.match(DSLParser.Plus)
              break
          }
          this.state = 598
          this._errHandler.sync(this)
          _la = this._input.LA(1)
        }
      }

      this.state = 601
      this.match(DSLParser.CloseParen)
    } catch (re) {
      if (re instanceof RecognitionException) {
        localctx.exception = re
        this._errHandler.reportError(this, re)
        this._errHandler.recover(this, re)
      } else {
        throw re
      }
    } finally {
      this.exitRule()
    }
    return localctx
  }

  eos() {
    const localctx = new EosContext(this, this._ctx, this.state)
    this.enterRule(localctx, 90, DSLParser.RULE_eos)
    let _la = 0 // Token type
    try {
      this.enterOuterAlt(localctx, 1)
      this.state = 603
      _la = this._input.LA(1)
      if (!(_la === DSLParser.NewLine || _la === DSLParser.SemiColon)) {
        this._errHandler.recoverInline(this)
      } else {
        this._errHandler.reportMatch(this)
        this.consume()
      }
    } catch (re) {
      if (re instanceof RecognitionException) {
        localctx.exception = re
        this._errHandler.reportError(this, re)
        this._errHandler.recover(this, re)
      } else {
        throw re
      }
    } finally {
      this.exitRule()
    }
    return localctx
  }

  literal() {
    const localctx = new LiteralContext(this, this._ctx, this.state)
    this.enterRule(localctx, 92, DSLParser.RULE_literal)
    let _la = 0 // Token type
    try {
      this.enterOuterAlt(localctx, 1)
      this.state = 605
      _la = this._input.LA(1)
      if (
        !(
          ((_la - 38) & ~0x1f) == 0 &&
          ((1 << (_la - 38)) &
            ((1 << (DSLParser.Number - 38)) |
              (1 << (DSLParser.NumberHex - 38)) |
              (1 << (DSLParser.BooleanTrue - 38)) |
              (1 << (DSLParser.BooleanFalse - 38)) |
              (1 << (DSLParser.String - 38)))) !==
            0
        )
      ) {
        this._errHandler.recoverInline(this)
      } else {
        this._errHandler.reportMatch(this)
        this.consume()
      }
    } catch (re) {
      if (re instanceof RecognitionException) {
        localctx.exception = re
        this._errHandler.reportError(this, re)
        this._errHandler.recover(this, re)
      } else {
        throw re
      }
    } finally {
      this.exitRule()
    }
    return localctx
  }
}
