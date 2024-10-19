//package br.com.mobhub.fdv.sync.utils;
//
//import br.com.mobhub.fdv.sync.App;
//import ch.qos.logback.classic.Level;
//import ch.qos.logback.classic.PatternLayout;
//import ch.qos.logback.classic.spi.ILoggingEvent;
//import ch.qos.logback.core.AppenderBase;
//
//import javax.swing.*;
//import javax.swing.text.*;
//import java.awt.*;
//
///**
// * @author Rodrigo Garcia Lima (email: rodgarcialima@gmail.com | github: rodgarcialima)
// * @see ch.qos.logback.core.AppenderBase
// */
//public class Appender extends AppenderBase<ILoggingEvent> {
//
//    /**
//     * Utilizo para formatar a mensagem de log
//     */
//    private PatternLayout patternLayout;
//
//    /**
//     * Cada nível de log tem um estilo próprio
//     */
//    private static SimpleAttributeSet ERROR_ATT, WARN_ATT, INFO_ATT, DEBUG_ATT, TRACE_ATT, RESTO_ATT;
//
//    /**
//     * Definição dos estilos de log
//     */
//    static {
//        // ERROR
//        ERROR_ATT = new SimpleAttributeSet();
//        ERROR_ATT.addAttribute(StyleConstants.CharacterConstants.Bold, Boolean.TRUE);
//        ERROR_ATT.addAttribute(StyleConstants.CharacterConstants.Italic, Boolean.FALSE);
//        ERROR_ATT.addAttribute(StyleConstants.CharacterConstants.Foreground, new Color(153, 0, 0));
//
//        // WARN
//        WARN_ATT = new SimpleAttributeSet();
//        WARN_ATT.addAttribute(StyleConstants.CharacterConstants.Bold, Boolean.FALSE);
//        WARN_ATT.addAttribute(StyleConstants.CharacterConstants.Italic, Boolean.FALSE);
//        WARN_ATT.addAttribute(StyleConstants.CharacterConstants.Foreground, new Color(153, 76, 0));
//
//        // INFO
//        INFO_ATT = new SimpleAttributeSet();
//        INFO_ATT.addAttribute(StyleConstants.CharacterConstants.Bold, Boolean.FALSE);
//        INFO_ATT.addAttribute(StyleConstants.CharacterConstants.Italic, Boolean.FALSE);
//        INFO_ATT.addAttribute(StyleConstants.CharacterConstants.Foreground, new Color(0, 0, 153));
//
//        // DEBUG
//        DEBUG_ATT = new SimpleAttributeSet();
//        DEBUG_ATT.addAttribute(StyleConstants.CharacterConstants.Bold, Boolean.FALSE);
//        DEBUG_ATT.addAttribute(StyleConstants.CharacterConstants.Italic, Boolean.TRUE);
//        DEBUG_ATT.addAttribute(StyleConstants.CharacterConstants.Foreground, new Color(64, 64, 64));
//
//        // TRACE
//        TRACE_ATT = new SimpleAttributeSet();
//        TRACE_ATT.addAttribute(StyleConstants.CharacterConstants.Bold, Boolean.FALSE);
//        TRACE_ATT.addAttribute(StyleConstants.CharacterConstants.Italic, Boolean.TRUE);
//        TRACE_ATT.addAttribute(StyleConstants.CharacterConstants.Foreground, new Color(153, 0, 76));
//
//        // RESTO
//        RESTO_ATT = new SimpleAttributeSet();
//        RESTO_ATT.addAttribute(StyleConstants.CharacterConstants.Bold, Boolean.FALSE);
//        RESTO_ATT.addAttribute(StyleConstants.CharacterConstants.Italic, Boolean.TRUE);
//        RESTO_ATT.addAttribute(StyleConstants.CharacterConstants.Foreground, new Color(0, 0, 0));
//    }
//
//    @Override
//    public void start() {
//        patternLayout = new PatternLayout();
//        patternLayout.setContext(getContext());
//        patternLayout.setPattern("%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n");
//        patternLayout.start();
//
//        super.start();
//    }
//
//    @Override
//    protected void append(ILoggingEvent event) {
//        // Formata mensagem do log
//        String formattedMsg = patternLayout.doLayout(event);
//
//        // Forma segura de atualizar o JTextpane
//        SwingUtilities.invokeLater(() -> {
//            // Alias para o JTextPane no frame da aplicação
//            JTextPane textPane = App.MAIN_FORM.getTextPane();
//
//            try {
//                // Trunca linhas para economizar memória
//                // Quando atingir 2000 linhas, eu quero que
//                // apague as 500 primeiras linhas
//                int limite = 1000;
//                int apaga = 200;
//                if (textPane.getDocument().getDefaultRootElement().getElementCount() > limite) {
//                    int end = getLineEndOffset(textPane, apaga);
//                    replaceRange(textPane, null, 0, end);
//                }
//
//                // Decide qual atributo (estilo) devo usar de acordo com o nível o log
//                if (event.getLevel() == Level.ERROR)
//                    textPane.getDocument().insertString(textPane.getDocument().getLength(), formattedMsg, ERROR_ATT);
//                else if (event.getLevel() == Level.WARN)
//                    textPane.getDocument().insertString(textPane.getDocument().getLength(), formattedMsg, WARN_ATT);
//                else if (event.getLevel() == Level.INFO)
//                    textPane.getDocument().insertString(textPane.getDocument().getLength(), formattedMsg, INFO_ATT);
//                else if (event.getLevel() == Level.DEBUG)
//                    textPane.getDocument().insertString(textPane.getDocument().getLength(), formattedMsg, DEBUG_ATT);
//                else if (event.getLevel() == Level.TRACE)
//                    textPane.getDocument().insertString(textPane.getDocument().getLength(), formattedMsg, TRACE_ATT);
//                else
//                    textPane.getDocument().insertString(textPane.getDocument().getLength(), formattedMsg, RESTO_ATT);
//
//            } catch (BadLocationException e) {
//                // Faz nada
//            }
//
//            // Vai para a última linha
//            textPane.setCaretPosition(textPane.getDocument().getLength());
//        });
//    }
//
//    /**
//     * Código copiado do {@link JTextArea#getLineCount()}
//     * @param textPane de onde quero as linhas contadas
//     * @return quantidade de linhas &gt; 0
//     */
//    private int getLineCount(JTextPane textPane) {
//        return textPane.getDocument().getDefaultRootElement().getElementCount();
//    }
//
//    /**
//     * Código copiado do {@link JTextArea#getLineEndOffset(int)}
//     * @param textPane de onde quero o offset
//     * @param line the line &gt;= 0
//     * @return the offset &gt;= 0
//     * @throws BadLocationException Thrown if the line is
//     * less than zero or greater or equal to the number of
//     * lines contained in the document (as reported by
//     * getLineCount)
//     */
//    private int getLineEndOffset(JTextPane textPane, int line) throws BadLocationException {
//        int lineCount = getLineCount(textPane);
//        if (line < 0) {
//            throw new BadLocationException("Negative line", -1);
//        } else if (line >= lineCount) {
//            throw new BadLocationException("No such line", textPane.getDocument().getLength()+1);
//        } else {
//            Element map = textPane.getDocument().getDefaultRootElement();
//            Element lineElem = map.getElement(line);
//            int endOffset = lineElem.getEndOffset();
//            // hide the implicit break at the end of the document
//            return ((line == lineCount - 1) ? (endOffset - 1) : endOffset);
//        }
//    }
//
//    /**
//     * Código copiado do {@link JTextArea#replaceRange(String, int, int)}<br>
//     *
//     * Replaces text from the indicated start to end position with the
//     * new text specified.  Does nothing if the model is null.  Simply
//     * does a delete if the new string is null or empty.<br>
//     *
//     * @param textPane de onde quero substituir o texto
//     * @param str the text to use as the replacement
//     * @param start the start position &gt;= 0
//     * @param end the end position &gt;= start
//     * @exception IllegalArgumentException if part of the range is an invalid position in the model
//     */
//    private void replaceRange(JTextPane textPane, String str, int start, int end) throws IllegalArgumentException {
//        if (end < start) {
//            throw new IllegalArgumentException("end before start");
//        }
//        Document doc = textPane.getDocument();
//        if (doc != null) {
//            try {
//                if (doc instanceof AbstractDocument) {
//                    ((AbstractDocument)doc).replace(start, end - start, str, null);
//                }
//                else {
//                    doc.remove(start, end - start);
//                    doc.insertString(start, str, null);
//                }
//            } catch (BadLocationException e) {
//                throw new IllegalArgumentException(e.getMessage());
//            }
//        }
//    }
//}