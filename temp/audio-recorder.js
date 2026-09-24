/**
 * 音频录制器
 * 封装浏览器 MediaRecorder API，提供录音、停止、音频数据获取等功能
 */
class AudioRecorder {
  constructor() {
    this.mediaRecorder = null;
    this.stream = null;
    this.audioChunks = [];
    this.isRecording = false;
    this.onDataAvailable = null;
    this.onStateChange = null;
    this.analyser = null;
    this.animationFrame = null;
    this.onVolumeChange = null;
  }

  async start() {
    if (this.isRecording) return;

    try {
      this.stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      this.audioChunks = [];

      const mimeType = this._getSupportedMimeType();
      this.mediaRecorder = new MediaRecorder(this.stream, {
        mimeType: mimeType,
        audioBitsPerSecond: 16000
      });

      this.mediaRecorder.ondataavailable = (event) => {
        if (event.data.size > 0) {
          this.audioChunks.push(event.data);
          if (this.onDataAvailable) {
            this.onDataAvailable(event.data);
          }
        }
      };

      this.mediaRecorder.onstart = () => {
        this.isRecording = true;
        if (this.onStateChange) this.onStateChange(true);
      };

      this.mediaRecorder.onstop = () => {
        this.isRecording = false;
        this._stopVolumeMeter();
        this._releaseStream();
        if (this.onStateChange) this.onStateChange(false);
      };

      this.mediaRecorder.onerror = (event) => {
        console.error('MediaRecorder错误:', event);
        this.isRecording = false;
        this._stopVolumeMeter();
        this._releaseStream();
        if (this.onStateChange) this.onStateChange(false);
      };

      this.mediaRecorder.start(100);
      this._startVolumeMeter();
    } catch (error) {
      console.error('启动录音失败:', error);
      throw error;
    }
  }

  stop() {
    return new Promise((resolve) => {
      if (!this.mediaRecorder || !this.isRecording) {
        resolve(null);
        return;
      }

      this.mediaRecorder.onstop = async () => {
        this.isRecording = false;
        this._stopVolumeMeter();
        this._releaseStream();
        if (this.onStateChange) this.onStateChange(false);

        const audioBlob = new Blob(this.audioChunks, { type: this.mediaRecorder.mimeType });
        const base64Data = await this._blobToBase64(audioBlob);
        resolve(base64Data);
      };

      this.mediaRecorder.stop();
    });
  }

  _getSupportedMimeType() {
    const types = ['audio/webm', 'audio/webm;codecs=opus', 'audio/ogg;codecs=opus', 'audio/mp4', 'audio/wav'];
    for (const type of types) {
      if (MediaRecorder.isTypeSupported(type)) {
        return type;
      }
    }
    return 'audio/webm';
  }

  _blobToBase64(blob) {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onloadend = () => {
        const base64 = reader.result.split(',')[1];
        resolve(base64);
      };
      reader.onerror = reject;
      reader.readAsDataURL(blob);
    });
  }

  _startVolumeMeter() {
    if (!this.stream) return;

    try {
      const audioContext = new (window.AudioContext || window.webkitAudioContext)();
      const source = audioContext.createMediaStreamSource(this.stream);
      this.analyser = audioContext.createAnalyser();
      this.analyser.fftSize = 256;
      source.connect(this.analyser);

      const dataArray = new Uint8Array(this.analyser.frequencyBinCount);
      const updateVolume = () => {
        if (!this.analyser) return;
        this.analyser.getByteFrequencyData(dataArray);
        let sum = 0;
        for (let i = 0; i < dataArray.length; i++) {
          sum += dataArray[i];
        }
        const avg = sum / dataArray.length;
        const volume = Math.min(100, Math.round((avg / 128) * 100));
        if (this.onVolumeChange) this.onVolumeChange(volume);
        this.animationFrame = requestAnimationFrame(updateVolume);
      };
      updateVolume();
    } catch (e) {
      console.warn('音量检测初始化失败:', e);
    }
  }

  _stopVolumeMeter() {
    if (this.animationFrame) {
      cancelAnimationFrame(this.animationFrame);
      this.animationFrame = null;
    }
    this.analyser = null;
  }

  _releaseStream() {
    if (this.stream) {
      this.stream.getTracks().forEach(track => track.stop());
      this.stream = null;
    }
  }
}