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

package edp.davinci.service.impl;

import com.alibaba.druid.util.StringUtils;
import edp.core.enums.HttpCodeEnum;
import edp.core.exception.ServerException;
import edp.core.utils.*;
import edp.davinci.core.common.Constants;
import edp.davinci.core.common.ResultMap;
import edp.davinci.core.enums.UserOrgRoleEnum;
import edp.davinci.dao.OrganizationMapper;
import edp.davinci.dao.RelUserOrganizationMapper;
import edp.davinci.dao.UserMapper;
import edp.davinci.dto.organizationDto.OrganizationInfo;
import edp.davinci.dto.userDto.*;
import edp.davinci.model.LdapPerson;
import edp.davinci.model.Organization;
import edp.davinci.model.RelUserOrganization;
import edp.davinci.model.User;
import edp.davinci.service.LdapService;
import edp.davinci.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.util.*;


@Slf4j
@Service("userService")
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private OrganizationMapper organizationMapper;

    @Autowired
    private RelUserOrganizationMapper relUserOrganizationMapper;

    @Autowired
    private TokenUtils tokenUtils;

    @Autowired
    private MailUtils mailUtils;


    @Autowired
    private FileUtils fileUtils;

    @Autowired
    private ServerUtils serverUtils;


    //@Autowired
    private LdapService ldapService;

    /**
     * ??????????????????
     *
     * @param name
     * @param scopeId
     * @return
     */
    @Override
    public synchronized boolean isExist(String name, Long id, Long scopeId) {
        Long userId = userMapper.getIdByName(name);
        if (null != id && null != userId) {
            return !id.equals(userId);
        }
        return null != userId && userId.longValue() > 0L;
    }

    /**
     * ??????????????????
     *
     * @param userRegist
     * @return
     */
    @Override
    @Transactional
    public synchronized User regist(UserRegist userRegist) throws ServerException {
        //???????????????????????????
        if (isExist(userRegist.getUsername(), null, null)) {
            log.info("the username {} has been registered", userRegist.getUsername());
            throw new ServerException("the username:" + userRegist.getUsername() + " has been registered");
        }
        //????????????????????????
        if (isExist(userRegist.getEmail(), null, null)) {
            log.info("the email:" + userRegist.getEmail() + " has been registered");
            throw new ServerException("the email:" + userRegist.getEmail() + " has been registered");
        }

        User user = new User();
        //????????????
        //userRegist.setPassword(BCrypt.hashpw(userRegist.getPassword(), BCrypt.gensalt()));
        BeanUtils.copyProperties(userRegist, user);
        //????????????
        int insert = userMapper.insert(user);
        if (insert > 0) {
            //?????????????????????????????????
            Map content = new HashMap<String, Object>();
            content.put("username", user.getUsername());
            content.put("host", serverUtils.getHost());
            content.put("token", AESUtils.encrypt(tokenUtils.generateContinuousToken(user), null));

            mailUtils.sendTemplateEmail(user.getEmail(),
                    Constants.USER_ACTIVATE_EMAIL_SUBJECT,
                    Constants.USER_ACTIVATE_EMAIL_TEMPLATE,
                    content);

            return user;
        } else {
            log.info("regist fail: {}", userRegist.toString());
            throw new ServerException("regist fail: unspecified error");
        }
    }

    /**
     * ???????????????????????????
     *
     * @param username
     * @return
     */
    @Override
    public User getByUsername(String username) {
        return userMapper.selectByUsername(username);
    }

    /**
     * ????????????
     *
     * @param userLogin
     * @return
     */
    @Override
    public User userLogin(UserLogin userLogin) throws ServerException {
        User user = userMapper.selectByUsername(userLogin.getUsername());
        ServerException e = null;
        if (null == user) {
            log.info("user not found: {}", userLogin.getUsername());
            e = new ServerException("user is not found");
        }
        //????????????
        if (null != user) {
            boolean checkpw = false;
            try {
                checkpw = BCrypt.checkpw(userLogin.getPassword(), user.getPassword());
            } catch (Exception e1) {
            }
            if (!checkpw) {
                log.info("password is wrong: {}", userLogin.getUsername());
                e = new ServerException("password is wrong");
            }
        }

        if (null != e) {
            if (!ldapService.existLdapServer()) {
                throw e;
            }
            LdapPerson ldapPerson = ldapService.findByUsername(userLogin.getUsername(), userLogin.getPassword());
            if (null == ldapPerson) {
                throw new ServerException("username or password is wrong");
            } else {
                if (null == user) {
                    if (userMapper.existEmail(ldapPerson.getEmail())) {
                        throw new ServerException("password is wrong");
                    }
                    if (userMapper.existUsername(ldapPerson.getSAMAccountName())) {
                        ldapPerson.setSAMAccountName(ldapPerson.getEmail());
                    }
                    user = ldapService.registPerson(ldapPerson);
                } else if (user.getEmail().toLowerCase().equals(ldapPerson.getEmail().toLowerCase())) {
                    return user;
                } else {
                    throw e;
                }
            }
        }

        return user;
    }

    /**
     * ????????????
     *
     * @param keyword
     * @param user
     * @param orgId
     * @return
     */
    @Override
    public List<UserBaseInfo> getUsersByKeyword(String keyword, User user, Long orgId) {
        List<UserBaseInfo> users = userMapper.getUsersByKeyword(keyword, orgId);

        Iterator<UserBaseInfo> iterator = users.iterator();
        while (iterator.hasNext()) {
            UserBaseInfo userBaseInfo = iterator.next();
            if (userBaseInfo.getId().equals(user.getId())) {
                iterator.remove();
            }
        }
        return users;
    }

    /**
     * ????????????
     *
     * @param user
     * @return
     */
    @Override
    @Transactional
    public boolean updateUser(User user) throws ServerException {
        if (userMapper.updateBaseInfo(user) > 0) {
            return true;
        } else {
            log.info("update user fail, username: {}", user.getUsername());
            throw new ServerException("update fail");
        }
    }

    @Override
    @Transactional
    public synchronized ResultMap activateUserNoLogin(String token, HttpServletRequest request) {
        ResultMap resultMap = new ResultMap(tokenUtils);

        token = AESUtils.decrypt(token, null);

        String username = tokenUtils.getUsername(token);

        User user = userMapper.selectByUsername(username);

        if (null == user) {
            return resultMap.fail().message("The activate toke is invalid");
        }

        //????????????????????????????????????
        if (user.getActive()) {
            return resultMap.fail(302).message("The current user is activated and doesn't need to be reactivated");
        }
        //????????????token
        if (tokenUtils.validateToken(token, user)) {
            user.setActive(true);
            user.setUpdateTime(new Date());
            userMapper.activeUser(user);

            String OrgName = user.getUsername() + "'s Organization";

            //???????????????????????????Orgnization
            Organization organization = new Organization(OrgName, null, user.getId());
            organizationMapper.insert(organization);

            //?????????????????????????????????????????????owner
            RelUserOrganization relUserOrganization = new RelUserOrganization(organization.getId(), user.getId(), UserOrgRoleEnum.OWNER.getRole());
            relUserOrganizationMapper.insert(relUserOrganization);

            UserLoginResult userLoginResult = new UserLoginResult();
            BeanUtils.copyProperties(user, userLoginResult);
            return resultMap.success(tokenUtils.generateToken(user)).payload(userLoginResult);
        } else {
            return resultMap.fail().message("The activate toke is invalid");
        }
    }

