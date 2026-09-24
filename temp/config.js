const CONFIG = {
  server: {
    host: 'localhost',
    port: 8088,
    get baseUrl() {
      return `http://${this.host}:${this.port}`;
    },
    get wsUrl() {
      return `http://${this.host}:${this.port}`;
    }
  },

  api: {
    sendMessage: '/user/conversation/ai-chat/user-message',
    conversationList: '/user/conversation/list',
    dialogueMemory: '/user/conversaion/dialogue/',
    emotionAnalysisList: '/user/conversation/emotion-analysis/list/',
    emotionAnalysisDetail: '/user/conversation/emotion-analysis/detail/',
    emotionDiagnosisList: '/user/conversation/emotion-diagnosis/list/',
    emotionDiagnosisDetail: '/user/conversation/emotion-diagnosis/detail/',
    conversationTestSend: '/temp/conversation-test/send',

    audioInit: '/user/conversation/audio/init',
    audioEnd: '/user/conversation/audio'
  },

  auth: {
    tokenHeader: 'user-token',
    token: 'eyJhbGciOiJIUzUxMiJ9.eyJ1c2VySWQiOiIyMDk2ODIzMzY1ODU1Mjg5MzQ1In0.PQa5joTOL_jTVe1absVF3nL8-w-5liZSYO4duorqrOzLvOt1j0EeaXHRrCPsEoRk4-LYRbXVdj6TGGVF_X6RTg'
  },

  websocket: {
    endpoint: '/ws/ai-mental-care',
    userPrefix: '/user',
    privatePrefix: '/queue',
    textReply: '/text/reply',
    conversationName: '/conversation/name',

    audioReply: '/audio/reply',
    audioBinary: '/audio/binary',
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

    getAsrIntermediatePath(conversationId) {
      return this.getSubscribePath(this.asrIntermediate, conversationId);
    },

    stompSend: {
      audioMessage: '/app/message/send',
      audioInterrupt: '/app/interrupt'
    }
  }
};