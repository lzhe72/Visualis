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
import edp.core.exception.NotFoundException;
import edp.core.exception.ServerException;
import edp.core.exception.UnAuthorizedExecption;
import edp.core.utils.*;
import edp.davinci.core.common.Constants;
import edp.davinci.core.enums.LogNameEnum;
import edp.davinci.core.enums.UserOrgRoleEnum;
import edp.davinci.core.model.TokenEntity;
import edp.davinci.dao.*;
import edp.davinci.dto.organizationDto.*;
import edp.davinci.model.Organization;
import edp.davinci.model.Project;
import edp.davinci.model.RelUserOrganization;
import edp.davinci.model.User;
import edp.davinci.service.OrganizationService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Slf4j
@Service("organizationService")
public class OrganizationServiceImpl implements OrganizationService {
    private static final Logger optLogger = LoggerFactory.getLogger(LogNameEnum.BUSINESS_OPERATION.getName());

    @Autowired
    private RelUserOrganizationMapper relUserOrganizationMapper;

    @Autowired
    public OrganizationMapper organizationMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ProjectMapper projectMapper;

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private TokenUtils tokenUtils;

    @Autowired
    private MailUtils mailUtils;

    @Autowired
    private FileUtils fileUtils;

    @Autowired
    private ServerUtils serverUtils;

    @Override
    public synchronized boolean isExist(String name, Long id, Long scopeId) {
        Long orgId = organizationMapper.getIdByName(name);
        if (null != id && null != orgId) {
            return !id.equals(orgId);
        }
        return null != orgId && orgId.longValue() > 0L;
    }

    /**
     * ????????????
     *
     * @param organizationCreate
     * @param user
     * @return
     */
    @Override
    @Transactional
    public OrganizationBaseInfo createOrganization(OrganizationCreate organizationCreate, User user) throws ServerException {
        if (isExist(organizationCreate.getName(), null, null)) {
            log.info("the organization name {} is alread taken", organizationCreate.getName());
            throw new ServerException("the organization name " + organizationCreate.getName() + " is alread taken");
        }

        //????????????
        Organization organization = new Organization(organizationCreate.getName(), organizationCreate.getDescription(), user.getId());
        int insert = organizationMapper.insert(organization);

        if (insert > 0) {
            optLogger.info("organization ({}) create by (:{})", organization.toString(), user.getId());
            //??????-?????? ????????????
            RelUserOrganization relUserOrganization = new RelUserOrganization(organization.getId(), user.getId(), UserOrgRoleEnum.OWNER.getRole());
            relUserOrganizationMapper.insert(relUserOrganization);
            OrganizationBaseInfo organizationBaseInfo = new OrganizationBaseInfo();
            BeanUtils.copyProperties(organization, organizationBaseInfo);
            organizationBaseInfo.setRole(relUserOrganization.getRole());
            return organizationBaseInfo;
        } else {
            log.info("create organization error");
            throw new ServerException("create organization error");
        }
    }

    /**
     * ??????????????????
     * ??????organization???????????????owner????????????
     *
     * @param organizationPut
     * @param user
     * @return
     */
    @Override
    @Transactional
    public boolean updateOrganization(OrganizationPut organizationPut, User user) throws NotFoundException, UnAuthorizedExecption, ServerException {
        Organization organization = organizationMapper.getById(organizationPut.getId());
        if (null == organization) {
            throw new NotFoundException("organization is not found");
        }

        //???????????????????????????organization???????????????owner????????????
        RelUserOrganization rel = relUserOrganizationMapper.getRel(user.getId(), organizationPut.getId());
        if (!organization.getUserId().equals(user.getId()) && (null == rel || rel.getRole() != UserOrgRoleEnum.OWNER.getRole())) {
            throw new UnAuthorizedExecption("you have not permission to update this organization");
        }

        String origin = organization.toString();
        BeanUtils.copyProperties(organizationPut, organization);
        organization.setUpdateBy(user.getId());
        organization.setUpdateTime(new Date());

        organizationMapper.update(organization);
        optLogger.info("organization ({}) is update by (:{}), origin: ({})", organization.toString(), user.getId(), origin);
        return true;
    }

