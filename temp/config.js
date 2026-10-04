const CONFIG = {
  server: {
    host: 'localhost',
    port: 8088,
    get baseUrl() {
      return `http://${this.host}:${this.port}`;
    },
    get wsUrl() {
      return `http://${this.host}:${this.port}`;
    },
    get wsBinaryUrl() {
      return `ws://${this.host}:${this.port}`;
    }
  },

  api: {
    sendMessage: '/user/conversation/adapter/text/send',
    conversationList: '/user/conversation/list',
    dialogueMemory: '/user/conversaion/dialogue/',
    emotionAnalysisList: '/user/conversation/emotion-analysis/list/',
    emotionAnalysisDetail: '/user/conversation/emotion-analysis/detail/',
    emotionDiagnosisList: '/user/conversation/emotion-diagnosis/list/',
    emotionDiagnosisDetail: '/user/conversation/emotion-diagnosis/detail/',
    conversationTestSend: '/temp/conversation-test/send',

    /* ==================== 适配器 + 管道方案 ==================== */
    /* 生命周期：会话创建/绑定适配器与管道、销毁 */
    lifecycleInit: '/user/conversation/lifecycle/init',
    lifecycleEnd: '/user/conversation/lifecycle',

    /* 以下为旧链路保留（测试/回退参考），新流程不再使用 */
    audioInit: '/user/conversation/audio/init',
    audioEnd: '/user/conversation/audio',

    audioTestStream: '/temp/audio-conversation-test/stream',
    getWsTicket: '/api/get-ws-ticket'
  },

  auth: {
    tokenHeader: 'user-token',
    token: 'eyJhbGciOiJIUzUxMiJ9.eyJ1c2VySWQiOiIyMDk2ODIzMzY1ODU1Mjg5MzQ1In0.PQa5joTOL_jTVe1absVF3nL8-w-5liZSYO4duorqrOzLvOt1j0EeaXHRrCPsEoRk4-LYRbXVdj6TGGVF_X6RTg'
  },

  websocket: {
    endpoint: '/ws/ai-mental-care',
    nativeEndpoint: '/ws-native',
    userPrefix: '/user',
    privatePrefix: '/queue',
    textReply: '/text/reply',
    conversationName: '/conversation/name',

    audioReply: '/audio/reply',
    audioBinary: '/audio/binary',
    audioTest: '/audio/test',
    asrIntermediate: '/audio/asr/intermediate',

    getSubscribePath(subDestination, conversationId) {
      return `${this.userPrefix}${this.privatePrefix}${subDestination}/${conversationId}`;
    },

    getTextReplyPath(conversationId) {
      return this.getSubscribePath(this.textReply, conversationId);
    },

    getConversationNamePath(conversationId) {
      return this.getSubscribePath(this.conversationName, conversationId);
    },

    getAudioReplyPath(conversationId) {
      return this.getSubscribePath(this.audioReply, conversationId);
    },

    getAudioBinaryPath(conversationId) {
      return this.getSubscribePath(this.audioBinary, conversationId);
    },

    getAudioTestPath(conversationId) {
      return this.getSubscribePath(this.audioTest, conversationId);
    },

    getAsrIntermediatePath(conversationId) {
      return this.getSubscribePath(this.asrIntermediate, conversationId);
    },

    /* ==================== 适配器 + 管道方案：STOMP 发送目的地 ==================== */
    /* 类级 @RequestMapping 不参与 STOMP 映射，此处为 app 前缀 + @MessageMapping 路径 */
    stompSend: {
      /* AdapterController @MessageMapping("/audio/frame") */
      audioMessage: '/app/audio/frame',
      /* AdapterController @MessageMapping("/audio/vad-stop") */
      audioVadStop: '/app/audio/vad-stop'
    }
  }
};