export function getStorage<T = any>(key: string, defaultValue: T | null = null): T | null {
  try {
    const value = localStorage.getItem(key);
    return value ? JSON.parse(value) : defaultValue;
  } catch {
    return defaultValue;
  }
}

export function setStorage(key: string, value: any) {
  localStorage.setItem(key, JSON.stringify(value));
}

export function removeStorage(key: string) {
  localStorage.removeItem(key);
}

export function getSession<T = any>(key: string, defaultValue: T | null = null): T | null {
  try {
    const value = sessionStorage.getItem(key);
    return value ? JSON.parse(value) : defaultValue;
  } catch {
    return defaultValue;
  }
}

export function setSession(key: string, value: any) {
  sessionStorage.setItem(key, JSON.stringify(value));
}

export function removeSession(key: string) {
  sessionStorage.removeItem(key);
}