    /**
     * ??????????????????
     *
     * @param id
     * @param file
     * @param user
     * @return
     */
    @Override
    @Transactional
    public Map<String, String> uploadAvatar(Long id, MultipartFile file, User user) throws NotFoundException, UnAuthorizedExecption, ServerException {
        Organization organization = organizationMapper.getById(id);
        if (null == organization) {
            throw new NotFoundException("organization is not found");
        }

        //???????????????????????????owner?????????
        RelUserOrganization rel = relUserOrganizationMapper.getRel(user.getId(), id);
        if (!organization.getUserId().equals(user.getId()) && (null == rel || rel.getRole() != UserOrgRoleEnum.OWNER.getRole())) {
            log.info("user({}) don't have permission to change avatar of this organization({})", user.getId(), organization.getId());
            throw new UnAuthorizedExecption("you have not permission to change avatar of this organization");
        }

        //????????????????????????
        if (!fileUtils.isImage(file)) {
            throw new ServerException("file format error");
        }

        //????????????
        String fileName = user.getUsername() + "_" + UUID.randomUUID();
        String avatar = null;
        try {
            avatar = fileUtils.upload(file, Constants.ORG_AVATAR_PATH, fileName);
            if (StringUtils.isEmpty(avatar)) {
                throw new ServerException("organization avatar upload error");
            }
        } catch (Exception e) {
            log.error("uploadAvatar: organization({}) avatar upload error, error: {}", organization.getName(), e.getMessage());
            e.printStackTrace();
            throw new ServerException("organization avatar upload error");
        }

        //???????????????
        if (!StringUtils.isEmpty(organization.getAvatar())) {
            fileUtils.remove(organization.getAvatar());
        }

        //????????????
        organization.setAvatar(avatar);
        organization.setUpdateTime(new Date());
        organization.setUpdateBy(user.getId());

        int i = organizationMapper.update(organization);
        if (i > 0) {
            Map<String, String> map = new HashMap<>();
            map.put("avatar", avatar);

            return map;
        } else {
            throw new ServerException("organization avatar update fail");
        }
    }


    /**
     * ????????????
     *
     * @param id
     * @param user
     * @return
     */
    @Override
    @Transactional
    public boolean deleteOrganization(Long id, User user) throws NotFoundException, UnAuthorizedExecption, ServerException {
        Organization organization = organizationMapper.getById(id);
        if (null == organization) {
            log.info("organization(:{}) is not found", id);
            throw new NotFoundException("organization is not found");
        }

        //???????????????????????????owner???????????????
        RelUserOrganization rel = relUserOrganizationMapper.getRel(user.getId(), id);
        if (!organization.getUserId().equals(user.getId()) && (null == rel || rel.getRole() != UserOrgRoleEnum.OWNER.getRole())) {
            log.info("user({}) have not permission to delete organization({})", user.getId(), organization.getId());
            throw new UnAuthorizedExecption("you have not permission to delete this organization");
        }

        //??????????????????????????????
        List<Project> projectList = projectMapper.getByOrgId(id);
        if (!CollectionUtils.isEmpty(projectList)) {
            log.info("There is at least one project under the organization({}), it is can not be deleted", organization.getId());
            throw new ServerException("There is at least one project under this organization, it is can not be deleted");
        }

        //??????????????????
        relUserOrganizationMapper.deleteByOrgId(id);

        roleMapper.deleteByOrg(id);

        //??????organization
        organizationMapper.deleteById(id);

        optLogger.info("organization ({}) is delete by (:{})", organization.toString(), user.getId());
        return true;
    }

    /**
     * ??????????????????
     *
     * @param id
     * @param user
     * @return
     */
    @Override
    public OrganizationInfo getOrganization(Long id, User user) throws NotFoundException, UnAuthorizedExecption {
        Organization organization = organizationMapper.getById(id);
        if (null == organization) {
            log.info("organization(:{}) is not found", id);
            throw new NotFoundException("organization is not found");
        }

        RelUserOrganization rel = relUserOrganizationMapper.getRel(user.getId(), id);
        if (null == rel) {
            throw new UnAuthorizedExecption("Insufficient permissions");
        }

        OrganizationInfo organizationInfo = new OrganizationInfo();
        BeanUtils.copyProperties(organization, organizationInfo);
        organizationInfo.setRole(rel.getRole());
        return organizationInfo;
    }

