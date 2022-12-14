package org.jeecg.modules.system.controller;


import cn.hutool.core.util.RandomUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.apache.shiro.authz.annotation.RequiresRoles;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.PermissionData;
import org.jeecg.common.constant.CommonConstant;
import org.jeecg.common.system.api.ISysBaseAPI;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.common.system.util.JwtUtil;
import org.jeecg.common.system.vo.DictModel;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.common.util.PasswordUtil;
import org.jeecg.common.util.PmsUtil;
import org.jeecg.common.util.RedisUtil;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.common.controller.BaseController;
import org.jeecg.modules.system.entity.*;
import org.jeecg.modules.system.model.DepartIdModel;
import org.jeecg.modules.system.model.SysUserModel;
import org.jeecg.modules.system.model.SysUserSysDepartModel;
import org.jeecg.modules.system.service.*;
import org.jeecg.modules.system.vo.SysDepartUsersVO;
import org.jeecg.modules.system.vo.SysUserRoleVO;
import org.jeecgframework.poi.excel.ExcelImportUtil;
import org.jeecgframework.poi.excel.def.NormalExcelConstants;
import org.jeecgframework.poi.excel.entity.ExportParams;
import org.jeecgframework.poi.excel.entity.ImportParams;
import org.jeecgframework.poi.excel.view.JeecgEntityExcelView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * ????????? ???????????????
 * </p>
 *
 * @Author scott
 * @since 2018-12-20
 */
@Slf4j
@RestController
@RequestMapping("/sys/user")
public class SysUserController extends BaseController {
	@Autowired
	private ISysBaseAPI sysBaseAPI;
	@Autowired
	private ISysUserService sysUserService;
    @Autowired
    private ISysDepartService sysDepartService;
	@Autowired
	private ISysUserRoleService sysUserRoleService;
	@Autowired
	private ISysUserDepartService sysUserDepartService;
	@Autowired
	private ISysRoleService sysRoleService;
    @Autowired
    private ISysDepartRoleUserService departRoleUserService;
    @Autowired
    private ISysDepartRoleService departRoleService;
    @Autowired
    private ISysConfigService sysConfigService;
	@Autowired
	private RedisUtil redisUtil;

    @Value("${jeecg.path.upload}")
    private String upLoadPath;

    @PermissionData(pageComponent = "system/UserList")
	@RequestMapping(value = "/list", method = RequestMethod.GET)
	public Result<IPage<SysUserModel>> queryPageList(SysUserModel user, @RequestParam(name="pageNo", defaultValue="1") Integer pageNo,
                                                @RequestParam(name="pageSize", defaultValue="10") Integer pageSize, HttpServletRequest req) {
		Result<IPage<SysUserModel>> result = new Result<IPage<SysUserModel>>();
//		QueryWrapper<SysUserModel> queryWrapper = QueryGenerator.initQueryWrapper(user, req.getParameterMap());
        Map<String, String[]> param = req.getParameterMap();
        String[] areaRaw = param.get("area");
        String provinceId = null;
        String cityId = null;
        if (areaRaw != null && areaRaw.length == 1){
            JSONObject area = JSONObject.parseObject(areaRaw[0]);
            provinceId = area.getString("provinceId");
            cityId = area.getString("cityId");
        }
        String roleId = param.containsKey("roleId")?param.get("roleId")[0]:null;
        String departName = param.containsKey("departName")?param.get("departName")[0]:null;
        QueryWrapper<SysUserModel> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(org.apache.commons.lang3.StringUtils.isNotEmpty(provinceId), "sys_user.province", provinceId);
        queryWrapper.eq(org.apache.commons.lang3.StringUtils.isNotEmpty(cityId),"sys_user.city", cityId);
        queryWrapper.eq(org.apache.commons.lang3.StringUtils.isNotEmpty(roleId), "role_id", roleId);
        queryWrapper.eq(org.apache.commons.lang3.StringUtils.isNotEmpty(departName), "sys_depart.depart_name", departName);
        queryWrapper.eq("sys_user.del_flag", 0);
        QueryGenerator.installMplus(queryWrapper, user, req.getParameterMap());

        //???admin???dev???????????????????????????????????????????????????
        List<String> myDeptIds = new ArrayList<>();
        if(!hasRole("admin") && !hasRole("dev")){
            myDeptIds = sysDepartService.getMySubDepIdsByDepId(getCurrentUser().getDepartIds());
            if (myDeptIds==null || myDeptIds.isEmpty()){
                result.error500("????????????????????????");
                return result;
            }
        }

		Page<SysUserModel> page = new Page<SysUserModel>(pageNo, pageSize);
        IPage<SysUserModel> pageList = sysUserService.getUserList(page, queryWrapper, myDeptIds);

//		IPage<SysUserModel> pageList = sysUserService.page(page, queryWrapper);

        //?????????????????????????????????
        //step.1 ?????????????????? useids
        //step.2 ?????? useids?????????????????????????????????????????????
        List<String> userIds = pageList.getRecords().stream().map(SysUser::getId).collect(Collectors.toList());
        if(userIds!=null && userIds.size()>0){
            Map<String,String>  useDepNames = sysUserService.getDepNamesByUserIds(userIds);
            pageList.getRecords().forEach(item->{
                item.setOrgCodeTxt(useDepNames.get(item.getId()));
            });
            Map<String,String>  roleNames = sysUserService.getRoleNamesByUserIds(userIds);
            pageList.getRecords().forEach(item->{
                item.setRoleTxt(roleNames.get(item.getId()));
            });
        }
		result.setSuccess(true);
		result.setResult(pageList);
		log.info(pageList.toString());
		return result;
	}

	@RequestMapping(value = "/add", method = RequestMethod.POST)
    //@RequiresPermissions("user:add")
	public Result<SysUser> add(@RequestBody JSONObject jsonObject) {
		Result<SysUser> result = new Result<SysUser>();
		String selectedRoles = jsonObject.getString("selectedroles");
        if (StringUtils.isNotBlank(selectedRoles)){
            int currentRoleLevel = getUserRoleLevel();
            for (String roleId: selectedRoles.split(",")){
                SysRole role = sysRoleService.getById(roleId);
                if (role.getRoleLevel() > currentRoleLevel){
                    result.error500("???????????????????????????????????????");
                    return result;
                }
            }
        }else{
            //??????????????????
            SysRole role = sysRoleService.getRoleByCode("student");
            if (role!=null){
                selectedRoles = role.getId();
            }
        }

		String selectedDeparts = jsonObject.getString("selecteddeparts");
		try {
			SysUser user = JSON.parseObject(jsonObject.toJSONString(), SysUser.class);
			user.setCreateTime(new Date());//??????????????????
			String salt = oConvertUtils.randomGen(8);
			user.setSalt(salt);
			String passwordEncode = PasswordUtil.encrypt(user.getUsername(), user.getPassword(), salt);
			user.setPassword(passwordEncode);
			user.setStatus(1);
			user.setDelFlag(CommonConstant.DEL_FLAG_0);
			sysUserService.save(user);
			sysUserService.addUserWithRole(user, selectedRoles);
            sysUserService.addUserWithDepart(user, selectedDeparts);
			result.success("???????????????");
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			result.error500("????????????");
		}
		return result;
	}

