public class MsgType {
    // 协议常量 - 参考Go版本的常量定义
    private static final byte PROTOCOL_VERSION = 0b0001;
    private static final byte DEFAULT_HEADER_SIZE = 0b0001;
    
    // Message Type
    private static final byte CLIENT_FULL_REQUEST = 0b0001;
    private static final byte CLIENT_AUDIO_ONLY_REQUEST = 0b0010;
    private static final byte SERVER_FULL_RESPONSE = 0b1001;
    private static final byte SERVER_ERROR_RESPONSE = 0b1111;
    
    // Message Type Specific Flags
    private static final byte NO_SEQUENCE = 0b0000;
    private static final byte POS_SEQUENCE = 0b0001;
    private static final byte NEG_SEQUENCE = 0b0010;
    private static final byte NEG_WITH_SEQUENCE = 0b0011;
    
    // Serialization Type
    private static final byte NO_SERIALIZATION = 0b0000;
    private static final byte JSON = 0b0001;
    
    // Compression Type
    private static final byte GZIP = 0b0001;
}