//    /**
//     * ????????????
//     *
//     * @param user
//     * @param token
//     * @param request
//     * @return
//     */
//    @Override
//    @Transactional
//    public ResultMap activateUser(User user, String token, HttpServletRequest request) {
//        //????????????????????????????????????
//        if (user.getActive()) {
//            return resultMap.failAndRefreshToken(request).message("The current user is activated and doesn't need to be reactivated");
//        }
//        //????????????token
//        if (tokenUtils.validateToken(token, user)) {
//            user.setActive(true);
//            user.setUpdateTime(new Date());
//            userMapper.activeUser(user);
//
//            //???????????????????????????Orgnization
//            Organization organization = new Organization(Constants.DEFAULT_ORGANIZATION_NAME, Constants.DEFAULT_ORGANIZATION_DES, user.getId());
//            organizationMapper.insert(organization);
//
//            //?????????????????????????????????????????????owner
//            RelUserOrganization relUserOrganization = new RelUserOrganization(organization.getId(), user.getId(), UserOrgRoleEnum.OWNER.getRole());
//            relUserOrganizationMapper.insert(relUserOrganization);
//
//            UserLoginResult userLoginResult = new UserLoginResult();
//            BeanUtils.copyProperties(user, userLoginResult);
//            return resultMap.successAndRefreshToken(request).payload(userLoginResult);
//        } else {
//            return resultMap.failAndRefreshToken(request).message("The activate toke is invalid");
//        }
//    }

    /**
     * ????????????
     *
     * @param email
     * @param user
     * @return
     */
    @Override
    public boolean sendMail(String email, User user) throws ServerException {
        //????????????
        if (!email.equals(user.getEmail())) {
            throw new ServerException("The current email address is not match user email address");
        }

        Map content = new HashMap<String, Object>();
        content.put("username", user.getUsername());
        content.put("host", serverUtils.getHost());
        content.put("token", AESUtils.encrypt(tokenUtils.generateContinuousToken(user), null));
        mailUtils.sendTemplateEmail(user.getEmail(),
                Constants.USER_ACTIVATE_EMAIL_SUBJECT,
                Constants.USER_ACTIVATE_EMAIL_TEMPLATE,
                content);

        return true;
    }

    /**
     * ??????????????????
     *
     * @param user
     * @param oldPassword
     * @param password
     * @param request
     * @return
     */
    @Override
    @Transactional
    public ResultMap changeUserPassword(User user, String oldPassword, String password, HttpServletRequest request) {
        ResultMap resultMap = new ResultMap(tokenUtils);

        //???????????????
        if (!BCrypt.checkpw(oldPassword, user.getPassword())) {
            return resultMap.failAndRefreshToken(request).message("Incorrect original password");
        }
        //???????????????
        user.setPassword(BCrypt.hashpw(password, BCrypt.gensalt()));
        user.setUpdateTime(new Date());
        int i = userMapper.changePassword(user);
        if (i > 0) {
            return resultMap.success().message("Successful password modification");
        } else {
            return resultMap.failAndRefreshToken(request);
        }
    }

    /**
     * ????????????
     *
     * @param user
     * @param file
     * @param request
     * @return
     */
    @Override
    @Transactional
    public ResultMap uploadAvatar(User user, MultipartFile file, HttpServletRequest request) {
        ResultMap resultMap = new ResultMap(tokenUtils);

        //????????????????????????
        if (!fileUtils.isImage(file)) {
            return resultMap.failAndRefreshToken(request).message("file format error");
        }

        //????????????
        String fileName = user.getUsername() + "_" + UUID.randomUUID();
        String avatar = null;
        try {
            avatar = fileUtils.upload(file, Constants.USER_AVATAR_PATH, fileName);
            if (StringUtils.isEmpty(avatar)) {
                return resultMap.failAndRefreshToken(request).message("user avatar upload error");
            }
        } catch (Exception e) {
            log.error("user avatar upload error, username: {}, error: {}", user.getUsername(), e.getMessage());
            e.printStackTrace();
            return resultMap.failAndRefreshToken(request).message("user avatar upload error");
        }

        //???????????????
        if (!StringUtils.isEmpty(user.getAvatar())) {
            fileUtils.remove(user.getAvatar());
        }

        //??????????????????
        user.setAvatar(avatar);
        user.setUpdateTime(new Date());
        int i = userMapper.updateAvatar(user);
        if (i > 0) {
            Map<String, String> map = new HashMap<>();
            map.put("avatar", avatar);

            return resultMap.successAndRefreshToken(request).payload(map);
        } else {
            return resultMap.failAndRefreshToken(request).message("server error, user avatar update fail");
        }
    }


    /**
     * ??????????????????
     *
     * @param id
     * @param user
     * @param request
     * @return
     */
    @Override
    public ResultMap getUserProfile(Long id, User user, HttpServletRequest request) {
        ResultMap resultMap = new ResultMap(tokenUtils);

        User user1 = userMapper.getById(id);
        if (null != user1) {
            UserProfile userProfile = new UserProfile();
            BeanUtils.copyProperties(user1, userProfile);
            if (user1.getId().equals(user.getId())) {
                List<OrganizationInfo> organizationInfos = organizationMapper.getOrganizationByUser(user.getId());
                userProfile.setOrganizations(organizationInfos);
                return resultMap.successAndRefreshToken(request).payload(userProfile);
            }
            Long[] userIds = {user.getId(), user1.getId()};
            List<OrganizationInfo> jointlyOrganization = organizationMapper.getJointlyOrganization(Arrays.asList(userIds), id);
            if (!CollectionUtils.isEmpty(jointlyOrganization)) {
                BeanUtils.copyProperties(user1, userProfile);
                userProfile.setOrganizations(jointlyOrganization);
                return resultMap.successAndRefreshToken(request).payload(userProfile);
            } else {
                return resultMap.failAndRefreshToken(request, HttpCodeEnum.UNAUTHORIZED).message("You have not permission to view the user's information because you don't have any organizations that join together");
            }
        } else {
            return resultMap.failAndRefreshToken(request).message("user not found");
        }
    }
}