	@RequestMapping(value = "/edit", method = RequestMethod.PUT)
    //@RequiresRoles({"admin"})
    //@RequiresPermissions("user:edit")
	public Result<SysUser> edit(@RequestBody JSONObject jsonObject) {
		Result<SysUser> result = new Result<SysUser>();
		try {
			SysUser sysUser = sysUserService.getById(jsonObject.getString("id"));
			sysBaseAPI.addLog("???????????????id??? " +jsonObject.getString("id") ,CommonConstant.LOG_TYPE_2, 2);
			if(sysUser==null) {
				result.error500("?????????????????????");
			}else {
                if (lessThanUserRoleLevel(sysUser.getId())){
                    result.error500("????????????");
                    return result;
                }

				SysUser user = JSON.parseObject(jsonObject.toJSONString(), SysUser.class);
				user.setUpdateTime(new Date());
				//String passwordEncode = PasswordUtil.encrypt(user.getUsername(), user.getPassword(), sysUser.getSalt());
				user.setPassword(sysUser.getPassword());
				String roles = jsonObject.getString("selectedroles");
                String departs = jsonObject.getString("selecteddeparts");

                if (StringUtils.isNotBlank(roles)){
                    int currentRoleLevel = getUserRoleLevel();
                    for (String roleId: roles.split(",")){
                        SysRole role = sysRoleService.getById(roleId);
                        if (role.getRoleLevel() > currentRoleLevel){
                            result.error500("???????????????????????????????????????");
                            return result;
                        }
                    }
                }else{
                    //??????????????????
                    SysRole role = sysRoleService.getRoleByCode("student");
                    if (role!=null){
                        roles = role.getId();
                    }
                }

				sysUserService.editUserWithRole(user, roles);
                sysUserService.editUserWithDepart(user, departs);
                sysUserService.updateNullPhoneEmail();
				result.success("????????????!");
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			result.error500("????????????");
		}
		return result;
	}

	/**
	 * ????????????
	 */
	//@RequiresRoles({"admin"})
	@RequestMapping(value = "/delete", method = RequestMethod.DELETE)
	public Result<?> delete(@RequestParam(name="id",required=true) String id) {
		sysBaseAPI.addLog("???????????????id??? " +id ,CommonConstant.LOG_TYPE_2, 3);
        if (lessThanUserRoleLevel(id)){
            return Result.error("????????????");
        }
		this.sysUserService.deleteUser(id);
		return Result.ok("??????????????????");
	}

	/**
	 * ??????????????????
	 */
	//@RequiresRoles({"admin"})
	@RequestMapping(value = "/deleteBatch", method = RequestMethod.DELETE)
	public Result<?> deleteBatch(@RequestParam(name="ids",required=true) String ids) {
		sysBaseAPI.addLog("????????????????????? ids??? " +ids ,CommonConstant.LOG_TYPE_2, 3);
		for (String id: ids.split(",")){
            if (lessThanUserRoleLevel(id)){
                return Result.error("????????????");
            }
        }
        this.sysUserService.deleteBatchUsers(ids);
		return Result.ok("????????????????????????");
	}

	/**
	  * ??????&????????????
	 * @param jsonObject
	 * @return
	 */
	//@RequiresRoles({"admin"})
	@RequestMapping(value = "/frozenBatch", method = RequestMethod.PUT)
	public Result<SysUser> frozenBatch(@RequestBody JSONObject jsonObject) {
		Result<SysUser> result = new Result<SysUser>();
		try {
			String ids = jsonObject.getString("ids");
			String status = jsonObject.getString("status");
			String[] arr = ids.split(",");
			for (String id : arr) {
				if(oConvertUtils.isNotEmpty(id)) {
                    if (lessThanUserRoleLevel(id)){
                        result.error500("????????????");
                        return result;
                    }
					this.sysUserService.update(new SysUser().setStatus(Integer.parseInt(status)),
							new UpdateWrapper<SysUser>().lambda().eq(SysUser::getId,id));
				}
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			result.error500("????????????"+e.getMessage());
		}
		result.success("????????????!");
		return result;

    }

    @RequestMapping(value = "/queryById", method = RequestMethod.GET)
    public Result<SysUser> queryById(@RequestParam(name = "id", required = true) String id) {
        Result<SysUser> result = new Result<SysUser>();
        SysUser sysUser = sysUserService.getById(id);
        if (sysUser == null) {
            result.error500("?????????????????????");
        } else {
            result.setResult(sysUser);
            result.setSuccess(true);
        }
        return result;
    }

    @RequestMapping(value = "/queryUserRole", method = RequestMethod.GET)
    public Result<List<String>> queryUserRole(@RequestParam(name = "userid", required = true) String userid) {
        Result<List<String>> result = new Result<>();
        List<String> list = new ArrayList<String>();
        List<SysUserRole> userRole = sysUserRoleService.list(new QueryWrapper<SysUserRole>().lambda().eq(SysUserRole::getUserId, userid));
        if (userRole == null || userRole.size() <= 0) {
            result.error500("?????????????????????????????????");
        } else {
            for (SysUserRole sysUserRole : userRole) {
                list.add(sysUserRole.getRoleId());
            }
            result.setSuccess(true);
            result.setResult(list);
        }
        return result;
    }


    /**
	  *  ??????????????????????????????<br>
	  *  ?????????????????? ???????????????????????????????????????
     *
     * @param sysUser
     * @return
     */
    @RequestMapping(value = "/checkOnlyUser", method = RequestMethod.GET)
    public Result<Boolean> checkOnlyUser(SysUser sysUser) {
        Result<Boolean> result = new Result<>();
        //??????????????????false?????????????????????
        result.setResult(true);
        try {
            //??????????????????????????????????????????
            SysUser user = sysUserService.getOne(new QueryWrapper<SysUser>(sysUser));
            if (user != null) {
                result.setSuccess(false);
                result.setMessage("?????????????????????");
                return result;
            }

        } catch (Exception e) {
            result.setSuccess(false);
            result.setMessage(e.getMessage());
            return result;
        }
        result.setSuccess(true);
        return result;
    }

    /**
     * ????????????
     */
    //@RequiresRoles({"admin"})
    @RequestMapping(value = "/changePassword", method = RequestMethod.PUT)
    public Result<?> changePassword(@RequestBody SysUser sysUser) {
        SysUser u = this.sysUserService.getOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, sysUser.getUsername()));
        if (u == null) {
            return Result.error("??????????????????");
        }
        if (lessThanUserRoleLevel(u.getId())){
            return Result.error("????????????");
        }
        sysUser.setId(u.getId());
        return sysUserService.changePassword(sysUser);
    }

