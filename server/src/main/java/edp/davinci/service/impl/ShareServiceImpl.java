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
import com.alibaba.fastjson.JSONObject;
import com.webank.wedatasphere.linkis.server.security.SecurityFilter;
import edp.core.enums.HttpCodeEnum;
import edp.core.exception.ForbiddenExecption;
import edp.core.exception.NotFoundException;
import edp.core.exception.ServerException;
import edp.core.exception.UnAuthorizedExecption;
import edp.core.model.Paginate;
import edp.core.model.PaginateWithQueryColumns;
import edp.core.model.QueryColumn;
import edp.core.utils.*;
import edp.davinci.core.common.Constants;
import edp.davinci.core.common.ResultMap;
import edp.davinci.core.model.TokenEntity;
import edp.davinci.core.utils.CsvUtils;
import edp.davinci.dao.*;
import edp.davinci.dto.displayDto.MemDisplaySlideWidgetWithSlide;
import edp.davinci.dto.projectDto.ProjectDetail;
import edp.davinci.dto.projectDto.ProjectPermission;
import edp.davinci.dto.shareDto.*;
import edp.davinci.dto.userDto.UserLogin;
import edp.davinci.dto.viewDto.DistinctParam;
import edp.davinci.dto.viewDto.ViewExecuteParam;
import edp.davinci.dto.viewDto.ViewWithProjectAndSource;
import edp.davinci.dto.viewDto.ViewWithSource;
import edp.davinci.model.*;
import edp.davinci.service.ProjectService;
import edp.davinci.service.ShareService;
import edp.davinci.service.ViewService;
import lombok.extern.slf4j.Slf4j;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.*;

import static edp.core.consts.Consts.EMPTY;


@Service
@Slf4j
public class ShareServiceImpl implements ShareService {

    @Autowired
    private TokenUtils tokenUtils;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private WidgetMapper widgetMapper;

    @Autowired
    private DisplayMapper displayMapper;

    @Autowired
    private DashboardMapper dashboardMapper;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ViewMapper viewMapper;

    @Autowired
    private ParamsMapper paramsMapper;

    @Autowired
    private ViewService viewService;

    @Autowired
    private MemDisplaySlideWidgetMapper memDisplaySlideWidgetMapper;

    @Autowired
    private MemDashboardWidgetMapper memDashboardWidgetMapper;

    @Autowired
    private FileUtils fileUtils;

    @Autowired
    private ServerUtils serverUtils;

    @Override
    public User shareLogin(String token, UserLogin userLogin) throws NotFoundException, ServerException, UnAuthorizedExecption {
        //AES??????
        String decrypt = AESUtils.decrypt(token, null);
        //??????????????????
        String tokenUserName = tokenUtils.getUsername(decrypt);
        String tokenPassword = tokenUtils.getPassword(decrypt);

        String[] tokenInfos = tokenUserName.split(Constants.SPLIT_CHAR_STRING);
        String[] tokenCrypts = tokenPassword.split(Constants.SPLIT_CHAR_STRING);

        if (tokenInfos.length < 2) {
            throw new ServerException("Invalid share token");
        }

        User loginUser = userMapper.selectByUsername(userLogin.getUsername());
        if (null == loginUser) {
            throw new NotFoundException("user is not found");
        }

        //????????????
        if (!BCrypt.checkpw(userLogin.getPassword(), loginUser.getPassword())) {
            throw new ServerException("password is wrong");
        }

        Long shareUserId = Long.parseLong(tokenInfos[1]);
        if (shareUserId.longValue() < 1L) {
            throw new ServerException("Invalid share token");
        }

        User shareUser = userMapper.getById(shareUserId);
        if (null == shareUser) {
            throw new ServerException("Invalid share token");
        }

        if (tokenInfos.length == 3) {
            if (tokenCrypts.length < 2) {
                throw new ServerException("Invalid share token");
            }
            try {
                String sharedUserName = tokenInfos[2];
                Long sharedUserId = Long.parseLong(tokenCrypts[1]);
                if (!(loginUser.getUsername().equals(sharedUserName) && loginUser.getId().equals(sharedUserId)) && !loginUser.getId().equals(shareUserId)) {
                    throw new ForbiddenExecption("The resource requires authentication, which was not supplied with the request");
                }
            } catch (NumberFormatException e) {
                throw new ForbiddenExecption("The resource requires authentication, which was not supplied with the request");
            }
        }

        //????????????
        if (!loginUser.getActive()) {
            throw new ServerException("this user is not active");
        }

        return loginUser;
    }

