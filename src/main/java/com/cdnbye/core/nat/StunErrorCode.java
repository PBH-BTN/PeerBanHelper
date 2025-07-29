package com.cdnbye.core.nat;

public class StunErrorCode {

    public int getCode() {
        return code;
    }

    public String getReasonText() {
        return reasonText;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public void setReasonText(String reasonText) {
        this.reasonText = reasonText;
    }

    private int code;
    private String reasonText;

    public StunErrorCode(int code, String reasonText) {
        this.code = code;
        this.reasonText = reasonText;
    }

}
