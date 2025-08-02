package com.cdnbye.core.nat;

import java.net.InetSocketAddress;
import java.util.Random;

public class StunMessage {

    private byte[] transactionId;

    public byte[] getTransactionId() {
        return transactionId;
    }

    public StunMessageType getType() {
        return type;
    }

    public int getMagicCookie() {
        return magicCookie;
    }

    public InetSocketAddress getMappedAddress() {
        return mappedAddress;
    }

    public InetSocketAddress getResponseAddress() {
        return responseAddress;
    }

    public InetSocketAddress getSourceAddress() {
        return sourceAddress;
    }

    public InetSocketAddress getChangedAddress() {
        return changedAddress;
    }

    public StunChangeRequest getChangeRequest() {
        return changeRequest;
    }

    public StunErrorCode getErrorCode() {
        return errorCode;
    }

    private StunMessageType type = StunMessageType.BindingRequest;
    private int magicCookie;
    private InetSocketAddress mappedAddress;
    private InetSocketAddress responseAddress;
    private InetSocketAddress sourceAddress;
    private InetSocketAddress changedAddress;
    private StunChangeRequest changeRequest;
    private StunErrorCode errorCode;


    private enum AttributeType {

        MappedAddress(0x0001),
        ResponseAddress(0x0002),
        ChangeRequest(0x0003),
        SourceAddress(0x0004),
        ChangedAddress(0x0005),
        Username(0x0006),
        Password(0x0007),
        MessageIntegrity(0x0008),
        ErrorCode(0x0009),
        UnknownAttribute(0x000A),
        ReflectedFrom(0x000B),
        XorMappedAddress(0x8020),
        XorOnly(0x0021),
        ServerName(0x8022);

        private int value = 0;

        AttributeType(int value) {
            this.value = value;
        }

        public int value() {
            return this.value;
        }

        public static AttributeType getTypeByValue(int value) {
            for (AttributeType at : values()) {
                if (at.value == value) {
                    return at;
                }
            }
            return null;
        }
    }

    public StunMessage() {
        this.transactionId = new byte[12];
        new Random().nextBytes(this.transactionId);
    }

    public StunMessage(StunMessageType type) {
        this();
        this.type = type;
    }

    public StunMessage(StunMessageType type, StunChangeRequest changeRequest) {
        this();
        this.type = type;
        this.changeRequest = changeRequest;
    }

    // Parses STUN message from raw data packet.
    public void parse(byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException("data is null");
        }

        /* RFC 5389 6.
            All STUN messages MUST start with a 20-byte header followed by zero
            or more Attributes.  The STUN header contains a STUN message type,
            magic cookie, transaction ID, and message length.

             0                   1                   2                   3
             0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
             +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
             |0 0|     STUN Message Type     |         Message Length        |
             +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
             |                         Magic Cookie                          |
             +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
             |                                                               |
             |                     Transaction ID (96 bits)                  |
             |                                                               |
             +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+

           The message length is the count, in bytes, of the size of the
           message, not including the 20 byte header.
        */

        if (data.length < 20) {
            throw new IllegalArgumentException("Invalid STUN message value !");
        }

        int offset = 0;

        //--- message header --------------------------------------------------

        // STUN Message Type
        int messageType = data[offset++] << 8 | data[offset++];
        if (messageType == StunMessageType.BindingErrorResponse.value()) {
            type = StunMessageType.BindingErrorResponse;
        } else if (messageType == StunMessageType.BindingRequest.value()) {
            type = StunMessageType.BindingRequest;
        } else if (messageType == StunMessageType.BindingResponse.value()) {
            type = StunMessageType.BindingResponse;
        } else if (messageType == StunMessageType.SharedSecretErrorResponse.value()) {
            type = StunMessageType.SharedSecretErrorResponse;
        } else if (messageType == StunMessageType.SharedSecretRequest.value()) {
            type = StunMessageType.SharedSecretRequest;
        } else if (messageType == StunMessageType.SharedSecretResponse.value()) {
            type = StunMessageType.SharedSecretResponse;
        } else {
            throw new IllegalArgumentException("Invalid STUN message type value !");
        }
//        System.out.println("StunMessageType " + type);
        // Message Length
        int messageLength = data[offset++] << 8 | data[offset++];
        // Magic Cookie
        magicCookie = data[offset++] << 24 | data[offset++] << 16 | data[offset++] << 8 | data[offset++];

        // Transaction ID
        transactionId = new byte[12];
        System.arraycopy(data, offset, transactionId, 0, 12);
        offset += 12;

