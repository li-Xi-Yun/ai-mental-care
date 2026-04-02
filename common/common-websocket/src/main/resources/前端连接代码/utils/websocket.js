import { API_BASE_URL } from '@/api'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

const WEBSOCKET_ENDPOINT = '/ws/ai-mental-care'
const MAX_RECONNECT_ATTEMPTS = 5
const INITIAL_RECONNECT_DELAY = 1000

class WebSocketService {
  constructor() {
    this.stompClient = null
    this.reconnectAttempts = 0
    this.reconnectTimer = null
    this.isManualClose = false
    this.connectionStatus = 'disconnected'
    this.subscriptions = new Map()
    this.listeners = {
      open: [],
      message: [],
      close: [],
      error: []
    }
  }

  getWebsocketUrl() {
    const { protocol, host } = new URL(API_BASE_URL)
    return `${protocol}//${host}${WEBSOCKET_ENDPOINT}`
  }

  connect() {
    if (this.stompClient?.connected) {
      console.log('WebSocket 已连接，无需重复连接')
      return
    }

    this.isManualClose = false
    const connectUrl = this.getWebsocketUrl()
    const token = localStorage.getItem('ai_mental_care_token')

    console.log('开始连接 WebSocket：', connectUrl)
    console.log('Token 存在:', !!token)

    this.stompClient = new Client({
      webSocketFactory: () => new SockJS(connectUrl),
      connectHeaders: token ? { token } : {},
      heartbeatIncoming: 20000,
      heartbeatOutgoing: 10000,
      reconnectDelay: 0,
      debug: (str) => console.log('STOMP Debug:', str),

      onConnect: (frame) => {
        console.log('WebSocket 连接成功', frame)
        this.connectionStatus = 'connected'
        this.reconnectAttempts = 0
        this.emit('open', frame)
      },

      onDisconnect: (frame) => {
        console.log('WebSocket 断开连接', frame)
        this.connectionStatus = 'disconnected'
        this.subscriptions.clear()
        this.emit('close', frame)
        if (!this.isManualClose) {
          this.scheduleReconnect()
        }
      },

      onStompError: (frame) => {
        console.error('STOMP 错误', frame)
        this.connectionStatus = 'error'
        this.emit('error', frame)
      },

      onWebSocketError: (error) => {
        console.error('WebSocket 错误', error)
        this.connectionStatus = 'error'
        this.emit('error', error)
      },

      onWebSocketClose: (event) => {
        console.log('WebSocket 关闭', event.code, event.reason)
        this.connectionStatus = 'disconnected'
        this.subscriptions.clear()
        this.emit('close', event)
        if (!this.isManualClose) {
          this.scheduleReconnect()
        }
      }
    })

    this.connectionStatus = 'connecting'
    this.stompClient.activate()
  }

  scheduleReconnect() {
    if (this.reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
      console.log('达到最大重连次数，停止重连')
      return
    }

    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
    }

    this.reconnectAttempts++
    const delay = INITIAL_RECONNECT_DELAY * this.reconnectAttempts
    console.log(`将在 ${delay}ms 后进行第 ${this.reconnectAttempts} 次重连`)

    this.reconnectTimer = setTimeout(() => {
      this.connect()
    }, delay)
  }

  subscribe(destination, callback, headers = {}) {
    if (!this.stompClient?.connected) {
      console.error('WebSocket 未连接，无法订阅')
      return null
    }

    try {
      const subscription = this.stompClient.subscribe(destination, (message) => {
        try {
          const data = JSON.parse(message.body)
          callback(data, message)
        } catch {
          callback(message.body, message)
        }
      }, headers)

      this.subscriptions.set(destination, subscription)
      console.log('订阅成功:', destination)
      return subscription
    } catch (error) {
      console.error('订阅失败', error)
      return null
    }
  }

  unsubscribe(destination) {
    const subscription = this.subscriptions.get(destination)
    if (subscription) {
      subscription.unsubscribe()
      this.subscriptions.delete(destination)
      return true
    }
    return false
  }

  send(destination, body = {}, headers = {}) {
    if (!this.stompClient?.connected) {
      console.error('WebSocket 未连接，无法发送消息')
      return false
    }

    try {
      this.stompClient.publish({
        destination,
        headers,
        body: typeof body === 'string' ? body : JSON.stringify(body)
      })
      console.log('消息已发送:', destination, body)
      return true
    } catch (error) {
      console.error('发送消息失败', error)
      return false
    }
  }

  close() {
    this.isManualClose = true
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
    if (this.stompClient) {
      this.subscriptions.forEach((subscription) => {
        try {
          subscription.unsubscribe()
        } catch {}
      })
      this.subscriptions.clear()
      this.stompClient.deactivate()
      this.stompClient = null
    }
    this.reconnectAttempts = 0
    this.connectionStatus = 'disconnected'
    console.log('手动关闭 WebSocket')
  }

  on(event, callback) {
    if (this.listeners[event]) {
      this.listeners[event].push(callback)
    }
  }

  off(event, callback) {
    if (this.listeners[event]) {
      this.listeners[event] = this.listeners[event].filter(cb => cb !== callback)
    }
  }

  emit(event, data) {
    if (this.listeners[event]) {
      this.listeners[event].forEach(callback => {
        try {
          callback(data)
        } catch (error) {
          console.error(`事件回调错误 [${event}]`, error)
        }
      })
    }
  }

  getStatus() {
    return this.connectionStatus
  }

  isConnected() {
    return this.stompClient?.connected || false
  }
}

const webSocketService = new WebSocketService()

export default webSocketService