    /**
     * ??????????????????
     * ?????????????????? + Member????????????????????????????????????
     *
     * @param user
     * @return
     */
    @Override
    public List<OrganizationInfo> getOrganizations(User user) {
        List<OrganizationInfo> organizationInfos = organizationMapper.getOrganizationByUser(user.getId());
        organizationInfos.forEach(o -> {
            if (o.getRole() == UserOrgRoleEnum.OWNER.getRole()) {
                o.setAllowCreateProject(true);
            }
        });
        return organizationInfos;
    }

    /**
     * ????????????????????????
     *
     * @param id
     * @return
     */
    @Override
    public List<OrganizationMember> getOrgMembers(Long id) {
        return relUserOrganizationMapper.getOrgMembers(id);
    }


    /**
     * ????????????
     *
     * @param orgId
     * @param memId
     * @param user
     * @return
     */
    @Override
    public void inviteMember(Long orgId, Long memId, User user) throws NotFoundException, UnAuthorizedExecption, ServerException {
        //????????????
        Organization organization = organizationMapper.getById(orgId);
        if (null == organization) {
            throw new NotFoundException("organization is not found");
        }

        //??????????????????
        User member = userMapper.getById(memId);
        if (null == member) {
            log.info("user (:{}) is not found", memId);
            throw new NotFoundException("user is not found");
        }

        // ???????????????????????????organization???owner????????????
        RelUserOrganization relOwner = relUserOrganizationMapper.getRel(user.getId(), orgId);
        if (null == relOwner || relOwner.getRole() != UserOrgRoleEnum.OWNER.getRole()) {
            throw new UnAuthorizedExecption("you cannot invite anyone to join this orgainzation, cause you are not the owner of this orginzation");
        }

        //???????????????????????????????????????
        RelUserOrganization rel = relUserOrganizationMapper.getRel(memId, orgId);
        if (null != rel) {
            throw new ServerException("the invitee is already a member of the this organization");
        }

        //????????????
        if (StringUtils.isEmpty(user.getEmail())) {
            throw new ServerException("The email address of the invitee is EMPTY");
        }

        /**
         * ??????????????????token????????????
         * ?????????
         * username: ?????????id:-:????????????id:-:??????id
         * password: ??????????????????
         */
        TokenEntity orgInviteDetail = new TokenEntity();
        orgInviteDetail.setUsername(user.getId() + Constants.SPLIT_CHAR_STRING + memId + Constants.SPLIT_CHAR_STRING + organization.getId());
        orgInviteDetail.setPassword(member.getPassword());

        Map<String, Object> content = new HashMap<>();
        content.put("username", member.getUsername());
        content.put("inviter", user.getUsername());
        content.put("orgName", organization.getName());
        content.put("host", serverUtils.getHost());
        //aes??????token
        content.put("token", AESUtils.encrypt(tokenUtils.generateContinuousToken(orgInviteDetail), null));
        try {
            mailUtils.sendTemplateEmail(member.getEmail(),
                    String.format(Constants.INVITE_ORG_MEMBER_MAIL_SUBJECT, user.getUsername(), organization.getName()),
                    Constants.INVITE_ORG_MEMBER_MAIL_TEMPLATE,
                    content);
        } catch (ServerException e) {
            log.info(e.getMessage());
            e.printStackTrace();
        }
    }