        //--- Message attributes ---------------------------------------------
        while (offset - 20 < messageLength) {
//            System.out.println("offset " + offset);
            /* RFC 3489 11.2.
                Each attribute is TLV encoded, with a 16 bit type, 16 bit length, and variable value:

                0                   1                   2                   3
                0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
               +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
               |         Type                  |            Length             |
               +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
               |                             Value                             ....
               +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
            */

            // Type
            AttributeType type = AttributeType.getTypeByValue((data[offset++] << 8 | data[offset++]) & 0xFFFF);
//            System.out.println("AttributeType " + type);
            // Length
            int length = data[offset++] << 8 | data[offset++];
//            System.out.println("Attribute Length " + length);
            // MAPPED-ADDRESS
            if (type == AttributeType.MappedAddress) {
//                System.out.println("type == AttributeType.MappedAddress");
                mappedAddress = parseIPAddr(data, offset);
//                System.out.println("mappedAddress " + mappedAddress.getAddress() + " " + mappedAddress.getPort());
                offset += 8;
            }
            // RESPONSE-ADDRESS
            else if (type == AttributeType.ResponseAddress) {
                responseAddress = parseIPAddr(data, offset);
                offset += 8;
            }
            // CHANGE-REQUEST
            else if (type == AttributeType.ChangeRequest) {
				/*
                    The CHANGE-REQUEST attribute is used by the client to request that
                    the server use a different address and/or port when sending the
                    response.  The attribute is 32 bits long, although only two bits (A
                    and B) are used:

                     0                   1                   2                   3
                     0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
                    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
                    |0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 A B 0|
                    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+

                    The meaning of the flags is:

                    A: This is the "change IP" flag.  If true, it requests the server
                       to send the Binding Response with a different IP address than the
                       one the Binding Request was received on.

                    B: This is the "change port" flag.  If true, it requests the
                       server to send the Binding Response with a different port than the
                       one the Binding Request was received on.
                */

                // Skip 3 bytes
                offset += 3;

                changeRequest = new StunChangeRequest((data[offset] & 4) != 0, (data[offset] & 2) != 0);
                offset++;
            }
            // SOURCE-ADDRESS
            else if (type == AttributeType.SourceAddress) {
                sourceAddress = parseIPAddr(data, offset);
                offset += 8;
            }
            // CHANGED-ADDRESS
            else if (type == AttributeType.ChangedAddress) {
                changedAddress = parseIPAddr(data, offset);
                offset += 8;
            }
            // USERNAME
//            else if (type == AttributeType.Username)
//            {
//                UserName = Encoding.Default.GetString(data, offset, length);
//                offset += length;
//            }
//            // PASSWORD
//            else if (type == AttributeType.Password)
//            {
//                Password = Encoding.Default.GetString(data, offset, length);
//                offset += length;
//            }
            // MESSAGE-INTEGRITY
            else if (type == AttributeType.MessageIntegrity) {
                offset += length;
            }
            // ERROR-CODE
            else if (type == AttributeType.ErrorCode) {
				/* 3489 11.2.9.
                    0                   1                   2                   3
                    0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
                    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
                    |                   0                     |Class|     Number    |
                    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
                    |      Reason Phrase (variable)                                ..
                    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
                */

                int code = (data[offset + 2] & 0x7) * 100 + (data[offset + 3] & 0xFF);

                errorCode = new StunErrorCode(code, new String(data, offset + 4, length - 4));
                offset += length;
            }
            // UNKNOWN-ATTRIBUTES
            else if (type == AttributeType.UnknownAttribute) {
                offset += length;
            }
            // REFLECTED-FROM
//            else if (type == AttributeType.ReflectedFrom)
//            {
//                ReflectedFrom = parseIPAddr(data, ref offset);
//            }
            // XorMappedAddress
            // XorOnly
            // ServerName
//            else if (type == AttributeType.ServerName)
//            {
//                ServerName = Encoding.Default.GetString(data, offset, length);
//                offset += length;
//            }
            // Unknown
            else {
                offset += length;
            }
        }
    }

    // Converts this to raw STUN packet.
    public byte[] toByteData() {
			/* RFC 5389 6.
                All STUN messages MUST start with a 20-byte header followed by zero
                or more Attributes.  The STUN header contains a STUN message type,
                magic cookie, transaction ID, and message length.

                 0                   1                   2                   3
                 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
                 +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
                 |0 0|     STUN Message Type     |         Message Length        |
                 +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
                 |                         Magic Cookie                          |
                 +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
                 |                                                               |
                 |                     Transaction ID (96 bits)                  |
                 |                                                               |
                 +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+

               The message length is the count, in bytes, of the size of the
               message, not including the 20 byte header.
            */

        // We allocate 512 for header, that should be more than enough.
        byte[] msg = new byte[512];

        int offset = 0;

        //--- message header -------------------------------------

        // STUN Message Type (2 bytes)
        msg[offset++] = (byte) ((type.value() >> 8) & 0x3F);
        msg[offset++] = (byte) (type.value() & 0xFF);

        // Message Length (2 bytes) will be assigned at last.
        msg[offset++] = 0;
        msg[offset++] = 0;

        // Magic Cookie
        msg[offset++] = (byte) ((magicCookie >> 24) & 0xFF);
        msg[offset++] = (byte) ((magicCookie >> 16) & 0xFF);
        msg[offset++] = (byte) ((magicCookie >> 8) & 0xFF);
        msg[offset++] = (byte) (magicCookie & 0xFF);

        // Transaction ID (16 bytes)
        System.arraycopy(transactionId, 0, msg, offset, 12);
        offset += 12;

        //--- Message attributes ------------------------------------

			/* RFC 3489 11.2.
                After the header are 0 or more attributes.  Each attribute is TLV
                encoded, with a 16 bit type, 16 bit length, and variable value:

                0                   1                   2                   3
                0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
               +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
               |         Type                  |            Length             |
               +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
               |                             Value                             ....
               +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
            */

        if (mappedAddress != null) {
            storeEndPoint(AttributeType.MappedAddress, mappedAddress, msg, offset);
            offset += 12;
        } else if (responseAddress != null) {
            storeEndPoint(AttributeType.ResponseAddress, responseAddress, msg, offset);
            offset += 12;
        } else if (changeRequest != null) {
				/*
                    The CHANGE-REQUEST attribute is used by the client to request that
                    the server use a different address and/or port when sending the
                    response.  The attribute is 32 bits long, although only two bits (A
                    and B) are used:

                     0                   1                   2                   3
                     0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
                    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
                    |0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 A B 0|
                    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+

                    The meaning of the flags is:

                    A: This is the "change IP" flag.  If true, it requests the server
                       to send the Binding Response with a different IP address than the
                       one the Binding Request was received on.

                    B: This is the "change port" flag.  If true, it requests the
                       server to send the Binding Response with a different port than the
                       one the Binding Request was received on.
                */

            // Attribute header
            msg[offset++] = (byte) (AttributeType.ChangeRequest.value() >> 8);
            msg[offset++] = (byte) (AttributeType.ChangeRequest.value() & 0xFF);
            msg[offset++] = 0;
            msg[offset++] = 4;

            msg[offset++] = 0;
            msg[offset++] = 0;
            msg[offset++] = 0;
            msg[offset++] = (byte) ((changeRequest.isChangeIp() ? 1 : 0) << 2 | (changeRequest.isChangePort() ? 1 : 0) << 1);
        } else if (sourceAddress != null) {
            storeEndPoint(AttributeType.SourceAddress, sourceAddress, msg, offset);
            offset += 12;
        } else if (changedAddress != null) {
            storeEndPoint(AttributeType.ChangedAddress, changedAddress, msg, offset);
            offset += 12;
        }
//        else if (UserName != null)
//        {
//            var userBytes = Encoding.ASCII.GetBytes(UserName);
//
//            // Attribute header
//            msg[offset++] = (int)AttributeType.Username >> 8;
//            msg[offset++] = (int)AttributeType.Username & 0xFF;
//            msg[offset++] = (byte)(userBytes.Length >> 8);
//            msg[offset++] = (byte)(userBytes.Length & 0xFF);
//
//            Array.Copy(userBytes, 0, msg, offset, userBytes.Length);
//            offset += userBytes.Length;
//        }
//        else if (Password != null)
//        {
//            var userBytes = Encoding.ASCII.GetBytes(UserName);
//
//            // Attribute header
//            msg[offset++] = (int)AttributeType.Password >> 8;
//            msg[offset++] = (int)AttributeType.Password & 0xFF;
//            msg[offset++] = (byte)(userBytes.Length >> 8);
//            msg[offset++] = (byte)(userBytes.Length & 0xFF);
//
//            Array.Copy(userBytes, 0, msg, offset, userBytes.Length);
//            offset += userBytes.Length;
//        }
        else if (errorCode != null) {
            /* 3489 11.2.9.
                0                   1                   2                   3
                0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
                +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
                |                   0                     |Class|     Number    |
                +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
                |      Reason Phrase (variable)                                ..
                +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
            */

            byte[] reasonBytes = errorCode.getReasonText().getBytes();

            // Header
            msg[offset++] = 0;
            msg[offset++] = (byte) AttributeType.ErrorCode.value();
            msg[offset++] = 0;
            msg[offset++] = (byte) (4 + reasonBytes.length);

            // Empty
            msg[offset++] = 0;
            msg[offset++] = 0;
            // Class
            msg[offset++] = (byte) Math.floor(errorCode.getCode() / 100.0);
            // Number
            msg[offset++] = (byte) (errorCode.getCode() & 0xFF);
            // ReasonPhrase
            System.arraycopy(reasonBytes, 0, msg, offset, reasonBytes.length);
            offset += reasonBytes.length;
        }
//        else if (ReflectedFrom != null)
//        {
//            storeEndPoint(AttributeType.ReflectedFrom, ReflectedFrom, msg, offset);
//            offset += 12;
//        }

        // Update Message Length. NOTE: 20 bytes header not included.
        msg[2] = (byte) ((offset - 20) >> 8);
        msg[3] = (byte) ((offset - 20) & 0xFF);

        // Make retVal with actual size.
        byte[] retVal = new byte[offset];
        System.arraycopy(msg, 0, retVal, 0, retVal.length);

        return retVal;
    }

    private static InetSocketAddress parseIPAddr(byte[] data, int offset) {
        /*
            It consists of an eight bit address family, and a sixteen bit
            port, followed by a fixed length value representing the IP address.

            0                   1                   2                   3
            0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
            +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
            |x x x x x x x x|    Family     |           Port                |
            +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
            |                             Address                           |
            +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
        */

        // Skip family
        offset++;
//        System.out.println("地址族 " + data[offset]);
        offset++;
        // Port
//        int port = data[offsset++] << 8 | data[offset++];
//        int port = data[offset++] << 8 | data[offset++];

//        System.out.println(conver2HexStr(data[offset++]));
//        System.out.println(conver2HexStr(data[offset++]));

        byte[] portBytes = {data[offset++], data[offset++]};
        int port = bytes2Int(portBytes);

//        String portStr = conver2HexStr(data[offset++]) + conver2HexStr(data[offset++]);
//        int port = Integer.valueOf(portStr, 2);
//        System.out.println("parseIPAddr port " + port);
//        Long a = Integer.toUnsignedLong(port);
//        System.out.println("parseIPAddr port " + a);

        // Address
//        byte[] ip = new byte[4];
//        System.out.println("ip[0] " + byte2Int(data[offset++]));
//        System.out.println("ip[1] " + byte2Int(data[offset++]));
//        System.out.println("ip[2] " + byte2Int(data[offset++]));
//        System.out.println("ip[3] " + byte2Int(data[offset++]));
//        ip[0] = data[offset++];
//        ip[1] = data[offset++];
//        ip[2] = data[offset++];
//        ip[3] = data[offset];
        String ip = byte2Int(data[offset++]) + "." + byte2Int(data[offset++]) + "." + byte2Int(data[offset++]) + "." + byte2Int(data[offset++]);
//        System.out.println("parseIPAddr ip " + ip);
        // offset总共加了8

        return new InetSocketAddress(ip, port);
    }

    private static String conver2HexStr(byte b) {
        StringBuffer result = new StringBuffer();
        return result.append(Long.toString(b & 0xff, 2)).toString();
    }

    private static int byte2Int(byte b) {
        return Integer.valueOf(conver2HexStr(b), 2);
    }

    private static int bytes2Int(byte[] b) {
        StringBuffer result = new StringBuffer();
        for (int i = 0; i < b.length; i++) {
            result.append(conver2HexStr(b[i]));
        }
        return Integer.valueOf(result.toString(), 2);
    }

    private static void storeEndPoint(AttributeType type, InetSocketAddress endPoint, byte[] message, int offset) {
        /*
            It consists of an eight bit address family, and a sixteen bit
            port, followed by a fixed length value representing the IP address.

            0                   1                   2                   3
            0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
            +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
            |x x x x x x x x|    Family     |           Port                |
            +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
            |                             Address                           |
            +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
        */

        // Header
        message[offset++] = (byte) (type.value >> 8);
        message[offset++] = (byte) (type.value & 0xFF);
        message[offset++] = 0;
        message[offset++] = 8;

        // Unused
        message[offset++] = 0;
        // Family
        message[offset++] = (byte) 0x01;
        // Port
        message[offset++] = (byte) (endPoint.getPort() >> 8);
        message[offset++] = (byte) (endPoint.getPort() & 0xFF);
        // Address
        byte[] ipBytes = endPoint.getAddress().getAddress();
        message[offset++] = ipBytes[0];
        message[offset++] = ipBytes[1];
        message[offset++] = ipBytes[2];
        message[offset++] = ipBytes[3];

        // offset总共加了12
    }
}
