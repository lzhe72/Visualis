/*
 * <<
 *  Davinci
 *  ==
 *  Copyright (C) 2016 - 2019 EDP
 *  ==
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *        http://www.apache.org/licenses/LICENSE-2.0
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *  >>
 *
 */

package edp.core.utils;

import com.alibaba.druid.util.StringUtils;
import edp.core.exception.ServerException;
import edp.davinci.core.enums.CronJobMediaType;
import edp.davinci.core.enums.FileTypeEnum;
import edp.davinci.dto.cronJobDto.ExcelContent;
import edp.davinci.service.screenshot.ImageContent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

import static edp.core.consts.Consts.PATTERN_EMAIL_FORMAT;

@Component
@Slf4j
public class MailUtils {

    @Autowired
    private JavaMailSender javaMailSender;

    @Autowired
    private TemplateEngine templateEngine;

    @Value("${spring.mail.username}")
    private String sendEmailfrom;

    @Value("${spring.mail.nickname}")
    private String nickName;

    /**
     * ??????????????????
     *
     * @param from    ?????????
     * @param subject ??????
     * @param to      ?????????
     * @param cc      ??????
     * @param bcc     ????????????
     * @param content ??????
     * @throws ServerException
     */
    public void sendSimpleEmail(String from, String subject, String[] to, String[] cc, String[] bcc, String content) throws ServerException {
        long startTimestamp = System.currentTimeMillis();
        log.info("start send email to {}", to.toString());
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setSubject(subject);
            message.setTo(to);
            if (null != cc && cc.length > 0) {
                message.setCc(cc);
            }
            if (null != bcc && bcc.length > 0) {
                message.setBcc(bcc);
            }
            message.setText(content);
            javaMailSender.send(message);
            log.info("send mail success, in {} million seconds", System.currentTimeMillis() - startTimestamp);
        } catch (MailException e) {
            log.error("send mail failed, {} \n", e.getMessage());
            e.printStackTrace();
            throw new ServerException(e.getMessage());
        }
    }

    /**
     * ??????????????????
     * ??????????????????????????????
     *
     * @param to      ??????????????????
     * @param subject ??????
     * @param content ??????
     * @throws ServerException
     */
    public void sendSimpleEmail(String to, String subject, String content) throws ServerException {
        sendSimpleEmail(sendEmailfrom, subject, new String[]{to}, null, null, content);

    }

    /**
     * ?????? Html ??????
     *
     * @param subject  ??????
     * @param from     ?????????
     * @param nickName ??????
     * @param to       ?????????
     * @param cc       ??????
     * @param bcc      ????????????
     * @param content  ??????
     * @throws ServerException
     */
    public void sendHtmlEmail(String from, String nickName, String subject, String[] to, String[] cc, String[] bcc,
                              String content, List<File> files) throws ServerException {

        if (StringUtils.isEmpty(from)) {
            log.info("email address(from) cannot be EMPTY");
            throw new ServerException("email address(from) cannot be EMPTY");
        }

        Matcher matcher = PATTERN_EMAIL_FORMAT.matcher(from);

        if (!matcher.find()) {
            log.info("unknow email address(from): {}", from);
            throw new ServerException("unknow email address(from)");
        }

        if (StringUtils.isEmpty(subject)) {
            log.info("email subject cannot be EMPTY");
            throw new ServerException("email subject cannot be EMPTY");
        }

        if (null == to || to.length < 1) {
            log.info("email address(to) cannot be EMPTY");
            throw new ServerException("email address(to) cannot be EMPTY");
        }

        if (StringUtils.isEmpty(content)) {
            log.info("email content cannot be EMPTY");
            throw new ServerException("email content cannot be EMPTY");
        }

        long startTimestamp = System.currentTimeMillis();
        log.info("start send email to {}", to);

        try {

            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper messageHelper = new MimeMessageHelper(message, true);

            messageHelper.setFrom(from, nickName);
            messageHelper.setSubject(subject);
            messageHelper.setTo(to);
            if (null != cc && cc.length > 0) {
                messageHelper.setCc(cc);
            }
            if (null != bcc && bcc.length > 0) {
                messageHelper.setBcc(bcc);
            }

            if (StringUtils.isEmpty(content)) {
                content = "<html></html>";
            }
            messageHelper.setText(content, true);

            if (!CollectionUtils.isEmpty(files)) {
                if (files.size() == 1) {
                    File file = files.get(0);
                    String attName = "attachment" + file.getName().substring(file.getName().lastIndexOf("."));
                    messageHelper.addAttachment(attName, file);
                } else {
                    for (int i = 0; i < files.size(); i++) {
                        File file = files.get(i);
                        String attName = "attachment-" + (i + 1) + file.getName().substring(file.getName().lastIndexOf("."));
                        messageHelper.addAttachment(attName, file);
                    }
                }
            }

            javaMailSender.send(message);
            log.info("Send mail success, in {} million seconds", System.currentTimeMillis() - startTimestamp);
        } catch (MessagingException e) {
            log.error("Send mail failed, {}\n", e.getMessage());
            e.printStackTrace();
            throw new ServerException(e.getMessage());
        } catch (UnsupportedEncodingException e) {
            log.error("Send mail failed, {}\n", e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * ?????? Html ??????
     * ??????????????????????????????
     *
     * @param subject
     * @param to
     * @param content
     * @throws ServerException
     */
    public void sendHtmlEmail(String subject, String to, String content, List<File> files) throws ServerException {
        sendHtmlEmail(sendEmailfrom, nickName, subject, new String[]{to}, null, null, content, files);
    }

    /**
     * ?????? Html ??????
     * ??????????????????????????????
     *
     * @param subject
     * @param to
     * @param content
     * @throws ServerException
     */
    public void sendHtmlEmail(String subject, String to, String[] cc, String[] bcc, String content, List<File> files) throws ServerException {
        sendHtmlEmail(sendEmailfrom, nickName, subject, new String[]{to}, cc, bcc, content, files);
    }

    /**
     * ??????????????????
     *
     * @param from     ????????????
     * @param nickName ??????
     * @param subject  ??????
     * @param to       ????????????
     * @param cc       ??????
     * @param bcc      ????????????
     * @param template ????????????
     * @param content  ????????????
     * @param excels   ??????
     * @throws ServerException
     */
    public void sendTemplateEmail(String from, String nickName, String subject, String[] to, String[] cc, String[] bcc,
                                  String template, Map<String, Object> content, List<ExcelContent> excels, List<ImageContent> images) throws ServerException {

        if (StringUtils.isEmpty(from)) {
            log.info("email address(from) cannot be EMPTY");
            throw new ServerException("email address(from) cannot be EMPTY");
        }

        Matcher matcher = PATTERN_EMAIL_FORMAT.matcher(from);

        if (!matcher.find()) {
            log.info("unknow email address(from): {}", from);
            throw new ServerException("unknow email address(from)");
        }

        if (StringUtils.isEmpty(subject)) {
            log.info("email subject cannot be EMPTY");
            throw new ServerException("email subject cannot be EMPTY");
        }

        if (null == to || to.length < 1) {
            log.info("email address(to) cannot be EMPTY");
            throw new ServerException("email address(to) cannot be EMPTY");
        }

        if (StringUtils.isEmpty(template)) {
            log.info("email template path is EMPTY");
            throw new ServerException("email template path is EMPTY");
        }

        if (null == content) {
            log.info("template content is EMPTY");
            throw new ServerException("template content is EMPTY");
        }

        Context context = new Context();
        for (String key : content.keySet()) {
            context.setVariable(key, content.get(key));
        }

        long startTimestamp = System.currentTimeMillis();
        log.info("start send email to {}", to);

        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper messageHelper = new MimeMessageHelper(message, true);

            messageHelper.setFrom(from, nickName);
            messageHelper.setSubject(subject);
            messageHelper.setSubject(subject);
            messageHelper.setTo(to);
            if (null != cc && cc.length > 0) {
                messageHelper.setCc(cc);
            }
            if (null != bcc && bcc.length > 0) {
                messageHelper.setBcc(bcc);
            }

            Map<String, File> imageFileMap = new HashMap<>();
            List<String> imageContentIds = new ArrayList<>();
            if (!CollectionUtils.isEmpty(images)) {
                images.forEach(imageContent -> {
                    if (imageContent.getImageFile() != null) {
                        String contentId = CronJobMediaType.IMAGE.getType() + imageContent.getOrder();
                        imageContentIds.add(contentId);
                        imageFileMap.put(contentId, imageContent.getImageFile());
                    }
                });
            }

            if (!imageFileMap.isEmpty()) {
                context.setVariable("images", imageContentIds);
            }

            String text = templateEngine.process(template, context);
            messageHelper.setText(text, true);

            if (!CollectionUtils.isEmpty(excels)) {
                excels.forEach(excel -> {
                    try {
                        messageHelper.addAttachment(excel.getName() + FileTypeEnum.XLSX.getFormat(), excel.getFile());
                    } catch (MessagingException e) {
                    }
                });
            }

            if (!imageFileMap.isEmpty()) {
                imageFileMap.forEach((contentId, file) -> {
                    try {
                        messageHelper.addInline(contentId, file);
                    } catch (MessagingException e) {
                    }
                });
            }

            javaMailSender.send(message);
            log.info("Send mail success, in {} million seconds", System.currentTimeMillis() - startTimestamp);
        } catch (MessagingException e) {
            log.error("Send mail failed, {}\n", e.getMessage());
            e.printStackTrace();
            throw new ServerException(e.getMessage());
        } catch (UnsupportedEncodingException e) {
            log.error("Send mail failed, {}\n", e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * ????????????????????????
     * ??????????????????????????????
     *
     * @param to       ??????????????????
     * @param subject  ??????
     * @param template ????????????
     * @param content  ????????????????????????????????????
     * @throws ServerException
     */
    public void sendTemplateEmail(String to, String subject, String template, Map<String, Object> content) throws ServerException {
        sendTemplateEmail(sendEmailfrom, nickName, subject, new String[]{to}, null, null, template, content, null, null);
    }

    /**
     * ????????????????????????
     *
     * @param subject  ??????
     * @param to       ?????????
     * @param cc       ??????
     * @param bcc      ????????????
     * @param template ????????????
     * @param content  ????????????
     * @param excels   ??????
     * @param images
     * @throws ServerException
     */
    public void sendTemplateAttachmentsEmail(String subject, String to, String[] cc, String[] bcc, String template, Map<String, Object> content, List<ExcelContent> excels, List<ImageContent> images) throws ServerException {
        sendTemplateEmail(sendEmailfrom, nickName, subject, new String[]{to}, cc, bcc, template, content, excels, images);
    }

}