    /**
     * ??????????????????????????????????????????
     *
     * @param userId
     * @return
     */
    @RequestMapping(value = "/userDepartList", method = RequestMethod.GET)
    public Result<List<DepartIdModel>> getUserDepartsList(@RequestParam(name = "userId", required = true) String userId) {
        Result<List<DepartIdModel>> result = new Result<>();
        try {
            List<DepartIdModel> depIdModelList = this.sysUserDepartService.queryDepartIdsOfUser(userId);
            if (depIdModelList != null && depIdModelList.size() > 0) {
                result.setSuccess(true);
                result.setMessage("????????????");
                result.setResult(depIdModelList);
            } else {
                result.setSuccess(false);
                result.setMessage("????????????");
            }
            return result;
        } catch (Exception e) {
        	log.error(e.getMessage(), e);
            result.setSuccess(false);
            result.setMessage("??????????????????????????????: " + e.getMessage());
            return result;
        }

    }

    /**
     * ???????????????????????????????????????????????????,???????????????,?????????id??????????????????
     *
     * @return
     */
    @RequestMapping(value = "/generateUserId", method = RequestMethod.GET)
    public Result<String> generateUserId() {
        Result<String> result = new Result<>();
        System.out.println("????????????,????????????ID==============================");
        String userId = UUID.randomUUID().toString().replace("-", "");
        result.setSuccess(true);
        result.setResult(userId);
        return result;
    }

    /**
     * ????????????id??????????????????
     *
     * @param id
     * @return
     */
    @RequestMapping(value = "/queryUserByDepId", method = RequestMethod.GET)
    public Result<List<SysUser>> queryUserByDepId(@RequestParam(name = "id", required = true) String id,@RequestParam(name="realname",required=false) String realname) {
        Result<List<SysUser>> result = new Result<>();
        //List<SysUser> userList = sysUserDepartService.queryUserByDepId(id);
        SysDepart sysDepart = sysDepartService.getById(id);
        List<SysUser> userList = sysUserDepartService.queryUserByDepCode(sysDepart.getOrgCode(),realname);

        //?????????????????????????????????
        //step.1 ?????????????????? useids
        //step.2 ?????? useids?????????????????????????????????????????????
        List<String> userIds = userList.stream().map(SysUser::getId).collect(Collectors.toList());
        if(userIds!=null && userIds.size()>0){
            Map<String,String>  useDepNames = sysUserService.getDepNamesByUserIds(userIds);
            userList.forEach(item->{
                //TODO ??????????????????????????????????????????
                item.setOrgCode(useDepNames.get(item.getId()));
            });
        }

        try {
            result.setSuccess(true);
            result.setResult(userList);
            return result;
        } catch (Exception e) {
        	log.error(e.getMessage(), e);
            result.setSuccess(false);
            return result;
        }
    }

    /**
     * ??????excel
     *
     * @param request
     */
    @RequestMapping(value = "/exportXls")
    public ModelAndView exportXls(SysUserModel sysUser,HttpServletRequest request) {
        // Step.1 ??????????????????
        Map<String, String[]> param = request.getParameterMap();
        log.info("param:{}", param);
        String roleId = param.containsKey("roleId")?param.get("roleId")[0]:null;
        String departId = param.containsKey("departId")?param.get("departId")[0]:null;
        String userSex = param.containsKey("userSex")?param.get("userSex")[0]:null;
        String status = param.containsKey("userStatus")?param.get("userStatus")[0]:null;

        QueryWrapper<SysUserModel> queryWrapper = new QueryWrapper<>();
//        queryWrapper.eq(org.apache.commons.lang3.StringUtils.isNotEmpty(provinceId), "sys_user.province", provinceId);
//        queryWrapper.eq(org.apache.commons.lang3.StringUtils.isNotEmpty(cityId),"sys_user.city", cityId);
        queryWrapper.eq(org.apache.commons.lang3.StringUtils.isNotEmpty(roleId), "role_id", roleId);
        queryWrapper.eq(org.apache.commons.lang3.StringUtils.isNotEmpty(userSex), "sys_user.sex", userSex);
        queryWrapper.eq(org.apache.commons.lang3.StringUtils.isNotEmpty(status), "sys_user.status", status);
        if(StringUtils.isNotEmpty(departId)){
            queryWrapper.in("sys_depart.id", Arrays.asList(departId.split(",")));
        }
        //TODO ????????????????????????????????????????????????
        queryWrapper.ne("username","_reserve_user_external");
        queryWrapper.eq("sys_user.del_flag", 0);
        queryWrapper.groupBy("sys_user.id");

        //????????????
        String selections = request.getParameter("selections");
        if(!oConvertUtils.isEmpty(selections)){
            queryWrapper.in("sys_user.id",selections.split(","));
        }

        //???admin???dev???????????????????????????????????????????????????
        List<String> myDeptIds = new ArrayList<>();
        if(!hasRole("admin") && !hasRole("dev")){
            myDeptIds = sysDepartService.getMySubDepIdsByDepId(getCurrentUser().getDepartIds());
            if (myDeptIds==null || myDeptIds.isEmpty()){
                return null;
            }
        }

        QueryGenerator.installMplus(queryWrapper, sysUser, request.getParameterMap());
        Page<SysUserModel> page = new Page<SysUserModel>(1, 999);
        IPage<SysUserModel> pageList = sysUserService.getUserList(page, queryWrapper, myDeptIds);

        //?????????????????????????????????
        //step.1 ?????????????????? useids
        //step.2 ?????? useids?????????????????????????????????????????????
        List<String> userIds = pageList.getRecords().stream().map(SysUser::getId).collect(Collectors.toList());
        if(userIds!=null && userIds.size()>0){
            Map<String,String>  useDepNames = sysUserService.getDepNamesByUserIds(userIds);
            pageList.getRecords().forEach(item->{
                item.setOrgCodeTxt(useDepNames.get(item.getId()));
            });

            Map<String,String>  roleNames = sysUserService.getRoleNamesByUserIds(userIds);
            pageList.getRecords().forEach(item->{
                item.setRoleTxt(roleNames.get(item.getId()));
            });
        }

//        QueryWrapper<SysUser> queryWrapper = QueryGenerator.initQueryWrapper(sysUser, request.getParameterMap());
        //Step.2 AutoPoi ??????Excel
        ModelAndView mv = new ModelAndView(new JeecgEntityExcelView());
        //update-begin--Author:kangxiaolin  Date:20180825 for???[03]?????????????????????????????????????????????????????????--------------------

        //update-end--Author:kangxiaolin  Date:20180825 for???[03]?????????????????????????????????????????????????????????----------------------
//        List<SysUser> pageList = sysUserService.list(queryWrapper);

        //??????????????????
        mv.addObject(NormalExcelConstants.FILE_NAME, "????????????");
        mv.addObject(NormalExcelConstants.CLASS, SysUserModel.class);
        LoginUser user = (LoginUser) SecurityUtils.getSubject().getPrincipal();
        ExportParams exportParams = new ExportParams("??????????????????", "?????????:"+user.getRealname(), "????????????");
        exportParams.setImageBasePath(upLoadPath);
        mv.addObject(NormalExcelConstants.PARAMS, exportParams);
        mv.addObject(NormalExcelConstants.DATA_LIST, pageList.getRecords());
        return mv;
    }

