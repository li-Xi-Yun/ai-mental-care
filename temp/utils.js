function formatTime(timeStr) {
  if (!timeStr) return '';
  if (typeof timeStr === 'string') {
    return timeStr.replace('T', ' ').substring(0, 19);
  }
  return String(timeStr);
}

function getEmotionTagClass(label) {
  if (!label) return 'neutral';
  const lower = label.toLowerCase();
  const positiveWords = ['开心', '喜悦', '满足', '放松', '平静', '感激', '希望', '自信', '愉快', 'happy', 'joy', 'satisfied', 'relaxed', 'calm', 'grateful', 'hopeful', 'confident', 'positive'];
  const negativeWords = ['愤怒', '悲伤', '焦虑', '恐惧', '不满', '抱怨', '沮丧', '绝望', '厌恶', '暴怒', 'anger', 'sad', 'anxiety', 'fear', 'frustrated', 'complaint', 'depressed', 'despair', 'disgust', 'negative'];
  if (positiveWords.some(w => lower.includes(w))) return 'positive';
  if (negativeWords.some(w => lower.includes(w))) return 'negative';
  return 'neutral';
}