    /**
     * ????????????widget
     *
     * @param token
     * @param user
     * @return
     */
    @Override
    public ShareWidget getShareWidget(String token, User user) throws NotFoundException, ServerException, ForbiddenExecption, UnAuthorizedExecption {

        ShareInfo shareInfo = getShareInfo(token, user);
        if (null == shareInfo || shareInfo.getShareId().longValue() < 1L) {
            throw new ServerException("Invalid share token");
        }

        if (!StringUtils.isEmpty(shareInfo.getSharedUserName())) {
            if (!shareInfo.getSharedUserName().equals(user.getUsername())) {
                throw new ForbiddenExecption("ERROR Permission denied");
            }
        }

        ShareWidget shareWidget = widgetMapper.getShareWidgetById(shareInfo.getShareId());

        if (null == shareWidget) {
            throw new NotFoundException("widget not found");
        }

        String dateToken = generateShareToken(shareWidget.getId(), shareInfo.getSharedUserName(), shareInfo.getShareUser().getId());
        shareWidget.setDataToken(dateToken);
        return shareWidget;
    }


    /**
     * ????????????Display
     *
     * @param token
     * @param user
     * @return
     */
    @Override
    public ShareDisplay getShareDisplay(String token, User user) throws NotFoundException, ServerException, ForbiddenExecption, UnAuthorizedExecption {
        ShareInfo shareInfo = getShareInfo(token, user);
        if (null == shareInfo || shareInfo.getShareId().longValue() < 1L) {
            throw new ServerException("Invalid share token");
        }

        Long displayId = shareInfo.getShareId();
        Display display = displayMapper.getById(displayId);

        if (!StringUtils.isEmpty(shareInfo.getSharedUserName())) {
            if (!shareInfo.getSharedUserName().equals(user.getUsername())) {
                throw new ForbiddenExecption("ERROR Permission denied");
            }
        }

        if (null == display) {
            throw new ServerException("display is not found");
        }

        ShareDisplay shareDisplay = new ShareDisplay();

        BeanUtils.copyProperties(display, shareDisplay);

        List<MemDisplaySlideWidgetWithSlide> memWithSlides = memDisplaySlideWidgetMapper.getMemWithSlideByDisplayId(displayId);

        if (!CollectionUtils.isEmpty(memWithSlides)) {
            Set<DisplaySlide> displaySlideSet = new HashSet<>();
            Set<MemDisplaySlideWidget> memDisplaySlideWidgetSet = new HashSet<>();
            for (MemDisplaySlideWidgetWithSlide memWithSlide : memWithSlides) {
                displaySlideSet.add(memWithSlide.getDisplaySlide());
                MemDisplaySlideWidget memDisplaySlideWidget = new MemDisplaySlideWidget();
                BeanUtils.copyProperties(memWithSlide, memDisplaySlideWidget);
                memDisplaySlideWidgetSet.add(memDisplaySlideWidget);
            }

            if (!CollectionUtils.isEmpty(displaySlideSet)) {
                Set<ShareDisplaySlide> shareDisplaySlideSet = new HashSet<>();
                Iterator<DisplaySlide> slideIterator = displaySlideSet.iterator();
                while (slideIterator.hasNext()) {
                    DisplaySlide displaySlide = slideIterator.next();
                    ShareDisplaySlide shareDisplaySlide = new ShareDisplaySlide();
                    BeanUtils.copyProperties(displaySlide, shareDisplaySlide);

                    Iterator<MemDisplaySlideWidget> memIterator = memDisplaySlideWidgetSet.iterator();
                    Set<MemDisplaySlideWidget> relations = new HashSet<>();
                    while (memIterator.hasNext()) {
                        MemDisplaySlideWidget memDisplaySlideWidget = memIterator.next();
                        if (memDisplaySlideWidget.getDisplaySlideId().equals(displaySlide.getId())) {
                            relations.add(memDisplaySlideWidget);
                        }
                    }
                    shareDisplaySlide.setRelations(relations);
                    shareDisplaySlideSet.add(shareDisplaySlide);
                }
                shareDisplay.setSlides(shareDisplaySlideSet);
            }
        }

        Set<ShareWidget> shareWidgets = widgetMapper.getShareWidgetsByDisplayId(displayId);
        if (!CollectionUtils.isEmpty(shareWidgets)) {
            Iterator<ShareWidget> widgetIterator = shareWidgets.iterator();
            while (widgetIterator.hasNext()) {
                ShareWidget shareWidget = widgetIterator.next();
                String dateToken = generateShareToken(shareWidget.getId(), shareInfo.getSharedUserName(), shareInfo.getShareUser().getId());
                shareWidget.setDataToken(dateToken);
            }
            shareDisplay.setWidgets(shareWidgets);
        }

        return shareDisplay;
    }