    /**
     * ??????excel????????????
     *
     * @param request
     * @param response
     * @return
     */
    //@RequiresPermissions("user:import")
    @RequestMapping(value = "/importExcel", method = RequestMethod.POST)
    public Result<?> importExcel(HttpServletRequest request, HttpServletResponse response) {
        MultipartHttpServletRequest multipartRequest = (MultipartHttpServletRequest) request;
        Map<String, MultipartFile> fileMap = multipartRequest.getFileMap();
        // ????????????
        List<String> errorMessage = new ArrayList<>();
        int successLines = 0, errorLines = 0;

        SysRole studentRole = sysRoleService.getRoleByCode("student");

        for (Map.Entry<String, MultipartFile> entity : fileMap.entrySet()) {
            MultipartFile file = entity.getValue();// ????????????????????????
            ImportParams params = new ImportParams();
            params.setTitleRows(2);
            params.setHeadRows(1);
            params.setNeedSave(true);
            try {
                List<SysUserModel> listSysUsers = ExcelImportUtil.importExcel(file.getInputStream(), SysUserModel.class, params);
                for (int i = 0; i < listSysUsers.size(); i++) {
                    SysUserModel sysUserExcel = listSysUsers.get(i);
                    if (StringUtils.isBlank(sysUserExcel.getPassword())) {
                        sysUserExcel.setPassword("123456");// ??????????????? ???123456???
                    }
                    sysUserExcel.setUserIdentity(sysUserExcel.getUserIdentity() == 2 ? 2:1);
                    // ??????????????????
                    String salt = oConvertUtils.randomGen(8);
                    sysUserExcel.setSalt(salt);
                    String passwordEncode = PasswordUtil.encrypt(sysUserExcel.getUsername(), sysUserExcel.getPassword(), salt);
                    sysUserExcel.setPassword(passwordEncode);
                    try {
                        sysUserService.save(sysUserExcel);
                        successLines++;
                    } catch (Exception e) {
                        errorLines++;
                        String message = e.getMessage();
                        int lineNumber = i + 1;
                        // ?????????????????????????????????
                        if (message.contains(CommonConstant.SQL_INDEX_UNIQ_SYS_USER_USERNAME)) {
                            errorMessage.add("??? " + lineNumber + " ?????????????????????????????????????????????");
                        } else if (message.contains(CommonConstant.SQL_INDEX_UNIQ_SYS_USER_WORK_NO)) {
                            errorMessage.add("??? " + lineNumber + " ??????????????????????????????????????????");
                        } else if (message.contains(CommonConstant.SQL_INDEX_UNIQ_SYS_USER_PHONE)) {
                            errorMessage.add("??? " + lineNumber + " ?????????????????????????????????????????????");
                        } else if (message.contains(CommonConstant.SQL_INDEX_UNIQ_SYS_USER_EMAIL)) {
                            errorMessage.add("??? " + lineNumber + " ????????????????????????????????????????????????");
                        } else {
                            errorMessage.add("??? " + lineNumber + " ?????????????????????????????????:" + e.getMessage());
                        }
                    }
                    // ??????????????????????????????????????????????????????
                    String departIds = sysUserExcel.getOrgCodeTxt();
                    if (StringUtils.isNotBlank(departIds)) {
                        String userId = sysUserExcel.getId();
                        String[] departIdArray = departIds.split(",");
                        List<SysUserDepart> userDepartList = new ArrayList<>(departIdArray.length);
                        for (String departId : departIdArray) {
                            userDepartList.add(new SysUserDepart(userId, departId));
                        }
                        sysUserDepartService.saveBatch(userDepartList);
                    }

                    // ????????????????????????????????????????????????
                    String roleIds = sysUserExcel.getRoleTxt();
                    if (StringUtils.isNotBlank(roleIds)) {
                        String userId = sysUserExcel.getId();
                        String[] roleIdArray = roleIds.split(",");
                        List<SysUserRole> userRoleList = new ArrayList<>(roleIdArray.length);
                        for (String roleId : roleIdArray) {
                            userRoleList.add(new SysUserRole(userId, roleId));
                        }
                        sysUserRoleService.saveBatch(userRoleList);
                    }else if(studentRole != null){ //??????student??????
                        sysUserRoleService.save(new SysUserRole(sysUserExcel.getId(), studentRole.getId()));
                    }

                }
            } catch (Exception e) {
                errorMessage.add("???????????????" + e.getMessage());
                log.error(e.getMessage(), e);
            } finally {
                try {
                    file.getInputStream().close();
                } catch (IOException e) {
                	log.error(e.getMessage(), e);
                }
            }
        }
        if (errorLines == 0) {
            return Result.ok("???" + successLines + "??????????????????????????????");
        } else {
            JSONObject result = new JSONObject(5);
            int totalCount = successLines + errorLines;
            result.put("totalCount", totalCount);
            result.put("errorCount", errorLines);
            result.put("successCount", successLines);
            result.put("msg", "??????????????????" + totalCount + "?????????????????????" + successLines + "??????????????????" + errorLines);
            String fileUrl = PmsUtil.saveErrorTxtByList(errorMessage, "userImportExcelErrorLog");
            int lastIndex = fileUrl.lastIndexOf(File.separator);
            String fileName = fileUrl.substring(lastIndex + 1);
            result.put("fileUrl", "/sys/common/static/" + fileUrl);
            result.put("fileName", fileName);
            Result res = Result.ok(result);

            res.setCode(201);
            res.setMessage("????????????????????????????????????");

            return res;
        }
    }