    /**
     * ????????????????????????
     *
     * @param token
     * @param user
     * @return
     */
    @Override
    @Transactional
    public OrganizationInfo confirmInvite(String token, User user) throws ServerException {
        //aes??????
        token = AESUtils.decrypt(token, null);

        //??????token(?????????????????????util)
        String tokenUserName = tokenUtils.getUsername(token);
        String tokenPassword = tokenUtils.getPassword(token);

        if (StringUtils.isEmpty(tokenUserName) || StringUtils.isEmpty(tokenPassword)) {
            log.info("confirmInvite error: token detail id EMPTY");
            throw new ServerException("username or password cannot be EMPTY");
        }

        String[] ids = tokenUserName.split(Constants.SPLIT_CHAR_STRING);
        if (ids.length != 3) {
            log.info("confirmInvite error: invalid token username");
            throw new ServerException("Invalid Token");
        }
        Long inviterId = Long.parseLong(ids[0]);
        Long memeberId = Long.parseLong(ids[1]);
        Long orgId = Long.parseLong(ids[2]);

        if (!user.getId().equals(memeberId)) {
            log.info("confirmInvite error: invalid token member, username is wrong");
            throw new ServerException("username is wrong");
        }

        if (!tokenPassword.equals(user.getPassword())) {
            log.info("confirmInvite error: invalid token password");
            throw new ServerException("password is wrong");
        }

        User inviter = userMapper.getById(inviterId);
        if (null == inviter) {
            log.info("confirmInvite error: invalid token inviter");
            throw new ServerException("Invalid Token");
        }

        Organization organization = organizationMapper.getById(orgId);
        if (null == organization) {
            log.info("confirmInvite error: invalid token organization");
            throw new ServerException("Invalid Token");
        }

        RelUserOrganization tokenRel = relUserOrganizationMapper.getRel(inviterId, orgId);
        if (null != tokenRel && tokenRel.getRole() != UserOrgRoleEnum.OWNER.getRole()) {
            log.info("confirmInvite error: invalid token inviter permission");
            throw new ServerException("Invalid Token");
        }

        RelUserOrganization rel = relUserOrganizationMapper.getRel(memeberId, orgId);

        OrganizationInfo organizationInfo = new OrganizationInfo();
        BeanUtils.copyProperties(organization, organizationInfo);
        if (rel != null) {
            organizationInfo.setRole(rel.getRole());
            throw new ServerException("You have joined the organization and don't need to repeat.");
        }
        //???????????????????????????
        rel = new RelUserOrganization(orgId, memeberId, UserOrgRoleEnum.MEMBER.getRole());
        int insert = relUserOrganizationMapper.insert(rel);

        if (insert > 0) {
            //??????????????????
            organization.setMemberNum(organization.getMemberNum() + 1);
            organizationMapper.updateMemberNum(organization);
            organizationInfo.setRole(rel.getRole());
            return organizationInfo;
        } else {
            throw new ServerException("unknown fail");
        }

    }

    @Override
    @Transactional
    public void confirmInviteNoLogin(String token) throws NotFoundException, ServerException {
        //aes??????
        token = AESUtils.decrypt(token, null);

        //??????token(?????????????????????util)
        String tokenUserName = tokenUtils.getUsername(token);
        String tokenPassword = tokenUtils.getPassword(token);

        if (StringUtils.isEmpty(tokenUserName) || StringUtils.isEmpty(tokenPassword)) {
            log.info("confirmInvite error: token detail id EMPTY");
            throw new ServerException("Invalid Token");
        }

        String[] ids = tokenUserName.split(Constants.SPLIT_CHAR_STRING);
        if (ids.length != 3) {
            log.info("confirmInvite error: invalid token username");
            throw new ServerException("Invalid Token");
        }
        Long inviterId = Long.parseLong(ids[0]);
        Long memeberId = Long.parseLong(ids[1]);
        Long orgId = Long.parseLong(ids[2]);

        User inviter = userMapper.getById(inviterId);
        if (null == inviter) {
            log.info("confirmInvite error: invalid token inviter");
            throw new ServerException("Invalid Token");
        }

        Organization organization = organizationMapper.getById(orgId);
        if (null == organization) {
            log.info("confirmInvite error: invalid token organization");
            throw new ServerException("Invalid Token");
        }

        RelUserOrganization tokenRel = relUserOrganizationMapper.getRel(inviterId, orgId);
        if (null != tokenRel && tokenRel.getRole() != UserOrgRoleEnum.OWNER.getRole()) {
            log.info("confirmInvite error: invalid token inviter permission");
            throw new ServerException("Invalid Token");
        }

        User member = userMapper.getById(memeberId);
        if (null == member) {
            throw new NotFoundException("user is not found");
        }

        RelUserOrganization memberRel = relUserOrganizationMapper.getRel(memeberId, orgId);
        if (null != memberRel) {
            throw new ServerException("You have joined the organization and don't need to repeat.");
        }

        //???????????????????????????
        RelUserOrganization rel = new RelUserOrganization(orgId, memeberId, UserOrgRoleEnum.MEMBER.getRole());
        relUserOrganizationMapper.insert(rel);
        //??????????????????
        organization.setMemberNum(organization.getMemberNum() + 1);
        organizationMapper.updateMemberNum(organization);

    }

