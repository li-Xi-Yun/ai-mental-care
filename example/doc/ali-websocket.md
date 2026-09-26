本文介绍如何使用智能语音交互流式文本语音合成的Java SDK，包括SDK的安装方法及SDK代码示例等。

## **前提条件**

在使用SDK之前，请先阅读[接口说明](https://help.aliyun.com/zh/isi/developer-reference/interface-description)。

## **下载安装**

1.  从Maven服务器下载最新版本的SDK[nls-sdk-java-demo+flowingtts+3.zip](https://help-static-aliyun-doc.aliyuncs.com/file-manage-files/zh-CN/20240322/wajhim/nls-sdk-java-demo_flowingtts_3.zip)。

    ```
    <dependency>
        <groupId>com.alibaba.nls</groupId>
        <artifactId>nls-sdk-tts</artifactId>
        <version>2.2.14</version>
    </dependency>
    <dependency>
        <groupId>com.alibaba.nls</groupId>
        <artifactId>nls-sdk-common</artifactId>
        <version>2.2.14</version>
    </dependency>
    ```

    **重要**

    Java SDK 从 **2.1.7** 版本开始（含2.1.7），语音合成SDK（包括实时长文本语音合成）**SpeechSynthesizer** 的 **waitForComplete** 接口的超时时间单位从 **秒** 变更为 **毫秒** 。

2.  解压该ZIP文件。

3.  在pom.xml文件所在的目录运行`mvn package`，会在target目录生成可执行JAR：nls-example-tts-2.0.0-jar-with-dependencies.jar。

4.  将JAR包拷贝到您应用所在的服务器，用于快速验证及压测服务。

5.  服务验证。运行如下代码，并按提示提供相应参数。运行后在命令执行目录生成logs/nls.log，并且将合成的音频保存在flowingTts.wav。

    ```
    java -cp nls-example-flowing-tts-2.0.0-jar-with-dependencies.jar com.alibaba.nls.client.FlowingSpeechSynthesizerDemo <your-api-key> <your-token>
    ```


## **关键接口**

-   NlsClient：语音处理客户端，利用该客户端可以进行一句话识别、实时语音识别和语音合成的语音处理任务。该客户端为线程安全，建议全局仅创建一个实例。

-   FlowingSpeechSynthesizer：流式文本语音合成实时语音合成处理类，通过该接口请求参数，发送请求，非线程安全。

-   FlowingSpeechSynthesizerListener：流式文本语音合成实时语音合成监听类，监听返回结果。非线程安全。需要实现如下抽象方法：

    ```
    /**
    * 服务端检测到了一句话的开始
    * @param response
    */
    abstract public void onSentenceBegin(FlowingSpeechSynthesizerResponse response) ;
    /**
    * 接收到语音合成音频数据流
    * @param message 二进制音频数据
    */
    abstract public void onAudioData(ByteBuffer message);
    /**
    * 服务端检测到了一句话的结束，并返回这句话的起止位置与所有时间戳
    * @param response
    */
    abstract public void onSentenceEnd(FlowingSpeechSynthesizerResponse response) ;
    /**
    * 合成结束
    * @param response
    */
    abstract public void onSynthesisComplete(FlowingSpeechSynthesizerResponse response) ;
    /**
    * 失败处理
    * @param response
    */
    abstract public void onFail(FlowingSpeechSynthesizerResponse response) ;
    /**
    * 增量在response=>payload中返回时间戳
    * @param response
    */
    abstract public void onSentenceSynthesis(FlowingSpeechSynthesizerResponse response) ;
    ```


**重要**

SDK调用注意事项：

-   NlsClient使用Netty框架，NlsClient对象的创建会消耗一定时间和资源，一经创建可以重复使用。建议调用程序将NlsClient的创建和关闭与程序本身的生命周期相结合。

-   SpeechSynthesizer对象不可重复使用，一个语音合成任务对应一个SpeechSynthesizer对象。例如，大模型和用户的N次对话要进行N次语音合成任务，创建N个SpeechSynthesizer对象。

-   SpeechSynthesizerListener对象和SpeechSynthesizer对象是一一对应的，不能将一个SpeechSynthesizerListener对象设置到多个SpeechSynthesizer对象中，否则无法区分各语音合成任务。

-   Java SDK依赖Netty网络库，如果您的应用依赖Netty，其版本需更新至4.1.17.Final及以上。


## **代码示例**

```
package com.alibaba.nls.client;

import com.alibaba.nls.client.protocol.NlsClient;
import com.alibaba.nls.client.protocol.OutputFormatEnum;
import com.alibaba.nls.client.protocol.SampleRateEnum;
import com.alibaba.nls.client.protocol.tts.FlowingSpeechSynthesizer;
import com.alibaba.nls.client.protocol.tts.FlowingSpeechSynthesizerListener;
import com.alibaba.nls.client.protocol.tts.FlowingSpeechSynthesizerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * 此示例演示了：
 *      流式文本语音合成API调用。
 */
public class FlowingSpeechSynthesizerDemo {
    private static final Logger logger = LoggerFactory.getLogger(FlowingSpeechSynthesizerDemo.class);
    private static long startTime;
    private String appKey;
    NlsClient client;
    public FlowingSpeechSynthesizerDemo(String appKey, String token, String url) {
        this.appKey = appKey;
        //创建NlsClient实例应用全局创建一个即可。生命周期可和整个应用保持一致，默认服务地址为阿里云线上服务地址。
        if(url.isEmpty()) {
            client = new NlsClient(token);
        } else {
            client = new NlsClient(url, token);
        }
    }
    private static FlowingSpeechSynthesizerListener getSynthesizerListener() {
        FlowingSpeechSynthesizerListener listener = null;
        try {
            listener = new FlowingSpeechSynthesizerListener() {
                File f=new File("flowingTts.wav");
                FileOutputStream fout = new FileOutputStream(f);
                private boolean firstRecvBinary = true;

                //流式文本语音合成开始
                public void onSynthesisStart(FlowingSpeechSynthesizerResponse response) {
                    System.out.println("name: " + response.getName() +
                                       ", status: " + response.getStatus());
                }
                //服务端检测到了一句话的开始
                public void onSentenceBegin(FlowingSpeechSynthesizerResponse response) {
                    System.out.println("name: " + response.getName() +
                                       ", status: " + response.getStatus());
                    System.out.println("Sentence Begin");
                }
                //服务端检测到了一句话的结束，获得这句话的起止位置和所有时间戳
                public void onSentenceEnd(FlowingSpeechSynthesizerResponse response) {
                    System.out.println("name: " + response.getName() +
                                       ", status: " + response.getStatus() + ", subtitles: " + response.getObject("subtitles"));

                }
                //流式文本语音合成结束
                @Override
                public void onSynthesisComplete(FlowingSpeechSynthesizerResponse response) {
                    // 调用onSynthesisComplete时，表示所有TTS数据已经接收完成，所有文本都已经合成音频并返回。
                    System.out.println("name: " + response.getName() + ", status: " + response.getStatus()+", output file :"+f.getAbsolutePath());
                }
                //收到语音合成的语音二进制数据
                @Override
                public void onAudioData(ByteBuffer message) {
                    try {
                        if(firstRecvBinary) {
                            // 此处计算首包语音流的延迟，收到第一包语音流时，即可以进行语音播放，以提升响应速度（特别是实时交互场景下）。
                            firstRecvBinary = false;
                            long now = System.currentTimeMillis();
                            logger.info("tts first latency : " + (now - FlowingSpeechSynthesizerDemo.startTime) + " ms");
                        }
                        byte[] bytesArray = new byte[message.remaining()];
                        message.get(bytesArray, 0, bytesArray.length);
                        System.out.println("write array:" + bytesArray.length);
                        fout.write(bytesArray);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                //收到语音合成的增量音频时间戳
                @Override
                public void onSentenceSynthesis(FlowingSpeechSynthesizerResponse response) {
                    System.out.println("name: " + response.getName() +
                            ", status: " + response.getStatus() + ", subtitles: " + response.getObject("subtitles"));
                }
                @Override
                public void onFail(FlowingSpeechSynthesizerResponse response){
                    // task_id是调用方和服务端通信的唯一标识，当遇到问题时，需要提供此task_id以便排查。
                    System.out.println(
                            "session_id: " + getFlowingSpeechSynthesizer().getCurrentSessionId() +
                                    ", task_id: " + response.getTaskId() +
                                    //状态码
                                    ", status: " + response.getStatus() +
                                    //错误信息
                                    ", status_text: " + response.getStatusText());
                }
            };
        } catch (Exception e) {
            e.printStackTrace();
        }
        return listener;
    }
    public void process(String[] textArray) {
        FlowingSpeechSynthesizer synthesizer = null;
        try {
            //创建实例，建立连接。
            synthesizer = new FlowingSpeechSynthesizer(client, getSynthesizerListener());
            synthesizer.setAppKey(appKey);
            //设置返回音频的编码格式。
            synthesizer.setFormat(OutputFormatEnum.WAV);
            //设置返回音频的采样率。
            synthesizer.setSampleRate(SampleRateEnum.SAMPLE_RATE_16K);
            //发音人。
            synthesizer.setVoice("siyue");
            //音量，范围是0~100，可选，默认50。
            synthesizer.setVolume(50);
            //语调，范围是-500~500，可选，默认是0。
            synthesizer.setPitchRate(0);
            //语速，范围是-500~500，默认是0。
            synthesizer.setSpeechRate(0);
            //此方法将以上参数设置序列化为JSON发送给服务端，并等待服务端确认。
            long start = System.currentTimeMillis();
            synthesizer.start();
            logger.info("tts start latency " + (System.currentTimeMillis() - start) + " ms");
            FlowingSpeechSynthesizerDemo.startTime = System.currentTimeMillis();
            //设置连续两次发送文本的最小时间间隔（毫秒），如果当前调用send时距离上次调用时间小于此值，则会阻塞并等待直到满足条件再发送文本
            synthesizer.setMinSendIntervalMS(100);
            for(String text :textArray) {
                //发送流式文本数据。
                synthesizer.send(text);
            }
            //通知服务端流式文本数据发送完毕，阻塞等待服务端处理完成。
            synthesizer.stop();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            //关闭连接
            if (null != synthesizer) {
                synthesizer.close();
            }
        }
    }
    public void shutdown() {
        client.shutdown();
    }
    public static void main(String[] args) throws Exception {
        String appKey = "your-api-key";
        String token = "your-token";
        // url取默认值
        String url = "wss://nls-gateway-cn-beijing.aliyuncs.com/ws/v1";
        if (args.length == 2) {
            appKey   = args[0];
            token       = args[1];
        } else if (args.length == 3) {
            appKey   = args[0];
            token       = args[1];
            url      = args[2];
        } else {
            System.err.println("run error, need params(url is optional): " + "<app-key> <token> [url]");
            System.exit(-1);
        }
        String[] textArray = {"百草堂与三", "味书屋 鲁迅 \n我家的后面有一个很", "大的园，相传叫作百草园。现在是早已并屋子一起卖", "给朱文公的子孙了，连那最末次的相见也已经",
                "隔了七八年，其中似乎确凿只有一些野草；但那时却是我的乐园。\n不必说碧绿的菜畦，光滑的石井栏，高大的皂荚树，紫红的桑葚；也不必说鸣蝉在树叶里长吟，肥胖的黄蜂伏在菜花",
                "上，轻捷的叫天子(云雀)忽然从草间直窜向云霄里去了。\n单是周围的短短的泥墙根一带，就有无限趣味。油蛉在这里低唱，蟋蟀们在这里弹琴。翻开断砖来，有时会遇见蜈蚣；还有斑",
                "蝥，倘若用手指按住它的脊梁，便会啪的一声，\n从后窍喷出一阵烟雾。何首乌藤和木莲藤缠络着，木莲有莲房一般的果实，何首乌有臃肿的根。有人说，何首乌根是有像人形的，吃了",
                "便可以成仙，我于是常常拔它起来，牵连不断地拔起来，\n也曾因此弄坏了泥墙，却从来没有见过有一块根像人样! 如果不怕刺，还可以摘到覆盆子，像小珊瑚珠攒成的小球，又酸又甜，",
                "色味都比桑葚要好得远......"};
        FlowingSpeechSynthesizerDemo demo = new FlowingSpeechSynthesizerDemo(appKey, token, url);
        demo.process(textArray);
        demo.shutdown();
    }
}
```

## **常见问题**

### 服务端返回“idle timeout”错误，应如何解决？

该报错是由于服务端在超过10s没有收到客户端消息，从而导致断连，返回`idle timeout`报错。

可以通过调用`FlowingSpeechSynthesizer.getConnection().sendPing()` 定期向服务端发送ping包为连接保活。
























实时语音识别通过 WebSocket 持续接收音频流并返回识别结果，适用于会议演讲、视频直播等长时间不间断识别场景。本文介绍服务地址、请求参数、识别事件和状态码。

## **计费和并发限制**

实时语音识别提供试用版和商用版，费用说明请参见[计费项](https://help.aliyun.com/zh/isi/product-overview/pricing)。

升级商用版和计费方式，请参见[计费方式](https://help.aliyun.com/zh/isi/product-overview/billing-10)；并发限制请参见[并发和 QPS 说明](https://help.aliyun.com/zh/isi/product-overview/faq-about-concurrency-and-monitoring)。

## 使用须知

**说明**

如需使用Android或iOS SDK，请参见[移动端接口说明](https://help.aliyun.com/zh/isi/developer-reference/overview-4#topic-2637808)。

调用接口前，确认音频格式、采样率和项目模型符合以下要求。

-   支持的输入格式：单声道（mono）、16 bit采样位数，包括PCM、PCM编码的WAV、OGG封装的OPUS、OGG封装的SPEEX、AMR、MP3、AAC。

-   支持的音频采样率：8000 Hz、16000 Hz。

-   支持设置返回结果：是否返回中间识别结果，在后处理中添加标点，将中文数字转为阿拉伯数字输出。

-   支持情感分析：目前仅开放中文8k情感识别功能，且使用时需关闭语义断句功能（即enable\_semantic\_sentence\_detection=False）。

-   不支持说话人分离，无法进行角色分析。

-   识别使用的语种和方言由项目模型决定，不能通过请求参数指定。模型配置方法，请参见[管理项目](https://help.aliyun.com/zh/isi/getting-started/manage-projects)。

    目前支持的语种和方言模型如下：

    -   **语种**

        | **语言** | **模型名称** | **采样率** | **标点** | **ITN** | **顺滑** | **语义断句** | **声音和文本对齐** |
                | --- | --- | --- | --- | --- | --- | --- | --- |
        | 英语  | 通用-英文，教育直播-英文，教育内容分析-英文 | 16k | 支持  | 支持  | 支持  | 不支持 | 支持  |
        | 电话客服（通用） | 8k  | 支持  | 支持  | 支持  | 不支持 | 不支持 |
        | 东南亚多语言 | 16k | 支持  | 不支持 | 不支持 | 不支持 | 不支持 |
        | 日语  | 通用-日语 | 16k | 支持  | 支持  | 不支持 | 不支持 | 支持  |
        | 西班牙语 | 通用-西班牙语 | 16k | 支持  | 支持  | 不支持 | 不支持 | 不支持 |
        | 通用-西班牙客服通用 | 8k  | 支持  | 支持  | 不支持 | 不支持 | 不支持 |
        | 阿拉伯语 | 通用-阿拉伯语 | 16k | 支持  | 不支持 | 不支持 | 不支持 | 不支持 |
        | 哈萨克语 | 通用-哈萨克语 | 16k | 支持  | 不支持 | 不支持 | 不支持 | 不支持 |
        | 韩语  | 通用-韩语 | 16k | 支持  | 支持  | 不支持 | 不支持 | 不支持 |
        | 泰语  | 通用-泰语 | 16k | 不支持 | 不支持 | 不支持 | 不支持 | 不支持 |
        | 通用-泰语客服通用 | 8k  | 不支持 | 不支持 | 不支持 | 不支持 | 不支持 |
        | 东南亚多语言 | 16k | 支持  | 不支持 | 不支持 | 不支持 | 不支持 |
        | 印尼语 | 通用-印尼语 | 16k | 支持  | 支持  | 不支持 | 不支持 | 不支持 |
        | 电话客服（通用） | 8k  | 支持  | 支持  | 不支持 | 不支持 | 不支持 |
        | 东南亚多语言 | 16k | 支持  | 不支持 | 不支持 | 不支持 | 不支持 |
        | 俄语  | 通用-俄语 | 16k | 支持  | 支持  | 不支持 | 不支持 | 不支持 |
        | 越南语 | 通用-越南语 | 16k | 支持  | 支持  | 不支持 | 不支持 | 不支持 |
        | 通用-越南语客服通用 | 8k  | 支持  | 支持  | 不支持 | 不支持 | 不支持 |
        | 东南亚多语言 | 16k | 支持  | 不支持 | 不支持 | 不支持 | 不支持 |
        | 法语  | 通用-法语 | 16k | 支持  | 支持  | 不支持 | 不支持 | 不支持 |
        | 德语  | 通用-德语 | 16k | 支持  | 支持  | 不支持 | 不支持 | 不支持 |
        | 意大利语 | 通用-意大利语 | 16k | 支持  | 不支持 | 不支持 | 不支持 | 不支持 |
        | 印地语 | 通用-印地语 | 16k | 支持  | 不支持 | 不支持 | 不支持 | 不支持 |
        | 马来语 | 通用-马来语 | 16k | 支持  | 不支持 | 不支持 | 不支持 | 不支持 |
        | 通用-马来语客服通用 | 8k  | 支持  | 不支持 | 不支持 | 不支持 | 不支持 |
        | 东南亚多语言 | 16k | 支持  | 不支持 | 不支持 | 不支持 | 不支持 |
        | 菲律宾语 | 通用-菲律宾语 | 16k | 支持  | 支持  | 不支持 | 不支持 | 不支持 |
        | 电话客服（通用） | 8k  | 支持  | 支持  | 不支持 | 不支持 | 不支持 |
        | 东南亚多语言 | 16k | 支持  | 不支持 | 不支持 | 不支持 | 不支持 |
        | 泰米尔语 | 通用-泰米尔语 | 16k | 支持  | 不支持 | 不支持 | 不支持 | 不支持 |
        | 葡萄牙语 | 通用-葡萄牙语 | 16k | 支持  | 支持  | 不支持 | 不支持 | 不支持 |
        | 土耳其语 | 通用-土耳其语 | 16k | 支持  | 不支持 | 不支持 | 不支持 | 不支持 |
        | 波兰语 | 通用-波兰语 | 16k | 支持  | 不支持 | 不支持 | 不支持 | 不支持 |
        | 乌克兰语 | 通用-乌克兰语 | 16k | 支持  | 不支持 | 不支持 | 不支持 | 不支持 |
        | 罗马尼亚语 | 通用-罗马尼亚语 | 16k | 支持  | 不支持 | 不支持 | 不支持 | 不支持 |
        | 荷兰语 | 通用-荷兰语 | 16k | 支持  | 不支持 | 不支持 | 不支持 | 不支持 |
        | 希腊语 | 通用-希腊语 | 16k | 支持  | 不支持 | 不支持 | 不支持 | 不支持 |
        | 匈牙利语 | 通用-匈牙利语 | 16k | 支持  | 不支持 | 不支持 | 不支持 | 不支持 |
        | 爪哇语 | 通用-爪哇语 | 16k | 支持  | 不支持 | 不支持 | 不支持 | 不支持 |
        | 孟加拉语 | 通用-孟加拉语 | 16k | 支持  | 不支持 | 不支持 | 不支持 | 不支持 |
        | 缅甸语 | 通用-缅甸语 | 16k | 支持  | 不支持 | 不支持 | 不支持 | 不支持 |
        | 老挝语 | 通用-老挝语 | 16k | 支持  | 不支持 | 不支持 | 不支持 | 不支持 |
        | 斯瓦希里语 | 通用-斯瓦希里语 | 16k | 支持  | 不支持 | 不支持 | 不支持 | 不支持 |
        | 阿塞拜疆语 | 通用-阿塞拜疆语 | 16k | 支持  | 不支持 | 不支持 | 不支持 | 不支持 |
        | 波斯语 | 通用-波斯语 | 16k | 支持  | 不支持 | 不支持 | 不支持 | 不支持 |
        | 僧伽罗语 | 通用-僧伽罗语 | 16k | 支持  | 不支持 | 不支持 | 不支持 | 不支持 |
        | 加泰罗尼亚语 | 通用-加泰罗尼亚语 | 16k | 支持  | 不支持 | 不支持 | 不支持 | 不支持 |
        | 高棉语 | 通用-高棉语 | 16k | 支持  | 不支持 | 不支持 | 不支持 | 不支持 |
        | 希伯来语 | 通用-希伯来语 | 16k | 支持  | 不支持 | 不支持 | 不支持 | 不支持 |
        | 克罗地亚语 | 通用-克罗地亚语 | 16k | 支持  | 不支持 | 不支持 | 不支持 | 不支持 |
        | 豪萨语 | 通用-豪萨语 | 16k | 支持  | 不支持 | 不支持 | 不支持 | 不支持 |
        | 马拉地语 | 通用-马拉地语 | 16k | 支持  | 不支持 | 不支持 | 不支持 | 不支持 |
        | 泰卢固语 | 通用-泰卢固语 | 16k | 支持  | 不支持 | 不支持 | 不支持 | 不支持 |
        | 旁遮普语 | 通用-旁遮普语 | 16k | 支持  | 不支持 | 不支持 | 不支持 | 不支持 |
        | 瑞典语 | 通用-瑞典语 | 16k | 支持  | 不支持 | 不支持 | 不支持 | 不支持 |
        | 保加利亚语 | 通用-保加利亚语 | 16k | 支持  | 不支持 | 不支持 | 不支持 | 不支持 |
        | 丹麦语 | 通用-丹麦语 | 16k | 支持  | 不支持 | 不支持 | 不支持 | 不支持 |
        | 挪威语 | 通用-挪威语 | 16k | 支持  | 不支持 | 不支持 | 不支持 | 不支持 |
        | 坎纳达语 | 通用-坎纳达语 | 16k | 支持  | 不支持 | 不支持 | 不支持 | 不支持 |
        | 马拉雅拉姆语 | 通用-马拉雅拉姆语 | 16k | 支持  | 不支持 | 不支持 | 不支持 | 不支持 |
        | 捷克语 | 通用-捷克语 | 16k | 支持  | 不支持 | 不支持 | 不支持 | 不支持 |
        | 乌尔都语 | 通用-乌尔都语 | 16k | 支持  | 不支持 | 不支持 | 不支持 | 不支持 |
        | 尼泊尔语 | 通用-尼泊尔语 | 16k | 支持  | 不支持 | 不支持 | 不支持 | 不支持 |
        | 蒙古语（外蒙） | 通用-蒙古语（外蒙） | 16k | 支持  | 不支持 | 不支持 | 不支持 | 不支持 |
        | 乌兹别克语 | 通用-乌兹别克语 | 16k | 支持  | 不支持 | 不支持 | 不支持 | 不支持 |

    -   **方言**

        | **语言** | **模型名称** | **采样率** | **标点** | **ITN** | **顺滑** | **语义断句** | **声音和文本对齐** |
                | --- | --- | --- | --- | --- | --- | --- | --- |
        | 粤语  | 通用-粤语 | 16k | 支持  | 支持  | 支持  | 不支持 | 支持  |
        | 电话客服（通用） | 8k  | 支持  | 支持  | 支持  | 不支持 | 支持  |
        | 粤中自由说 | 8k  | 支持  | 支持  | 支持  | 不支持 | 不支持 |
        | 东南亚多语言 | 16k | 支持  | 不支持 | 不支持 | 不支持 | 不支持 |
        | 粤语（繁体） | 通用-粤语（繁体） | 8k  | 支持  | 不支持 | 不支持 | 不支持 | 不支持 |
        | 通用-粤语（繁体） | 16k | 支持  | 不支持 | 不支持 | 不支持 | 不支持 |
        | 四川话 | 通用-四川话 | 16k | 支持  | 支持  | 支持  | 支持  | 支持  |
        | 电话客服（通用） | 8k  | 支持  | 支持  | 支持  | 支持  | 支持  |
        | 湖北话 | 通用-湖北话 | 16k | 支持  | 支持  | 支持  | 支持  | 支持  |
        | 通用-湖北话 | 8k  | 支持  | 支持  | 支持  | 支持  | 支持  |
        | 上海话 | 通用-上海话 | 16k | 支持  | 支持  | 支持  | 支持  | 不支持 |
        | 湖南话 | 通用-湖南话 | 16k | 支持  | 支持  | 支持  | 支持  | 支持  |
        | 河南话 | 通用-河南话 | 16k | 支持  | 支持  | 支持  | 支持  | 支持  |
        | 通用-河南话 | 8k  | 支持  | 支持  | 支持  | 支持  | 支持  |
        | 浙江话 | 通用-浙江话 | 16k | 支持  | 支持  | 支持  | 支持  | 不支持 |
        | 东北话 | 通用-东北话 | 16k | 支持  | 支持  | 支持  | 支持  | 支持  |
        | 山东话 | 通用-山东话 | 16k | 支持  | 支持  | 支持  | 支持  | 支持  |
        | 天津话 | 通用-天津话 | 16k | 支持  | 支持  | 支持  | 支持  | 支持  |
        | 陕西话 | 通用-陕西话 | 16k | 支持  | 支持  | 支持  | 支持  | 支持  |
        | 山西话 | 通用-山西话 | 16k | 支持  | 支持  | 支持  | 支持  | 支持  |
        | 贵州话 | 通用-贵州话 | 16k | 支持  | 支持  | 支持  | 支持  | 支持  |
        | 云南话 | 通用-云南话 | 16k | 支持  | 支持  | 支持  | 支持  | 支持  |
        | 甘肃话 | 通用-甘肃话 | 16k | 支持  | 支持  | 支持  | 支持  | 支持  |
        | 维吾尔语 | 通用-维吾尔语 | 16k | 不支持 | 不支持 | 不支持 | 不支持 | 不支持 |
        | 通用-维吾尔语 | 8k  | 不支持 | 不支持 | 不支持 | 不支持 | 不支持 |
        | 苏州话 | 通用-苏州话 | 16k | 支持  | 支持  | 支持  | 支持  | 不支持 |
        | 闽南语 | 通用-闽南语 | 16k | 支持  | 支持  | 支持  | 支持  | 不支持 |
        | 江西话 | 通用-江西话 | 16k | 支持  | 支持  | 支持  | 支持  | 支持  |
        | 宁夏话 | 通用-宁夏话 | 16k | 支持  | 支持  | 支持  | 支持  | 支持  |
        | 广西话 | 通用-广西话 | 16k | 支持  | 支持  | 支持  | 支持  | 支持  |
        | 通用-广西话 | 8k  | 支持  | 支持  | 支持  | 支持  | 支持  |
        | 中文普通话 | 识音石 V1 - 端到端模型，教育内容分析，医疗内容分析，新闻媒体内容分析，娱乐视频内容分析，音视频离线转写（升级版），新零售领域识别模型，出行领域识别模型，汽车领域 | 16k | 支持  | 支持  | 支持  | 支持  | 支持  |
        | 中英自由说 | 16k | 支持  | 支持  | 支持  | 支持  | 不支持 |
        | 识音石 V1 - 端到端模型 | 8k  | 支持  | 支持  | 支持  | 支持  | 支持  |
        | 东南亚多语言 | 16k | 支持  | 不支持 | 不支持 | 不支持 | 不支持 |


## 就近地域智能接入

实时语音识别支持就近地域智能接入，域名为`nls-gateway.aliyuncs.com`。

推荐终端用户使用就近地域接入域名。根据调用接口时客户端所在的地理位置，系统会自动解析到最近的某个具体地域的服务器。例如在北京地域发起请求，系统会自动解析到北京地域的服务器，与指定域名`nls-gateway-cn-beijing.aliyuncs.com`的实现效果一致。

## 服务地址

| **访问类型** | **说明** | **URL** |
| 外网访问（默认上海地域） | 所有服务器均可使用外网访问URL（SDK中默认设置了外网访问URL）。 | - 上海：`wss://nls-gateway-cn-shanghai.aliyuncs.com/ws/v1` - 北京：`wss://nls-gateway-cn-beijing.aliyuncs.com/ws/v1` - 深圳：`wss://nls-gateway-cn-shenzhen.aliyuncs.com/ws/v1` |
| ECS内网访问 | 使用阿里云上海、北京、深圳ECS（即ECS地域为华东2（上海）、华北2（北京）、华南1（深圳）），可使用内网访问URL。 ECS的经典网络不能访问AnyTunnel，即不能在内网访问语音服务；如果希望使用AnyTunnel，需要创建专有网络在其内部访问。 **说明** - 使用内网访问方式，将不产生ECS实例的公网流量费用。 | - 上海：`ws://nls-gateway-cn-shanghai-internal.aliyuncs.com:80/ws/v1` - 北京：`ws://nls-gateway-cn-beijing-internal.aliyuncs.com:80/ws/v1` - 深圳：`ws://nls-gateway-cn-shenzhen-internal.aliyuncs.com:80/ws/v1` |

## 交互流程

![image](https://help-static-aliyun-doc.aliyuncs.com/assets/img/zh-CN/4003698871/CAEQShiBgMDrtPzJ2BgiIGZkZTYwMzFiMGYyMjQzNTk5MDUyODBlNTc5Y2FhNjIz4024928_20231008170233.737.svg)

**说明**

服务端响应的 `header.task_id` 标识本次识别任务。记录该值，便于排查问题。

### 1\. 鉴权

客户端与服务端建立 WebSocket 连接时，使用 NLS Token 进行鉴权。

获取方法，请参见[获取 Token](https://help.aliyun.com/zh/isi/getting-started/obtain-an-access-token-1/)。

### 2\. 开始识别

客户端发送 `StartTranscription` 指令，并设置识别参数。服务端返回 `TranscriptionStarted` 后，客户端开始发送音频。使用 SDK 时，通过对应的参数设置方法完成配置。参数含义如下：

| **参数** | **类型** | **是否必选** | **说明** |
| --- | --- | --- | --- |
| appkey | String | 是   | 在智能语音交互控制台创建的项目 Appkey。 |
| format | String | 否   | 音频格式：pcm、wav、opus、speex、amr、mp3、aac。 |
| sample\\_rate | Integer | 否   | 音频采样率，默认是16000 Hz，根据音频采样率在控制台对应项目中配置支持该采样率及场景的模型。 |
| enable\\_intermediate\\_result | Boolean | 否   | 是否返回中间识别结果，默认是false。 |
| enable\\_punctuation\\_prediction | Boolean | 否   | 是否在后处理中添加标点，默认是false。 |
| enable\\_inverse\\_text\\_normalization | Boolean | 否   | 是否开启逆文本正则化（ITN），将中文数字转为阿拉伯数字输出。默认值为 false。 |
| customization\\_id | String | 否   | 自学习模型ID。 |
| vocabulary\\_id | String | 否   | 定制泛热词ID。 |
| max\\_sentence\\_silence | Integer | 否   | 静音断句阈值，单位为毫秒。检测到语音后的静音时长超过阈值时触发断句。取值范围：200～6000，默认值：800。 开启 enable\\_semantic\\_sentence\\_detection 后，不使用此阈值进行静音断句，但参数值仍需在允许范围内。 静音时长按音频数据计算，不是停止发送数据后的等待时长。需要通过静音断句时，应持续发送包含静音段的音频；PCM 音频的静音段可以用零值采样表示。 |
| enable\\_words | Boolean | 否   | 是否开启返回词信息，默认是false。 |
| disfluency | Boolean | 否   | 过滤语气词，即声音顺滑，默认值false（关闭）。 |
| speech\\_noise\\_threshold | Float | 否   | 噪音参数阈值，参数范围：\\[-1,1\\]。取值说明如下： - 取值越趋于-1，噪音被判定为语音的概率越大。 - 取值越趋于+1，语音被判定为噪音的概率越大。 **重要** 该参数属高级参数，调整需慎重并重点测试。 |
| enable\\_semantic\\_sentence\\_detection | Boolean | 否   | 是否开启语义断句，可选，默认是False。语义断句参数需要和开启中间结果配合使用，即开启该语义断句参数需将中间结果参数同时打开：enable\\_intermediate\\_result=true。 **说明** 开启语义断句可提升识别准确率，但会小幅增加延迟，适合会议转写等场景。 |
| special\\_word\\_filter | Object（JSON 对象） | 否   | 自定义敏感词过滤，词语总数不超过 32 个。可将指定词语替换为空字符串或星号（\\*）。 直接使用 WebSocket 协议时，在请求的 payload 中传入 JSON 对象，不要传入 JSON 序列化后的字符串。 SDK 配置示例见下文。 |
| enable\\_multi\\_thresh\\_mod | Boolean | 否   | 该参数仅在enable\\_semantic\\_sentence\\_detection参数为False（即VAD断句）时生效。取值如下： - true：可以防止VAD断句切割过长。 - False（默认）：关闭。 |

以下为 Java SDK 的自定义过滤词配置片段。`transcriber` 为已初始化的识别对象。

```
            // 以实时转写为例，
            JSONObject root = new JSONObject();
            root.put("system_reserved_filter", true);

            // 将以下词语替换成空
            JSONObject root1 = new JSONObject();
            JSONArray array1 = new JSONArray();
            array1.add("开始");
            array1.add("发生");
            root1.put("word_list", array1);

            // 将以下词语替换成*
            JSONObject root2 = new JSONObject();
            JSONArray array2 = new JSONArray();
            array2.add("测试");
            root2.put("word_list", array2);

						// 可以全部设置，也可以部分设置
            root.put("filter_with_empty", root1);
            root.put("filter_with_signed", root2);

            transcriber.addCustomedParam("special_word_filter", root);
```

### 3\. 接收识别结果

客户端持续发送音频数据并接收识别事件。以下 JSON 示例使用中文语音，展示各事件的消息结构。

header对象参数说明：

| **参数** | **类型** | **说明** |
| --- | --- | --- |
| namespace | String | 消息所属的命名空间。 |
| name | String | 事件名称。 |
| status | Integer | 状态码，表示请求是否成功，见服务状态码。 |
| status\\_text | String | 状态消息。 |
| task\\_id | String | 任务全局唯一ID，请记录该值，便于排查问题。 |
| message\\_id | String | 本次消息的ID。 |

## **SentenceBegin**

SentenceBegin事件表示服务端检测到了一句话的开始。实时语音识别服务的智能断句功能会判断出一句话的开始与结束，举例如下：

```
{
        "header": {
                "namespace": "SpeechTranscriber",
                "name": "SentenceBegin",
                "status": 20000000,
                "message_id": "a426f3d4618447519c9d85d1a0d1****",
                "task_id": "5ec521b5aa104e3abccf3d361822****",
                "status_text": "Gateway:SUCCESS:Success."
        },
        "payload": {
                "index": 1,
                "time": 0
        }
}
```

payload对象参数说明：

| **参数** | **类型** | **说明** |
| --- | --- | --- |
| index | Integer | 句子编号，从1开始递增。 |
| time | Integer | 当前已处理的音频时长，单位为毫秒。 |

## **TranscriptionResultChanged**

TranscriptionResultChanged事件表示识别结果发生了变化。仅当enable\_intermediate\_result取值为true时会多次返回此消息，即一句话的中间识别结果，举例如下：

```
{
        "header": {
                "namespace": "SpeechTranscriber",
                "name": "TranscriptionResultChanged",
                "status": 20000000,
                "message_id": "dc21193fada84380a3b6137875ab****",
                "task_id": "5ec521b5aa104e3abccf3d361822****",
                "status_text": "Gateway:SUCCESS:Success."
        },
        "payload": {
                "index": 1,
                "time": 1835,
                "result": "北京的天",
                "confidence": 1.0,
                "words": [{
                        "text": "北京",
                        "startTime": 630,
                        "endTime": 930
                }, {
                        "text": "的",
                        "startTime": 930,
                        "endTime": 1110
                }, {
                        "text": "天",
                        "startTime": 1110,
                        "endTime": 1140
                }]
        }
}       
```

此事件的 header.name 为 TranscriptionResultChanged，表示句子的中间识别结果。

payload对象参数说明：

| **参数** | **类型** | **说明** |
| --- | --- | --- |
| index | Integer | 句子编号，从1开始递增。 |
| time | Integer | 当前已处理的音频时长，单位为毫秒。 |
| result | String | 当前句子的识别结果。 |
| words | List< Word > | 当前句子的词信息，需要将enable\\_words设置为true。 |
| confidence | Double | 当前句子识别结果的置信度，取值范围：\\[0.0,1.0\\]。值越大表示置信度越高。 |

## **SentenceEnd**

SentenceEnd事件表示服务端检测到了一句话的结束，并附带返回该句话的识别结果，举例如下：

```
{
        "header": {
                "namespace": "SpeechTranscriber",
                "name": "SentenceEnd",
                "status": 20000000,
                "message_id": "c3a9ae4b231649d5ae05d4af36fd****",
                "task_id": "5ec521b5aa104e3abccf3d361822****",
                "status_text": "Gateway:SUCCESS:Success."
        },
        "payload": {
                "index": 1,
                "time": 1820,
                "begin_time": 0,
                "result": "北京的天气。",
                "confidence": 1.0,
                "words": [{
                        "text": "北京",
                        "startTime": 630,
                        "endTime": 930
                }, {
                        "text": "的",
                        "startTime": 930,
                        "endTime": 1110
                }, {
                        "text": "天气",
                        "startTime": 1110,
                        "endTime": 1380
                }],
                  "emo_tag": "neutral",
                  "emo_confidence": 0.931
        }
}
```

此事件的 header.name 为 SentenceEnd，表示识别到句子的结束。

payload对象参数说明：

| **参数** | **类型** | **说明** |
| --- | --- | --- |
| index | Integer | 句子编号，从1开始递增。 |
| time | Integer | 当前已处理的音频时长，单位为毫秒。 |
| begin\\_time | Integer | 当前句子对应的SentenceBegin事件的时间，单位是毫秒。 |
| result | String | 当前的识别结果。 |
| words | List< Word > | 当前句子的词信息，需要将enable\\_words设置为true。 |
| confidence | Double | 当前句子识别结果的置信度，取值范围：\\[0.0,1.0\\]。值越大表示置信度越高。 |

中文 8 kHz 情感识别还会返回以下字段。使用时需关闭语义断句。

| **参数** | **类型** | **说明** |
| --- | --- | --- |
| emo\\_tag | String | 当前句子的情感，包含positive（正面情感，如开心、满意）、negative（负面情感，如愤怒、沉闷、失望）、neutral（无明显情感）三种类别。 |
| emo\\_confidence | Double | 当前句子识别情感的置信度，取值范围：\\[0.0,1.0\\]。值越大表示置信度越高。 |

Words对象参数说明：

| **参数** | **类型** | **说明** |
| --- | --- | --- |
| text | String | 文本。 |
| startTime | Integer | 词开始时间，单位为毫秒。 |
| endTime | Integer | 词结束时间，单位为毫秒。 |

### 4\. 结束识别

音频发送完成后，发送 `StopTranscription` 指令结束本次识别任务。服务端处理剩余音频，并在任务结束时返回 `TranscriptionCompleted`。收到该事件后再关闭连接。

`StopTranscription` 不是保持任务运行的强制断句指令。如果剩余音频中有有效语音，服务端可能先返回 `SentenceEnd`；仅包含静音的任务不一定返回 `SentenceEnd`。

## 服务状态码

通过响应中的 `header.status` 和 `header.status_text` 判断请求状态。下表列出常见错误及处理方法。

### **通用错误码**

| **状态码** | **状态消息** | **原因** | **解决方案** |
| --- | --- | --- | --- |
| 40000000 | 默认的客户端错误码，对应了多个错误消息。 | 用户使用了不合理的参数或者调用逻辑。 | 请参考官网文档示例代码进行对比测试验证。 |
| 40000001 | The token 'xxx' has expired； The token 'xxx' is invalid | 用户使用了不合理的参数或者调用逻辑。通用客户端错误码，通常是涉及Token相关的不正确使用，例如Token过期或者非法。 | 请参考官网文档示例代码进行对比测试验证。 |
| 40000002 | Gateway:MESSAGE\\_INVALID:Can't process message in state'FAILED'! | 无效或者错误的报文消息。 | 请参考官网文档示例代码进行对比测试验证。 |
| 40000003 | PARAMETER\\_INVALID; Failed to decode url params | 用户传递的参数有误，一般常见于RESTful接口调用。 | 请参考官网文档示例代码进行对比测试验证。 |
| 40000005 | Gateway:TOO\\_MANY\\_REQUESTS:Too many requests! | 并发请求过多。 | 如果是试用版调用，建议升级为商用版本以增大并发。 如果已是商用版，可购买并发资源包，扩充并发额度。 |
| 40000009 | Invalid wav header! | 错误的消息头。 | 如果发送的是WAV语音文件，且设置`format`为`wav`，请注意检查该语音文件的WAV头是否正确，否则可能会被服务端拒绝。 |
| 40000009 | Too large wav header! | 传输的语音WAV头不合法。 | 建议使用PCM、OPUS等格式发送音频流，如果是WAV，建议关注语音文件的WAV头信息是否为正确的数据长度大小。 |
| 40000010 | Gateway:FREE\\_TRIAL\\_EXPIRED:The free trial has expired! | 试用期已结束，并且未开通商用版、或账号欠费。 | 检查服务开通状态和账户余额。 购买资源包不等于开通商用版。即使已购买资源包，仍需将实时语音识别服务升级为商用版后才能使用。升级方法请参见[计费方式](https://help.aliyun.com/zh/isi/product-overview/billing-10)。 |
| 40010001 | Gateway:NAMESPACE\\_NOT\\_FOUND:RESTful url path illegal | 不支持的接口或参数。 | 请检查调用时传递的参数内容是否和官网文档要求的一致，并结合错误信息对比排查，设置为正确的参数。 比如是否通过curl命令执行RESTful接口请求， 拼接的URL是否合法。 |
| 40010003 | Gateway:DIRECTIVE\\_INVALID:\\[xxx\\] | 客户端侧通用错误码。 | 表示客户端传递了不正确的参数或指令，在不同的接口上有对应的详细报错信息，请参考对应文档进行正确设置。 |
| 40010004 | Gateway:CLIENT\\_DISCONNECT:Client disconnected before task finished! | 在请求处理完成前客户端主动结束。 | 收到 TranscriptionCompleted 后再关闭连接。 |
| 40010005 | Gateway:TASK\\_STATE\\_ERROR:Got stop directive while task is stopping! | 客户端发送了当前不支持的消息指令。 | 检查指令发送顺序。任务正在结束时，不要重复发送 StopTranscription。 |
| 40020105 | Meta:APPKEY\\_NOT\\_EXIST:Appkey not exist! | 使用了不存在的Appkey。 | 请确认是否使用了不存在的Appkey，Appkey可以通过登录控制台后查看项目配置。 |
| 40020106 | Meta:APPKEY\\_UID\\_MISMATCH:Appkey and user mismatch! | 调用时传递的Appkey和Token并非同一个账号UID所创建，导致不匹配。 | 请检查是否存在两个账号混用的情况，避免使用账号A名下的Appkey和账号B名下生成的Token搭配使用。 |
| 403 | Forbidden | 使用的Token无效，例如Token不存在或者已过期。 | 请设置正确的Token。Token存在有效期限制，请及时在过期前获取新的Token。 |
| 41000003 | MetaInfo doesn't have end point info | 无法获取该Appkey的路由信息。 | 请检查是否存在两个账号混用的情况，避免使用账号A名下的Appkey和账号B名下生成的Token搭配使用。 |
| 41010101 | UNSUPPORTED\\_SAMPLE\\_RATE | 不支持的采样率格式。 | 当前实时语音识别只支持8000 Hz和16000 Hz两种采样率格式的音频。 |
| 41040201 | Realtime:GET\\_CLIENT\\_DATA\\_TIMEOUT:Client data does not send continuously! | 获取客户端发送的数据超时失败。 | 按实时速率持续发送音频。发送完成后发送 StopTranscription，收到 TranscriptionCompleted 后再关闭连接。 |
| 50000000 | GRPC\\_ERROR:Grpc error! | 受机器负载、网络等因素导致的异常，通常为偶发出现。 | 一般重试调用即可恢复。 |
| 50000001 | GRPC\\_ERROR:Grpc error! | 受机器负载、网络等因素导致的异常，通常为偶发出现。 | 一般重试调用即可恢复。 |
| 52010001 | GRPC\\_ERROR:Grpc error! | 受机器负载、网络等因素导致的异常，通常为偶发出现。 | 一般重试调用即可恢复。 |

### **实时语音识别错误码**

| **状态码** | **状态消息** | **原因** | **解决方案** |
| --- | --- | --- | --- |
| 40000004 | Gateway:IDLE\\_TIMEOUT:Websocket session is idle for too long time | 请求建立连接后，长时间没有发送任何数据，超过10s后，服务端会返回此错误信息。 | 建立连接后持续发送音频，可边采集边发送。音频发送完成后发送 StopTranscription，收到 TranscriptionCompleted 后再关闭连接。 |
| 40270002 | NO\\_VALID\\_AUDIO\\_ERROR | 无效的音频。 | 从音频中没有识别出有效文本。 |
| 40270003 | DECODE\\_ERROR | 音频解码失败。 | 请根据实际音频格式，设置对应的format参数。 |
| 41000002 | APPKEY\\_KEY\\_IS\\_NULL | 没有正确设置appkey。 | 请参考官网文档及示例代码。 |