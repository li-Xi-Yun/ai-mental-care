const AppState = {
  conversationId: null,
  currentRound: 0,
  currentAiMessageEl: null,
  currentAiText: '',
  isSending: false,
  wsSubscriptions: {},

  /** 当前会话是否已完成适配器/管道生命周期绑定 */
  lifecycleInitialized: false,

  /** 本页会话内已绑定生命周期的会话ID（用于判断切换/模式切换时是否需要先 endLifecycle） */
  lifecycleBoundId: null,

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