    /**
     * ??????????????????
     *
     * @param relationId
     * @param user
     * @return
     */
    @Override
    @Transactional
    public boolean deleteOrgMember(Long relationId, User user) throws NotFoundException, UnAuthorizedExecption, ServerException {
        RelUserOrganization rel = relUserOrganizationMapper.getById(relationId);
        if (null == rel) {
            throw new ServerException("this member are no longer member of the organization");
        }

        Organization organization = organizationMapper.getById(rel.getOrgId());
        if (null == organization) {
            throw new NotFoundException("organization is not found");
        }

        //?????????????????????owner????????????
        RelUserOrganization ownerRel = relUserOrganizationMapper.getRel(user.getId(), rel.getOrgId());
        if (null != ownerRel && ownerRel.getRole() != UserOrgRoleEnum.OWNER.getRole()) {
            throw new UnAuthorizedExecption("you cannot delete any member of this organization, cause you are not the owner of this ordination");
        }

        if (organization.getUserId().equals(rel.getUserId())) {
            throw new UnAuthorizedExecption("you have not permission delete the creator of the organization");
        }

        if (rel.getUserId().equals(user.getId())) {
            throw new ServerException("you cannot delete yourself in this organization");
        }

        //????????????
        int i = relUserOrganizationMapper.deleteById(relationId);

        if (i > 0) {
            //????????????????????????
            organization.setMemberNum(organization.getMemberNum() > 0 ? organization.getMemberNum() - 1 : organization.getMemberNum());
            organizationMapper.updateMemberNum(organization);

            return true;
        } else {
            throw new ServerException("unknown fail");
        }
    }

    /**
     * ??????????????????
     *
     * @param relationId
     * @param user
     * @param role
     * @return
     */
    @Override
    @Transactional
    public boolean updateMemberRole(Long relationId, User user, int role) throws NotFoundException, UnAuthorizedExecption, ServerException {
        RelUserOrganization rel = relUserOrganizationMapper.getById(relationId);
        if (null == rel) {
            throw new ServerException("this member are no longer member of the organization");
        }

        Organization organization = organizationMapper.getById(rel.getOrgId());
        if (null == organization) {
            log.info("organization(:{}) is not found", organization.getId());
            throw new NotFoundException("organization is not found");
        }

        //?????????????????????owner????????????
        RelUserOrganization ownerRel = relUserOrganizationMapper.getRel(user.getId(), rel.getOrgId());
        if (null != ownerRel && ownerRel.getRole() != UserOrgRoleEnum.OWNER.getRole()) {
            throw new UnAuthorizedExecption("you cannot change role of the member in this orgainzation, cause you are not the owner of this orginzation");
        }

        UserOrgRoleEnum userOrgRoleEnum = UserOrgRoleEnum.roleOf(role);
        if (null == userOrgRoleEnum) {
            throw new ServerException("Invalid role");
        }

        //??????????????????????????????
        if (user.getId().equals(rel.getUserId())) {
            throw new ServerException("you cannot change your own role");
        }

        //???????????????
        if ((int) rel.getRole() == role) {
            throw new ServerException("this member does not need to change role");
        }

        String origin = rel.toString();

        rel.setRole(userOrgRoleEnum.getRole());
        rel.updatedBy(user.getId());
        int i = relUserOrganizationMapper.updateMemberRole(rel);
        if (i > 0) {
            optLogger.info("RelUserOrganization ({}) is update by (:{}), origin", rel.toString(), user.getId(), origin);
            return true;
        } else {
            throw new ServerException("unknown fail");
        }
    }
}