    /**
     * ????????????dashboard
     *
     * @param token
     * @param user
     * @return
     */
    @Override
    public ShareDashboard getShareDashboard(String token, User user) throws NotFoundException, ServerException, ForbiddenExecption, UnAuthorizedExecption {
        ShareInfo shareInfo = getShareInfo(token, user);
        if (null == shareInfo || shareInfo.getShareId().longValue() < 1L) {
            throw new ServerException("Invalid share token");
        }

        if (!StringUtils.isEmpty(shareInfo.getSharedUserName())) {
            if (!shareInfo.getSharedUserName().equals(user.getUsername())) {
                throw new ForbiddenExecption("ERROR Permission denied");
            }
        }

        Long dashboardId = shareInfo.getShareId();
        Dashboard dashboard = dashboardMapper.getById(dashboardId);

        if (null == dashboard) {
            throw new NotFoundException("dashboard is not found");
        }

        ShareDashboard shareDashboard = new ShareDashboard();
        BeanUtils.copyProperties(dashboard, shareDashboard);

        List<MemDashboardWidget> memDashboardWidgets = memDashboardWidgetMapper.getByDashboardId(dashboardId);
        shareDashboard.setRelations(memDashboardWidgets);

        Set<ShareWidget> shareWidgets = widgetMapper.getShareWidgetsByDashboard(dashboardId);
        if (!CollectionUtils.isEmpty(shareWidgets)) {
            Iterator<ShareWidget> iterator = shareWidgets.iterator();
            while (iterator.hasNext()) {
                ShareWidget shareWidget = iterator.next();
                String dateToken = generateShareToken(shareWidget.getId(), shareInfo.getSharedUserName(), shareInfo.getShareUser().getId());
                shareWidget.setDataToken(dateToken);
            }
        }
        shareDashboard.setWidgets(shareWidgets);
        return shareDashboard;
    }

