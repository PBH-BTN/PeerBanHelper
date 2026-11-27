package com.ghostchu.peerbanhelper.util.scriptengine.sandbox;

import java.util.regex.Pattern;

final class WorkerLibraries {
    private static final Pattern JAR = Pattern.compile("(?:aviator|gson|luaj-jse|slf4j-api|kotlin-stdlib|"
            + "polyglot|js-language|regex|icu4j|xz|collections|jniutils|nativeimage|word|"
            + "truffle-api|truffle-compiler|truffle-runtime)-[0-9][A-Za-z0-9._+-]*\\.jar");

    private WorkerLibraries() {}

    static boolean includes(String filename) {
        return JAR.matcher(filename).matches();
    }
}
