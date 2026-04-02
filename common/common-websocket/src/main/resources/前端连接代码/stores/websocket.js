import { defineStore } from 'pinia'
import { ref } from 'vue'
import webSocketService from '@/utils/websocket'

export const useWebSocketStore = defineStore('websocket', () => {
  const status = ref('disconnected')
  const messages = ref([])
  const reconnectAttempts = ref(0)
  const subscriptions = ref(new Map())

  const init = () => {
    webSocketService.on('open', (frame) => {
      status.value = 'connected'
      reconnectAttempts.value = 0
    })

    webSocketService.on('close', (frame) => {
      status.value = 'disconnected'
      subscriptions.value.clear()
    })

    webSocketService.on('error', (error) => {
      status.value = 'error'
    })

    status.value = webSocketService.getStatus()
  }

  const connect = () => {
    webSocketService.connect()
  }

  const disconnect = () => {
    webSocketService.close()
  }

  const send = (destination, body = {}, headers = {}) => {
    return webSocketService.send(destination, body, headers)
  }

  const subscribe = (destination, callback, headers = {}) => {
    const subscription = webSocketService.subscribe(destination, (data, message) => {
      addMessage(destination, data)
      callback(data, message)
    }, headers)

    if (subscription) {
      subscriptions.value.set(destination, subscription)
    }

    return subscription
  }

  const unsubscribe = (destination) => {
    const success = webSocketService.unsubscribe(destination)
    if (success) {
      subscriptions.value.delete(destination)
    }
    return success
  }

  const addMessage = (destination, data) => {
    messages.value.push({
      id: Date.now(),
      destination,
      data,
      timestamp: new Date().toISOString()
    })
    if (messages.value.length > 100) {
      messages.value.shift()
    }
  }

  const clearMessages = () => {
    messages.value = []
  }

  const isConnected = () => {
    return webSocketService.isConnected()
  }

  return {
    status,
    messages,
    reconnectAttempts,
    subscriptions,
    init,
    connect,
    disconnect,
    send,
    subscribe,
    unsubscribe,
    clearMessages,
    isConnected
  }
})