    /**
     * ??????????????????
     *
     * @param token
     * @param executeParam
     * @param user
     * @return
     */
    @Override
    public Paginate<Map<String, Object>> getShareData(String token, ViewExecuteParam executeParam, User user)
            throws NotFoundException, ServerException, ForbiddenExecption, UnAuthorizedExecption, SQLException {
        ShareInfo shareInfo = getShareInfo(token, user);
        if (null == shareInfo || shareInfo.getShareId().longValue() < 1L) {
            throw new ServerException("Invalid share token");
        }

        if (!StringUtils.isEmpty(shareInfo.getSharedUserName())) {
            if (!shareInfo.getSharedUserName().equals(user.getUsername())) {
                throw new ForbiddenExecption("ERROR Permission denied");
            }
        }

        ViewWithProjectAndSource viewWithProjectAndSource = viewMapper.getViewWithProjectAndSourceByWidgetId(shareInfo.getShareId());

        ProjectDetail projectDetail = projectService.getProjectDetail(viewWithProjectAndSource.getProjectId(), shareInfo.getShareUser(), false);
        boolean maintainer = projectService.isMaintainer(projectDetail, shareInfo.getShareUser());

        //TODO parameters replacement
//=======
//            Long viewId = shareInfo.getShareId();
//            ViewWithProjectAndSource viewWithProjectAndSource = viewMapper.getViewWithProjectAndSourceById(viewId);
//            viewWithProjectAndSource = updateViewWithProjectAndSource(viewWithProjectAndSource,request);
//
//            // replace user self-defined params
//            String uuid = request.getParameter("parameters");
//            if(!StringUtils.isEmpty(uuid) && tokens.length == 3){
//                Params params = paramsMapper.getByUuid(uuid);
//                params.setParamDetails(JSONObject.parseArray(params.getParams(), ParamsDetail.class));
//                String type = tokens[1];
//                Long contentId = Long.parseLong(tokens[2]);
//                for(ParamsDetail detail : params.getParamDetails()){
//                    if(detail.getViewId().equals(viewId)){
//                        if("dashboard".equals(type) && contentId.equals(detail.getDashboardId())){
//                            executeParam.setParams(detail.getVariables());
//                        } else if("widget".equals(type) && contentId.equals(detail.getWidgetId())) {
//                            executeParam.setParams(detail.getVariables());
//                        }
//
//                    }
//                }
//            }
//
//            String sharedUser = SecurityFilter.getLoginUsername(request);
//            list = viewService.getResultDataList(viewWithProjectAndSource, executeParam, shareInfo.getShareUser(),sharedUser);
////            if(VGUtils.isHiveDataSource(viewWithProjectAndSource.getSource())){
////                //if it slows down the query, change it to async scheduled job
////                sessionQueryThrottle.syncParameter(user.username);
////                final ViewWithProjectAndSource fViewWithProjectAndSource = viewWithProjectAndSource;
////                list = sessionQueryThrottle.controlQuery(new Callable<List<Map<String, Object>>>() {
////                    @Override
////                    public List<Map<String, Object>> call() throws Exception {
////                        return viewService.getResultDataList(fViewWithProjectAndSource, executeParam, shareInfo.getShareUser(),sharedUser);
////                    }
////                }, user.username);
////            } else {
////                list = viewService.getResultDataList(viewWithProjectAndSource, executeParam, shareInfo.getShareUser(),sharedUser);
////            }
//        } catch (ServerException e) {
//            return resultFail(user, request, null).message(e.getMessage());
//        } catch (UnAuthorizedExecption e) {
//            return resultFail(user, request, HttpCodeEnum.FORBIDDEN).message(e.getMessage());
//        }
//>>>>>>> drawis

        Paginate paginate = viewService.getResultDataList(maintainer, viewWithProjectAndSource, executeParam, shareInfo.getShareUser());
        return paginate;
    }

    /**
     * ???source???config??????????????????????????????????????????sqlUtils init ????????????????????????
     * @param viewWithProjectAndSource
     * @param request
     * @return
     */
    private ViewWithProjectAndSource updateViewWithProjectAndSource(ViewWithProjectAndSource viewWithProjectAndSource,HttpServletRequest request){
        JSONObject config = JSONObject.parseObject(viewWithProjectAndSource.getSource().getConfig());
        config.put("isSchedulerTask",true);
        viewWithProjectAndSource.getSource().setConfig(config.toJSONString());
        return viewWithProjectAndSource;
    }


