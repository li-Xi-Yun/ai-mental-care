const MAX_SAFE_INTEGER = Number.MAX_SAFE_INTEGER

const ID_FIELD_PATTERNS = [
  'id', 'conversationId', 'userId', 'adminId', 'analysisId',
  'currentRound', 'roundNum', 'emotionPeakRound',
  'emotionValleyRound', 'emotionStableRounds',
  'triggerRoundNum', 'needManualIntervene', 'scaleCategoryId', 'scaleId'
]

function parseJSONWithBigInt(text) {
  if (typeof text !== 'string') {
    return text
  }

  try {
    let processedText = text

    const idFieldRegex = new RegExp(`("(?:${ID_FIELD_PATTERNS.join('|')})")\\s*:\\s*(\\d+)`, 'g')
    processedText = processedText.replace(idFieldRegex, '$1:"$2"')

    const bigIntRegex = /:\s*(\d{16,})(?=\s*[,}\]])/g
    processedText = processedText.replace(bigIntRegex, ':"$1"')

    return JSON.parse(processedText)
  } catch {
    try {
      return JSON.parse(text)
    } catch {
      return text
    }
  }
}

function isUnsafeInteger(value) {
  if (typeof value === 'number') {
    return Math.abs(value) > MAX_SAFE_INTEGER
  }
  if (typeof value === 'string') {
    const num = Number(value)
    return !isNaN(num) && Math.abs(num) > MAX_SAFE_INTEGER
  }
  return false
}

function safeNumberToString(value) {
  if (value === null || value === undefined) {
    return value
  }

  if (typeof value === 'number') {
    if (isUnsafeInteger(value)) {
      return value.toString()
    }
    return value
  }

  if (typeof value === 'string') {
    const num = Number(value)
    if (!isNaN(num) && isUnsafeInteger(num)) {
      return value
    }
    return value
  }

  return value
}

function processBigIntFields(obj, fields) {
  if (obj === null || obj === undefined) {
    return obj
  }

  if (Array.isArray(obj)) {
    return obj.map(item =>
      typeof item === 'object' && item !== null
        ? processBigIntFields(item, fields)
        : item
    )
  }

  if (typeof obj !== 'object') {
    return obj
  }

  const result = { ...obj }

  for (const key in result) {
    if (result.hasOwnProperty(key)) {
      const value = result[key]

      if (fields && fields.length > 0) {
        if (fields.includes(key)) {
          result[key] = safeNumberToString(value)
        } else if (typeof value === 'object') {
          result[key] = processBigIntFields(value, fields)
        }
      } else {
        if (typeof value === 'number' || (typeof value === 'string' && !isNaN(Number(value)))) {
          result[key] = safeNumberToString(value)
        } else if (typeof value === 'object') {
          result[key] = processBigIntFields(value, fields)
        }
      }
    }
  }

  return result
}

function processIdFields(obj) {
  return processBigIntFields(obj, [...ID_FIELD_PATTERNS])
}

function safeBigIntEqual(a, b) {
  const strA = String(a)
  const strB = String(b)
  return strA === strB
}

function safeStringToNumber(value) {
  if (typeof value !== 'string') {
    return value
  }

  const num = Number(value)
  if (isNaN(num)) {
    return value
  }

  if (Math.abs(num) <= MAX_SAFE_INTEGER) {
    return num
  }

  return value
}