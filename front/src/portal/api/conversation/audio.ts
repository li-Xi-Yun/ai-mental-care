/**
 * @deprecated 音频输入走 WebSocket（STOMP /user/conversation/adapter/audio/frame），
 * 后端不存在 /user/conversation/audio/* HTTP 接口，本文件不再使用。
 * 如需接入语音，请对接 AdapterController 的 @MessageMapping("/audio/frame")。
 */
export {};