    /**
     * ??????????????????csv???????????????
     *
     * @param executeParam
     * @param user
     * @param token
     * @return
     */
    @Override
    public String generationShareDataCsv(ViewExecuteParam executeParam, User user, String token) throws NotFoundException, ServerException, ForbiddenExecption, UnAuthorizedExecption {
        String filePath = null;
        ShareInfo shareInfo = getShareInfo(token, user);
        if (null == shareInfo || shareInfo.getShareId().longValue() < 1L) {
            throw new ServerException("Invalid share token");
        }

        if (!StringUtils.isEmpty(shareInfo.getSharedUserName())) {
            if (!shareInfo.getSharedUserName().equals(user.getUsername())) {
                throw new ForbiddenExecption("ERROR Permission denied");
            }
        }

        ViewWithSource viewWithSource = viewMapper.getViewWithProjectAndSourceByWidgetId(shareInfo.getShareId());
        ProjectDetail projectDetail = projectService.getProjectDetail(viewWithSource.getProjectId(), shareInfo.getShareUser(), false);
        ProjectPermission projectPermission = projectService.getProjectPermission(projectDetail, shareInfo.getShareUser());

        if (!projectPermission.getDownloadPermission()) {
            throw new ForbiddenExecption("ERROR Permission denied");
        }

        executeParam.setLimit(-1);
        executeParam.setPageSize(-1);
        executeParam.setPageNo(-1);

        PaginateWithQueryColumns paginate = null;
        try {
            boolean maintainer = projectService.isMaintainer(projectDetail, shareInfo.getShareUser());
            paginate = viewService.getResultDataList(maintainer, viewWithSource, executeParam, shareInfo.getShareUser());
        } catch (SQLException e) {
            e.printStackTrace();
            throw new ServerException(HttpCodeEnum.SERVER_ERROR.getMessage());
        }
        List<QueryColumn> columns = paginate.getColumns();

        if (!CollectionUtils.isEmpty(columns)) {
            String csvPath = fileUtils.fileBasePath + File.separator + "csv";
            File file = new File(csvPath);
            if (!file.exists()) {
                file.mkdirs();
            }
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
            String csvName = viewWithSource.getName() + "_" + sdf.format(new Date());
            String fileFullPath = CsvUtils.formatCsvWithFirstAsHeader(csvPath, csvName, columns, paginate.getResultList());
            filePath = fileFullPath.replace(fileUtils.fileBasePath, EMPTY);
        }

        return serverUtils.getHost() + filePath;
    }

    /**
     * ????????????distinct value
     *
     * @param token
     * @param viewId
     * @param param
     * @param user
     * @param request
     * @return
     */
    @Override
    public ResultMap getDistinctValue(String token, Long viewId, DistinctParam param, User user, HttpServletRequest request) {
        List<Map<String, Object>> list = null;
        try {

            ShareInfo shareInfo = getShareInfo(token, user);
            if (null == shareInfo || shareInfo.getShareId().longValue() < 1L) {
                return resultFail(user, request, null).message("Invalid share token");
            }

            if (!StringUtils.isEmpty(shareInfo.getSharedUserName())) {
                if (!shareInfo.getSharedUserName().equals(user.getUsername())) {
                    resultFail(user, request, HttpCodeEnum.FORBIDDEN).message("ERROR Permission denied");
                }
            }

            ViewWithProjectAndSource viewWithProjectAndSource = viewMapper.getViewWithProjectAndSourceById(viewId);
            if (null == viewWithProjectAndSource) {
                log.info("view (:{}) not found", viewId);
                return resultFail(user, request, null).message("view not found");
            }

            ProjectDetail projectDetail = projectService.getProjectDetail(viewWithProjectAndSource.getProjectId(), shareInfo.getShareUser(), false);

            if (!projectService.allowGetData(projectDetail, shareInfo.getShareUser())) {
                return resultFail(user, request, HttpCodeEnum.UNAUTHORIZED).message("ERROR Permission denied");
            }

            try {
                boolean maintainer = projectService.isMaintainer(projectDetail, shareInfo.getShareUser());
                list = viewService.getDistinctValueData(maintainer, viewWithProjectAndSource, param, shareInfo.getShareUser());
            } catch (ServerException e) {
                return resultFail(user, request, HttpCodeEnum.UNAUTHORIZED).message(e.getMessage());
            }
        } catch (NotFoundException e) {
            return resultFail(user, request, null).message(e.getMessage());
        } catch (ServerException e) {
            return resultFail(user, request, null).message(e.getMessage());
        } catch (UnAuthorizedExecption e) {
            return resultFail(user, request, HttpCodeEnum.FORBIDDEN).message(e.getMessage());
        }

        return resultSuccess(user, request).payloads(list);
    }


