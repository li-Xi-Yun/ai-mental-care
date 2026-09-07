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
    sendMessage: '/user/conversation/ai-chat/user-message'
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

    getSubscribePath(subDestination, conversationId) {
      return `${this.userPrefix}${this.privatePrefix}${subDestination}/${conversationId}`;
    },

    getTextReplyPath(conversationId) {
      return this.getSubscribePath(this.textReply, conversationId);
    },

    getConversationNamePath(conversationId) {
      return this.getSubscribePath(this.conversationName, conversationId);
    }
  }
};