    /**
	 * @???????????????id ????????????
	 * @param userIds
	 * @return
	 */
	@RequestMapping(value = "/queryByIds", method = RequestMethod.GET)
	public Result<Collection<SysUser>> queryByIds(@RequestParam String userIds) {
		Result<Collection<SysUser>> result = new Result<>();
		String[] userId = userIds.split(",");
		Collection<String> idList = Arrays.asList(userId);
		Collection<SysUser> userRole = sysUserService.listByIds(idList);
		result.setSuccess(true);
		result.setResult(userRole);
		return result;
	}

	/**
	 * ????????????????????????
	 */
	//@RequiresRoles({"admin"})
	@RequestMapping(value = "/updatePassword", method = RequestMethod.PUT)
	public Result<?> changPassword(@RequestBody JSONObject json) {
		String username = json.getString("username");
		String oldpassword = json.getString("oldpassword");
		String password = json.getString("password");
		String confirmpassword = json.getString("confirmpassword");
		SysUser user = this.sysUserService.getOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
		if(user==null) {
			return Result.error("??????????????????");
		}
		return sysUserService.resetPassword(username,oldpassword,password,confirmpassword);
	}

    @RequestMapping(value = "/userRoleList", method = RequestMethod.GET)
    public Result<IPage<SysUser>> userRoleList(@RequestParam(name="pageNo", defaultValue="1") Integer pageNo,
                                               @RequestParam(name="pageSize", defaultValue="10") Integer pageSize, HttpServletRequest req) {
        Result<IPage<SysUser>> result = new Result<IPage<SysUser>>();
        Page<SysUser> page = new Page<SysUser>(pageNo, pageSize);
        String roleId = req.getParameter("roleId");
        String username = req.getParameter("username");
        IPage<SysUser> pageList = sysUserService.getUserByRoleId(page,roleId,username);
        result.setSuccess(true);
        result.setResult(pageList);
        return result;
    }

    /**
     * ???????????????????????????
     *
     * @param
     * @return
     */
    //@RequiresRoles({"admin"})
    @RequestMapping(value = "/addSysUserRole", method = RequestMethod.POST)
    public Result<String> addSysUserRole(@RequestBody SysUserRoleVO sysUserRoleVO) {
        Result<String> result = new Result<String>();
        try {
            String sysRoleId = sysUserRoleVO.getRoleId();
            for(String sysUserId:sysUserRoleVO.getUserIdList()) {
                if (lessThanUserRoleLevel(sysUserId)){
                    result.error500("????????????");
                    return result;
                }
                SysUserRole sysUserRole = new SysUserRole(sysUserId,sysRoleId);
                QueryWrapper<SysUserRole> queryWrapper = new QueryWrapper<SysUserRole>();
                queryWrapper.eq("role_id", sysRoleId).eq("user_id",sysUserId);
                SysUserRole one = sysUserRoleService.getOne(queryWrapper);
                if(one==null){
                    sysUserRoleService.save(sysUserRole);
                }

            }
            result.setMessage("????????????!");
            result.setSuccess(true);
            return result;
        }catch(Exception e) {
            log.error(e.getMessage(), e);
            result.setSuccess(false);
            result.setMessage("?????????: " + e.getMessage());
            return result;
        }
    }
    /**
     *   ?????????????????????????????????
     * @param
     * @return
     */
    //@RequiresRoles({"admin"})
    @RequestMapping(value = "/deleteUserRole", method = RequestMethod.DELETE)
    public Result<SysUserRole> deleteUserRole(@RequestParam(name="roleId") String roleId,
                                                    @RequestParam(name="userId",required=true) String userId
    ) {
        Result<SysUserRole> result = new Result<SysUserRole>();
        if (lessThanUserRoleLevel(userId)){
            result.error500("????????????");
            return result;
        }
        try {
            QueryWrapper<SysUserRole> queryWrapper = new QueryWrapper<SysUserRole>();
            queryWrapper.eq("role_id", roleId).eq("user_id",userId);
            sysUserRoleService.remove(queryWrapper);
            result.success("????????????!");
        }catch(Exception e) {
            log.error(e.getMessage(), e);
            result.error500("???????????????");
        }
        return result;
    }

    /**
     * ???????????????????????????????????????
     *
     * @param
     * @return
     */
    //@RequiresRoles({"admin"})
    @RequestMapping(value = "/deleteUserRoleBatch", method = RequestMethod.DELETE)
    public Result<SysUserRole> deleteUserRoleBatch(
            @RequestParam(name="roleId") String roleId,
            @RequestParam(name="userIds",required=true) String userIds) {
        Result<SysUserRole> result = new Result<SysUserRole>();
        for (String id: userIds.split(",")){
            if (lessThanUserRoleLevel(id)){
                result.error500("????????????");
                return result;
            }
        }
        try {
            QueryWrapper<SysUserRole> queryWrapper = new QueryWrapper<SysUserRole>();
            queryWrapper.eq("role_id", roleId).in("user_id",Arrays.asList(userIds.split(",")));
            sysUserRoleService.remove(queryWrapper);
            result.success("????????????!");
        }catch(Exception e) {
            log.error(e.getMessage(), e);
            result.error500("???????????????");
        }
        return result;
    }

    /**
     * ??????????????????
     */
    @RequestMapping(value = "/departUserList", method = RequestMethod.GET)
    public Result<IPage<SysUser>> departUserList(@RequestParam(name="pageNo", defaultValue="1") Integer pageNo,
                                                 @RequestParam(name="pageSize", defaultValue="10") Integer pageSize, HttpServletRequest req) {
        Result<IPage<SysUser>> result = new Result<IPage<SysUser>>();
        Page<SysUser> page = new Page<SysUser>(pageNo, pageSize);
        String depId = req.getParameter("depId");
        String username = req.getParameter("username");
        String realname = req.getParameter("realname");
        //????????????ID??????,??????????????????????????????IDS
        List<String> subDepids = new ArrayList<>();
        //??????id?????????????????????????????????????????????
        if(oConvertUtils.isEmpty(depId)){
            LoginUser user = (LoginUser) SecurityUtils.getSubject().getPrincipal();
            int userIdentity = user.getUserIdentity() != null?user.getUserIdentity():CommonConstant.USER_IDENTITY_1;
            if(oConvertUtils.isNotEmpty(userIdentity) && userIdentity == CommonConstant.USER_IDENTITY_2 ){
                subDepids = sysDepartService.getMySubDepIdsByDepId(user.getDepartIds());
            }
        }else{
            subDepids = sysDepartService.getSubDepIdsByDepId(depId);
        }
        if(subDepids != null && subDepids.size()>0){
            IPage<SysUser> pageList = sysUserService.getUserByDepIds(page,subDepids,username,realname);
            //?????????????????????????????????
            //step.1 ?????????????????? useids
            //step.2 ?????? useids?????????????????????????????????????????????
            List<String> userIds = pageList.getRecords().stream().map(SysUser::getId).collect(Collectors.toList());
            if(userIds!=null && userIds.size()>0){
                Map<String, String> useDepNames = sysUserService.getDepNamesByUserIds(userIds);
                pageList.getRecords().forEach(item -> {
                    //?????????????????????????????????
                    item.setOrgCode(useDepNames.get(item.getId()));
                });
            }
            result.setSuccess(true);
            result.setResult(pageList);
        }else{
            result.setSuccess(true);
            result.setResult(null);
        }
        return result;
    }


