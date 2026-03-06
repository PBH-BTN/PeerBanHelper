package com.ghostchu.peerbanhelper.util.scriptengine;

import jep.Interpreter;
import jep.JepException;
import jep.SharedInterpreter;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

public class PyThreadSafeInterpreter implements Interpreter {
    private final ReentrantLock lock = new ReentrantLock();
    private final Interpreter interpreter;
    private final ExecutorService executor = Executors.newSingleThreadExecutor((r) -> {
        Thread t = new Thread(r, "py-interpreter-thread");
        t.setDaemon(false);
        return t;
    });

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
        return null;
    }

    @Override
    public void close() throws JepException {
        submitToInterpreter(() -> {
            interpreter.close();
            return null;
        });
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
