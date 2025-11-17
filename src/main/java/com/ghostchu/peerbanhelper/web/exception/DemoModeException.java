package com.ghostchu.peerbanhelper.web.exception;

public class DemoModeException extends Exception {
    public DemoModeException(){
        super("Demo Mode Enabled - Operation Not Permitted");
    }
}