    /**
     * ?????? orgCode ??????????????????????????????????????????
     * ?????????????????????????????????????????????????????????????????????????????????????????????
     */
    @GetMapping("/queryByOrgCode")
    public Result<?> queryByDepartId(
            @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(name = "orgCode") String orgCode,
            SysUser userParams
    ) {
        IPage<SysUserSysDepartModel> pageList = sysUserService.queryUserByOrgCode(orgCode, userParams, new Page(pageNo, pageSize));
        return Result.ok(pageList);
    }

    /**
     * ?????? orgCode ??????????????????????????????????????????
     * ?????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
     */
    @GetMapping("/queryByOrgCodeForAddressList")
    public Result<?> queryByOrgCodeForAddressList(
            @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(name = "orgCode",required = false) String orgCode,
            SysUser userParams
    ) {
        IPage page = new Page(pageNo, pageSize);
        IPage<SysUserSysDepartModel> pageList = sysUserService.queryUserByOrgCode(orgCode, userParams, page);
        List<SysUserSysDepartModel> list = pageList.getRecords();

        // ???????????????????????? user, key = userId
        Map<String, JSONObject> hasUser = new HashMap<>(list.size());

        JSONArray resultJson = new JSONArray(list.size());

        for (SysUserSysDepartModel item : list) {
            String userId = item.getId();
            // userId
            JSONObject getModel = hasUser.get(userId);
            // ????????????????????????????????????????????????
            if (getModel != null) {
                String departName = getModel.get("departName").toString();
                getModel.put("departName", (departName + " | " + item.getDepartName()));
            } else {
                // ????????????????????????json???????????????????????????????????? json ???
                JSONObject json = JSON.parseObject(JSON.toJSONString(item));
                json.remove("id");
                json.put("userId", userId);
                json.put("departId", item.getDepartId());
                json.put("departName", item.getDepartName());
//                json.put("avatar", item.getSysUser().getAvatar());
                resultJson.add(json);
                hasUser.put(userId, json);
            }
        }

        IPage<JSONObject> result = new Page<>(pageNo, pageSize, pageList.getTotal());
        result.setRecords(resultJson.toJavaList(JSONObject.class));
        return Result.ok(result);
    }

    /**
     * ????????????????????????????????????
     */
    //@RequiresRoles({"admin"})
    @RequestMapping(value = "/editSysDepartWithUser", method = RequestMethod.POST)
    public Result<String> editSysDepartWithUser(@RequestBody SysDepartUsersVO sysDepartUsersVO) {
        Result<String> result = new Result<String>();
        try {
            String sysDepId = sysDepartUsersVO.getDepId();
            for(String sysUserId:sysDepartUsersVO.getUserIdList()) {
                SysUserDepart sysUserDepart = new SysUserDepart(null,sysUserId,sysDepId);
                QueryWrapper<SysUserDepart> queryWrapper = new QueryWrapper<SysUserDepart>();
                queryWrapper.eq("dep_id", sysDepId).eq("user_id",sysUserId);
                SysUserDepart one = sysUserDepartService.getOne(queryWrapper);
                if(one==null){
                    sysUserDepartService.save(sysUserDepart);
                }
            }
            result.setMessage("????????????!");
            result.setSuccess(true);
            return result;
        }catch(Exception e) {
            log.error(e.getMessage(), e);
            result.setSuccess(false);
            result.setMessage("?????????: " + e.getMessage());
            return result;
        }
    }

    /**
     *   ?????????????????????????????????
     */
    //@RequiresRoles({"admin"})
    @RequestMapping(value = "/deleteUserInDepart", method = RequestMethod.DELETE)
    public Result<SysUserDepart> deleteUserInDepart(@RequestParam(name="depId") String depId,
                                                    @RequestParam(name="userId",required=true) String userId
    ) {
        Result<SysUserDepart> result = new Result<SysUserDepart>();
        if (lessThanUserRoleLevel(userId)){
            result.error500("????????????");
            return result;
        }
        try {
            QueryWrapper<SysUserDepart> queryWrapper = new QueryWrapper<SysUserDepart>();
            queryWrapper.eq("dep_id", depId).eq("user_id",userId);
            boolean b = sysUserDepartService.remove(queryWrapper);
            if(b){
                List<SysDepartRole> sysDepartRoleList = departRoleService.list(new QueryWrapper<SysDepartRole>().eq("depart_id",depId));
                List<String> roleIds = sysDepartRoleList.stream().map(SysDepartRole::getId).collect(Collectors.toList());
                if(roleIds != null && roleIds.size()>0){
                    QueryWrapper<SysDepartRoleUser> query = new QueryWrapper<>();
                    query.eq("user_id",userId).in("drole_id",roleIds);
                    departRoleUserService.remove(query);
                }
                result.success("????????????!");
            }else{
                result.error500("??????????????????????????????????????????!");
            }
        }catch(Exception e) {
            log.error(e.getMessage(), e);
            result.error500("???????????????");
        }
        return result;
    }

