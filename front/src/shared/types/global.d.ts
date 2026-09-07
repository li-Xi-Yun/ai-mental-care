declare global {
  interface Window {
    __APP_INFO__: {
      env: string;
      version: string;
    };
  }
}

export {};