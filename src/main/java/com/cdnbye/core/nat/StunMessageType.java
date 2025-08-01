package com.cdnbye.core.nat;

public enum StunMessageType {

    // STUN message is binding request.
    BindingRequest(0x0001),

    // STUN message is binding request response.
    BindingResponse(0x0101),

    // STUN message is binding request error response.
    BindingErrorResponse(0x0111),

    // STUN message is "shared secret" request.
    SharedSecretRequest(0x0002),

    // STUN message is "shared secret" request response.
    SharedSecretResponse(0x0102),

    // STUN message is "shared secret" request error response.
    SharedSecretErrorResponse(0x0112);


    private int value = 0;

    StunMessageType(int value) {
        this.value = value;
    }

    public int value() {
        return this.value;
    }

}
