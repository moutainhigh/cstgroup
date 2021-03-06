package com.drag.cstgroup.user.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson.JSON;
import com.drag.cstgroup.common.BaseResponse;
import com.drag.cstgroup.common.Constant;
import com.drag.cstgroup.common.exception.AMPException;
import com.drag.cstgroup.keruyun.service.KeruyunService;
import com.drag.cstgroup.pay.form.PayForm;
import com.drag.cstgroup.pay.resp.PayResp;
import com.drag.cstgroup.user.dao.UserDao;
import com.drag.cstgroup.user.dao.UserScoreRecordDao;
import com.drag.cstgroup.user.dao.UserScoreUsedRecordDao;
import com.drag.cstgroup.user.entity.User;
import com.drag.cstgroup.user.entity.UserScoreRecord;
import com.drag.cstgroup.user.entity.UserScoreUsedRecord;
import com.drag.cstgroup.user.resp.ScoreResp;
import com.drag.cstgroup.user.vo.ScoreRecordVo;
import com.drag.cstgroup.utils.BeanUtils;
import com.drag.cstgroup.utils.DateUtil;
import com.drag.cstgroup.utils.StringUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ScoreService {
	
	@Autowired
	private UserDao userDao;
	@Autowired
	private UserScoreRecordDao scoreRecordDao;
	@Autowired
	private UserScoreUsedRecordDao scoreUsedRecordDao;
	@Autowired
	private KeruyunService keruyunService;
	
	/**
	 * 查询用户积分记录
	 * @param openid
	 * @return
	 */
	public List<ScoreRecordVo> queryScore(String openid) {
		log.info("【会员查询积分传入参数】:openid= {}",openid);
		List<ScoreRecordVo> goodsResp = new ArrayList<ScoreRecordVo>();
		User user = userDao.findByOpenid(openid);
		int uid = user.getId();
		
		List<UserScoreUsedRecord> userRecords = scoreUsedRecordDao.findByUid(uid);
		if(userRecords != null & userRecords.size() > 0) {
			for(UserScoreUsedRecord record : userRecords) {
				ScoreRecordVo vo = new ScoreRecordVo();
				BeanUtils.copyProperties(record, vo,new String[]{"createTime", "updateTime"});
				vo.setScore(record.getScore());
				vo.setUsedscore(-record.getUsedScore());
				vo.setGoodsName(record.getGoodsName());
				vo.setCreateTime((DateUtil.format(record.getCreateTime(), "yyyy-MM-dd HH:mm:ss")));
				goodsResp.add(vo);
			}
		}
		
		List<UserScoreRecord> records = scoreRecordDao.findByUid(uid);
		if(records != null & records.size() > 0) {
			for(UserScoreRecord record : records) {
				ScoreRecordVo vo = new ScoreRecordVo();
				BeanUtils.copyProperties(record, vo,new String[]{"createTime", "updateTime"});
				vo.setScore(record.getScore());
				vo.setUsedscore(record.getAvailableScore());
				vo.setGoodsName(record.getGoodsName());
				vo.setCreateTime((DateUtil.format(record.getCreateTime(), "yyyy-MM-dd HH:mm:ss")));
				goodsResp.add(vo);
			}
		}
		return goodsResp;
	}
	
	
	/**
	 * 会员购买积分
	 * @param openid
	 * @param score
	 * @return
	 */
	@Transactional
	public BaseResponse buyScore(String openid,int score,BigDecimal price) {
		BaseResponse resp = new BaseResponse();
		try {
			log.info("【会员购买积分传入参数】:openid= {},score= {}",openid,score);
			User us = userDao.findByOpenid(openid);
			if(us == null) {
				resp.setReturnCode(Constant.FAIL);
				resp.setErrorMessage("该用户不存在!");
				log.error("【用户信息不存在】{}:openid = ",openid);
				return resp;
			}
			int uid = us.getId();
			String customerId = us.getCustomerId();
			BigDecimal balance = us.getBalance();
			if(balance.compareTo(price)  < 0) {
				resp.setReturnCode(Constant.MONEY_NOTENOUGH);
				resp.setErrorMessage("该用户余额不足!");
				log.error("【用户余额不足】:openid ={}, balance ={},price = {}",openid,balance,price);
				return resp;
			}
			
			//余额 = 余额 - 消耗金额
			balance = balance.subtract(price);
			//自己生产的订单号
			String tpOrderId = StringUtil.uuid();
			//客如云没有直接扣除余额的接口,调用下单接口扣减余额
			PayForm form = new PayForm();
			form.setTpOrderId(tpOrderId);
			form.setOpenid(openid);
			form.setPrice(price);
			PayResp payResp = keruyunService.createOrder(form);
			if(!Constant.SUCCESS.equals(payResp.getReturnCode())) {
				resp.setReturnCode(payResp.getReturnCode());
				resp.setErrorMessage(payResp.getErrorMessage());
				log.error("【下单，异常】payResp:{}",JSON.toJSONString(payResp));
				return resp;
			}
			//客如云返回的订单号
			String orderId = payResp.getOrderId();
			
			String remark = String.format("%s购买积分",customerId);
			ScoreResp sresp = keruyunService.addScore(customerId, score,remark);
			String sreturnCode = sresp.getReturnCode();
			if(!Constant.SUCCESS.equals(sreturnCode)) {
				resp.setReturnCode(Constant.RECHARGE_ERROR);
				resp.setErrorMessage("该用户积分购买异常，请联系客服!");
				log.error("【用户积分增加异常】:sresp = {}",JSON.toJSONString(sresp));
				return resp;
			}
			String kryCurrentPoints = sresp.getCurrentPoints();
			if(!StringUtil.isEmpty(kryCurrentPoints)) {
				us.setScore(Integer.parseInt(kryCurrentPoints));
			}else {
				resp.setReturnCode(Constant.RECHARGE_ERROR);
				resp.setErrorMessage("该用户积分异常，请联系客服!");
				log.error("【用户积分增加异常】:sresp = {}",JSON.toJSONString(sresp));
				return resp;
			}
			
			BigDecimal consumeBalance = us.getConsumeBalance();
			consumeBalance = consumeBalance.add(price);
			int totalConsumeBalance = consumeBalance.intValue();
			
			// 0-普通会员，1-银卡，2-金卡，3-铂金卡
			int level = us.getRankLevel();
			
			if (totalConsumeBalance < 5000) {
				if(level < 1) {
					us.setRankLevel(0);
				}
			} else if (totalConsumeBalance >= 5000 && totalConsumeBalance < 20000) {
				if(level < 2) {
					us.setRankLevel(1);
				}
			} else if (totalConsumeBalance >= 20000 && totalConsumeBalance < 50000) {
				if(level < 3) {
					us.setRankLevel(2);
				}
			} else {
				us.setRankLevel(3);
			}
			
			us.setConsumeBalance(consumeBalance);
			us.setBalance(balance);
			userDao.saveAndFlush(us);
			
			//新增积分记录
			UserScoreRecord scoreRecord = new UserScoreRecord();
			scoreRecord.setId(scoreRecord.getId());
			scoreRecord.setUid(uid);
			scoreRecord.setGoodsId(0);
			scoreRecord.setGoodsName("购买积分" + score);
			scoreRecord.setType(UserScoreRecord.TYPE_BUY);
			scoreRecord.setScore(Integer.parseInt(kryCurrentPoints));
			scoreRecord.setAvailableScore(score);
			scoreRecord.setTpOrderId(tpOrderId);
			scoreRecord.setOrderId(orderId);
			scoreRecord.setCreateTime(new Date(System.currentTimeMillis()));
			scoreRecordDao.save(scoreRecord);
		} catch (Exception e) {
			log.error("【积分购买异常】{}",e);
			throw AMPException.getException("系统异常!");
		}
		resp.setReturnCode(Constant.SUCCESS);
		resp.setErrorMessage("积分购买成功!");
		return resp;
	}
	
	/**
	 * 下级消费返回积分
	 * @param openid
	 * @param score
	 * @return
	 */
	@Transactional
	public BaseResponse returnScore(int chidid,int parentid,int score) {
		BaseResponse resp = new BaseResponse();
		try {
			log.info("【会员下级返回积分传入参数】:parentid= {},score= {}",parentid,score);
			User us = userDao.findById(parentid);
			if(us == null) {
				resp.setReturnCode(Constant.FAIL);
				resp.setErrorMessage("上级用户不存在!");
				log.error("【该上级用户信息不存在】{}:parentid = ",parentid);
				return resp;
			}
			int uid = us.getId();
			String customerId = us.getCustomerId();
			
			//百分之10返回给上级
			score = score / 10;
			String remark = String.format("%s下级消费返回积分",customerId);
			ScoreResp sresp = keruyunService.addScore(customerId, score,remark);
			String sreturnCode = sresp.getReturnCode();
			if(!Constant.SUCCESS.equals(sreturnCode)) {
				resp.setReturnCode(Constant.RECHARGE_ERROR);
				resp.setErrorMessage("该用户积分异常，请联系客服!");
				log.error("【用户积分增加异常】:sresp = {}",JSON.toJSONString(sresp));
				return resp;
			}
			String kryCurrentPoints = sresp.getCurrentPoints();
			if(!StringUtil.isEmpty(kryCurrentPoints)) {
				us.setScore(Integer.parseInt(kryCurrentPoints));
			}else {
				resp.setReturnCode(Constant.RECHARGE_ERROR);
				resp.setErrorMessage("该用户积分异常，请联系客服!");
				log.error("【用户积分增加异常】:sresp = {}",JSON.toJSONString(sresp));
				return resp;
			}
			
			userDao.saveAndFlush(us);
			//新增积分记录
			UserScoreRecord scoreRecord = new UserScoreRecord();
			scoreRecord.setId(scoreRecord.getId());
			scoreRecord.setUid(uid);
			scoreRecord.setFuid(chidid);
			scoreRecord.setGoodsId(0);
			scoreRecord.setGoodsName("下级消费返回积分" + score);
			scoreRecord.setType(UserScoreRecord.TYPE_RETURN);
			scoreRecord.setScore(Integer.parseInt(kryCurrentPoints));
			scoreRecord.setAvailableScore(score);
			scoreRecord.setCreateTime(new Date(System.currentTimeMillis()));
			scoreRecordDao.save(scoreRecord);
		} catch (Exception e) {
			log.error("【积分返回异常】{}",e);
			throw AMPException.getException("系统异常!");
		}
		resp.setReturnCode(Constant.SUCCESS);
		resp.setErrorMessage("下级消费积分返回成功!");
		return resp;
	}

	/**
	 * 本人赠送
	 * @param openid
	 * @param sendOpenid
	 * @param score
	 * @return
	 */
	@Transactional
	public BaseResponse sendScore(String openid,String sendOpenid,int score) {
		BaseResponse resp = new BaseResponse();
		try {
			log.info("【会员赠送积分传入参数】:openid= {},sendOpenid= {},score= {}",openid,sendOpenid,score);
			User sendUs = userDao.findByOpenid(sendOpenid);
			User us = userDao.findByOpenid(openid);
			if(sendUs == null) {
				resp.setReturnCode(Constant.FAIL);
				resp.setErrorMessage("赠送用户不存在!");
				log.error("【该赠送用户信息不存在】{}:sendOpenid ={} ",sendOpenid);
				return resp;
			}
			//本人的积分
			int uid = us.getId();
			int uScore = us.getScore();
			String uCustomerId = us.getCustomerId();
			
			if(uScore < score) {
				resp.setReturnCode(Constant.SCORE_NOTENOUGH);
				resp.setErrorMessage("用户积分不足!");
				log.error("【该用户积分不足】{}:openid = {}",openid);
				return resp;
			}
			
			//对方的积分
			int sUid = sendUs.getId();
//			int sScore = sendUs.getScore();
			String sCustomerId = sendUs.getCustomerId();
			
//			int nowMyScore = uScore - score;
//			int nowYouScore = sScore + score;
			
			//赠送的用户加积分
			String remark = String.format("%s赠送%s",uCustomerId,sCustomerId);
			ScoreResp sresp = keruyunService.addScore(sCustomerId, score,remark);
			String sreturnCode = sresp.getReturnCode();
			if(!Constant.SUCCESS.equals(sreturnCode)) {
				resp.setReturnCode(Constant.RECHARGE_ERROR);
				resp.setErrorMessage("该用户积分异常，请联系客服!");
				log.error("【用户积分增加异常】:sresp = {}",JSON.toJSONString(sresp));
				return resp;
			}
			String kryYouCurrentPoints = sresp.getCurrentPoints();
			if(!StringUtil.isEmpty(kryYouCurrentPoints)) {
				sendUs.setScore(Integer.parseInt(kryYouCurrentPoints));
			}else {
				resp.setReturnCode(Constant.RECHARGE_ERROR);
				resp.setErrorMessage("该用户积分异常，请联系客服!");
				log.error("【用户积分增加异常】:sresp = {}",JSON.toJSONString(sresp));
				return resp;
			}
			
			//本人减积分
			ScoreResp mySresp = keruyunService.cutScore(uCustomerId, score,remark);
			String mySreturnCode = mySresp.getReturnCode();
			if(!Constant.SUCCESS.equals(mySreturnCode)) {
				resp.setReturnCode(Constant.RECHARGE_ERROR);
				resp.setErrorMessage("该用户积分异常，请联系客服!");
				log.error("【用户积分扣减异常】:sresp = {}",JSON.toJSONString(mySresp));
				return resp;
			}
			String kryMyCurrentPoints = mySresp.getCurrentPoints();
			if(!StringUtil.isEmpty(kryMyCurrentPoints)) {
				us.setScore(Integer.parseInt(kryMyCurrentPoints));
			}else {
				resp.setReturnCode(Constant.RECHARGE_ERROR);
				resp.setErrorMessage("该用户积分异常，请联系客服!");
				log.error("【用户积分扣减异常】:sresp = {}",JSON.toJSONString(sresp));
				return resp;
			}
			
			userDao.saveAndFlush(us);
			userDao.saveAndFlush(sendUs);
			
			/**
			 * 新增本人的积分记录
			 */
			UserScoreRecord scoreRecord = new UserScoreRecord();
			scoreRecord.setId(scoreRecord.getId());
			scoreRecord.setUid(uid);
			scoreRecord.setFuid(sUid);
			scoreRecord.setGoodsId(0);
			scoreRecord.setGoodsName("赠送积分" + score);
			scoreRecord.setType(UserScoreRecord.TYPE_SEND);
			scoreRecord.setScore(Integer.parseInt(kryMyCurrentPoints));
			scoreRecord.setAvailableScore(score);
			scoreRecord.setCreateTime(new Date(System.currentTimeMillis()));
			scoreRecordDao.save(scoreRecord);
			//使用记录
			UserScoreUsedRecord userUsedRecord = new UserScoreUsedRecord();
			userUsedRecord.setId(userUsedRecord.getId());
			userUsedRecord.setUid(uid);
			userUsedRecord.setFuid(sUid);
			userUsedRecord.setGoodsId(0);
			userUsedRecord.setGoodsName("赠送积分" + score);
			userUsedRecord.setType(UserScoreRecord.TYPE_SEND);
			userUsedRecord.setScore(Integer.parseInt(kryMyCurrentPoints));
			userUsedRecord.setUsedScore(score);
			userUsedRecord.setCreateTime(new Date(System.currentTimeMillis()));
			scoreUsedRecordDao.save(userUsedRecord);
			/**
			 * 对方的积分记录
			 */
			UserScoreRecord youScoreRecord = new UserScoreRecord();
			youScoreRecord.setId(scoreRecord.getId());
			youScoreRecord.setUid(sUid);
			youScoreRecord.setFuid(uid);
			youScoreRecord.setGoodsId(0);
			youScoreRecord.setGoodsName("获赠积分" + score);
			youScoreRecord.setType(UserScoreRecord.TYPE_GET);
			youScoreRecord.setScore(Integer.parseInt(kryYouCurrentPoints));
			youScoreRecord.setAvailableScore(score);
			youScoreRecord.setCreateTime(new Date(System.currentTimeMillis()));
			scoreRecordDao.save(youScoreRecord);
		} catch (Exception e) {
			log.error("【积分返回异常】{}",e);
			throw AMPException.getException("系统异常!");
		}
		resp.setReturnCode(Constant.SUCCESS);
		resp.setErrorMessage("下级消费积分返回成功!");
		return resp;
	}
	
	
	
}
