package com.cdnbye.core.nat;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class StunErrorCode {

    private int code;
    private String reasonText;

    public StunErrorCode(int code, String reasonText) {
        this.code = code;
        this.reasonText = reasonText;
    }

}
