package com.ghostchu.peerbanhelper.util.scriptengine;

import jep.Interpreter;
import jep.JepException;
import jep.MainInterpreter;
import jep.SharedInterpreter;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
public class PyThreadSafeInterpreter implements Interpreter {
    private final ReentrantLock lock = new ReentrantLock();
    private final Interpreter interpreter;
    private final ExecutorService executor = Executors.newSingleThreadExecutor((r) -> {
        Thread t = new Thread(r, "py-interpreter-thread");
        t.setDaemon(false);
        return t;
    });
    private final static String CHECK_SCRIPT = "import site,pathlib; print('\\n'.join([str(f.absolute()) for p in [site.getusersitepackages()]+site.getsitepackages() for n in ('libjep.jnilib','libjep.so','jep.dll') for f in (pathlib.Path(p)/'jep').glob(n)]))";

    static {
        // 设置 Jep jni 文件位置
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"python" ,"-c", CHECK_SCRIPT});
            try (BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String ret = in.readLine();
                if (ret != null && !ret.trim().isEmpty()) {
                    log.debug("Found libjep path: {}", ret);
                    MainInterpreter.setJepLibraryPath(ret);
                }
            }
        } catch (Exception e) {
            // 失败时保持静默
            log.debug("Failed to search libjep path", e);
        }
    }

    public PyThreadSafeInterpreter() {
        try {
            interpreter = executor.submit(SharedInterpreter::new).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while initializing Python interpreter", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Failed to initialize Python interpreter", e.getCause());
        }
    }

    private <T> T submitToInterpreter(Callable<T> task) throws JepException {
        Future<T> f = executor.submit(task);
        try {
            return f.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for Python task", e);
        } catch (ExecutionException e) {
            Throwable c = e.getCause();
            switch (c) {
                case JepException jepException -> throw jepException;
                case RuntimeException runtimeException -> throw runtimeException;
                case null, default -> throw new RuntimeException(c);
            }
        }
    }

    @Override
    public Object invoke(String name, Object... args) throws JepException {
        return submitToInterpreter(() -> interpreter.invoke(name, args));
    }

    @Override
    public Object invoke(String name, Map<String, Object> kwargs) throws JepException {
        return submitToInterpreter(() -> interpreter.invoke(name, kwargs));
    }

    @Override
    public Object invoke(String name, Object[] args, Map<String, Object> kwargs) throws JepException {
        return submitToInterpreter(() -> interpreter.invoke(name, args, kwargs));
    }

    @Override
    public boolean eval(String str) throws JepException {
        return submitToInterpreter(() -> interpreter.eval(str));
    }

    @Override
    public void exec(String str) throws JepException {
        submitToInterpreter(() -> {
            interpreter.exec(str);
            return null;
        });
    }

    @Override
    public void runScript(String script) throws JepException {
        submitToInterpreter(() -> {
            interpreter.runScript(script);
            return null;
        });
    }

    @Override
    public Object getValue(String str) throws JepException {
        return submitToInterpreter(() -> interpreter.getValue(str));
    }

    @Override
    public <T> T getValue(String str, Class<T> clazz) throws JepException {
        return submitToInterpreter(() -> interpreter.getValue(str, clazz));
    }

    @Override
    public void set(String name, Object v) throws JepException {
        submitToInterpreter(() -> {
            interpreter.set(name, v);
            return null;
        });
    }

    @Override
    public Interpreter attach(boolean shareGlobals) {
        throw new UnsupportedOperationException("PySafeInterpreter does not support attach");
    }

    @Override
    public void close() throws JepException {
        submitToInterpreter(() -> {
            interpreter.close();
            return null;
        });
    }

    public void shutdown() {
        try (final Lock _ = getLock()) {
            close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            executor.shutdownNow();
        }
    }

    public Lock getLock() {
        return new Lock();
    }

    public class Lock implements AutoCloseable {
        public Lock () {
            lock.lock();
        }
        @Override
        public void close() throws Exception {
            lock.unlock();
        }
    }
}