    /**
     * ????????????token
     *
     * @param shareEntityId
     * @param username
     * @return
     * @throws ServerException
     */
    @Override
    public String generateShareToken(Long shareEntityId, String username, Long userId) throws ServerException {
        /**
         * username: share??????Id:-:?????????id[:-:?????????????????????]
         * password: share??????Id[:-:????????????Id]
         */
        TokenEntity shareToken = new TokenEntity();
        String tokenUserName = shareEntityId + Constants.SPLIT_CHAR_STRING + userId;
        String tokenPassword = shareEntityId + EMPTY;
        if (!StringUtils.isEmpty(username)) {
            User shareUser = userMapper.selectByUsername(username);
            if (null == shareUser) {
                throw new ServerException("user : \"" + username + "\" not found");
            }
            tokenUserName += Constants.SPLIT_CHAR_STRING + username;
            tokenPassword += (Constants.SPLIT_CHAR_STRING + shareUser.getId());
        }
        shareToken.setUsername(tokenUserName);
        shareToken.setPassword(tokenPassword);

        //??????token ??? aes??????
        return AESUtils.encrypt(tokenUtils.generateContinuousToken(shareToken), null);
    }

    /**
     * ??????????????????id
     *
     * @param token
     * @param user
     * @return
     * @throws ServerException
     * @throws UnAuthorizedExecption
     */
    public ShareInfo getShareInfo(String token, User user) throws ServerException, ForbiddenExecption {

        if (StringUtils.isEmpty(token)) {
            throw new ServerException("Invalid share token");
        }

        //AES??????
        String decrypt = AESUtils.decrypt(token, null);
        //??????????????????
        String tokenUserName = tokenUtils.getUsername(decrypt);
        String tokenPassword = tokenUtils.getPassword(decrypt);

        String[] tokenInfos = tokenUserName.split(Constants.SPLIT_CHAR_STRING);
        String[] tokenCrypts = tokenPassword.split(Constants.SPLIT_CHAR_STRING);

        if (tokenInfos.length < 2) {
            throw new ServerException("Invalid share token");
        }

        Long shareUserId = Long.parseLong(tokenInfos[1]);
        if (shareUserId.longValue() < 1L) {
            throw new ServerException("Invalid share token");
        }

        User shareUser = userMapper.getById(shareUserId);
        if (null == shareUser) {
            throw new ServerException("Invalid share token");
        }

        String sharedUserName = null;
        if (tokenInfos.length == 3) {
            if (tokenCrypts.length < 2) {
                throw new ServerException("Invalid share token");
            }
            String username = tokenInfos[2];
            Long sharedUserId = Long.parseLong(tokenCrypts[1]);
            User sharedUser = userMapper.selectByUsername(username);
            if (null == sharedUser || !sharedUser.getId().equals(sharedUserId)) {
                throw new ForbiddenExecption("The resource requires authentication, which was not supplied with the request");
            }

            if (null == user || (!user.getId().equals(sharedUserId) && !user.getId().equals(shareUserId))) {
                throw new ForbiddenExecption("The resource requires authentication, which was not supplied with the request");
            }

            sharedUserName = username;
        }

        Long shareId1 = Long.parseLong(tokenInfos[0]);
        Long shareId2 = Long.parseLong(tokenCrypts[0]);

        if (shareId1.longValue() < 1L || shareId2.longValue() < 1L || !shareId1.equals(shareId2)) {
            throw new ServerException("Invalid share token");
        }

        return new ShareInfo(shareId1, shareUser, sharedUserName);
    }


    private ResultMap resultSuccess(User user, HttpServletRequest request) {
        if (null == user) {
            return new ResultMap().success();
        } else {
            return new ResultMap(tokenUtils).successAndRefreshToken(request);
        }
    }


    private ResultMap resultFail(User user, HttpServletRequest request, HttpCodeEnum httpCodeEnum) {
        if (null == user) {
            if (null != httpCodeEnum) {
                return new ResultMap().fail(httpCodeEnum.getCode());
            } else {
                return new ResultMap().fail();
            }
        } else {
            if (null != httpCodeEnum) {
                return new ResultMap(tokenUtils).failAndRefreshToken(request, httpCodeEnum);
            } else {
                return new ResultMap(tokenUtils).failAndRefreshToken(request);
            }
        }
    }
}

