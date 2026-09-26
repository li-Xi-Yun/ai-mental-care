const AppState = {
  conversationId: null,
  currentRound: 0,
  currentAiMessageEl: null,
  currentAiText: '',
  isSending: false,
  wsSubscriptions: {},

  autoTestEnabled: false,
  playSimulatedVoice: false,
  autoTestRunning: false,

  chatPageNum: 1,
  chatPageSize: 20,
  chatTotalPages: 1,
  chatLoadingOlder: false,
  chatAllLoaded: false,

  convPageNum: 1,
  convPageSize: 20,
  convTotalPages: 1,
  convListData: [],

  sidebarOpen: false,
  sidebarPageNum: 1,
  sidebarPageSize: 10,
  sidebarTotalPages: 1,

  ws: null,

  fn: {}
};