    /**
     * ???????????????????????????????????????
     */
    //@RequiresRoles({"admin"})
    @RequestMapping(value = "/deleteUserInDepartBatch", method = RequestMethod.DELETE)
    public Result<SysUserDepart> deleteUserInDepartBatch(
            @RequestParam(name="depId") String depId,
            @RequestParam(name="userIds",required=true) String userIds) {
        Result<SysUserDepart> result = new Result<SysUserDepart>();
        for (String id: userIds.split(",")){
            if (lessThanUserRoleLevel(id)){
                result.error500("????????????");
                return result;
            }
        }
        try {
            QueryWrapper<SysUserDepart> queryWrapper = new QueryWrapper<SysUserDepart>();
            queryWrapper.eq("dep_id", depId).in("user_id",Arrays.asList(userIds.split(",")));
            boolean b = sysUserDepartService.remove(queryWrapper);
            if(b){
                departRoleUserService.removeDeptRoleUser(Arrays.asList(userIds.split(",")),depId);
            }
            result.success("????????????!");
        }catch(Exception e) {
            log.error(e.getMessage(), e);
            result.error500("???????????????");
        }
        return result;
    }
    
    /**
         *  ?????????????????????????????????/??????????????????
     * @return
     */
    @RequestMapping(value = "/getCurrentUserDeparts", method = RequestMethod.GET)
    public Result<Map<String,Object>> getCurrentUserDeparts() {
        Result<Map<String,Object>> result = new Result<Map<String,Object>>();
        try {
        	LoginUser sysUser = (LoginUser)SecurityUtils.getSubject().getPrincipal();
            List<SysDepart> list = this.sysDepartService.queryUserDeparts(sysUser.getId());
            Map<String,Object> map = new HashMap<String,Object>();
            map.put("list", list);
            map.put("orgCode", sysUser.getOrgCode());
            result.setSuccess(true);
            result.setResult(map);
        }catch(Exception e) {
            log.error(e.getMessage(), e);
            result.error500("???????????????");
        }
        return result;
    }

    


	/**
	 * ??????????????????
	 * 
	 * @param jsonObject
	 * @param user
	 * @return
	 */
	@PostMapping("/register")
	public Result<JSONObject> userRegister(@RequestBody JSONObject jsonObject, SysUser user) {
		Result<JSONObject> result = new Result<JSONObject>();
		String phone = jsonObject.getString("phone");
		String smscode = jsonObject.getString("smscode");
		Object code = redisUtil.get(phone);
		String username = jsonObject.getString("username");
		String realname = jsonObject.getString("realname");
		//???????????????????????????????????????????????????
		if(oConvertUtils.isEmpty(username)){
            username = phone;
        }
        //?????????????????????????????????????????????
		String password = jsonObject.getString("password");
		if(oConvertUtils.isEmpty(password)){
            password = RandomUtil.randomString(8);
        }
		String email = jsonObject.getString("email");
		SysUser sysUser1 = sysUserService.getUserByName(username);
		if (sysUser1 != null) {
			result.setMessage("??????????????????");
			result.setSuccess(false);
			return result;
		}
		SysUser sysUser2 = sysUserService.getUserByPhone(phone);
		if (sysUser2 != null) {
			result.setMessage("?????????????????????");
			result.setSuccess(false);
			return result;
		}

		if(oConvertUtils.isNotEmpty(email)){
            SysUser sysUser3 = sysUserService.getUserByEmail(email);
            if (sysUser3 != null) {
                result.setMessage("??????????????????");
                result.setSuccess(false);
                return result;
            }
        }
        if(oConvertUtils.isNotEmpty(phone)){
            if (!smscode.equals(code)) {
                result.setMessage("?????????????????????");
                result.setSuccess(false);
                return result;
            }
        }

        String allowReg = sysConfigService.getConfigItem("allowReg");
        if (!"1".equals(allowReg)){
            result.setSuccess(false);
            result.setMessage("???????????????");
            return result;
        }
        String defaultRole = sysConfigService.getConfigItem("_defaultRole");
        String defaultDepart = sysConfigService.getConfigItem("_defaultDepart");

		try {
			user.setCreateTime(new Date());// ??????????????????
			String salt = oConvertUtils.randomGen(8);
			String passwordEncode = PasswordUtil.encrypt(username, password, salt);
			user.setSalt(salt);
			user.setUsername(username);
			user.setRealname(realname);
			user.setPassword(passwordEncode);
			user.setEmail(email);
			user.setPhone(phone);
			user.setStatus(CommonConstant.USER_UNFREEZE);
			user.setDelFlag(CommonConstant.DEL_FLAG_0);
			user.setActivitiSync(CommonConstant.ACT_SYNC_0);
			sysUserService.save(user);
            if (defaultRole != null){
                sysUserService.addUserWithRole(user,defaultRole);
            }
            if (defaultDepart != null){
                sysUserService.addUserWithDepart(user, defaultDepart);
            }
            result.success("????????????");
		} catch (Exception e) {
			result.error500("????????????");
		}
		return result;
	}

	/**
	 * ?????????????????????????????????????????????
	 * @param
	 * @return
	 */
	@GetMapping("/querySysUser")
	public Result<Map<String, Object>> querySysUser(SysUser sysUser) {
		String phone = sysUser.getPhone();
		String username = sysUser.getUsername();
		Result<Map<String, Object>> result = new Result<Map<String, Object>>();
		Map<String, Object> map = new HashMap<String, Object>();
		if (oConvertUtils.isNotEmpty(phone)) {
			SysUser user = sysUserService.getUserByPhone(phone);
			if(user!=null) {
				map.put("username",user.getUsername());
				map.put("phone",user.getPhone());
				result.setSuccess(true);
				result.setResult(map);
				return result;
			}
		}
		if (oConvertUtils.isNotEmpty(username)) {
			SysUser user = sysUserService.getUserByName(username);
			if(user!=null) {
				map.put("username",user.getUsername());
				map.put("phone",user.getPhone());
				result.setSuccess(true);
				result.setResult(map);
				return result;
			}
		}
		result.setSuccess(false);
		result.setMessage("????????????");
		return result;
	}
	
	/**
	 * ?????????????????????
	 */
	@PostMapping("/phoneVerification")
	public Result<String> phoneVerification(@RequestBody JSONObject jsonObject) {
		Result<String> result = new Result<String>();
		String phone = jsonObject.getString("phone");
		String smscode = jsonObject.getString("smscode");
		Object code = redisUtil.get(phone);
		if (!smscode.equals(code)) {
			result.setMessage("?????????????????????");
			result.setSuccess(false);
			return result;
		}
		redisUtil.set(phone, smscode);
		result.setResult(smscode);
		result.setSuccess(true);
		return result;
	}
	
