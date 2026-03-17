package org.lixiyun.common.mail.utils;

import cn.hutool.extra.spring.SpringUtil;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

@Slf4j
public class SendSimpleEmailUtil {

    private static final JavaMailSender mailSender = SpringUtil.getBean(JavaMailSender.class);

    /**
     * 发送简单文本邮件
     * @param to 收件人邮箱地址
     * @param subject 邮件主题
     * @param text 邮件正文内容（纯文本）
     * @param from  发件人邮箱地址
     */
    public static void sendTextEmail(String to, String subject, String text, String from) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false);
            helper.setFrom(from);  // 发件人邮箱（需与配置一致）
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, false);  // false 表示纯文本
            mailSender.send(message);
            log.info("简单文本邮件发送成功！");
        } catch (MessagingException e) {
            log.error("发送简单邮件失败: {}", e.getMessage());
        }
    }

    /**
     * 发送 HTML 格式邮件
     * @param to 收件人邮箱地址
     * @param subject 邮件主题
     * @param htmlContent HTML 内容
     * @param from  发件人邮箱地址
     */
    public static void sendHtmlEmail(String to, String subject, String htmlContent, String from) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);  // true 表示 HTML 内容
            mailSender.send(message);
            log.info("发送 HTML 邮件成功！");
        } catch (MessagingException e) {
            log.error("发送 HTML 邮件失败: {}", e.getMessage());
        }
    }

}

