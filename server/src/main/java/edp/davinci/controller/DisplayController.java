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

package edp.davinci.controller;

import com.alibaba.druid.util.StringUtils;
import com.google.common.collect.Iterables;
import edp.core.annotation.CurrentUser;
import edp.core.common.job.ScheduleService;
import edp.davinci.common.controller.BaseController;
import edp.davinci.core.common.Constants;
import edp.davinci.core.common.ResultMap;
import edp.davinci.dao.DisplayMapper;
import edp.davinci.dao.ProjectMapper;
import edp.davinci.dto.displayDto.*;
import edp.davinci.model.*;
import edp.davinci.service.DisplayService;
import edp.davinci.service.screenshot.ImageContent;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import springfox.documentation.annotations.ApiIgnore;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

@Api(value = "/displays", tags = "displays", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
@ApiResponses(@ApiResponse(code = 404, message = "display not found"))
@Slf4j
@RestController
@RequestMapping(value = Constants.BASE_API_PATH + "/displays", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
public class DisplayController extends BaseController {

    @Autowired
    private DisplayService displayService;

    @Autowired
    ScheduleService scheduleService;

    //TODO not this layer, should be removed
    @Autowired
    DisplayMapper displayMapper;
    @Autowired
    ProjectMapper projectMapper;

    @Value("${file.userfiles-path}")
    private String fileBasePath;

    /**
     * ??????display
     *
     * @param displayInfo
     * @param bindingResult
     * @param user
     * @param request
     * @return
     */
    @ApiOperation(value = "create new display", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity createDisplay(@Valid @RequestBody DisplayInfo displayInfo,
                                        @ApiIgnore BindingResult bindingResult,
                                        @ApiIgnore @CurrentUser User user,
                                        HttpServletRequest request) {
        if (bindingResult.hasErrors()) {
            ResultMap resultMap = new ResultMap(tokenUtils).failAndRefreshToken(request).message(bindingResult.getFieldErrors().get(0).getDefaultMessage());
            return ResponseEntity.status(resultMap.getCode()).body(resultMap);
        }

        Display display;
        if(displayInfo.getIsCopy()){
            display = displayService.copyDisplay(displayInfo, user);
        } else {
            display = displayService.createDisplay(displayInfo, user);
        }
        return ResponseEntity.ok(new ResultMap(tokenUtils).successAndRefreshToken(request).payload(display));
    }

    /**
     * ??????display ??????
     *
     * @param display
     * @param bindingResult
     * @param user
     * @param id
     * @param request
     * @return
     */
    @ApiOperation(value = "update display info", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity updateDisplay(@Valid @RequestBody DisplayUpdate display,
                                        @ApiIgnore BindingResult bindingResult,
                                        @ApiIgnore @CurrentUser User user,
                                        @PathVariable Long id, HttpServletRequest request) {

        if (bindingResult.hasErrors()) {
            ResultMap resultMap = new ResultMap(tokenUtils).failAndRefreshToken(request).message(bindingResult.getFieldErrors().get(0).getDefaultMessage());
            return ResponseEntity.status(resultMap.getCode()).body(resultMap);
        }

        if (invalidId(id) || !id.equals(display.getId())) {
            ResultMap resultMap = new ResultMap(tokenUtils).failAndRefreshToken(request).message("Invalid project id");
            return ResponseEntity.status(resultMap.getCode()).body(resultMap);
        }

        displayService.updateDisplay(display, user);
        return ResponseEntity.ok(new ResultMap(tokenUtils).successAndRefreshToken(request));
    }

    /**
     * ??????display
     *
     * @param id
     * @param user
     * @param request
     * @return
     */
    @ApiOperation(value = "delete a display", consumes = MediaType.APPLICATION_JSON_VALUE)
    @DeleteMapping("/{id}")
    public ResponseEntity deleteDisplay(@PathVariable Long id,
                                        @ApiIgnore @CurrentUser User user,
                                        HttpServletRequest request) {

        if (invalidId(id)) {
            ResultMap resultMap = new ResultMap(tokenUtils).failAndRefreshToken(request).message("Invalid display id");
            return ResponseEntity.status(resultMap.getCode()).body(resultMap);
        }

        displayService.deleteDisplay(id, user);

        return ResponseEntity.ok(new ResultMap(tokenUtils).successAndRefreshToken(request));
    }


    /**
     * ??????displaySlide
     *
     * @param displaySlideCreate
     * @param bindingResult
     * @param displayId
     * @param user
     * @param request
     * @return
     */
    @ApiOperation(value = "create new display slide", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PostMapping(value = "/{id}/slides", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity createDisplaySlide(@Valid @RequestBody DisplaySlideCreate displaySlideCreate,
                                             @ApiIgnore BindingResult bindingResult,
                                             @PathVariable("id") Long displayId,
                                             @ApiIgnore @CurrentUser User user,
                                             HttpServletRequest request) {

        if (bindingResult.hasErrors()) {
            ResultMap resultMap = new ResultMap(tokenUtils).failAndRefreshToken(request).message(bindingResult.getFieldErrors().get(0).getDefaultMessage());
            return ResponseEntity.status(resultMap.getCode()).body(resultMap);
        }

        if (invalidId(displayId) || !displayId.equals(displaySlideCreate.getDisplayId())) {
            ResultMap resultMap = new ResultMap(tokenUtils).failAndRefreshToken(request).message("Invalid display id");
            return ResponseEntity.status(resultMap.getCode()).body(resultMap);
        }

        DisplaySlide displaySlide = displayService.createDisplaySlide(displaySlideCreate, user);
        return ResponseEntity.ok(new ResultMap(tokenUtils).successAndRefreshToken(request).payload(displaySlide));
    }

    /**
     * ??????displayslides??????
     *
     * @param displaySlides
     * @param bindingResult
     * @param user
     * @param displayId
     * @param request
     * @return
     */
    @ApiOperation(value = "update display slides info", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PutMapping(value = "/{id}/slides", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity updateDisplaySlide(@Valid @RequestBody DisplaySlide[] displaySlides,
                                             @ApiIgnore BindingResult bindingResult,
                                             @ApiIgnore @CurrentUser User user,
                                             @PathVariable("id") Long displayId, HttpServletRequest request) {

        if (bindingResult.hasErrors()) {
            ResultMap resultMap = new ResultMap(tokenUtils).failAndRefreshToken(request).message(bindingResult.getFieldErrors().get(0).getDefaultMessage());
            return ResponseEntity.status(resultMap.getCode()).body(resultMap);
        }

        if (invalidId(displayId)) {
            ResultMap resultMap = new ResultMap(tokenUtils).failAndRefreshToken(request).message("Invalid display id");
            return ResponseEntity.status(resultMap.getCode()).body(resultMap);
        }

        if (null == displaySlides || displaySlides.length < 1) {
            ResultMap resultMap = new ResultMap(tokenUtils).failAndRefreshToken(request).message("display slide info cannot be EMPTY");
            return ResponseEntity.status(resultMap.getCode()).body(resultMap);
        }

        displayService.updateDisplaySildes(displayId, displaySlides, user);
        return ResponseEntity.ok(new ResultMap(tokenUtils).successAndRefreshToken(request));
    }


    /**
     * ??????DisplaySlide
     *
     * @param slideId
     * @param user
     * @param request
     * @return
     */
    @ApiOperation(value = "delete display slide", consumes = MediaType.APPLICATION_JSON_VALUE)
    @DeleteMapping("/slides/{slideId}")
    public ResponseEntity deleteDisplaySlide(@PathVariable("slideId") Long slideId,
                                             @ApiIgnore @CurrentUser User user,
                                             HttpServletRequest request) {

        if (invalidId(slideId)) {
            ResultMap resultMap = new ResultMap(tokenUtils).failAndRefreshToken(request).message("Invalid display slide id");
            return ResponseEntity.status(resultMap.getCode()).body(resultMap);
        }

        displayService.deleteDisplaySlide(slideId, user);
        return ResponseEntity.ok(new ResultMap(tokenUtils).successAndRefreshToken(request));
    }


    /**
     * ???displaySlide?????????widget??????
     *
     * @param slideWidgetCreates
     * @param displayId
     * @param slideId
     * @param bindingResult
     * @param user
     * @param request
     * @return
     */
    @ApiOperation(value = "add display slide widgets", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PostMapping(value = "/{displayId}/slides/{slideId}/widgets", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity addMemDisplaySlideWidgets(@PathVariable("displayId") Long displayId,
                                                    @PathVariable("slideId") Long slideId,
                                                    @Valid @RequestBody MemDisplaySlideWidgetCreate[] slideWidgetCreates,
                                                    @ApiIgnore BindingResult bindingResult,
                                                    @ApiIgnore @CurrentUser User user,
                                                    HttpServletRequest request) {

        if (invalidId(displayId)) {
            ResultMap resultMap = new ResultMap(tokenUtils).failAndRefreshToken(request).message("Invalid display id");
            return ResponseEntity.status(resultMap.getCode()).body(resultMap);
        }

        if (invalidId(slideId)) {
            ResultMap resultMap = new ResultMap(tokenUtils).failAndRefreshToken(request).message("Invalid display slide id");
            return ResponseEntity.status(resultMap.getCode()).body(resultMap);
        }

        if (null == slideWidgetCreates || slideWidgetCreates.length < 1) {
            ResultMap resultMap = new ResultMap(tokenUtils).failAndRefreshToken(request).message("display slide widget info cannot be EMPTY");
            return ResponseEntity.status(resultMap.getCode()).body(resultMap);
        }

        for (MemDisplaySlideWidgetCreate slideWidgetCreate : slideWidgetCreates) {
            if (!slideWidgetCreate.getDisplaySlideId().equals(slideId)) {
                ResultMap resultMap = new ResultMap(tokenUtils).failAndRefreshToken(request).message("Invalid display slide id");
                return ResponseEntity.status(resultMap.getCode()).body(resultMap);
            }
            if (slideWidgetCreate.getType() == 1 && invalidId(slideWidgetCreate.getWidgetId())) {
                ResultMap resultMap = new ResultMap(tokenUtils).failAndRefreshToken(request).message("Invalid widget id");
                return ResponseEntity.status(resultMap.getCode()).body(resultMap);
            }
        }

        if (bindingResult.hasErrors()) {
            ResultMap resultMap = new ResultMap(tokenUtils).failAndRefreshToken(request).message(bindingResult.getFieldErrors().get(0).getDefaultMessage());
            return ResponseEntity.status(resultMap.getCode()).body(resultMap);
        }

        List<MemDisplaySlideWidget> memDisplaySlideWidgets = displayService.addMemDisplaySlideWidgets(displayId, slideId, slideWidgetCreates, user);
        return ResponseEntity.ok(new ResultMap(tokenUtils).successAndRefreshToken(request).payloads(memDisplaySlideWidgets));
    }

    /**
     * ????????????widget??????
     *
     * @param memDisplaySlideWidgets
     * @param displayId
     * @param slideId
     * @param bindingResult
     * @param user
     * @param request
     * @return
     */
    @ApiOperation(value = "update display slide widgets", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PutMapping(value = "/{displayId}/slides/{slideId}/widgets", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity updateMemDisplaySlideWidgets(@PathVariable("displayId") Long displayId,
                                                       @PathVariable("slideId") Long slideId,
                                                       @Valid @RequestBody MemDisplaySlideWidgetDto[] memDisplaySlideWidgets,
                                                       @ApiIgnore BindingResult bindingResult,
                                                       @ApiIgnore @CurrentUser User user,
                                                       HttpServletRequest request) {

        if (invalidId(displayId)) {
            ResultMap resultMap = new ResultMap(tokenUtils).failAndRefreshToken(request).message("Invalid display id");
            return ResponseEntity.status(resultMap.getCode()).body(resultMap);
        }

        if (invalidId(slideId)) {
            ResultMap resultMap = new ResultMap(tokenUtils).failAndRefreshToken(request).message("Invalid display slide id");
            return ResponseEntity.status(resultMap.getCode()).body(resultMap);
        }

        if (null == memDisplaySlideWidgets || memDisplaySlideWidgets.length < 1) {
            ResultMap resultMap = new ResultMap(tokenUtils).failAndRefreshToken(request).message("display slide widget info cannot be EMPTY");
            return ResponseEntity.status(resultMap.getCode()).body(resultMap);
        }

        for (MemDisplaySlideWidget slideWidgetCreate : memDisplaySlideWidgets) {
            if (!slideWidgetCreate.getDisplaySlideId().equals(slideId)) {
                ResultMap resultMap = new ResultMap(tokenUtils).failAndRefreshToken(request).message("Invalid display slide id");
                return ResponseEntity.status(resultMap.getCode()).body(resultMap);
            }
            if (1 == slideWidgetCreate.getType() && invalidId(slideWidgetCreate.getWidgetId())) {
                ResultMap resultMap = new ResultMap(tokenUtils).failAndRefreshToken(request).message("Invalid widget id");
                return ResponseEntity.status(resultMap.getCode()).body(resultMap);
            }
        }

        if (bindingResult.hasErrors()) {
            ResultMap resultMap = new ResultMap(tokenUtils).failAndRefreshToken(request).message(bindingResult.getFieldErrors().get(0).getDefaultMessage());
            return ResponseEntity.status(resultMap.getCode()).body(resultMap);
        }

        displayService.updateMemDisplaySlideWidgets(displayId, slideId, memDisplaySlideWidgets, user);
        return ResponseEntity.ok(new ResultMap(tokenUtils).successAndRefreshToken(request));
    }


    /**
     * ??????displaySlide??????widget????????????
     *
     * @param memDisplaySlideWidget
     * @param bindingResult
     * @param relationId
     * @param user
     * @param request
     * @return
     */
    @ApiOperation(value = "update display slide widget", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PutMapping(value = "/slides/widgets/{relationId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity updateMemDisplaySlideWidget(@PathVariable("relationId") Long relationId,
                                                      @Valid @RequestBody MemDisplaySlideWidget memDisplaySlideWidget,
                                                      @ApiIgnore BindingResult bindingResult,
                                                      @ApiIgnore @CurrentUser User user,
                                                      HttpServletRequest request) {

        if (bindingResult.hasErrors()) {
            ResultMap resultMap = new ResultMap(tokenUtils).failAndRefreshToken(request).message(bindingResult.getFieldErrors().get(0).getDefaultMessage());
            return ResponseEntity.status(resultMap.getCode()).body(resultMap);
        }

        if (invalidId(relationId) || !memDisplaySlideWidget.getId().equals(relationId)) {
            ResultMap resultMap = new ResultMap(tokenUtils).failAndRefreshToken(request).message("Invalid relation id");
            return ResponseEntity.status(resultMap.getCode()).body(resultMap);
        }

        displayService.updateMemDisplaySlideWidget(memDisplaySlideWidget, user);
        return ResponseEntity.ok(new ResultMap(tokenUtils).successAndRefreshToken(request));
    }


    /**
     * ??????displaySlide??????widget????????????
     *
     * @param relationId
     * @param user
     * @param request
     * @return
     */
    @ApiOperation(value = "delete display slide widget", consumes = MediaType.APPLICATION_JSON_VALUE)
    @DeleteMapping("/slides/widgets/{relationId}")
    public ResponseEntity deleteMemDisplaySlideWidget(@PathVariable("relationId") Long relationId,
                                                      @ApiIgnore @CurrentUser User user,
                                                      HttpServletRequest request) {

        if (invalidId(relationId)) {
            ResultMap resultMap = new ResultMap(tokenUtils).failAndRefreshToken(request).message("Invalid relation id");
            return ResponseEntity.status(resultMap.getCode()).body(resultMap);
        }

        displayService.deleteMemDisplaySlideWidget(relationId, user);
        return ResponseEntity.ok(new ResultMap(tokenUtils).successAndRefreshToken(request));
    }

    /**
     * ??????display??????
     *
     * @param projectId
     * @param user
     * @param request
     * @return
     */
    @ApiOperation(value = "get displays")
    @GetMapping
    public ResponseEntity getDisplays(@RequestParam Long projectId,
                                      @ApiIgnore @CurrentUser User user,
                                      HttpServletRequest request) {

        if (invalidId(projectId)) {
            ResultMap resultMap = new ResultMap(tokenUtils).failAndRefreshToken(request).message("Invalid project id");
            return ResponseEntity.status(resultMap.getCode()).body(resultMap);
        }
        List<Display> displayList = displayService.getDisplayListByProject(projectId, user);
        return ResponseEntity.ok(new ResultMap(tokenUtils).successAndRefreshToken(request).payloads(displayList));
    }


    /**
     * ??????display slide??????
     *
     * @param id
     * @param user
     * @param request
     * @return
     */
    @ApiOperation(value = "get display slides")
    @GetMapping("/{id}/slides")
    public ResponseEntity getDisplaySlide(@PathVariable Long id,
                                          @ApiIgnore @CurrentUser User user,
                                          HttpServletRequest request) {
        if (invalidId(id)) {
            ResultMap resultMap = new ResultMap(tokenUtils).failAndRefreshToken(request).message("Invalid Display id");
            return ResponseEntity.status(resultMap.getCode()).body(resultMap);
        }
        DisplayWithSlides displayWithSlides = displayService.getDisplaySlideList(id, user);
        return ResponseEntity.ok(new ResultMap(tokenUtils).successAndRefreshToken(request).payload(displayWithSlides));
    }


    /**
     * ??????displaySlide???widgets??????????????????
     *
     * @param displayId
     * @param slideId
     * @param user
     * @param request
     * @return
     */
    @ApiOperation(value = "get display slide widgets")
    @GetMapping("/{displayId}/slides/{slideId}")
    public ResponseEntity getDisplaySlideWidgets(@PathVariable("displayId") Long displayId,
                                                 @PathVariable("slideId") Long slideId,
                                                 @ApiIgnore @CurrentUser User user,
                                                 HttpServletRequest request) {

        if (invalidId(displayId)) {
            ResultMap resultMap = new ResultMap(tokenUtils).failAndRefreshToken(request).message("Invalid Display id");
            return ResponseEntity.status(resultMap.getCode()).body(resultMap);
        }

        if (invalidId(slideId)) {
            ResultMap resultMap = new ResultMap(tokenUtils).failAndRefreshToken(request).message("Invalid Display Slide id");
            return ResponseEntity.status(resultMap.getCode()).body(resultMap);
        }

        SlideWithMem displaySlideMem = displayService.getDisplaySlideMem(displayId, slideId, user);
        return ResponseEntity.ok(new ResultMap(tokenUtils).successAndRefreshToken(request).payload(displaySlideMem));
    }


    /**
     * ??????displaySlide???widgets??????????????????
     *
     * @param displayId
     * @param slideId
     * @param user
     * @param request
     * @return
     */
    @ApiOperation(value = "delete display slide widgets")
    @DeleteMapping("/{displayId}/slides/{slideId}/widgets")
    public ResponseEntity deleteDisplaySlideWeight(@PathVariable("displayId") Long displayId,
                                                   @PathVariable("slideId") Long slideId,
                                                   @RequestBody Long[] ids,
                                                   @ApiIgnore @CurrentUser User user,
                                                   HttpServletRequest request) {

        if (invalidId(displayId)) {
            ResultMap resultMap = new ResultMap(tokenUtils).failAndRefreshToken(request).message("Invalid Display id");
            return ResponseEntity.status(resultMap.getCode()).body(resultMap);
        }

        if (invalidId(slideId)) {
            ResultMap resultMap = new ResultMap(tokenUtils).failAndRefreshToken(request).message("Invalid Display Slide id");
            return ResponseEntity.status(resultMap.getCode()).body(resultMap);
        }

        if (null == ids || ids.length < 1) {
            ResultMap resultMap = new ResultMap(tokenUtils).failAndRefreshToken(request).message("nothing be deleted");
            return ResponseEntity.status(resultMap.getCode()).body(resultMap);
        }

        displayService.deleteDisplaySlideWidgetList(displayId, slideId, ids, user);
        return ResponseEntity.ok(new ResultMap(tokenUtils).successAndRefreshToken(request));
    }


    /**
     * ???????????????
     *
     * @param file
     * @param request
     * @return
     */
    @ApiOperation(value = "upload avatar")
    @PostMapping(value = "/upload/coverImage")
    public ResponseEntity uploadAvatar(@RequestParam("coverImage") MultipartFile file,
                                       HttpServletRequest request) {


        if (file.isEmpty() || StringUtils.isEmpty(file.getOriginalFilename())) {
            ResultMap resultMap = new ResultMap(tokenUtils).failAndRefreshToken(request).message("file can not be EMPTY");
            return ResponseEntity.status(resultMap.getCode()).body(resultMap);
        }

        String avatar = displayService.uploadAvatar(file);
        return ResponseEntity.ok(new ResultMap(tokenUtils).successAndRefreshToken(request).payload(avatar));
    }


    /**
     * ??????slide?????????
     *
     * @param slideId
     * @param file
     * @param user
     * @param request
     * @return
     */
    @ApiOperation(value = "upload avatar")
    @PostMapping(value = "/slide/{slideId}/upload/bgImage")
    public ResponseEntity uploadSlideBGImage(@PathVariable Long slideId,
                                             @RequestParam("backgroundImage") MultipartFile file,
                                             @ApiIgnore @CurrentUser User user,
                                             HttpServletRequest request) {

        if (invalidId(slideId)) {
            ResultMap resultMap = new ResultMap(tokenUtils).failAndRefreshToken(request).message("Invalid slide id");
            return ResponseEntity.status(resultMap.getCode()).body(resultMap);
        }

        if (file.isEmpty() || StringUtils.isEmpty(file.getOriginalFilename())) {
            ResultMap resultMap = new ResultMap(tokenUtils).failAndRefreshToken(request).message("file can not be EMPTY");
            return ResponseEntity.status(resultMap.getCode()).body(resultMap);
        }

        String slideBGImage = displayService.uploadSlideBGImage(slideId, file, user);
        return ResponseEntity.ok(new ResultMap(tokenUtils).successAndRefreshToken(request).payload(slideBGImage));
    }

    /**
     * ??????slide?????????
     *
     * @param relationId
     * @param file
     * @param user
     * @param request
     * @return
     */
    @ApiOperation(value = "upload subwidget bgImage")
    @PostMapping(value = "/slide/widget/{relationId}/bgImage")
    public ResponseEntity uploadSlideSubWidgetBGImage(@PathVariable Long relationId,
                                                      @RequestParam("backgroundImage") MultipartFile file,
                                                      @ApiIgnore @CurrentUser User user,
                                                      HttpServletRequest request) {

        if (invalidId(relationId)) {
            ResultMap resultMap = new ResultMap(tokenUtils).failAndRefreshToken(request).message("Invalid relation id");
            return ResponseEntity.status(resultMap.getCode()).body(resultMap);
        }

        if (file.isEmpty() || StringUtils.isEmpty(file.getOriginalFilename())) {
            ResultMap resultMap = new ResultMap(tokenUtils).failAndRefreshToken(request).message("file can not be EMPTY");
            return ResponseEntity.status(resultMap.getCode()).body(resultMap);
        }

        String bgImage = displayService.uploadSlideSubWidgetBGImage(relationId, file, user);
        return ResponseEntity.ok(new ResultMap(tokenUtils).successAndRefreshToken(request).payload(bgImage));
    }

    /**
     * ??????display
     *
     * @param id
     * @param username
     * @param user
     * @param request
     * @return
     */
    @ApiOperation(value = "share display")
    @GetMapping("/{id}/share")
    public ResponseEntity shareDisplay(@PathVariable Long id,
                                       @RequestParam(required = false) String username,
                                       @ApiIgnore @CurrentUser User user,
                                       HttpServletRequest request) {
        if (invalidId(id)) {
            ResultMap resultMap = new ResultMap(tokenUtils).failAndRefreshToken(request).message("Invalid id");
            return ResponseEntity.status(resultMap.getCode()).body(resultMap);
        }

        String shareToken = displayService.shareDisplay(id, user, username);
        return ResponseEntity.ok(new ResultMap(tokenUtils).successAndRefreshToken(request).payload(shareToken));
    }


    /**
     * ??????Display ???????????????????????????
     *
     * @param id
     * @param request
     * @return
     */
    @ApiOperation(value = "get display  exclude roles")
    @GetMapping("/{id}/exclude/roles")
    public ResponseEntity getDisplayExcludeRoles(@PathVariable Long id,
                                                 HttpServletRequest request) {
        if (invalidId(id)) {
            ResultMap resultMap = new ResultMap(tokenUtils).failAndRefreshToken(request).message("Invalid id");
            return ResponseEntity.status(resultMap.getCode()).body(resultMap);
        }

        List<Long> excludeRoles = displayService.getDisplayExcludeRoles(id);
        return ResponseEntity.ok(new ResultMap(tokenUtils).successAndRefreshToken(request).payloads(excludeRoles));
    }


    /**
     * ??????Display ???????????????????????????
     *
     * @param id
     * @param request
     * @return
     */
    @ApiOperation(value = "get display slide exclude roles")
    @GetMapping("/slide/{id}/exclude/roles")
    public ResponseEntity getSlideExcludeRoles(@PathVariable Long id,
                                               HttpServletRequest request) {
        if (invalidId(id)) {
            ResultMap resultMap = new ResultMap(tokenUtils).failAndRefreshToken(request).message("Invalid id");
            return ResponseEntity.status(resultMap.getCode()).body(resultMap);
        }

        List<Long> excludeRoles = displayService.getSlideExecludeRoles(id);
        return ResponseEntity.ok(new ResultMap(tokenUtils).successAndRefreshToken(request).payloads(excludeRoles));
    }

    @ApiOperation(value = "preview display")
    @GetMapping(value = "/{id}/preview", produces = MediaType.IMAGE_PNG_VALUE)
    @ResponseBody
    public void previewDisplay(@PathVariable Long id,
                                        @RequestParam(required = false) String username,
                                        @ApiIgnore @CurrentUser User user,
                                        HttpServletRequest request,
                                        HttpServletResponse response) throws IOException {
        Display display = displayMapper.getById(id);
        Project project = projectMapper.getById(display.getProjectId());
        if(!user.getId().equals(project.getUserId())){
            response.setContentType(MediaType.TEXT_PLAIN_VALUE);
            response.getWriter().write("You have no access to this display.");
            return;
        }

        FileInputStream inputStream = null;
        try {
            List<ImageContent> imageFiles = scheduleService.getPreviewImage(user.getId(), "display", id);
            File imageFile = Iterables.getFirst(imageFiles, null).getImageFile();
            inputStream = new FileInputStream(imageFile);
            response.setContentType(MediaType.IMAGE_PNG_VALUE);
            IOUtils.copy(inputStream, response.getOutputStream());
        } catch (IOException e) {
            e.printStackTrace();
            log.error(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            log.error(e.getMessage());
        } finally {
            inputStream.close();
        }
    }
}