	/**
	 * ??????????????????
	 */
	@GetMapping("/passwordChange")
	public Result<SysUser> passwordChange(@RequestParam(name="username")String username,
										  @RequestParam(name="password")String password,
			                              @RequestParam(name="smscode")String smscode,
			                              @RequestParam(name="phone") String phone) {
        Result<SysUser> result = new Result<SysUser>();
        if(oConvertUtils.isEmpty(username) || oConvertUtils.isEmpty(password) || oConvertUtils.isEmpty(smscode)  || oConvertUtils.isEmpty(phone) ) {
            result.setMessage("?????????????????????");
            result.setSuccess(false);
            return result;
        }

        SysUser sysUser=new SysUser();
        Object object= redisUtil.get(phone);
        if(null==object) {
        	result.setMessage("????????????????????????");
            result.setSuccess(false);
            return result;
        }
        if(!smscode.equals(object)) {
        	result.setMessage("???????????????????????????");
            result.setSuccess(false);
            return result;
        }
        sysUser = this.sysUserService.getOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername,username).eq(SysUser::getPhone,phone));
        if (sysUser == null) {
            result.setMessage("??????????????????");
            result.setSuccess(false);
            return result;
        } else {
            String salt = oConvertUtils.randomGen(8);
            sysUser.setSalt(salt);
            String passwordEncode = PasswordUtil.encrypt(sysUser.getUsername(), password, salt);
            sysUser.setPassword(passwordEncode);
            this.sysUserService.updateById(sysUser);
            result.setSuccess(true);
            result.setMessage("?????????????????????");
            return result;
        }
    }
	

	/**
	 * ??????TOKEN???????????????????????????????????????????????????????????????????????????????????????
	 * 
	 * @return
	 */
	@GetMapping("/getUserSectionInfoByToken")
	public Result<?> getUserSectionInfoByToken(HttpServletRequest request, @RequestParam(name = "token", required = false) String token) {
		try {
			String username = null;
			// ??????????????????token?????????header?????????token?????????????????????
			if (oConvertUtils.isEmpty(token)) {
				 username = JwtUtil.getUserNameByToken(request);
			} else {
				 username = JwtUtil.getUsername(token);				
			}

			log.info(" ------ ?????????????????????????????????????????????????????? " + username);

			// ?????????????????????????????????
			SysUser sysUser = sysUserService.getUserByName(username);
			Map<String, Object> map = new HashMap<String, Object>();
			map.put("sysUserId", sysUser.getId());
			map.put("sysUserCode", sysUser.getUsername()); // ??????????????????????????????
			map.put("sysUserName", sysUser.getRealname()); // ??????????????????????????????
			map.put("sysOrgCode", sysUser.getOrgCode()); // ??????????????????????????????

			log.info(" ------ ?????????????????????????????????????????????????????????????????? " + map);

			return Result.ok(map);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return Result.error(500, "????????????:" + e.getMessage());
		}
	}
	
	/**
	 * ???APP??????????????????????????????  ??????????????????????????? ????????????
	 * @param keyword
	 * @param pageNo
	 * @param pageSize
	 * @return
	 */
	@GetMapping("/appUserList")
	public Result<?> appUserList(@RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "username", required = false) String username,
			@RequestParam(name="pageNo", defaultValue="1") Integer pageNo,
			@RequestParam(name="pageSize", defaultValue="10") Integer pageSize) {
		try {
			//TODO ??????????????????????????????mp????????????page???????????? ???????????????????????????
			LambdaQueryWrapper<SysUser> query = new LambdaQueryWrapper<SysUser>();
			query.eq(SysUser::getActivitiSync, "1");
			query.eq(SysUser::getDelFlag,"0");
			if(oConvertUtils.isNotEmpty(username)){
			    query.eq(SysUser::getUsername,username);
            }else{
                query.and(i -> i.like(SysUser::getUsername, keyword).or().like(SysUser::getRealname, keyword));
            }
			Page<SysUser> page = new Page<>(pageNo, pageSize);
			IPage<SysUser> res = this.sysUserService.page(page, query);
			return Result.ok(res);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return Result.error(500, "????????????:" + e.getMessage());
		}
		
	}

    /**
     * ????????????????????????????????????????????????
     *
     * @return logicDeletedUserList
     */
    @GetMapping("/recycleBin")
    public Result getRecycleBin() {
        List<SysUser> logicDeletedUserList = sysUserService.queryLogicDeleted();
        if (logicDeletedUserList.size() > 0) {
            // ?????????????????????????????????
            // step.1 ?????????????????? userIds
            List<String> userIds = logicDeletedUserList.stream().map(SysUser::getId).collect(Collectors.toList());
            // step.2 ?????? userIds?????????????????????????????????????????????
            Map<String, String> useDepNames = sysUserService.getDepNamesByUserIds(userIds);
            logicDeletedUserList.forEach(item -> item.setOrgCode(useDepNames.get(item.getId())));
        }
        return Result.ok(logicDeletedUserList);
    }

    /**
     * ??????????????????????????????
     *
     * @param jsonObject
     * @return
     */
    @RequestMapping(value = "/putRecycleBin", method = RequestMethod.PUT)
    public Result putRecycleBin(@RequestBody JSONObject jsonObject, HttpServletRequest request) {
        String userIds = jsonObject.getString("userIds");
        if (StringUtils.isNotBlank(userIds)) {
            SysUser updateUser = new SysUser();
            updateUser.setUpdateBy(JwtUtil.getUserNameByToken(request));
            updateUser.setUpdateTime(new Date());
            sysUserService.revertLogicDeleted(Arrays.asList(userIds.split(",")), updateUser);
        }
        return Result.ok("????????????");
    }

    /**
     * ??????????????????
     *
     * @param userIds ??????????????????ID?????????id?????????????????????
     * @return
     */
    @RequestMapping(value = "/deleteRecycleBin", method = RequestMethod.DELETE)
    public Result deleteRecycleBin(@RequestParam("userIds") String userIds) {
        if (StringUtils.isNotBlank(userIds)) {
            sysUserService.removeLogicDeleted(Arrays.asList(userIds.split(",")));
        }
        return Result.ok("????????????");
    }


    /**
     * ???????????????????????????
     * @param jsonObject
     * @return
     */
    @RequestMapping(value = "/appEdit", method = RequestMethod.PUT)
    public Result<SysUser> appEdit(@RequestBody JSONObject jsonObject) {
        Result<SysUser> result = new Result<SysUser>();
        try {
            SysUser sysUser = sysUserService.getById(jsonObject.getString("id"));
            sysBaseAPI.addLog("????????????????????????id??? " +jsonObject.getString("id") ,CommonConstant.LOG_TYPE_2, 2);
            if(sysUser==null) {
                result.error500("?????????????????????!");
            }else {
                SysUser user = JSON.parseObject(jsonObject.toJSONString(), SysUser.class);
                user.setUpdateTime(new Date());
                user.setPassword(sysUser.getPassword());
                sysUserService.updateById(user);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            result.error500("????????????!");
        }
        return result;
    }

}
